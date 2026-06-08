package com.cs.eventgateway.client;

/**
 * Result of attempting to apply an event to the internal Account Service.
 *
 * @param successful true when Account Service accepted the transaction
 * @param retryable true when the failure should be retried later
 * @param errorMessage optional failure reason
 */
public record AccountApplyResult(boolean successful, boolean retryable, String errorMessage) {

    /**
     * Creates a result for a transaction that Account Service accepted.
     *
     * @return successful non-retryable apply result
     */
    public static AccountApplyResult success() {
        return new AccountApplyResult(true, false, null);
    }

    /**
     * Creates a result for a transient Account Service apply failure.
     *
     * <p>Gateway ledger retry logic treats this result as retryable and stores
     * the supplied message for operational visibility.</p>
     *
     * @param errorMessage downstream failure description
     * @return retryable failure result
     */
    public static AccountApplyResult failure(String errorMessage) {
        return new AccountApplyResult(false, true, errorMessage);
    }

    /**
     * Creates a result for a permanent Account Service rejection.
     *
     * <p>Gateway ledger retry logic treats this result as terminal because
     * repeating the same request is not expected to succeed.</p>
     *
     * @param errorMessage downstream rejection description
     * @return non-retryable rejected result
     */
    public static AccountApplyResult rejected(String errorMessage) {
        return new AccountApplyResult(false, false, errorMessage);
    }
}
