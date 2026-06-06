package com.eventgateway.api.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.eventgateway.api.entity.ApplyStatus;

/**
 * Response returned after submitting a transaction event.
 *
 * @param eventId unique event identifier
 * @param accountId account affected by the event
 * @param type CREDIT or DEBIT
 * @param amount positive transaction amount
 * @param currency ISO-style currency code
 * @param eventTimestamp original event occurrence time
 * @param metadata optional upstream context
 * @param status current Account Service application status
 * @param duplicate true when the request was an idempotent duplicate
 * @param createdAt server-side creation time
 * @param updatedAt last server-side update time
 * @param message concise processing outcome
 * @param accountServiceError most recent Account Service failure reason, if any
 */
public record EventSubmissionResponse(
        String eventId,
        String accountId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Map<String, Object> metadata,
        ApplyStatus status,
        boolean duplicate,
        Instant createdAt,
        Instant updatedAt,
        String message,
        String accountServiceError
) {
}
