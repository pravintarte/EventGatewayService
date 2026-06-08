package com.cs.eventgateway.client;

import java.net.URI;

import com.cs.eventgateway.config.AccountServiceProperties;
import com.cs.eventgateway.config.tracing.TraceContext;
import com.cs.eventgateway.contract.AccountServiceHttpContract;
import com.cs.eventgateway.dto.ApiCodes;
import com.cs.eventgateway.dto.event.AccountTransactionRequest;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

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
    private final AccountServiceProperties accountServiceProperties;

    /**
     * Applies a transaction to the Account Service. Resilience4j returns a
     * failure result instead of allowing downstream outages to fail ingestion.
     *
     * @param accountId account id path variable
     * @param request transaction request body
     * @return application result
     */
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fallbackApplyTransaction")
    @Bulkhead(name = RESILIENCE_INSTANCE)
    public AccountApplyResult applyTransaction(String accountId, AccountTransactionRequest request) {
        try {
            accountServiceRestClient.post()
                    .uri(transactionUri(accountId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AccountServiceHttpContract.IDEMPOTENCY_KEY_HEADER, request.eventId().toString())
                    .header(AccountServiceHttpContract.TRACE_ID_HEADER, TraceContext.currentOrNewTraceId())
                    .header(accountServiceProperties.internalCallerHeader(), accountServiceProperties.internalCaller())
                    .header(accountServiceProperties.internalTokenHeader(), accountServiceProperties.internalToken())
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.warn("Account Service rejected event {} on account {} with status {}",
                        request.eventId(), accountId, ex.getStatusCode());
                return AccountApplyResult.rejected(accountApplyErrorMessage(ex));
            }
            throw ex;
        }

        return AccountApplyResult.success();
    }

    /**
     * Proxies current account balance reads to Account Service.
     *
     * @param accountId account id path variable
     * @return Account Service response body and status
     */
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fallbackReadAccount")
    @Bulkhead(name = RESILIENCE_INSTANCE)
    public ResponseEntity<String> getBalance(String accountId) {
        return proxyGet("balance", accountId, balanceUri(accountId));
    }

    /**
     * Proxies account details reads to Account Service.
     *
     * @param accountId account id path variable
     * @return Account Service response body and status
     */
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fallbackReadAccount")
    @Bulkhead(name = RESILIENCE_INSTANCE)
    public ResponseEntity<String> getAccount(String accountId) {
        return proxyGet("account", accountId, accountUri(accountId));
    }

    /**
     * Builds the Account Service transaction endpoint URI for one account.
     *
     * @param accountId account id path segment
     * @return absolute URI for the Account Service transaction endpoint
     */
    URI transactionUri(String accountId) {
        return accountUriBuilder()
                .pathSegment(
                        AccountServiceHttpContract.ACCOUNTS_PATH_SEGMENT,
                        accountId,
                        AccountServiceHttpContract.TRANSACTIONS_PATH_SEGMENT
                )
                .build()
                .toUri();
    }

    /**
     * Builds the Account Service balance endpoint URI for one account.
     *
     * @param accountId account id path segment
     * @return absolute URI for the Account Service balance endpoint
     */
    URI balanceUri(String accountId) {
        return accountUriBuilder()
                .pathSegment(
                        AccountServiceHttpContract.ACCOUNTS_PATH_SEGMENT,
                        accountId,
                        AccountServiceHttpContract.BALANCE_PATH_SEGMENT
                )
                .build()
                .toUri();
    }

    /**
     * Builds the Account Service account-detail endpoint URI for one account.
     *
     * @param accountId account id path segment
     * @return absolute URI for the Account Service account endpoint
     */
    URI accountUri(String accountId) {
        return accountUriBuilder()
                .pathSegment(AccountServiceHttpContract.ACCOUNTS_PATH_SEGMENT, accountId)
                .build()
                .toUri();
    }

    /**
     * Creates a URI builder from the currently resolved Account Service base URI.
     *
     * @return URI builder rooted at the discovered or fallback Account Service URL
     */
    private UriComponentsBuilder accountUriBuilder() {
        return UriComponentsBuilder.fromUri(accountServiceInstanceResolver.baseUri());
    }

    /**
     * Performs a read-through Account Service GET call and preserves the downstream response.
     *
     * <p>Error statuses are intentionally not converted into exceptions so the
     * gateway can pass Account Service read failures, such as {@code 404}, back
     * to the caller with the original status and body.</p>
     *
     * @param operation short operation name used in logs
     * @param accountId account being read
     * @param uri absolute Account Service URI to call
     * @return Account Service response body and status
     */
    private ResponseEntity<String> proxyGet(String operation, String accountId, URI uri) {
        log.debug("Calling Account Service operation={} accountId={} uri={}", operation, accountId, uri);
        ResponseEntity<String> response = accountServiceRestClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .header(AccountServiceHttpContract.TRACE_ID_HEADER, TraceContext.currentOrNewTraceId())
                .header(accountServiceProperties.internalCallerHeader(), accountServiceProperties.internalCaller())
                .header(accountServiceProperties.internalTokenHeader(), accountServiceProperties.internalToken())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, downstreamResponse) -> {
                    // Preserve Account Service error statuses/bodies for read-through endpoints.
                    log.error("Error while calling account svc {}", downstreamResponse);
                })
                .toEntity(String.class);
        if (response.getStatusCode().isError()) {
            log.warn("Account Service returned error operation={} accountId={} status={}",
                    operation, accountId, response.getStatusCode());
        } else {
            log.info("Account Service read succeeded operation={} accountId={} status={}",
                    operation, accountId, response.getStatusCode());
        }
        return response;
    }

    /**
     * Converts Resilience4j Account Service apply failures into retryable ledger results.
     *
     * @param accountId account id path variable from the original call
     * @param request original Account Service transaction request
     * @param throwable failure that triggered the fallback
     * @return retryable failure result for ledger persistence
     */
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

    /**
     * Extracts a useful rejection message from an Account Service error response.
     *
     * @param ex downstream response exception raised by {@link RestClient}
     * @return downstream body when present, otherwise a status-based fallback message
     */
    private String accountApplyErrorMessage(RestClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        if (responseBody == null || responseBody.isBlank()) {
            return "Account Service rejected transaction with status " + ex.getStatusCode();
        }
        return responseBody;
    }

    /**
     * Converts Resilience4j Account Service read failures into a stable gateway response.
     *
     * @param accountId account id path variable from the original read call
     * @param throwable failure that triggered the fallback
     * @return {@code 503} JSON response indicating Account Service unavailability
     */
    @SuppressWarnings("unused")
    private ResponseEntity<String> fallbackReadAccount(String accountId, Throwable throwable) {
        log.warn("Account Service read failed for account {}: {}", accountId, throwable.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"code":"%s","description":"Account Service is unavailable."}
                        """.formatted(ApiCodes.ACCOUNT_SERVICE_UNAVAILABLE));
    }
}
