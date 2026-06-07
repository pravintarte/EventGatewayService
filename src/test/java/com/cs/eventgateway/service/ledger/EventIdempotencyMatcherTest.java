package com.cs.eventgateway.service.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.cs.eventgateway.dto.event.EventType;
import com.cs.eventgateway.dto.event.TransactionEventRequest;
import com.cs.eventgateway.entity.EventRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventIdempotencyMatcherTest {

    private final EventMetadataJsonConverter metadataJsonConverter = mock(EventMetadataJsonConverter.class);
    private final EventIdempotencyMatcher matcher = new EventIdempotencyMatcher(metadataJsonConverter);

    @Test
    void isExactDuplicate_whenAllBusinessFieldsAndMetadataMatch_returnsTrue() {
        EventRecord existing = existingRecord();
        TransactionEventRequest request = matchingRequest();
        when(metadataJsonConverter.structurallyEquals(existing.getMetadataJson(), request.metadata()))
                .thenReturn(true);

        assertThat(matcher.isExactDuplicate(existing, request)).isTrue();

        verify(metadataJsonConverter).structurallyEquals(existing.getMetadataJson(), request.metadata());
    }

    @Test
    void isExactDuplicate_whenAccountIdDiffers_returnsFalseWithoutCheckingMetadata() {
        EventRecord existing = existingRecord();
        TransactionEventRequest request = requestWith(accountId("acct-999"));

        assertThat(matcher.isExactDuplicate(existing, request)).isFalse();

        verify(metadataJsonConverter, never()).structurallyEquals(existing.getMetadataJson(), request.metadata());
    }

    @Test
    void isExactDuplicate_whenTypeDiffers_returnsFalseWithoutCheckingMetadata() {
        EventRecord existing = existingRecord();
        TransactionEventRequest request = requestWith(type(EventType.DEBIT));

        assertThat(matcher.isExactDuplicate(existing, request)).isFalse();

        verify(metadataJsonConverter, never()).structurallyEquals(existing.getMetadataJson(), request.metadata());
    }

    @Test
    void isExactDuplicate_whenAmountDiffers_returnsFalseWithoutCheckingMetadata() {
        EventRecord existing = existingRecord();
        TransactionEventRequest request = requestWith(amount("151.00"));

        assertThat(matcher.isExactDuplicate(existing, request)).isFalse();

        verify(metadataJsonConverter, never()).structurallyEquals(existing.getMetadataJson(), request.metadata());
    }

    @Test
    void isExactDuplicate_whenCurrencyDiffers_returnsFalseWithoutCheckingMetadata() {
        EventRecord existing = existingRecord();
        TransactionEventRequest request = requestWith(currency("EUR"));

        assertThat(matcher.isExactDuplicate(existing, request)).isFalse();

        verify(metadataJsonConverter, never()).structurallyEquals(existing.getMetadataJson(), request.metadata());
    }

    @Test
    void isExactDuplicate_whenEventTimestampDiffers_returnsFalseWithoutCheckingMetadata() {
        EventRecord existing = existingRecord();
        TransactionEventRequest request = requestWith(eventTimestamp("2026-05-15T14:03:11Z"));

        assertThat(matcher.isExactDuplicate(existing, request)).isFalse();

        verify(metadataJsonConverter, never()).structurallyEquals(existing.getMetadataJson(), request.metadata());
    }

    @Test
    void isExactDuplicate_whenMetadataDiffers_returnsFalse() {
        EventRecord existing = existingRecord();
        TransactionEventRequest request = matchingRequest();
        when(metadataJsonConverter.structurallyEquals(existing.getMetadataJson(), request.metadata()))
                .thenReturn(false);

        assertThat(matcher.isExactDuplicate(existing, request)).isFalse();

        verify(metadataJsonConverter).structurallyEquals(existing.getMetadataJson(), request.metadata());
    }

    private EventRecord existingRecord() {
        EventRecord eventRecord = new EventRecord();
        eventRecord.setEventId(UUID.fromString("9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2"));
        eventRecord.setAccountId("acct-123");
        eventRecord.setType(EventType.CREDIT);
        eventRecord.setAmount(new BigDecimal("150.00"));
        eventRecord.setCurrency("USD");
        eventRecord.setEventTimestamp(Instant.parse("2026-05-15T14:02:11Z"));
        eventRecord.setMetadataJson("{\"source\":\"mainframe-batch\"}");
        return eventRecord;
    }

    private TransactionEventRequest matchingRequest() {
        return requestWith();
    }

    private TransactionEventRequest requestWith(RequestOverride... overrides) {
        RequestValues values = new RequestValues(
                UUID.fromString("9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2"),
                "acct-123",
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                Map.of("source", "mainframe-batch")
        );
        for (RequestOverride override : overrides) {
            override.apply(values);
        }
        return new TransactionEventRequest(
                values.eventId,
                values.accountId,
                values.type,
                values.amount,
                values.currency,
                values.eventTimestamp,
                values.metadata
        );
    }

    private RequestOverride accountId(String accountId) {
        return values -> values.accountId = accountId;
    }

    private RequestOverride type(EventType type) {
        return values -> values.type = type;
    }

    private RequestOverride amount(String amount) {
        return values -> values.amount = new BigDecimal(amount);
    }

    private RequestOverride currency(String currency) {
        return values -> values.currency = currency;
    }

    private RequestOverride eventTimestamp(String eventTimestamp) {
        return values -> values.eventTimestamp = Instant.parse(eventTimestamp);
    }

    @FunctionalInterface
    private interface RequestOverride {
        void apply(RequestValues values);
    }

    private static final class RequestValues {
        private UUID eventId;
        private String accountId;
        private EventType type;
        private BigDecimal amount;
        private String currency;
        private Instant eventTimestamp;
        private Map<String, Object> metadata;

        private RequestValues(
                UUID eventId,
                String accountId,
                EventType type,
                BigDecimal amount,
                String currency,
                Instant eventTimestamp,
                Map<String, Object> metadata
        ) {
            this.eventId = eventId;
            this.accountId = accountId;
            this.type = type;
            this.amount = amount;
            this.currency = currency;
            this.eventTimestamp = eventTimestamp;
            this.metadata = metadata;
        }
    }
}
