package com.eventgateway.api.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.eventgateway.api.client.AccountApplyResult;
import com.eventgateway.api.client.AccountServiceClient;
import com.eventgateway.api.dto.event.AccountTransactionRequest;
import com.eventgateway.api.dto.event.EventResponse;
import com.eventgateway.api.dto.event.EventSubmissionResponse;
import com.eventgateway.api.dto.event.TransactionEventRequest;
import com.eventgateway.api.entity.ApplyStatus;
import com.eventgateway.api.entity.EventRecord;
import com.eventgateway.api.exception.DuplicateEventConflictException;
import com.eventgateway.api.exception.EventNotFoundException;
import com.eventgateway.api.repository.EventRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final EventRecordRepository eventRecordRepository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;
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
        return eventRecordRepository.findById(request.eventId())
                .map(existing -> existingDuplicateResponse(existing, request))
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
    public EventResponse getEvent(String eventId) {
        return eventRecordRepository.findById(eventId)
                .map(this::toEventResponse)
                .orElseThrow(() -> new EventNotFoundException(eventId));
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
        return eventRecordRepository.findByAccountIdOrderByEventTimestampAscCreatedAtAscEventIdAsc(accountId)
                .stream()
                .map(this::toEventResponse)
                .toList();
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
        EventRecord eventRecord = newRecord(request);
        EventRecord saved;

        try {
            saved = eventRecordRepository.saveAndFlush(eventRecord);
        } catch (DataIntegrityViolationException ex) {
            EventRecord existing = eventRecordRepository.findById(request.eventId())
                    .orElseThrow(() -> ex);
            return existingDuplicateResponse(existing, request);
        }

        AccountApplyResult applyResult = accountServiceClient.applyTransaction(
                request.accountId(),
                new AccountTransactionRequest(
                        request.eventId(),
                        request.type(),
                        request.amount(),
                        request.currency(),
                        request.eventTimestamp(),
                        safeMetadata(request.metadata())
                )
        );

        if (applyResult.successful()) {
            saved.setApplyStatus(ApplyStatus.APPLIED);
            saved.setAccountServiceError(null);
        } else {
            saved.setApplyStatus(ApplyStatus.APPLY_FAILED);
            saved.setAccountServiceError(trimError(applyResult.errorMessage()));
        }
        saved.setUpdatedAt(Instant.now(clock));

        EventRecord updated = eventRecordRepository.save(saved);
        return toSubmissionResponse(
                updated,
                false,
                applyResult.successful()
                        ? "EVENT_ACCEPTED_AND_APPLIED"
                        : "EVENT_ACCEPTED_ACCOUNT_SERVICE_UNAVAILABLE"
        );
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
        if (!samePayload(existing, request)) {
            throw new DuplicateEventConflictException(request.eventId());
        }

        return toSubmissionResponse(existing, true, "DUPLICATE_EVENT_IGNORED");
    }

    /**
     * Converts a validated inbound request into a new pending JPA entity.
     *
     * <p>The new record starts in {@link ApplyStatus#PENDING} because it has
     * been accepted by the gateway but has not yet received an Account Service
     * result. The metadata map is serialized into JSON for storage while still
     * being exposed as a map in public DTOs.</p>
     *
     * @param request validated event submission request
     * @return unsaved event record ready for persistence
     */
    private EventRecord newRecord(TransactionEventRequest request) {
        Instant now = Instant.now(clock);
        EventRecord eventRecord = new EventRecord();
        eventRecord.setEventId(request.eventId());
        eventRecord.setAccountId(request.accountId());
        eventRecord.setType(request.type());
        eventRecord.setAmount(request.amount());
        eventRecord.setCurrency(request.currency());
        eventRecord.setEventTimestamp(request.eventTimestamp());
        eventRecord.setMetadataJson(writeMetadata(safeMetadata(request.metadata())));
        eventRecord.setApplyStatus(ApplyStatus.PENDING);
        eventRecord.setCreatedAt(now);
        eventRecord.setUpdatedAt(now);
        return eventRecord;
    }

    /**
     * Compares the business payload of an existing ledger record with a retry.
     *
     * <p>The {@code eventId} is deliberately not compared here because this
     * method is only invoked after the repository has loaded {@code existing}
     * using {@code request.eventId()}. Comparing it again would be redundant.
     * The method focuses on fields that determine whether the duplicate delivery
     * is semantically identical: account, transaction type, amount, currency,
     * event timestamp, and metadata.</p>
     *
     * @param existing ledger row already matched by event id
     * @param request inbound request carrying the same event id
     * @return true when the non-id payload is equivalent and safe to treat as a duplicate
     */
    private boolean samePayload(EventRecord existing, TransactionEventRequest request) {
        return Objects.equals(existing.getAccountId(), request.accountId())
                && Objects.equals(existing.getType(), request.type())
                && existing.getAmount().compareTo(request.amount()) == 0
                && Objects.equals(existing.getCurrency(), request.currency())
                && Objects.equals(existing.getEventTimestamp(), request.eventTimestamp())
                && sameMetadata(existing.getMetadataJson(), safeMetadata(request.metadata()));
    }

    /**
     * Compares stored metadata JSON with request metadata using JSON structure.
     *
     * <p>Metadata maps can be serialized with different key ordering, so this
     * method parses both sides into Jackson tree nodes before comparing them.
     * That prevents a retry from being incorrectly rejected just because JSON
     * object fields were written in a different order.</p>
     *
     * @param existingMetadataJson metadata JSON stored in the ledger row
     * @param requestMetadata metadata map from the inbound request
     * @return true when both metadata values represent the same JSON object
     */
    private boolean sameMetadata(String existingMetadataJson, Map<String, Object> requestMetadata) {
        try {
            JsonNode existing = objectMapper.readTree(
                    existingMetadataJson == null || existingMetadataJson.isBlank() ? "{}" : existingMetadataJson
            );
            JsonNode requested = objectMapper.valueToTree(requestMetadata);
            return Objects.equals(existing, requested);
        } catch (JacksonException ex) {
            log.warn("Unable to compare metadata JSON for idempotency", ex);
            return false;
        }
    }

    /**
     * Converts a persisted ledger entity into the public read response DTO.
     *
     * <p>The gateway stores metadata as JSON but returns it as a map so clients
     * receive the same logical structure they submitted. The apply status and
     * Account Service error fields expose whether the downstream account update
     * succeeded or needs operational attention.</p>
     *
     * @param eventRecord persisted ledger entity
     * @return public event response
     */
    private EventResponse toEventResponse(EventRecord eventRecord) {
        return new EventResponse(
                eventRecord.getEventId(),
                eventRecord.getAccountId(),
                eventRecord.getType(),
                eventRecord.getAmount(),
                eventRecord.getCurrency(),
                eventRecord.getEventTimestamp(),
                readMetadata(eventRecord.getMetadataJson()),
                eventRecord.getApplyStatus(),
                eventRecord.getCreatedAt(),
                eventRecord.getUpdatedAt(),
                eventRecord.getAccountServiceError()
        );
    }

    /**
     * Converts a persisted ledger entity into the public submission response.
     *
     * <p>This response adds submission-specific context on top of the event
     * fields: whether the request was a duplicate and a short message describing
     * the processing outcome. Controllers wrap this DTO in the standard API
     * response envelope with a stable response code.</p>
     *
     * @param eventRecord persisted ledger entity
     * @param duplicate true when the caller submitted an exact duplicate event
     * @param message concise processing outcome used by API clients and tests
     * @return public event submission response
     */
    private EventSubmissionResponse toSubmissionResponse(
            EventRecord eventRecord,
            boolean duplicate,
            String message
    ) {
        return new EventSubmissionResponse(
                eventRecord.getEventId(),
                eventRecord.getAccountId(),
                eventRecord.getType(),
                eventRecord.getAmount(),
                eventRecord.getCurrency(),
                eventRecord.getEventTimestamp(),
                readMetadata(eventRecord.getMetadataJson()),
                eventRecord.getApplyStatus(),
                duplicate,
                eventRecord.getCreatedAt(),
                eventRecord.getUpdatedAt(),
                message,
                eventRecord.getAccountServiceError()
        );
    }

    /**
     * Serializes metadata into JSON for database storage.
     *
     * <p>Request validation ensures metadata is a JSON object at the API
     * boundary, but serialization can still fail if an unsupported object type
     * reaches this service. In that case the method raises an
     * {@link IllegalArgumentException}, which is treated as an application error
     * instead of silently storing a corrupted metadata value.</p>
     *
     * @param metadata metadata map to serialize
     * @return JSON string stored in the ledger table
     */
    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("metadata must be JSON serializable", ex);
        }
    }

    /**
     * Deserializes stored metadata JSON for API responses and comparisons.
     *
     * <p>A missing or blank metadata value is treated as an empty map because
     * metadata is optional in the public API. If stored JSON cannot be parsed,
     * the service logs the issue and returns an empty map to avoid breaking
     * unrelated event reads.</p>
     *
     * @param metadataJson JSON stored in the ledger table
     * @return metadata map suitable for response DTOs
     */
    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(metadataJson, METADATA_TYPE);
        } catch (JacksonException ex) {
            log.warn("Unable to read stored metadata JSON", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Normalizes nullable request metadata to an immutable empty map.
     *
     * <p>Using a single empty-map representation simplifies JSON comparison and
     * response serialization. It also makes requests that omit metadata compare
     * equal to requests that send an empty metadata object.</p>
     *
     * @param metadata nullable metadata map from the request
     * @return original metadata when present, otherwise an empty map
     */
    private Map<String, Object> safeMetadata(Map<String, Object> metadata) {
        return metadata == null ? Collections.emptyMap() : metadata;
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
        return errorMessage.length() > 1024 ? errorMessage.substring(0, 1024) : errorMessage;
    }
}
