package com.eventgateway.api.client;

/**
 * Result of attempting to apply an event to the internal Account Service.
 *
 * @param successful true when Account Service accepted the transaction
 * @param errorMessage optional failure reason
 */
public record AccountApplyResult(boolean successful, String errorMessage) {

    public static AccountApplyResult success() {
        return new AccountApplyResult(true, null);
    }

    public static AccountApplyResult failure(String errorMessage) {
        return new AccountApplyResult(false, errorMessage);
    }
}
