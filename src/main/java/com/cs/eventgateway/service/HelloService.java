package com.cs.eventgateway.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.cs.eventgateway.dto.HelloResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for building the hello-world response for the API skeleton.
 *
 * <p>The method is intentionally protected by Resilience4j circuit breaker and
 * bulkhead annotations so resilience wiring is present from the first iteration.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelloService {

    private static final String RESILIENCE_INSTANCE = "helloWorld";
    private static final List<String> BASELINE_CAPABILITIES = List.of(
            "spring-boot",
            "zipkin-tracing",
            "h2-in-memory-db",
            "resilience4j-circuit-breaker",
            "resilience4j-bulkhead",
            "docker-ready"
    );

    private final Clock clock;

    @Value("${spring.application.name:event-gateway-api}")
    private String serviceName;

    /**
     * Builds the hello response using current service metadata.
     *
     * @return hello-world response with timestamp and capability metadata
     */
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fallbackHelloResponse")
    @Bulkhead(name = RESILIENCE_INSTANCE, fallbackMethod = "fallbackHelloResponse")
    public HelloResponse getHelloResponse() {
        log.debug("Building hello response for service {}", serviceName);
        return HelloResponse.builder()
                .message("Hello World from Event Gateway API")
                .serviceName(serviceName)
                .timestamp(Instant.now(clock))
                .capabilities(CollectionUtils.emptyIfNull(BASELINE_CAPABILITIES).stream().toList())
                .build();
    }

    /**
     * Provides a stable fallback response if the protected hello path fails.
     *
     * @param throwable exception raised by the protected method
     * @return fallback hello-world response
     */
    @SuppressWarnings("unused")
    private HelloResponse fallbackHelloResponse(Throwable throwable) {
        log.warn("Falling back while building hello response: {}", throwable.getMessage(), throwable);
        return HelloResponse.builder()
                .message("Hello World from Event Gateway API fallback")
                .serviceName(serviceName)
                .timestamp(Instant.now(clock))
                .capabilities(List.of("fallback-response"))
                .build();
    }
}
