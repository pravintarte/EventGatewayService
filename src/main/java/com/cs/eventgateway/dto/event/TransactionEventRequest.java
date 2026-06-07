package com.cs.eventgateway.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Public request body for submitting a transaction event.
 *
 * @param eventId unique event identifier supplied by the upstream system
 * @param accountId account affected by the event
 * @param type CREDIT or DEBIT
 * @param amount positive transaction amount
 * @param currency ISO-style uppercase currency code
 * @param eventTimestamp original event occurrence time
 * @param metadata optional upstream context
 */
@Schema(description = "Inbound transaction event supplied by an upstream system.")
public record TransactionEventRequest(
        @Schema(description = "Unique upstream event UUID used as the idempotency key.", example = "9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2")
        @NotNull
        UUID eventId,

        @Schema(description = "Account affected by the transaction.", example = "acct-123")
        @NotBlank
        String accountId,

        @Schema(description = "Transaction direction.", example = "CREDIT", allowableValues = {"CREDIT", "DEBIT"})
        @NotNull
        EventType type,

        @Schema(description = "Positive transaction amount.", example = "150.00")
        @NotNull
        @Positive
        BigDecimal amount,

        @Schema(description = "Three-letter uppercase currency code.", example = "USD", pattern = "^[A-Z]{3}$")
        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency,

        @Schema(description = "Original event occurrence timestamp in UTC.", example = "2026-05-15T14:02:11Z")
        @NotNull
        @JsonDeserialize(using = UtcInstantDeserializer.class)
        Instant eventTimestamp,

        @Schema(description = "Optional upstream metadata represented as a JSON object.")
        Map<String, Object> metadata
) {
}
