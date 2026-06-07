package com.cs.eventgateway.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the internal Account Service dependency.
 *
 * @param serviceId Eureka service id used to locate Account Service instances
 * @param defaultUrl fallback Account Service base URL used when discovery fails
 * @param connectTimeout maximum time to establish Account Service connections
 * @param readTimeout maximum time to wait for Account Service responses
 */
@ConfigurationProperties(prefix = "account-service")
public record AccountServiceProperties(
        String serviceId,
        URI defaultUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final String DEFAULT_SERVICE_ID = "account-service";
    private static final URI DEFAULT_URL = URI.create("http://localhost:8081");
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

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
    }
}
