package com.cs.eventgateway.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Status of applying a persisted ledger event to Account Service.
 */
@Schema(description = "Status of applying a persisted ledger event to Account Service.")
public enum ApplyStatus {

    /**
     * Event is stored in the gateway ledger and has not yet been applied successfully.
     */
    PENDING,

    /**
     * Event was accepted by Account Service and does not need further retries.
     */
    APPLIED,

    /**
     * Last Account Service apply attempt failed with a retryable error.
     */
    APPLY_FAILED,

    /**
     * Account Service rejected the event permanently and it should not be retried.
     */
    APPLY_REJECTED
}
