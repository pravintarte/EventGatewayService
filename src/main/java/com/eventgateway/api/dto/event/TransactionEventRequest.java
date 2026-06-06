package com.eventgateway.api.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

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
public record TransactionEventRequest(
        @NotBlank String eventId,
        @NotBlank String accountId,
        @NotNull EventType type,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull Instant eventTimestamp,
        Map<String, Object> metadata
) {
}
