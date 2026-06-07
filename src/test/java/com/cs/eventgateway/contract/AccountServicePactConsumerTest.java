package com.cs.eventgateway.contract;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.cs.eventgateway.client.AccountApplyResult;
import com.cs.eventgateway.client.AccountServiceClient;
import com.cs.eventgateway.client.AccountServiceInstanceResolver;
import com.cs.eventgateway.config.AccountServiceProperties;
import com.cs.eventgateway.dto.event.AccountTransactionRequest;
import com.cs.eventgateway.dto.event.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pact consumer contracts for Event Gateway calls to Account Service.
 *
 * <p>These tests generate the consumer pact that Account Service must verify in
 * its provider pipeline. Publishing the generated pact to PactFlow is handled by
 * the Pact Maven plugin configuration in {@code pom.xml}.</p>
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = AccountServicePactConsumerTest.PROVIDER)
class AccountServicePactConsumerTest {

    static final String CONSUMER = "event-gateway-api";
    static final String PROVIDER = "account-service";

    private static final String ACCOUNT_ID = "acct-123";
    private static final UUID EVENT_ID = UUID.fromString("9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2");
    private static final String INTERNAL_CALLER_HEADER = AccountServiceHttpContract.DEFAULT_INTERNAL_CALLER_HEADER;
    private static final String INTERNAL_TOKEN_HEADER = AccountServiceHttpContract.DEFAULT_INTERNAL_TOKEN_HEADER;
    private static final String INTERNAL_CALLER = AccountServiceHttpContract.DEFAULT_INTERNAL_CALLER;
    private static final String INTERNAL_TOKEN = AccountServiceHttpContract.DEFAULT_INTERNAL_TOKEN;

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    V4Pact applyTransactionAccepted(PactBuilder builder) {
        return builder
                .given("account acct-123 accepts credit transactions")
                .expectsToReceiveHttpInteraction("apply a gateway transaction event to an account", interaction -> interaction
                        .withRequest(request -> request
                                .path("/accounts/acct-123/transactions")
                                .method("POST")
                                .headers(applyHeaders())
                                .body("""
                                        {
                                          "eventId": "9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2",
                                          "type": "CREDIT",
                                          "amount": 150.00,
                                          "currency": "USD",
                                          "eventTimestamp": "2026-05-15T14:02:11Z",
                                          "metadata": {
                                            "source": "pact-consumer-test"
                                          }
                                        }
                                        """, "application/json"))
                        .willRespondWith(response -> response.status(204)))
                .toPact();
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    V4Pact applyTransactionRejected(PactBuilder builder) {
        return builder
                .given("account acct-123 rejects the transaction")
                .expectsToReceiveHttpInteraction("apply a gateway transaction event that Account Service rejects", interaction -> interaction
                        .withRequest(request -> request
                                .path("/accounts/acct-123/transactions")
                                .method("POST")
                                .headers(applyHeaders())
                                .body("""
                                        {
                                          "eventId": "9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2",
                                          "type": "DEBIT",
                                          "amount": 150.00,
                                          "currency": "USD",
                                          "eventTimestamp": "2026-05-15T14:02:11Z",
                                          "metadata": {
                                            "source": "pact-consumer-test"
                                          }
                                        }
                                        """, "application/json"))
                        .willRespondWith(response -> response
                                .status(409)
                                .headers(jsonHeaders())
                                .body("""
                                        {"code":"ACCOUNT_TRANSACTION_REJECTED","description":"Insufficient funds."}
                                        """, "application/json")))
                .toPact();
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    V4Pact getBalance(PactBuilder builder) {
        return builder
                .given("account acct-123 has a balance")
                .expectsToReceiveHttpInteraction("get account balance through Account Service", interaction -> interaction
                        .withRequest(request -> request
                                .path("/accounts/acct-123/balance")
                                .method("GET")
                                .headers(readHeaders()))
                        .willRespondWith(response -> response
                                .status(200)
                                .headers(jsonHeaders())
                                .body("""
                                        {"accountId":"acct-123","balance":275.50,"currency":"USD"}
                                        """, "application/json")))
                .toPact();
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    V4Pact getAccountNotFound(PactBuilder builder) {
        return builder
                .given("account acct-404 does not exist")
                .expectsToReceiveHttpInteraction("get a missing account through Account Service", interaction -> interaction
                        .withRequest(request -> request
                                .path("/accounts/acct-404")
                                .method("GET")
                                .headers(readHeaders()))
                        .willRespondWith(response -> response
                                .status(404)
                                .headers(jsonHeaders())
                                .body("""
                                        {"code":"ACCOUNT_NOT_FOUND","description":"Account not found."}
                                        """, "application/json")))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "applyTransactionAccepted")
    void applyTransaction_whenAccountServiceAccepts_returnsSuccess(MockServer mockServer) {
        AccountApplyResult result = client(mockServer).applyTransaction(ACCOUNT_ID, request(EventType.CREDIT));

        assertThat(result.successful()).isTrue();
        assertThat(result.retryable()).isFalse();
    }

    @Test
    @PactTestFor(pactMethod = "applyTransactionRejected")
    void applyTransaction_whenAccountServiceRejects_returnsNonRetryableRejection(MockServer mockServer) {
        AccountApplyResult result = client(mockServer).applyTransaction(ACCOUNT_ID, request(EventType.DEBIT));

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.errorMessage()).contains("ACCOUNT_TRANSACTION_REJECTED");
    }

    @Test
    @PactTestFor(pactMethod = "getBalance")
    void getBalance_returnsAccountServiceResponse(MockServer mockServer) {
        ResponseEntity<String> response = client(mockServer).getBalance(ACCOUNT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"balance\":275.50");
    }

    @Test
    @PactTestFor(pactMethod = "getAccountNotFound")
    void getAccount_whenAccountServiceReturnsNotFound_preservesDownstreamStatusAndBody(MockServer mockServer) {
        ResponseEntity<String> response = client(mockServer).getAccount("acct-404");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("ACCOUNT_NOT_FOUND");
    }

    private AccountServiceClient client(MockServer mockServer) {
        AccountServiceInstanceResolver resolver = mock(AccountServiceInstanceResolver.class);
        when(resolver.baseUri()).thenReturn(URI.create(mockServer.getUrl()));
        return new AccountServiceClient(RestClient.builder().build(), resolver, properties());
    }

    private AccountServiceProperties properties() {
        return new AccountServiceProperties(
                AccountServiceHttpContract.SERVICE_ID,
                URI.create("http://unused.example"),
                null,
                null,
                INTERNAL_CALLER_HEADER,
                INTERNAL_TOKEN_HEADER,
                INTERNAL_CALLER,
                INTERNAL_TOKEN
        );
    }

    private Map<String, Object> applyHeaders() {
        return Map.of(
                "Content-Type", "application/json",
                AccountServiceHttpContract.IDEMPOTENCY_KEY_HEADER, EVENT_ID.toString(),
                INTERNAL_CALLER_HEADER, INTERNAL_CALLER,
                INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN
        );
    }

    private Map<String, Object> readHeaders() {
        return Map.of(
                "Accept", "application/json",
                INTERNAL_CALLER_HEADER, INTERNAL_CALLER,
                INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN
        );
    }

    private Map<String, Object> jsonHeaders() {
        return Map.of("Content-Type", "application/json");
    }

    private AccountTransactionRequest request(EventType type) {
        return new AccountTransactionRequest(
                EVENT_ID,
                type,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                Map.of("source", "pact-consumer-test")
        );
    }
}
