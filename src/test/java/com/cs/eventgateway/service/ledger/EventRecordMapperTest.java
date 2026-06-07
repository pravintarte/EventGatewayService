package com.cs.eventgateway.service.ledger;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import com.cs.eventgateway.dto.event.AccountTransactionRequest;
import com.cs.eventgateway.dto.event.EventResponse;
import com.cs.eventgateway.dto.event.EventSubmissionResponse;
import com.cs.eventgateway.dto.event.EventType;
import com.cs.eventgateway.dto.event.TransactionEventRequest;
import com.cs.eventgateway.entity.ApplyStatus;
import com.cs.eventgateway.entity.EventRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class EventRecordMapperTest {

    private static final Instant NOW = Instant.parse("2026-06-06T21:00:00Z");
    private static final Instant EVENT_TIMESTAMP = Instant.parse("2026-05-15T14:02:11Z");
    private static final UUID EVENT_ID = UUID.fromString("9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2");

    private final EventMetadataJsonConverter metadataJsonConverter =
            new EventMetadataJsonConverter(new ObjectMapper());
    private final EventRecordMapper mapper = new EventRecordMapper(
            Clock.fixed(NOW, ZoneOffset.UTC),
            metadataJsonConverter
    );

    @Test
    void newPendingRecord_mapsRequestToPendingLedgerEntity() {
        TransactionEventRequest request = request();

        EventRecord eventRecord = mapper.newPendingRecord(request);

        assertThat(eventRecord.getEventId()).isEqualTo(EVENT_ID);
        assertThat(eventRecord.getAccountId()).isEqualTo("acct-123");
        assertThat(eventRecord.getType()).isEqualTo(EventType.CREDIT);
        assertThat(eventRecord.getAmount()).isEqualByComparingTo("150.00");
        assertThat(eventRecord.getCurrency()).isEqualTo("USD");
        assertThat(eventRecord.getEventTimestamp()).isEqualTo(EVENT_TIMESTAMP);
        assertThat(metadataJsonConverter.structurallyEquals(eventRecord.getMetadataJson(), request.metadata())).isTrue();
        assertThat(eventRecord.getApplyStatus()).isEqualTo(ApplyStatus.PENDING);
        assertThat(eventRecord.getApplyAttemptCount()).isZero();
        assertThat(eventRecord.getNextApplyAttemptAt()).isEqualTo(NOW);
        assertThat(eventRecord.getCreatedAt()).isEqualTo(NOW);
        assertThat(eventRecord.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void toAccountTransactionRequest_mapsLedgerEntityToDownstreamRequest() {
        EventRecord eventRecord = persistedRecord();

        AccountTransactionRequest request = mapper.toAccountTransactionRequest(eventRecord);

        assertThat(request.eventId()).isEqualTo(EVENT_ID);
        assertThat(request.type()).isEqualTo(EventType.CREDIT);
        assertThat(request.amount()).isEqualByComparingTo("150.00");
        assertThat(request.currency()).isEqualTo("USD");
        assertThat(request.eventTimestamp()).isEqualTo(EVENT_TIMESTAMP);
        assertThat(request.metadata()).containsEntry("source", "mainframe-batch");
    }

    @Test
    void toEventResponse_mapsLedgerEntityToPublicReadResponse() {
        EventRecord eventRecord = persistedRecord();

        EventResponse response = mapper.toEventResponse(eventRecord);

        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.accountId()).isEqualTo("acct-123");
        assertThat(response.type()).isEqualTo(EventType.CREDIT);
        assertThat(response.amount()).isEqualByComparingTo("150.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.eventTimestamp()).isEqualTo(EVENT_TIMESTAMP);
        assertThat(response.metadata()).containsEntry("source", "mainframe-batch");
        assertThat(response.status()).isEqualTo(ApplyStatus.APPLY_FAILED);
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-06-06T20:59:00Z"));
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThat(response.accountServiceError()).isEqualTo("Connection refused");
    }

    @Test
    void toSubmissionResponse_mapsLedgerEntityToPublicSubmissionResponse() {
        EventRecord eventRecord = persistedRecord();

        EventSubmissionResponse response = mapper.toSubmissionResponse(eventRecord, true, "DUPLICATE_EVENT_IGNORED");

        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.accountId()).isEqualTo("acct-123");
        assertThat(response.type()).isEqualTo(EventType.CREDIT);
        assertThat(response.amount()).isEqualByComparingTo("150.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.eventTimestamp()).isEqualTo(EVENT_TIMESTAMP);
        assertThat(response.metadata()).containsEntry("source", "mainframe-batch");
        assertThat(response.status()).isEqualTo(ApplyStatus.APPLY_FAILED);
        assertThat(response.duplicate()).isTrue();
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-06-06T20:59:00Z"));
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThat(response.message()).isEqualTo("DUPLICATE_EVENT_IGNORED");
        assertThat(response.accountServiceError()).isEqualTo("Connection refused");
    }

    private TransactionEventRequest request() {
        return new TransactionEventRequest(
                EVENT_ID,
                "acct-123",
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                EVENT_TIMESTAMP,
                Map.of("source", "mainframe-batch")
        );
    }

    private EventRecord persistedRecord() {
        EventRecord eventRecord = new EventRecord();
        eventRecord.setEventId(EVENT_ID);
        eventRecord.setAccountId("acct-123");
        eventRecord.setType(EventType.CREDIT);
        eventRecord.setAmount(new BigDecimal("150.00"));
        eventRecord.setCurrency("USD");
        eventRecord.setEventTimestamp(EVENT_TIMESTAMP);
        eventRecord.setMetadataJson("{\"source\":\"mainframe-batch\"}");
        eventRecord.setApplyStatus(ApplyStatus.APPLY_FAILED);
        eventRecord.setApplyAttemptCount(1);
        eventRecord.setCreatedAt(Instant.parse("2026-06-06T20:59:00Z"));
        eventRecord.setUpdatedAt(NOW);
        eventRecord.setAccountServiceError("Connection refused");
        return eventRecord;
    }
}
