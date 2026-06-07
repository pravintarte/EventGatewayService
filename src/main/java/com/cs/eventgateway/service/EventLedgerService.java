package com.cs.eventgateway.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.cs.eventgateway.client.AccountApplyResult;
import com.cs.eventgateway.client.AccountServiceClient;
import com.cs.eventgateway.dto.ApiCodes;
import com.cs.eventgateway.dto.event.EventResponse;
import com.cs.eventgateway.dto.event.EventSubmissionResponse;
import com.cs.eventgateway.dto.event.TransactionEventRequest;
import com.cs.eventgateway.entity.ApplyStatus;
import com.cs.eventgateway.entity.EventRecord;
import com.cs.eventgateway.exception.DuplicateEventConflictException;
import com.cs.eventgateway.exception.EventNotFoundException;
import com.cs.eventgateway.repository.EventRecordRepository;
import com.cs.eventgateway.service.ledger.EventIdempotencyMatcher;
import com.cs.eventgateway.service.ledger.EventRecordMapper;
import com.cs.eventgateway.service.ledger.RetryBackoffPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Coordinates the core Event Ledger workflow for the gateway.
 *
 * <p>This service owns the gateway-side correctness rules that matter most for
 * financial event ingestion: idempotency, out-of-order event storage, and
 * graceful handling of Account Service outages. The gateway stores the event
 * before calling Account Service so that an unavailable downstream service does
 * not cause the inbound event to be lost. Once stored, the event id becomes the
 * idempotency key used to identify retries from upstream systems.</p>
 *
 * <p>For duplicate delivery, the service distinguishes between an exact retry
 * and an unsafe id reuse. If the same {@code eventId} arrives with an identical
 * payload, the existing ledger record is returned and Account Service is not
 * called again. If the same {@code eventId} arrives with any business payload
 * difference, the request is rejected as a conflict because applying it would
 * make the ledger ambiguous.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventLedgerService {

    private static final EnumSet<ApplyStatus> RETRYABLE_APPLY_STATUSES =
            EnumSet.of(ApplyStatus.PENDING, ApplyStatus.APPLY_FAILED);

    private final EventRecordRepository eventRecordRepository;
    private final AccountServiceClient accountServiceClient;
    private final EventRecordMapper eventRecordMapper;
    private final EventIdempotencyMatcher eventIdempotencyMatcher;
    private final RetryBackoffPolicy retryBackoffPolicy;
    private final Clock clock;

    /**
     * Accepts an upstream transaction event and enforces gateway idempotency.
     *
     * <p>The method first checks whether a ledger record already exists for
     * {@link TransactionEventRequest#eventId()}. That lookup is the primary
     * idempotency check. If a record is found, the request is handled as a
     * potential duplicate by {@link #existingDuplicateResponse(EventRecord,
     * TransactionEventRequest)}. If no record is found, the service attempts to
     * persist a new ledger record and then apply it to Account Service.</p>
     *
     * <p>The create path also handles a concurrent race where two threads submit
     * the same new event id at nearly the same time. One thread wins the insert;
     * the other receives a database uniqueness violation and then re-reads the
     * winning row as a duplicate. This is why {@code event_id} is the primary
     * key in the schema.</p>
     *
     * @param request public event request
     * @return submission response describing whether the event was newly stored,
     *         already present as a duplicate, or stored but not applied downstream
     */
    public EventSubmissionResponse submit(TransactionEventRequest request) {
        log.info("Submitting event eventId={} accountId={} type={} eventTimestamp={}",
                request.eventId(), request.accountId(), request.type(), request.eventTimestamp());
        return eventRecordRepository.findById(request.eventId())
                .map(existing -> {
                    log.info("Existing ledger event found for submission eventId={} accountId={} status={}",
                            existing.getEventId(), existing.getAccountId(), existing.getApplyStatus());
                    return existingDuplicateResponse(existing, request);
                })
                .orElseGet(() -> createAndApply(request));
    }

    /**
     * Retrieves a single ledger event by its upstream event id.
     *
     * <p>This is a read-only query over the gateway ledger. It does not call
     * Account Service, because the gateway is the source of truth for received
     * event records and their current apply status.</p>
     *
     * @param eventId event id
     * @return event response
     * @throws EventNotFoundException when no ledger record exists for the event id
     */
    public EventResponse getEvent(UUID eventId) {
        log.info("Looking up ledger event eventId={}", eventId);
        return eventRecordRepository.findById(eventId)
                .map((eventRecord) -> {
                    log.info("Ledger event retrieved eventId={} accountId={} status={}",
                            eventRecord.getEventId(),
                            eventRecord.getAccountId(),
                            eventRecord.getApplyStatus());
                    return eventRecordMapper.toEventResponse(eventRecord);
                })
                .orElseThrow(() -> {
                    log.warn("Ledger event not found eventId={}", eventId);
                    return new EventNotFoundException(eventId);
                });
    }

    /**
     * Lists all ledger events for one account in business-time order.
     *
     * <p>Upstream systems can deliver events out of order, so this method sorts
     * by the original {@code eventTimestamp} rather than by gateway arrival time.
     * The repository adds deterministic tie-breakers so multiple events with the
     * same timestamp still produce stable output.</p>
     *
     * @param accountId account id
     * @return event responses ordered by event timestamp, creation time, and event id
     */
    public List<EventResponse> listEventsForAccount(String accountId) {
        log.info("Listing ledger events accountId={}", accountId);
        List<EventResponse> events = eventRecordRepository.findByAccountIdOrderByEventTimestampAscCreatedAtAscEventIdAsc(accountId)
                .stream()
                .map(eventRecordMapper::toEventResponse)
                .toList();
        log.info("Listed ledger events accountId={} count={}", accountId, events.size());
        return events;
    }

    /**
     * Retries Account Service application for due pending or failed events.
     *
     * <p>This method is safe to call repeatedly because both the gateway ledger
     * and Account Service use the event id as an idempotency key. Only transient
     * failures are retried; permanent Account Service rejections move the event
     * to {@link ApplyStatus#APPLY_REJECTED}.</p>
     *
     * @return number of events attempted
     */
    public int retryDueEvents() {
        Instant now = Instant.now(clock);
        List<EventRecord> dueEvents =
                eventRecordRepository.findTop100ByApplyStatusInAndNextApplyAttemptAtLessThanEqualOrderByUpdatedAtAsc(
                        RETRYABLE_APPLY_STATUSES,
                        now
                );
        if (dueEvents.isEmpty()) {
            log.debug("No due Account Service apply retries found");
            return 0;
        }

        log.info("Retrying {} due Account Service apply event(s)", dueEvents.size());
        for (EventRecord eventRecord : dueEvents) {
            try {
                EventRecord updated = applyAndSave(eventRecord);
                log.info("Retried event {} accountId={} status={} attempts={}",
                        updated.getEventId(),
                        updated.getAccountId(),
                        updated.getApplyStatus(),
                        updated.getApplyAttemptCount());
            } catch (RuntimeException ex) {
                log.error("Unexpected retry failure for event {}", eventRecord.getEventId(), ex);
            }
        }
        return dueEvents.size();
    }

    /**
     * Persists a new ledger record and attempts to apply it to Account Service.
     *
     * <p>The gateway intentionally saves the event before the downstream call.
     * This guarantees that, even if Account Service is down, the event remains
     * visible in the ledger with {@link ApplyStatus#APPLY_FAILED}. A later
     * operational process can inspect or retry those failed records without
     * losing the original inbound event.</p>
     *
     * <p>If another request inserts the same {@code eventId} between the initial
     * lookup and this method's insert, the database primary key rejects this
     * insert. In that case the method reloads the existing record and applies the
     * duplicate-payload rules rather than calling Account Service again.</p>
     *
     * @param request validated event submission request
     * @return response for a new event or a duplicate discovered during a race
     */
    private EventSubmissionResponse createAndApply(TransactionEventRequest request) {
        log.info("Creating new ledger event eventId={} accountId={} type={} amount={} currency={}",
                request.eventId(), request.accountId(), request.type(), request.amount(), request.currency());
        EventRecord eventRecord = eventRecordMapper.newPendingRecord(request);
        EventRecord saved;

        try {
            saved = eventRecordRepository.saveAndFlush(eventRecord);
            log.info("Ledger event persisted eventId={} accountId={} status={}",
                    saved.getEventId(), saved.getAccountId(), saved.getApplyStatus());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate insert detected eventId={} accountId={}",
                    request.eventId(), request.accountId());
            EventRecord existing = eventRecordRepository.findById(request.eventId())
                    .orElseThrow(() -> ex);
            return existingDuplicateResponse(existing, request);
        }

        EventRecord updated = applyAndSave(saved);
        log.info("New ledger event processing completed eventId={} accountId={} status={} attempts={}",
                updated.getEventId(),
                updated.getAccountId(),
                updated.getApplyStatus(),
                updated.getApplyAttemptCount());
        return eventRecordMapper.toSubmissionResponse(
                updated,
                false,
                updated.getApplyStatus() == ApplyStatus.APPLIED
                        ? ApiCodes.EVENT_ACCEPTED_AND_APPLIED
                        : updated.getApplyStatus() == ApplyStatus.APPLY_REJECTED
                                ? ApiCodes.EVENT_ACCEPTED_ACCOUNT_APPLY_REJECTED
                                : ApiCodes.EVENT_ACCEPTED_ACCOUNT_SERVICE_UNAVAILABLE
        );
    }

    private EventRecord applyAndSave(EventRecord eventRecord) {
        log.info("Applying ledger event to Account Service eventId={} accountId={} currentStatus={} nextAttempt={}",
                eventRecord.getEventId(),
                eventRecord.getAccountId(),
                eventRecord.getApplyStatus(),
                eventRecord.getNextApplyAttemptAt());
        AccountApplyResult applyResult = accountServiceClient.applyTransaction(
                eventRecord.getAccountId(),
                eventRecordMapper.toAccountTransactionRequest(eventRecord)
        );

        Instant now = Instant.now(clock);
        eventRecord.setApplyAttemptCount(eventRecord.getApplyAttemptCount() + 1);
        eventRecord.setLastApplyAttemptAt(now);
        eventRecord.setUpdatedAt(now);

        if (applyResult.successful()) {
            eventRecord.setApplyStatus(ApplyStatus.APPLIED);
            eventRecord.setAccountServiceError(null);
            eventRecord.setNextApplyAttemptAt(null);
            log.info("Account Service apply succeeded eventId={} accountId={} attempt={}",
                    eventRecord.getEventId(), eventRecord.getAccountId(), eventRecord.getApplyAttemptCount());
        } else if (applyResult.retryable()) {
            eventRecord.setApplyStatus(ApplyStatus.APPLY_FAILED);
            eventRecord.setAccountServiceError(trimError(applyResult.errorMessage()));
            eventRecord.setNextApplyAttemptAt(now.plus(retryBackoffPolicy.nextBackoff(eventRecord.getApplyAttemptCount())));
            log.warn("Account Service apply failed with retryable error eventId={} accountId={} attempt={} nextAttemptAt={} error={}",
                    eventRecord.getEventId(),
                    eventRecord.getAccountId(),
                    eventRecord.getApplyAttemptCount(),
                    eventRecord.getNextApplyAttemptAt(),
                    eventRecord.getAccountServiceError());
        } else {
            eventRecord.setApplyStatus(ApplyStatus.APPLY_REJECTED);
            eventRecord.setAccountServiceError(trimError(applyResult.errorMessage()));
            eventRecord.setNextApplyAttemptAt(null);
            log.warn("Account Service rejected ledger event eventId={} accountId={} attempt={} error={}",
                    eventRecord.getEventId(),
                    eventRecord.getAccountId(),
                    eventRecord.getApplyAttemptCount(),
                    eventRecord.getAccountServiceError());
        }

        EventRecord saved = eventRecordRepository.save(eventRecord);
        log.debug("Ledger event apply state saved eventId={} accountId={} status={} attempts={}",
                saved.getEventId(), saved.getAccountId(), saved.getApplyStatus(), saved.getApplyAttemptCount());
        return saved;
    }

    /**
     * Builds the response for a request whose {@code eventId} already exists.
     *
     * <p>The caller reaches this method only after finding a row by
     * {@code request.eventId()}, so the id match has already been established by
     * the repository lookup. This method compares the rest of the business
     * payload to distinguish a safe idempotent retry from a dangerous id reuse.
     * A safe retry returns the existing record and marks the response as
     * duplicate. A conflicting retry throws
     * {@link DuplicateEventConflictException}, which the controller advice maps
     * to {@code 409 Conflict}.</p>
     *
     * @param existing existing ledger record loaded by event id
     * @param request inbound request using the same event id
     * @return existing event response with the duplicate flag set
     */
    private EventSubmissionResponse existingDuplicateResponse(
            EventRecord existing,
            TransactionEventRequest request
    ) {
        if (!eventIdempotencyMatcher.isExactDuplicate(existing, request)) {
            log.warn("Duplicate event id conflict eventId={} existingAccountId={} requestAccountId={}",
                    request.eventId(), existing.getAccountId(), request.accountId());
            throw new DuplicateEventConflictException(request.eventId());
        }

        log.info("Exact duplicate event ignored eventId={} accountId={} status={}",
                existing.getEventId(), existing.getAccountId(), existing.getApplyStatus());
        return eventRecordMapper.toSubmissionResponse(existing, true, ApiCodes.DUPLICATE_EVENT_IGNORED);
    }

    /**
     * Normalizes Account Service error text before storing it in the ledger.
     *
     * <p>The database column has a bounded length, so long downstream exception
     * messages are truncated. Blank downstream errors are replaced with a stable
     * generic message that still communicates the operational state clearly.</p>
     *
     * @param errorMessage nullable error text returned from Account Service client
     * @return non-blank error message no longer than the database column
     */
    private String trimError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Account Service unavailable";
        }
        if (errorMessage.length() > 1024) {
            log.debug("Truncating Account Service error message length={}", errorMessage.length());
        }
        return errorMessage.length() > 1024 ? errorMessage.substring(0, 1024) : errorMessage;
    }

}
