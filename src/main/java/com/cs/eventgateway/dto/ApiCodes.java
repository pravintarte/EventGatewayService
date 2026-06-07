package com.cs.eventgateway.dto;

/**
 * Stable application-level API codes returned in response envelopes.
 */
public final class ApiCodes {

    public static final String EVENT_CREATED = "EVENT_CREATED";
    public static final String EVENT_DUPLICATE = "EVENT_DUPLICATE";
    public static final String EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED = "EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED";
    public static final String EVENT_ACCEPTED_ACCOUNT_APPLY_REJECTED = "EVENT_ACCEPTED_ACCOUNT_APPLY_REJECTED";
    public static final String EVENT_RETRIEVED = "EVENT_RETRIEVED";
    public static final String EVENTS_LISTED = "EVENTS_LISTED";
    public static final String HEALTH_OK = "HEALTH_OK";

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String EVENT_NOT_FOUND = "EVENT_NOT_FOUND";
    public static final String DUPLICATE_EVENT_CONFLICT = "DUPLICATE_EVENT_CONFLICT";
    public static final String ACCOUNT_SERVICE_UNAVAILABLE = "ACCOUNT_SERVICE_UNAVAILABLE";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    public static final String EVENT_ACCEPTED_AND_APPLIED = "EVENT_ACCEPTED_AND_APPLIED";
    public static final String EVENT_ACCEPTED_ACCOUNT_SERVICE_UNAVAILABLE =
            "EVENT_ACCEPTED_ACCOUNT_SERVICE_UNAVAILABLE";
    public static final String DUPLICATE_EVENT_IGNORED = "DUPLICATE_EVENT_IGNORED";

    private ApiCodes() {
    }
}
