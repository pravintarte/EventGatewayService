package com.cs.eventgateway.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Status of applying a persisted ledger event to Account Service.
 */
@Schema(description = "Status of applying a persisted ledger event to Account Service.")
public enum ApplyStatus {
    PENDING,
    APPLIED,
    APPLY_FAILED,
    APPLY_REJECTED
}
