package com.cs.eventgateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically retries ledger events that were stored but not applied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "event-gateway.retry.enabled", havingValue = "true")
public class EventApplyRetryScheduler {

    private final EventLedgerService eventLedgerService;

    @Scheduled(
            fixedDelayString = "${event-gateway.retry.fixed-delay:30s}",
            initialDelayString = "${event-gateway.retry.initial-delay:30s}"
    )
    public void retryDueEvents() {
        int attempted = eventLedgerService.retryDueEvents();
        if (attempted > 0) {
            log.info("Account Service apply retry cycle attempted {} event(s)", attempted);
        }
    }
}
