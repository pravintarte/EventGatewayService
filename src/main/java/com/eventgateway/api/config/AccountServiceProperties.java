package com.eventgateway.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the internal Account Service dependency.
 *
 * @param serviceId Eureka service id used to locate Account Service instances
 */
@ConfigurationProperties(prefix = "account-service")
public record AccountServiceProperties(String serviceId) {

    private static final String DEFAULT_SERVICE_ID = "account-service";

    public AccountServiceProperties {
        if (serviceId == null || serviceId.isBlank()) {
            serviceId = DEFAULT_SERVICE_ID;
        }
    }
}
