package com.cs.eventgateway.client;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.cs.eventgateway.config.AccountServiceProperties;
import com.cs.eventgateway.config.tracing.TraceContext;
import com.cs.eventgateway.dto.event.AccountTransactionRequest;
import com.cs.eventgateway.dto.event.EventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

/**
 * Unit tests for Account Service client URI construction.
 */
class AccountServiceClientTest {

    private static final AccountServiceProperties ACCOUNT_SERVICE_PROPERTIES = new AccountServiceProperties(
            "account-service",
            URI.create("http://account-service-host:8081"),
            null,
            null,
            "X-Internal-Caller",
            "X-Internal-Token",
            "event-gateway-api",
            "local-dev-token"
    );

    @AfterEach
    void tearDown() {
        MDC.remove(TraceContext.MDC_TRACE_ID_KEY);
    }

    /**
     * Verifies the client owns Account Service resource path construction.
     *
     * <p>The resolver provides only the Account Service base URI. The client
     * appends the account transaction resource path because that path is part of
     * the Account Service API contract, not service discovery.</p>
     */
    @Test
    void transactionUri_appendsAccountTransactionPathToResolvedBaseUri() {
        AccountServiceInstanceResolver resolver = mock(AccountServiceInstanceResolver.class);
        when(resolver.baseUri()).thenReturn(URI.create("http://account-service-host:8081"));
        AccountServiceClient client = new AccountServiceClient(null, resolver, ACCOUNT_SERVICE_PROPERTIES);

        URI uri = client.transactionUri("acct-123");

        assertThat(uri).isEqualTo(URI.create("http://account-service-host:8081/accounts/acct-123/transactions"));
    }

    @Test
    void balanceUri_appendsAccountBalancePathToResolvedBaseUri() {
        AccountServiceInstanceResolver resolver = mock(AccountServiceInstanceResolver.class);
        when(resolver.baseUri()).thenReturn(URI.create("http://account-service-host:8081"));
        AccountServiceClient client = new AccountServiceClient(null, resolver, ACCOUNT_SERVICE_PROPERTIES);

        URI uri = client.balanceUri("acct-123");

        assertThat(uri).isEqualTo(URI.create("http://account-service-host:8081/accounts/acct-123/balance"));
    }

    @Test
    void accountUri_appendsAccountPathToResolvedBaseUri() {
        AccountServiceInstanceResolver resolver = mock(AccountServiceInstanceResolver.class);
        when(resolver.baseUri()).thenReturn(URI.create("http://account-service-host:8081"));
        AccountServiceClient client = new AccountServiceClient(null, resolver, ACCOUNT_SERVICE_PROPERTIES);

        URI uri = client.accountUri("acct-123");

        assertThat(uri).isEqualTo(URI.create("http://account-service-host:8081/accounts/acct-123"));
    }

    @Test
    void applyTransaction_whenTraceIdExists_propagatesTraceIdHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AccountServiceInstanceResolver resolver = mock(AccountServiceInstanceResolver.class);
        when(resolver.baseUri()).thenReturn(URI.create("http://account-service-host:8081"));
        AccountServiceClient client = new AccountServiceClient(builder.build(), resolver, ACCOUNT_SERVICE_PROPERTIES);
        MDC.put(TraceContext.MDC_TRACE_ID_KEY, "trace-abc");

        server.expect(requestTo("http://account-service-host:8081/accounts/acct-123/transactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Trace-Id", "trace-abc"))
                .andRespond(withNoContent());

        AccountApplyResult result = client.applyTransaction("acct-123", request());

        assertThat(result.successful()).isTrue();
        server.verify();
    }

    private AccountTransactionRequest request() {
        return new AccountTransactionRequest(
                UUID.fromString("9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2"),
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                Map.of("source", "junit")
        );
    }
}
