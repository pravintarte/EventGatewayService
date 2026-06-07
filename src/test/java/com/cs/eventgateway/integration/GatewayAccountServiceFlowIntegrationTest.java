package com.cs.eventgateway.integration;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cs.eventgateway.repository.EventRecordRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "event-gateway.retry.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.zipkin.autoconfigure.ZipkinAutoConfiguration"
        }
)
class GatewayAccountServiceFlowIntegrationTest {

    private static final ContractAccountService ACCOUNT_SERVICE = ContractAccountService.start();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private EventRecordRepository eventRecordRepository;

    @DynamicPropertySource
    static void accountServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("account-service.default-url", ACCOUNT_SERVICE::baseUrl);
    }

    @BeforeEach
    void resetState() {
        eventRecordRepository.deleteAll();
        ACCOUNT_SERVICE.reset();
    }

    @AfterAll
    static void stopAccountService() {
        ACCOUNT_SERVICE.stop();
    }

    @Test
    void postEvents_thenReadBalance_exercisesGatewayToAccountServiceHttpFlow() throws Exception {
        HttpResponse<String> credit = postEvent(
                eventJson(
                        "0bd17867-d631-44f1-ae8f-a6a726761785",
                        "acct-full-flow",
                        "CREDIT",
                        "150.00"
                )
        );
        HttpResponse<String> debit = postEvent(
                eventJson(
                        "991f1f51-27cf-4f90-8ea9-2e867a044581",
                        "acct-full-flow",
                        "DEBIT",
                        "25.00"
                )
        );
        HttpResponse<String> duplicateCredit = postEvent(
                eventJson(
                        "0bd17867-d631-44f1-ae8f-a6a726761785",
                        "acct-full-flow",
                        "CREDIT",
                        "150.00"
                )
        );
        HttpResponse<String> balance = getBalance();

        assertThat(credit.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(credit.body()).contains("\"status\":\"APPLIED\"");
        assertThat(debit.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(debit.body()).contains("\"status\":\"APPLIED\"");
        assertThat(duplicateCredit.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(duplicateCredit.body()).contains("\"duplicate\":true");

        assertThat(balance.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(balance.body()).contains("\"accountId\":\"acct-full-flow\"");
        assertThat(balance.body()).contains("\"balance\":125.00");
        assertThat(balance.body()).contains("\"currency\":\"USD\"");

        assertThat(ACCOUNT_SERVICE.applyCalls()).isEqualTo(2);
        assertThat(ACCOUNT_SERVICE.balanceCalls()).isEqualTo(1);
        assertThat(ACCOUNT_SERVICE.lastTraceId()).isEqualTo("trace-full-flow");
        assertThat(ACCOUNT_SERVICE.seenIdempotencyKeys())
                .containsKeys(
                        "0bd17867-d631-44f1-ae8f-a6a726761785",
                        "991f1f51-27cf-4f90-8ea9-2e867a044581"
                );
    }

    private HttpResponse<String> postEvent(String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/events"))
                .header("Content-Type", "application/json")
                .header("X-Trace-Id", "trace-full-flow")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getBalance() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/accounts/acct-full-flow/balance"))
                .header("X-Trace-Id", "trace-full-flow")
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI gatewayUri(String path) {
        return URI.create("http://127.0.0.1:" + gatewayPort + path);
    }

    private String eventJson(String eventId, String accountId, String type, String amount) {
        return """
                {
                  "eventId": "%s",
                  "accountId": "%s",
                  "type": "%s",
                  "amount": %s,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z",
                  "metadata": {
                    "source": "gateway-account-service-flow-test"
                  }
                }
                """.formatted(eventId, accountId, type, amount);
    }

    private static final class ContractAccountService {

        private static final Pattern ACCOUNT_TRANSACTION_PATH =
                Pattern.compile("^/accounts/([^/]+)/transactions$");
        private static final Pattern ACCOUNT_BALANCE_PATH =
                Pattern.compile("^/accounts/([^/]+)/balance$");
        private static final Pattern TYPE_PATTERN =
                Pattern.compile("\"type\"\\s*:\\s*\"(CREDIT|DEBIT)\"");
        private static final Pattern AMOUNT_PATTERN =
                Pattern.compile("\"amount\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

        private final HttpServer server;
        private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
        private final Map<String, Boolean> seenIdempotencyKeys = new ConcurrentHashMap<>();
        private final AtomicInteger applyCalls = new AtomicInteger();
        private final AtomicInteger balanceCalls = new AtomicInteger();
        private final AtomicReference<String> lastTraceId = new AtomicReference<>();

        private ContractAccountService(HttpServer server) {
            this.server = server;
        }

        static ContractAccountService start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                ContractAccountService service = new ContractAccountService(server);
                server.createContext("/", service::handle);
                server.start();
                return service;
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to start embedded Account Service", ex);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset() {
            balances.clear();
            seenIdempotencyKeys.clear();
            applyCalls.set(0);
            balanceCalls.set(0);
            lastTraceId.set(null);
        }

        void stop() {
            server.stop(0);
        }

        int applyCalls() {
            return applyCalls.get();
        }

        int balanceCalls() {
            return balanceCalls.get();
        }

        String lastTraceId() {
            return lastTraceId.get();
        }

        Map<String, Boolean> seenIdempotencyKeys() {
            return seenIdempotencyKeys;
        }

        private void handle(HttpExchange exchange) throws IOException {
            lastTraceId.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
            Matcher transactionMatcher = ACCOUNT_TRANSACTION_PATH.matcher(exchange.getRequestURI().getPath());
            Matcher balanceMatcher = ACCOUNT_BALANCE_PATH.matcher(exchange.getRequestURI().getPath());
            if ("POST".equals(exchange.getRequestMethod()) && transactionMatcher.matches()) {
                handleApply(exchange, transactionMatcher.group(1));
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && balanceMatcher.matches()) {
                handleBalance(exchange, balanceMatcher.group(1));
                return;
            }
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }

        private void handleApply(HttpExchange exchange, String accountId) throws IOException {
            applyCalls.incrementAndGet();
            String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            String caller = exchange.getRequestHeaders().getFirst("X-Internal-Caller");
            String token = exchange.getRequestHeaders().getFirst("X-Internal-Token");
            if (idempotencyKey == null
                    || !"event-gateway-api".equals(caller)
                    || !"local-dev-token".equals(token)
                    || lastTraceId.get() == null) {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }
            if (seenIdempotencyKeys.putIfAbsent(idempotencyKey, true) != null) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            BigDecimal signedAmount = "DEBIT".equals(requiredMatch(TYPE_PATTERN, body))
                    ? requiredAmount(body).negate()
                    : requiredAmount(body);
            balances.merge(accountId, signedAmount, BigDecimal::add);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }

        private void handleBalance(HttpExchange exchange, String accountId) throws IOException {
            balanceCalls.incrementAndGet();
            BigDecimal balance = balances.getOrDefault(accountId, BigDecimal.ZERO).setScale(2);
            byte[] response = """
                    {"accountId":"%s","balance":%s,"currency":"USD"}
                    """.formatted(accountId, balance).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        }

        private String requiredMatch(Pattern pattern, String body) {
            Matcher matcher = pattern.matcher(body);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Request body does not match " + pattern);
            }
            return matcher.group(1);
        }

        private BigDecimal requiredAmount(String body) {
            return new BigDecimal(requiredMatch(AMOUNT_PATTERN, body));
        }
    }
}
