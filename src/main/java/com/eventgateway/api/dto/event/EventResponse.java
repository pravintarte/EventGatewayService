package com.eventgateway.api.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.eventgateway.api.entity.ApplyStatus;

/**
 * Public representation of a persisted ledger event.
 *
 * @param eventId unique event identifier
 * @param accountId account affected by the event
 * @param type CREDIT or DEBIT
 * @param amount positive transaction amount
 * @param currency ISO-style currency code
 * @param eventTimestamp original event occurrence time
 * @param metadata optional upstream context
 * @param status current Account Service application status
 * @param createdAt server-side creation time
 * @param updatedAt last server-side update time
 * @param accountServiceError most recent Account Service failure reason, if any
 */
public record EventResponse(
        String eventId,
        String accountId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Map<String, Object> metadata,
        ApplyStatus status,
        Instant createdAt,
        Instant updatedAt,
        String accountServiceError
) {
}
