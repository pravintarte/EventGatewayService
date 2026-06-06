package com.eventgateway.api.client;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.eventgateway.api.config.AccountServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Resolves Account Service instances from the configured service registry.
 */
@Component
@RequiredArgsConstructor
public class AccountServiceInstanceResolver {

    private final AccountServiceProperties properties;
    private final ObjectProvider<DiscoveryClient> discoveryClientProvider;
    private final AtomicInteger nextInstance = new AtomicInteger();

    /**
     * Resolves the Account Service transaction endpoint for an account.
     *
     * @param accountId account id path segment
     * @return absolute transaction endpoint URI
     */
    public URI transactionUri(String accountId) {
        DiscoveryClient discoveryClient = discoveryClientProvider.getIfAvailable();
        if (discoveryClient == null) {
            throw new IllegalStateException("No service discovery client is available");
        }

        List<ServiceInstance> instances = discoveryClient.getInstances(properties.serviceId());
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException("No Account Service instances found for service id "
                    + properties.serviceId());
        }

        ServiceInstance instance = instances.get(Math.floorMod(nextInstance.getAndIncrement(), instances.size()));
        return UriComponentsBuilder.fromUri(instance.getUri())
                .pathSegment("accounts", accountId, "transactions")
                .build()
                .toUri();
    }
}
