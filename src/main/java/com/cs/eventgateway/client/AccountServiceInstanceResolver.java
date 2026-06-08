package com.cs.eventgateway.client;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.cs.eventgateway.config.AccountServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

/**
 * Resolves the Account Service base URL from the configured service registry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountServiceInstanceResolver {

    private final AccountServiceProperties properties;
    private final ObjectProvider<DiscoveryClient> discoveryClientProvider;
    private final AtomicInteger nextInstance = new AtomicInteger();

    /**
     * Resolves the Account Service base URI.
     *
     * <p>Service discovery owns service location only. Endpoint paths remain in
     * the REST client so this resolver can fall back cleanly to a configured
     * base URL when discovery is unavailable, empty, or temporarily failing.</p>
     *
     * @return discovered Account Service base URI, or configured default URL
     */
    public URI baseUri() {
        DiscoveryClient discoveryClient = discoveryClientProvider.getIfAvailable();
        if (discoveryClient == null) {
            return fallbackBaseUri("No service discovery client is available");
        }

        List<ServiceInstance> instances;
        try {
            instances = discoveryClient.getInstances(properties.serviceId());
        } catch (RuntimeException ex) {
            return fallbackBaseUri("Service discovery lookup failed for service id " + properties.serviceId(), ex);
        }

        if (instances.isEmpty()) {
            return fallbackBaseUri("No Account Service instances found for service id " + properties.serviceId());
        }

        ServiceInstance instance = instances.get(Math.floorMod(nextInstance.getAndIncrement(), instances.size()));
        URI instanceUri = instance.getUri();
        if (instanceUri == null) {
            return fallbackBaseUri("Discovered Account Service instance did not include a URI");
        }
        return instanceUri;
    }

    /**
     * Returns the configured fallback URL and logs the reason discovery was not used.
     *
     * @param reason human-readable discovery failure reason
     * @return configured default Account Service base URI
     */
    private URI fallbackBaseUri(String reason) {
        log.warn("{}; using default Account Service URL {}", reason, properties.defaultUrl());
        return properties.defaultUrl();
    }

    /**
     * Returns the configured fallback URL and logs the exception that interrupted discovery.
     *
     * @param reason human-readable discovery failure reason
     * @param ex exception raised by the discovery client
     * @return configured default Account Service base URI
     */
    private URI fallbackBaseUri(String reason, RuntimeException ex) {
        log.warn("{}; using default Account Service URL {}", reason, properties.defaultUrl(), ex);
        return properties.defaultUrl();
    }
}
