package com.eventgateway.api.entity;

/**
 * Status of applying a persisted ledger event to Account Service.
 */
public enum ApplyStatus {
    PENDING,
    APPLIED,
    APPLY_FAILED
}
