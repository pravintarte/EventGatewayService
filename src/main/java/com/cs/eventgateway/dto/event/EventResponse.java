package com.cs.eventgateway.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.cs.eventgateway.entity.ApplyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Persisted ledger event returned by read endpoints.")
public record EventResponse(
        @Schema(description = "Unique upstream event UUID.", example = "9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2")
        UUID eventId,

        @Schema(description = "Account affected by the event.", example = "acct-123")
        String accountId,

        @Schema(description = "Transaction direction.", example = "CREDIT")
        EventType type,

        @Schema(description = "Transaction amount.", example = "150.00")
        BigDecimal amount,

        @Schema(description = "Three-letter currency code.", example = "USD")
        String currency,

        @Schema(description = "Original event occurrence timestamp.", example = "2026-05-15T14:02:11Z")
        Instant eventTimestamp,

        @Schema(description = "Optional upstream metadata represented as a JSON object.")
        Map<String, Object> metadata,

        @Schema(description = "Current Account Service apply status.", example = "APPLIED")
        ApplyStatus status,

        @Schema(description = "Server-side creation timestamp.", example = "2026-06-06T21:00:00Z")
        Instant createdAt,

        @Schema(description = "Server-side last update timestamp.", example = "2026-06-06T21:00:00Z")
        Instant updatedAt,

        @Schema(description = "Most recent Account Service failure reason, when apply failed.", nullable = true)
        String accountServiceError
) {
}
