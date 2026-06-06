package com.eventgateway.api.client;

import java.net.URI;

import com.eventgateway.api.dto.event.AccountTransactionRequest;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Synchronous REST client for the internal Account Service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceClient {

    private static final String RESILIENCE_INSTANCE = "accountService";

    private final RestClient accountServiceRestClient;
    private final AccountServiceInstanceResolver accountServiceInstanceResolver;

    /**
     * Applies a transaction to the Account Service. Resilience4j returns a
     * failure result instead of allowing downstream outages to fail ingestion.
     *
     * @param accountId account id path variable
     * @param request transaction request body
     * @return application result
     */
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fallbackApplyTransaction")
    @Bulkhead(name = RESILIENCE_INSTANCE, fallbackMethod = "fallbackApplyTransaction")
    public AccountApplyResult applyTransaction(String accountId, AccountTransactionRequest request) {
        URI transactionUri = accountServiceInstanceResolver.transactionUri(accountId);
        accountServiceRestClient.post()
                .uri(transactionUri)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        return AccountApplyResult.success();
    }

    @SuppressWarnings("unused")
    private AccountApplyResult fallbackApplyTransaction(
            String accountId,
            AccountTransactionRequest request,
            Throwable throwable
    ) {
        log.warn("Account Service apply failed for event {} on account {}: {}",
                request.eventId(), accountId, throwable.getMessage());
        return AccountApplyResult.failure(throwable.getMessage());
    }
}
