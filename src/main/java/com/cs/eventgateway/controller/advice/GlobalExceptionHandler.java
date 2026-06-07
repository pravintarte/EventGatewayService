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
