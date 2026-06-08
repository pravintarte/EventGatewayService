package com.cs.eventgateway.controller.advice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.cs.eventgateway.dto.ApiCodes;
import com.cs.eventgateway.exception.DuplicateEventConflictException;
import com.cs.eventgateway.exception.EventNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Converts expected application and validation failures into stable API errors.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    /**
     * Handles request-body Bean Validation failures raised by annotated DTO fields.
     *
     * @param ex validation exception produced while binding the request body
     * @return stable {@code 400} error envelope with field-level validation details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        log.warn("Request validation failed: {}", details);
        return error(
                HttpStatus.BAD_REQUEST,
                ApiCodes.VALIDATION_ERROR,
                "Request validation failed.",
                details
        );
    }

    /**
     * Handles validation failures raised outside request-body binding.
     *
     * <p>This covers constrained path variables, query parameters, and other
     * method-level validation failures that Spring reports as constraint
     * violations instead of field binding errors.</p>
     *
     * @param ex constraint violation exception produced by Jakarta Validation
     * @return stable {@code 400} error envelope with property-level details
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        log.warn("Request constraint validation failed: {}", details);
        return error(
                HttpStatus.BAD_REQUEST,
                ApiCodes.VALIDATION_ERROR,
                "Request validation failed.",
                details
        );
    }

    /**
     * Handles malformed JSON and invalid serialized field values.
     *
     * @param ex unreadable-message exception produced by the HTTP converter
     * @return stable {@code 400} error envelope with a concise parse failure detail
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        List<String> details = malformedRequestDetails(ex);
        log.warn("Malformed request body: {}", details);
        return error(
                HttpStatus.BAD_REQUEST,
                ApiCodes.MALFORMED_REQUEST,
                "Request body is malformed or contains invalid field values.",
                details
        );
    }

    /**
     * Handles path-variable or query-parameter type conversion failures.
     *
     * @param ex Spring MVC type mismatch exception
     * @return stable {@code 400} error envelope naming the invalid parameter
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Request parameter type mismatch name={} value={} requiredType={}",
                ex.getName(), ex.getValue(), ex.getRequiredType());
        return error(
                HttpStatus.BAD_REQUEST,
                ApiCodes.VALIDATION_ERROR,
                "Request path or query parameter has an invalid value.",
                List.of(ex.getName() + ": " + ex.getValue())
        );
    }

    /**
     * Handles required query parameters that are absent from the request.
     *
     * @param ex Spring MVC missing-parameter exception
     * @return stable {@code 400} error envelope naming the missing parameter
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter name={} expectedType={}",
                ex.getParameterName(), ex.getParameterType());
        return error(
                HttpStatus.BAD_REQUEST,
                ApiCodes.VALIDATION_ERROR,
                "Required request parameter is missing.",
                List.of(ex.getParameterName() + ": required parameter is missing")
        );
    }

    /**
     * Handles reads for event ids that are not present in the gateway ledger.
     *
     * @param ex domain exception containing the missing event id
     * @return stable {@code 404} error envelope
     */
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EventNotFoundException ex) {
        log.warn("Event lookup failed: {}", ex.getMessage());
        return error(
                HttpStatus.NOT_FOUND,
                ApiCodes.EVENT_NOT_FOUND,
                ex.getMessage(),
                List.of()
        );
    }

    /**
     * Handles unsafe idempotency conflicts where an event id is reused for different payloads.
     *
     * @param ex domain exception containing the conflicting event id
     * @return stable {@code 409} error envelope
     */
    @ExceptionHandler(DuplicateEventConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateConflict(DuplicateEventConflictException ex) {
        log.warn("Duplicate event conflict: {}", ex.getMessage());
        return error(
                HttpStatus.CONFLICT,
                ApiCodes.DUPLICATE_EVENT_CONFLICT,
                ex.getMessage(),
                List.of()
        );
    }

    /**
     * Handles all unexpected exceptions that were not mapped by a more specific handler.
     *
     * @param ex unhandled exception from the request pipeline
     * @return stable {@code 500} error envelope without leaking implementation details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled API error", ex);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiCodes.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred while processing the request.",
                List.of()
        );
    }

    /**
     * Builds the common API error envelope used by every exception handler.
     *
     * @param status HTTP status to return
     * @param code stable application error code
     * @param description human-readable error description
     * @param details optional validation or parsing details
     * @return response entity containing the standardized error body
     */
    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String description,
            List<String> details
    ) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        Instant.now(clock),
                        status.value(),
                        code,
                        description,
                        details
                ));
    }

    /**
     * Extracts a safe one-line detail from a malformed request-body exception.
     *
     * <p>Jackson and Spring exception messages can include deeply nested parser
     * context. This helper keeps the client-facing detail concise by selecting
     * the most specific cause, trimming it to the first line, and removing the
     * generic Spring JSON parse prefix when present.</p>
     *
     * @param ex unreadable-message exception raised by request deserialization
     * @return singleton detail list suitable for the public error envelope
     */
    private List<String> malformedRequestDetails(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                ? ex.getMessage()
                : cause.getMessage();
        if (message == null || message.isBlank()) {
            return List.of("Request body is malformed or contains invalid field values.");
        }

        String firstLine = message.lines()
                .findFirst()
                .orElse("Request body is malformed or contains invalid field values.")
                .strip();
        if (firstLine.startsWith("JSON parse error: ")) {
            firstLine = firstLine.substring("JSON parse error: ".length()).strip();
        }
        return List.of(firstLine);
    }
}
