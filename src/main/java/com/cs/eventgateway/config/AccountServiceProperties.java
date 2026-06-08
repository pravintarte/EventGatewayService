package com.cs.eventgateway.config;

import java.net.URI;
import java.time.Duration;

import com.cs.eventgateway.contract.AccountServiceHttpContract;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the internal Account Service dependency.
 *
 * @param serviceId Eureka service id used to locate Account Service instances
 * @param defaultUrl fallback Account Service base URL used when discovery fails
 * @param connectTimeout maximum time to establish Account Service connections
 * @param readTimeout maximum time to wait for Account Service responses
 * @param internalCallerHeader header used to identify Event Gateway to Account Service
 * @param internalTokenHeader header used to send the shared Account Service POC token
 * @param internalCaller caller value Account Service expects from Event Gateway
 * @param internalToken shared POC token Account Service validates before allowing account endpoints
 */
@ConfigurationProperties(prefix = "account-service")
public record AccountServiceProperties(
        String serviceId,
        URI defaultUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String internalCallerHeader,
        String internalTokenHeader,
        String internalCaller,
        String internalToken
) {

    private static final String DEFAULT_SERVICE_ID = AccountServiceHttpContract.SERVICE_ID;
    private static final URI DEFAULT_URL = URI.create("http://localhost:8081");
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    private static final String DEFAULT_INTERNAL_CALLER_HEADER = AccountServiceHttpContract.DEFAULT_INTERNAL_CALLER_HEADER;
    private static final String DEFAULT_INTERNAL_TOKEN_HEADER = AccountServiceHttpContract.DEFAULT_INTERNAL_TOKEN_HEADER;
    private static final String DEFAULT_INTERNAL_CALLER = AccountServiceHttpContract.DEFAULT_INTERNAL_CALLER;
    private static final String DEFAULT_INTERNAL_TOKEN = AccountServiceHttpContract.DEFAULT_INTERNAL_TOKEN;

    /**
     * Normalizes optional configuration values to safe local-development defaults.
     *
     * <p>The compact constructor runs after Spring binds externalized
     * configuration. Blank service identifiers, header names, caller names, and
     * tokens are replaced with stable contract defaults; missing timeouts and
     * fallback URLs receive bounded values so the gateway can still start and
     * fail gracefully when Account Service discovery is unavailable.</p>
     */
    public AccountServiceProperties {
        if (serviceId == null || serviceId.isBlank()) {
            serviceId = DEFAULT_SERVICE_ID;
        }
        if (defaultUrl == null) {
            defaultUrl = DEFAULT_URL;
        }
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
        if (internalCallerHeader == null || internalCallerHeader.isBlank()) {
            internalCallerHeader = DEFAULT_INTERNAL_CALLER_HEADER;
        }
        if (internalTokenHeader == null || internalTokenHeader.isBlank()) {
            internalTokenHeader = DEFAULT_INTERNAL_TOKEN_HEADER;
        }
        if (internalCaller == null || internalCaller.isBlank()) {
            internalCaller = DEFAULT_INTERNAL_CALLER;
        }
        if (internalToken == null || internalToken.isBlank()) {
            internalToken = DEFAULT_INTERNAL_TOKEN;
        }
    }
}
