package com.cs.eventgateway.client;

import java.net.URI;
import java.util.List;

import com.cs.eventgateway.config.AccountServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Account Service discovery base URI resolution.
 *
 * <p>The resolver bridges Eureka service discovery and the REST client, but it
 * deliberately resolves only the service base URI. Resource paths are owned by
 * {@link AccountServiceClient}.</p>
 */
class AccountServiceInstanceResolverTest {

    /**
     * Verifies successful Account Service base URI resolution from a discovered
     * Eureka instance.
     *
     * <p>The test mocks {@link DiscoveryClient} and {@link ServiceInstance} so
     * it can assert service location lookup without running a real Eureka
     * registry. Endpoint paths are intentionally not appended here.</p>
     */
    @Test
    void baseUri_whenEurekaReturnsAccountServiceInstance_returnsDiscoveredBaseUri() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        ServiceInstance serviceInstance = mock(ServiceInstance.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DiscoveryClient> provider = mock(ObjectProvider.class);

        when(provider.getIfAvailable()).thenReturn(discoveryClient);
        when(discoveryClient.getInstances("account-service")).thenReturn(List.of(serviceInstance));
        when(serviceInstance.getUri()).thenReturn(URI.create("http://account-service-host:8081"));

        AccountServiceInstanceResolver resolver = new AccountServiceInstanceResolver(
                new AccountServiceProperties("account-service", URI.create("http://localhost:8081"), null, null, null, null, null, null),
                provider
        );

        URI uri = resolver.baseUri();

        assertThat(uri).isEqualTo(URI.create("http://account-service-host:8081"));
    }

    /**
     * Verifies fallback behavior when service discovery is not available.
     *
     * <p>This keeps local development and degraded discovery scenarios from
     * failing before the REST client can attempt the configured default Account
     * Service URL.</p>
     */
    @Test
    void baseUri_whenDiscoveryClientIsUnavailable_returnsDefaultUrl() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DiscoveryClient> provider = mock(ObjectProvider.class);

        when(provider.getIfAvailable()).thenReturn(null);

        AccountServiceInstanceResolver resolver = new AccountServiceInstanceResolver(
                new AccountServiceProperties("account-service", URI.create("http://localhost:8081"), null, null, null, null, null, null),
                provider
        );

        URI uri = resolver.baseUri();

        assertThat(uri).isEqualTo(URI.create("http://localhost:8081"));
    }

    /**
     * Verifies fallback behavior when Eureka has no Account Service instances.
     */
    @Test
    void baseUri_whenNoAccountServiceInstancesAreDiscovered_returnsDefaultUrl() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DiscoveryClient> provider = mock(ObjectProvider.class);

        when(provider.getIfAvailable()).thenReturn(discoveryClient);
        when(discoveryClient.getInstances("account-service")).thenReturn(List.of());

        AccountServiceInstanceResolver resolver = new AccountServiceInstanceResolver(
                new AccountServiceProperties("account-service", URI.create("http://localhost:8081"), null, null, null, null, null, null),
                provider
        );

        URI uri = resolver.baseUri();

        assertThat(uri).isEqualTo(URI.create("http://localhost:8081"));
    }
}

