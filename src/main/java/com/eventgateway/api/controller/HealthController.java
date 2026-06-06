package com.eventgateway.api.controller;

import java.time.Clock;
import java.time.Instant;

import com.eventgateway.api.dto.ApiResponse;
import com.eventgateway.api.dto.event.HealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health endpoint matching the gateway contract.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final Clock clock;

    @Value("${spring.application.name:event-gateway-api}")
    private String serviceName;

    /**
     * Returns a lightweight health response for clients/load balancers.
     *
     * @return health response
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        Instant now = Instant.now(clock);
        return ResponseEntity.ok(new ApiResponse<>(
                now,
                HttpStatus.OK.value(),
                "HEALTH_OK",
                "Service is healthy.",
                new HealthResponse("UP", serviceName, now)
        ));
    }
}
