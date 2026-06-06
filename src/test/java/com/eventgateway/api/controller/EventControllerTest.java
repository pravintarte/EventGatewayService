package com.eventgateway.api.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import com.eventgateway.api.dto.ApiResponse;
import com.eventgateway.api.dto.event.EventSubmissionResponse;
import com.eventgateway.api.dto.event.EventType;
import com.eventgateway.api.dto.event.TransactionEventRequest;
import com.eventgateway.api.entity.ApplyStatus;
import com.eventgateway.api.service.EventLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EventController} response envelope selection.
 *
 * <p>The controller does not own ledger persistence; that belongs to
 * {@link EventLedgerService}. These tests mock the service and verify the HTTP
 * status, response code, and response description selected for each major
 * submission outcome. This keeps the API contract explicit and protects clients
 * from accidental response-code regressions.</p>
 */
class EventControllerTest {

    private static final Instant NOW = Instant.parse("2026-06-06T21:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /**
     * Verifies the controller response for a newly created and applied event.
     *
     * <p>When the service reports {@link ApplyStatus#APPLIED} and
     * {@code duplicate=false}, the controller should return HTTP 201 and the
     * stable application code {@code EVENT_CREATED}. The response body should be
     * wrapped in the standard {@link ApiResponse} envelope with the original
     * service payload under {@code data}.</p>
     */
    @Test
    void submitEvent_whenServiceReportsNewAppliedEvent_returnsCreatedEnvelopeWithEventCreatedCode() {
        EventLedgerService eventLedgerService = mock(EventLedgerService.class);
        EventController controller = new EventController(eventLedgerService, CLOCK);
        TransactionEventRequest request = request("evt-001");
        EventSubmissionResponse serviceResponse = submission("evt-001", ApplyStatus.APPLIED, false);

        when(eventLedgerService.submit(request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<EventSubmissionResponse>> response = controller.submitEvent(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("EVENT_CREATED");
        assertThat(response.getBody().description()).isEqualTo("Event was stored and applied to the account.");
        assertThat(response.getBody().data().eventId()).isEqualTo("evt-001");
    }

    /**
     * Verifies the controller response for an exact duplicate submission.
     *
     * <p>When the service says the request is a duplicate, the controller should
     * not return 201 because no new ledger event was created. It should return
     * HTTP 200 and the stable code {@code EVENT_DUPLICATE}, signaling that the
     * stored event was returned and Account Service was not re-applied.</p>
     */
    @Test
    void submitEvent_whenServiceReportsExactDuplicate_returnsOkEnvelopeWithDuplicateCode() {
        EventLedgerService eventLedgerService = mock(EventLedgerService.class);
        EventController controller = new EventController(eventLedgerService, CLOCK);
        TransactionEventRequest request = request("evt-duplicate");
        EventSubmissionResponse serviceResponse = submission("evt-duplicate", ApplyStatus.APPLIED, true);

        when(eventLedgerService.submit(request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<EventSubmissionResponse>> response = controller.submitEvent(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("EVENT_DUPLICATE");
    }

    /**
     * Verifies the controller response when the gateway stores the event but
     * Account Service cannot apply it.
     *
     * <p>This is a partial-success outcome. The event is accepted into the
     * ledger, but account application failed, so the controller should return
     * HTTP 202 and {@code EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED}. That tells
     * clients the request was not lost but may need downstream retry handling.</p>
     */
    @Test
    void submitEvent_whenServiceReportsApplyFailed_returnsAcceptedEnvelopeWithApplyFailedCode() {
        EventLedgerService eventLedgerService = mock(EventLedgerService.class);
        EventController controller = new EventController(eventLedgerService, CLOCK);
        TransactionEventRequest request = request("evt-outage");
        EventSubmissionResponse serviceResponse = submission("evt-outage", ApplyStatus.APPLY_FAILED, false);

        when(eventLedgerService.submit(request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<EventSubmissionResponse>> response = controller.submitEvent(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED");
    }

    /**
     * Builds a valid event submission request for controller unit tests.
     *
     * @param eventId event id to place in the request
     * @return transaction request with stable default field values
     */
    private TransactionEventRequest request(String eventId) {
        return new TransactionEventRequest(
                eventId,
                "acct-123",
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                Map.of("source", "mainframe-batch")
        );
    }

    /**
     * Builds the service-layer submission DTO that the controller wraps.
     *
     * @param eventId event id returned by the mocked service
     * @param status apply status returned by the mocked service
     * @param duplicate duplicate flag returned by the mocked service
     * @return event submission response used as controller input
     */
    private EventSubmissionResponse submission(String eventId, ApplyStatus status, boolean duplicate) {
        return new EventSubmissionResponse(
                eventId,
                "acct-123",
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                Map.of("source", "mainframe-batch"),
                status,
                duplicate,
                NOW,
                NOW,
                "test",
                status == ApplyStatus.APPLY_FAILED ? "Account Service unavailable" : null
        );
    }
}
