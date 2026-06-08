package com.cs.eventgateway.dto;

/**
 * Stable application-level API codes returned in response envelopes.
 */
public final class ApiCodes {

    /**
     * Successful event submission where the event was newly stored and applied.
     */
    public static final String EVENT_CREATED = "EVENT_CREATED";

    /**
     * Successful event submission where the request was an exact duplicate.
     */
    public static final String EVENT_DUPLICATE = "EVENT_DUPLICATE";

    /**
     * Accepted event submission where Account Service apply failed and can be retried.
     */
    public static final String EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED = "EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED";

    /**
     * Accepted event submission where Account Service permanently rejected the transaction.
     */
    public static final String EVENT_ACCEPTED_ACCOUNT_APPLY_REJECTED = "EVENT_ACCEPTED_ACCOUNT_APPLY_REJECTED";

    /**
     * Successful single-event lookup.
     */
    public static final String EVENT_RETRIEVED = "EVENT_RETRIEVED";

    /**
     * Successful account event-list lookup.
     */
    public static final String EVENTS_LISTED = "EVENTS_LISTED";

    /**
     * Successful health endpoint response.
     */
    public static final String HEALTH_OK = "HEALTH_OK";

    /**
     * Validation failure for request bodies, path variables, or query parameters.
     */
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    /**
     * Malformed JSON or unreadable request-body value.
     */
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    /**
     * Requested event id does not exist in the gateway ledger.
     */
    public static final String EVENT_NOT_FOUND = "EVENT_NOT_FOUND";

    /**
     * Event id was reused with a different business payload.
     */
    public static final String DUPLICATE_EVENT_CONFLICT = "DUPLICATE_EVENT_CONFLICT";

    /**
     * Account Service could not be reached for read-through endpoints.
     */
    public static final String ACCOUNT_SERVICE_UNAVAILABLE = "ACCOUNT_SERVICE_UNAVAILABLE";

    /**
     * Unexpected server-side failure.
     */
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    /**
     * Ledger submission result message for an event accepted and applied immediately.
     */
    public static final String EVENT_ACCEPTED_AND_APPLIED = "EVENT_ACCEPTED_AND_APPLIED";

    /**
     * Ledger submission result message for an event accepted while Account Service was unavailable.
     */
    public static final String EVENT_ACCEPTED_ACCOUNT_SERVICE_UNAVAILABLE =
            "EVENT_ACCEPTED_ACCOUNT_SERVICE_UNAVAILABLE";

    /**
     * Ledger submission result message for an exact duplicate ignored by idempotency handling.
     */
    public static final String DUPLICATE_EVENT_IGNORED = "DUPLICATE_EVENT_IGNORED";

    /**
     * Prevents instantiation of the application code constants holder.
     */
    private ApiCodes() {
    }
}
