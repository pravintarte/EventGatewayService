package com.cs.eventgateway.service.ledger;

import java.time.Clock;
import java.time.Instant;

import com.cs.eventgateway.dto.event.AccountTransactionRequest;
import com.cs.eventgateway.dto.event.EventResponse;
import com.cs.eventgateway.dto.event.EventSubmissionResponse;
import com.cs.eventgateway.dto.event.TransactionEventRequest;
import com.cs.eventgateway.entity.ApplyStatus;
import com.cs.eventgateway.entity.EventRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps between public DTOs, Account Service DTOs, and ledger entities.
 */
@Component
@RequiredArgsConstructor
public class EventRecordMapper {

    private final Clock clock;
    private final EventMetadataJsonConverter metadataJsonConverter;

    /**
     * Converts a validated inbound request into a new pending ledger entity.
     *
     * @param request event submission request
     * @return unsaved pending ledger record
     */
    public EventRecord newPendingRecord(TransactionEventRequest request) {
        Instant now = Instant.now(clock);
        EventRecord eventRecord = new EventRecord();
        eventRecord.setEventId(request.eventId());
        eventRecord.setAccountId(request.accountId());
        eventRecord.setType(request.type());
        eventRecord.setAmount(request.amount());
        eventRecord.setCurrency(request.currency());
        eventRecord.setEventTimestamp(request.eventTimestamp());
        eventRecord.setMetadataJson(metadataJsonConverter.write(request.metadata()));
        eventRecord.setApplyStatus(ApplyStatus.PENDING);
        eventRecord.setApplyAttemptCount(0);
        eventRecord.setNextApplyAttemptAt(now);
        eventRecord.setCreatedAt(now);
        eventRecord.setUpdatedAt(now);
        return eventRecord;
    }

    /**
     * Builds the request sent to Account Service for transaction application.
     *
     * @param eventRecord persisted event record
     * @return Account Service transaction request
     */
    public AccountTransactionRequest toAccountTransactionRequest(EventRecord eventRecord) {
        return new AccountTransactionRequest(
                eventRecord.getEventId(),
                eventRecord.getType(),
                eventRecord.getAmount(),
                eventRecord.getCurrency(),
                eventRecord.getEventTimestamp(),
                metadataJsonConverter.read(eventRecord.getMetadataJson())
        );
    }

    /**
     * Builds the public read response for a persisted event.
     *
     * @param eventRecord persisted event record
     * @return public event response
     */
    public EventResponse toEventResponse(EventRecord eventRecord) {
        return new EventResponse(
                eventRecord.getEventId(),
                eventRecord.getAccountId(),
                eventRecord.getType(),
                eventRecord.getAmount(),
                eventRecord.getCurrency(),
                eventRecord.getEventTimestamp(),
                metadataJsonConverter.read(eventRecord.getMetadataJson()),
                eventRecord.getApplyStatus(),
                eventRecord.getCreatedAt(),
                eventRecord.getUpdatedAt(),
                eventRecord.getAccountServiceError()
        );
    }

    /**
     * Builds the public submission response for a persisted event.
     *
     * @param eventRecord persisted event record
     * @param duplicate true for exact idempotent duplicates
     * @param message stable submission outcome code/message
     * @return public submission response
     */
    public EventSubmissionResponse toSubmissionResponse(
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
                metadataJsonConverter.read(eventRecord.getMetadataJson()),
                eventRecord.getApplyStatus(),
                duplicate,
                eventRecord.getCreatedAt(),
                eventRecord.getUpdatedAt(),
                message,
                eventRecord.getAccountServiceError()
        );
    }
}
