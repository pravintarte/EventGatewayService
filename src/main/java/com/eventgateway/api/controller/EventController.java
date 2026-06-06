package com.eventgateway.api.controller;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.eventgateway.api.dto.ApiResponse;
import com.eventgateway.api.dto.event.EventResponse;
import com.eventgateway.api.dto.event.EventSubmissionResponse;
import com.eventgateway.api.dto.event.TransactionEventRequest;
import com.eventgateway.api.entity.ApplyStatus;
import com.eventgateway.api.service.EventLedgerService;
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
public class EventController {

    private final EventLedgerService eventLedgerService;
    private final Clock clock;

    /**
     * Submits a transaction event into the ledger.
     *
     * @param request event request body
     * @return created, duplicate, or accepted response depending on processing outcome
     */
    @PostMapping("/events")
    public ResponseEntity<ApiResponse<EventSubmissionResponse>> submitEvent(
            @Valid @RequestBody TransactionEventRequest request
    ) {
        EventSubmissionResponse response = eventLedgerService.submit(request);
        log.info("Processed event {} with status {} duplicate={}",
                response.eventId(), response.status(), response.duplicate());

        if (response.duplicate()) {
            return ResponseEntity.ok(apiResponse(
                    HttpStatus.OK,
                    "EVENT_DUPLICATE",
                    "Duplicate event received. Existing ledger event returned and transaction was not re-applied.",
                    response
            ));
        }
        if (response.status() == ApplyStatus.APPLY_FAILED) {
            return ResponseEntity.accepted().body(apiResponse(
                    HttpStatus.ACCEPTED,
                    "EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED",
                    "Event was stored, but Account Service could not apply the transaction.",
                    response
            ));
        }

        return ResponseEntity
                .created(URI.create("/events/" + response.eventId()))
                .body(apiResponse(
                        HttpStatus.CREATED,
                        "EVENT_CREATED",
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
    @GetMapping("/events/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(@PathVariable String id) {
        return ResponseEntity.ok(apiResponse(
                HttpStatus.OK,
                "EVENT_RETRIEVED",
                "Event retrieved successfully.",
                eventLedgerService.getEvent(id)
        ));
    }

    /**
     * Lists events for an account ordered by original event timestamp.
     *
     * @param accountId account id query parameter
     * @return ordered events
     */
    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<EventResponse>>> listEvents(
            @RequestParam("account") @NotBlank String accountId
    ) {
        return ResponseEntity.ok(apiResponse(
                HttpStatus.OK,
                "EVENTS_LISTED",
                "Events retrieved successfully in event timestamp order.",
                eventLedgerService.listEventsForAccount(accountId)
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
}
