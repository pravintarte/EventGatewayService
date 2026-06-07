package com.cs.eventgateway.controller;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cs.eventgateway.dto.ApiCodes;
import com.cs.eventgateway.dto.ApiResponse;
import com.cs.eventgateway.dto.event.EventResponse;
import com.cs.eventgateway.dto.event.EventSubmissionResponse;
import com.cs.eventgateway.dto.event.TransactionEventRequest;
import com.cs.eventgateway.entity.ApplyStatus;
import com.cs.eventgateway.service.EventLedgerService;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public API for the Event Gateway ledger.
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "Events", description = "Event Gateway ledger endpoints")
public class EventController {

    private final EventLedgerService eventLedgerService;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    /**
     * Submits a transaction event into the ledger.
     *
     * @param request event request body
     * @return created, duplicate, or accepted response depending on processing outcome
     */
    @Operation(
            summary = "Submit a transaction event",
            description = """
                    Stores a transaction event idempotently and attempts to apply it to Account Service.
                    Exact duplicate submissions return the existing event without re-applying the transaction.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Event was stored and applied to the account.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Exact duplicate event was received and existing ledger event was returned.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "Event was stored, but Account Service could not apply the transaction.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed or body could not be parsed.",
                    content = @Content(schema = @Schema(implementation = com.cs.eventgateway.controller.advice.ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Event id already exists with a different payload.",
                    content = @Content(schema = @Schema(implementation = com.cs.eventgateway.controller.advice.ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server-side error.",
                    content = @Content(schema = @Schema(implementation = com.cs.eventgateway.controller.advice.ApiErrorResponse.class))
            )
    })
    @PostMapping("/events")
    public ResponseEntity<ApiResponse<EventSubmissionResponse>> submitEvent(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Transaction event to store in the ledger.",
                    content = @Content(
                            schema = @Schema(implementation = TransactionEventRequest.class),
                            examples = @ExampleObject(
                                    name = "Credit event",
                                    value = """
                                            {
                                              "eventId": "9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2",
                                              "accountId": "acct-123",
                                              "type": "CREDIT",
                                              "amount": 150.00,
                                              "currency": "USD",
                                              "eventTimestamp": "2026-05-15T14:02:11Z",
                                              "metadata": {
                                                "source": "mainframe-batch",
                                                "batchId": "B-9042"
                                              }
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody TransactionEventRequest request
    ) {
        log.info("Received event submission eventId={} accountId={} type={}",
                request.eventId(), request.accountId(), request.type());
        EventSubmissionResponse response = eventLedgerService.submit(request);
        recordSubmission(response);
        log.info("Processed event {} with status {} duplicate={}",
                response.eventId(), response.status(), response.duplicate());

        if (response.duplicate()) {
            return ResponseEntity.ok(apiResponse(
                    HttpStatus.OK,
                    ApiCodes.EVENT_DUPLICATE,
                    "Duplicate event received. Existing ledger event returned and transaction was not re-applied.",
                    response
            ));
        }
        if (response.status() == ApplyStatus.APPLY_FAILED) {
            return ResponseEntity.accepted().body(apiResponse(
                    HttpStatus.ACCEPTED,
                    ApiCodes.EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED,
                    "Event was stored, but Account Service could not apply the transaction.",
                    response
            ));
        }
        if (response.status() == ApplyStatus.APPLY_REJECTED) {
            return ResponseEntity.accepted().body(apiResponse(
                    HttpStatus.ACCEPTED,
                    ApiCodes.EVENT_ACCEPTED_ACCOUNT_APPLY_REJECTED,
                    "Event was stored, but Account Service rejected the transaction.",
                    response
            ));
        }

        return ResponseEntity
                .created(URI.create("/events/" + response.eventId()))
                .body(apiResponse(
                        HttpStatus.CREATED,
                        ApiCodes.EVENT_CREATED,
                        "Event was stored and applied to the account.",
                        response
                ));
    }

    /**
     * Retrieves one ledger event by id.
     *
     * @param id event id
     * @return event response
     */
    @Operation(
            summary = "Get an event by id",
            description = "Retrieves one persisted ledger event by upstream event id."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Event retrieved successfully.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "No ledger event exists for the supplied id.",
                    content = @Content(schema = @Schema(implementation = com.cs.eventgateway.controller.advice.ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server-side error.",
                    content = @Content(schema = @Schema(implementation = com.cs.eventgateway.controller.advice.ApiErrorResponse.class))
            )
    })
    @GetMapping("/events/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(
            @Parameter(
                    name = "id",
                    description = "Upstream event UUID.",
                    example = "9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2",
                    required = true,
                    in = ParameterIn.PATH
            )
            @PathVariable UUID id
    ) {
        log.info("Received event lookup eventId={}", id);
        EventResponse event = eventLedgerService.getEvent(id);
        log.info("Completed event lookup eventId={} status={}", id, HttpStatus.OK);
        return ResponseEntity.ok(apiResponse(
                HttpStatus.OK,
                ApiCodes.EVENT_RETRIEVED,
                "Event retrieved successfully.",
                event
        ));
    }

    /**
     * Lists events for an account ordered by original event timestamp.
     *
     * @param accountId account id query parameter
     * @return ordered events
     */
    @Operation(
            summary = "List events for an account",
            description = "Lists ledger events for an account ordered by original event timestamp."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Events retrieved successfully in event timestamp order.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Account query parameter is missing or blank.",
                    content = @Content(schema = @Schema(implementation = com.cs.eventgateway.controller.advice.ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server-side error.",
                    content = @Content(schema = @Schema(implementation = com.cs.eventgateway.controller.advice.ApiErrorResponse.class))
            )
    })
    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<EventResponse>>> listEvents(
            @Parameter(
                    name = "account",
                    description = "Account id whose ledger events should be returned.",
                    example = "acct-123",
                    required = true,
                    in = ParameterIn.QUERY
            )
            @RequestParam("account") @NotBlank String accountId
    ) {
        log.info("Received account event list request accountId={}", accountId);
        List<EventResponse> events = eventLedgerService.listEventsForAccount(accountId);
        log.info("Completed account event list request accountId={} count={}", accountId, events.size());
        return ResponseEntity.ok(apiResponse(
                HttpStatus.OK,
                ApiCodes.EVENTS_LISTED,
                "Events retrieved successfully in event timestamp order.",
                events
        ));
    }

    private <T> ApiResponse<T> apiResponse(
            HttpStatus status,
            String code,
            String description,
            T data
    ) {
        return new ApiResponse<>(
                Instant.now(clock),
                status.value(),
                code,
                description,
                data
        );
    }

    private void recordSubmission(EventSubmissionResponse response) {
        meterRegistry.counter(
                "event_gateway.events.submitted",
                "status",
                response.status().name(),
                "duplicate",
                Boolean.toString(response.duplicate())
        ).increment();
    }
}
