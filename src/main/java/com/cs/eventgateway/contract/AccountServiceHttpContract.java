package com.cs.eventgateway.contract;

/**
 * HTTP contract between Event Gateway and Account Service.
 *
 * <p>This class intentionally contains only stable protocol names and path
 * shapes. Event Gateway uses these constants when calling Account Service, and
 * contract tests assert that the generated HTTP requests still match this
 * contract. Account Service must expose compatible endpoints for gateway
 * integration to work.</p>
 */
public final class AccountServiceHttpContract {

    public static final String SERVICE_ID = "account-service";

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String DEFAULT_INTERNAL_CALLER_HEADER = "X-Internal-Caller";
    public static final String DEFAULT_INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String DEFAULT_INTERNAL_CALLER = "event-gateway-api";
    public static final String DEFAULT_INTERNAL_TOKEN = "local-dev-token";

    public static final String ACCOUNTS_PATH_SEGMENT = "accounts";
    public static final String TRANSACTIONS_PATH_SEGMENT = "transactions";
    public static final String BALANCE_PATH_SEGMENT = "balance";

    public static final String ACCOUNT_RESOURCE_TEMPLATE = "/accounts/{accountId}";
    public static final String ACCOUNT_BALANCE_RESOURCE_TEMPLATE = "/accounts/{accountId}/balance";
    public static final String ACCOUNT_TRANSACTION_RESOURCE_TEMPLATE = "/accounts/{accountId}/transactions";

    /**
     * Prevents instantiation of the shared HTTP contract constants holder.
     */
    private AccountServiceHttpContract() {
    }
}
