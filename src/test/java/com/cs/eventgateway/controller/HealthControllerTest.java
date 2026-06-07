package com.cs.eventgateway.controller;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import com.cs.eventgateway.dto.ApiResponse;
import com.cs.eventgateway.dto.event.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void health_whenDatabaseConnectionIsValid_returnsDatabaseDiagnostic() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        HealthController controller = new HealthController(
                Clock.fixed(Instant.parse("2026-06-06T21:00:00Z"), ZoneOffset.UTC),
                dataSource
        );
        ReflectionTestUtils.setField(controller, "serviceName", "event-gateway-api");

        ResponseEntity<ApiResponse<HealthResponse>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo("UP");
        assertThat(response.getBody().data().diagnostics()).containsEntry("database", "UP");
    }
}
