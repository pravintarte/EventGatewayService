package com.cs.eventgateway.service.ledger;

import java.util.Objects;

import com.cs.eventgateway.dto.event.TransactionEventRequest;
import com.cs.eventgateway.entity.EventRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides whether a repeated event id is a safe idempotent retry.
 */
@Component
@RequiredArgsConstructor
public class EventIdempotencyMatcher {

    private final EventMetadataJsonConverter metadataJsonConverter;

    /**
     * Checks whether the inbound request exactly matches the stored ledger event.
     *
     * @param existing event already stored under the same event id
     * @param request repeated request using that event id
     * @return true when the repeated request is safe to treat as a duplicate
     */
    public boolean isExactDuplicate(EventRecord existing, TransactionEventRequest request) {
        return Objects.equals(existing.getAccountId(), request.accountId())
                && Objects.equals(existing.getType(), request.type())
                && existing.getAmount().compareTo(request.amount()) == 0
                && Objects.equals(existing.getCurrency(), request.currency())
                && Objects.equals(existing.getEventTimestamp(), request.eventTimestamp())
                && metadataJsonConverter.structurallyEquals(existing.getMetadataJson(), request.metadata());
    }
}
