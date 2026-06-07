package com.cs.eventgateway.client;

/**
 * Result of attempting to apply an event to the internal Account Service.
 *
 * @param successful true when Account Service accepted the transaction
 * @param retryable true when the failure should be retried later
 * @param errorMessage optional failure reason
 */
public record AccountApplyResult(boolean successful, boolean retryable, String errorMessage) {

    public static AccountApplyResult success() {
        return new AccountApplyResult(true, false, null);
    }

    public static AccountApplyResult failure(String errorMessage) {
        return new AccountApplyResult(false, true, errorMessage);
    }

    public static AccountApplyResult rejected(String errorMessage) {
        return new AccountApplyResult(false, false, errorMessage);
    }
}
