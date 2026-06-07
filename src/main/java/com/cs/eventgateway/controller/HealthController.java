package com.cs.eventgateway.controller;

import java.time.Clock;
import java.time.Instant;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import com.cs.eventgateway.dto.ApiCodes;
import com.cs.eventgateway.dto.ApiResponse;
import com.cs.eventgateway.dto.event.HealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health endpoint matching the gateway contract.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final Clock clock;
    private final DataSource dataSource;

    @Value("${spring.application.name:event-gateway-api}")
    private String serviceName;

    /**
     * Returns a lightweight health response for clients/load balancers.
     *
     * @return health response
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        log.debug("Received health request");
        Instant now = Instant.now(clock);
        String databaseStatus = databaseStatus();
        String serviceStatus = "UP".equals(databaseStatus) ? "UP" : "DOWN";
        return ResponseEntity.ok(new ApiResponse<>(
                now,
                HttpStatus.OK.value(),
                ApiCodes.HEALTH_OK,
                "Service is healthy.",
                new HealthResponse(serviceStatus, serviceName, now, Map.of("database", databaseStatus))
        ));
    }

    private String databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1) ? "UP" : "DOWN";
        } catch (SQLException ex) {
            log.warn("Health database check failed", ex);
            return "DOWN";
        }
    }
}
