package com.eventgateway.api.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Request sent from the gateway to the internal Account Service.
 *
 * @param eventId upstream event identifier
 * @param type CREDIT or DEBIT
 * @param amount positive transaction amount
 * @param currency ISO-style currency code
 * @param eventTimestamp original event occurrence time
 * @param metadata optional upstream context
 */
public record AccountTransactionRequest(
        String eventId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Map<String, Object> metadata
) {
}
