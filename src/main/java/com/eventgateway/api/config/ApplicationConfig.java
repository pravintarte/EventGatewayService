package com.eventgateway.api.config;

import java.time.Clock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Application-wide bean configuration for infrastructure concerns.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AccountServiceProperties.class)
public class ApplicationConfig {

    /**
     * Provides a UTC clock for timestamp creation and deterministic testing.
     *
     * @return system UTC clock
     */
    @Bean
    public Clock clock() {
        log.info("Configuring system UTC clock");
        return Clock.systemUTC();
    }

    /**
     * Provides a shared REST client builder when one is not auto-configured.
     *
     * @return RestClient builder
     */
    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * REST client used for internal Account Service calls. Absolute URIs are
     * resolved dynamically from service discovery per request.
     *
     * @param builder shared RestClient builder
     * @return configured RestClient
     */
    @Bean
    public RestClient accountServiceRestClient(RestClient.Builder builder) {
        log.info("Configuring Account Service REST client with service discovery URI resolution");
        return builder.build();
    }
}
