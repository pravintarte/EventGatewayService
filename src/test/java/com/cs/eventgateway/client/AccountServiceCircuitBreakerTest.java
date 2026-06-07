package com.cs.eventgateway.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.cs.eventgateway.dto.event.AccountTransactionRequest;
import com.cs.eventgateway.dto.event.EventType;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "resilience4j.circuitbreaker.instances.accountService.sliding-window-size=5",
        "resilience4j.circuitbreaker.instances.accountService.minimum-number-of-calls=5",
        "resilience4j.circuitbreaker.instances.accountService.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.accountService.wait-duration-in-open-state=60s"
})
class AccountServiceCircuitBreakerTest {

    private static final AtomicInteger ACCOUNT_SERVICE_CALLS = new AtomicInteger();
    private static final HttpServer ACCOUNT_SERVICE = startAccountService();

    @Autowired
    private AccountServiceClient accountServiceClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void accountServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("account-service.default-url",
                () -> "http://127.0.0.1:" + ACCOUNT_SERVICE.getAddress().getPort());
    }

    @BeforeEach
    void resetCircuitBreaker() {
        ACCOUNT_SERVICE_CALLS.set(0);
        circuitBreakerRegistry.circuitBreaker("accountService").reset();
    }

    @AfterAll
    static void stopAccountService() {
        ACCOUNT_SERVICE.stop(0);
    }

    @Test
    void applyTransaction_whenAccountServiceKeepsFailing_opensCircuitBreaker() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("accountService");

        for (int i = 0; i < 5; i++) {
            AccountApplyResult result = accountServiceClient.applyTransaction(
                    "acct-123",
                    request(UUID.randomUUID())
            );

            assertThat(result.successful()).isFalse();
            assertThat(result.retryable()).isTrue();
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(ACCOUNT_SERVICE_CALLS).hasValue(5);

        AccountApplyResult shortCircuitedResult = accountServiceClient.applyTransaction(
                "acct-123",
                request(UUID.randomUUID())
        );

        assertThat(shortCircuitedResult.successful()).isFalse();
        assertThat(shortCircuitedResult.retryable()).isTrue();
        assertThat(ACCOUNT_SERVICE_CALLS).hasValue(5);
    }

    private static AccountTransactionRequest request(UUID eventId) {
        return new AccountTransactionRequest(
                eventId,
                EventType.CREDIT,
                BigDecimal.TEN,
                "USD",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of()
        );
    }

    private static HttpServer startAccountService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                ACCOUNT_SERVICE_CALLS.incrementAndGet();
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start test Account Service", ex);
        }
    }
}
