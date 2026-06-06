package com.eventgateway.api.client;

import java.net.URI;
import java.util.List;

import com.eventgateway.api.config.AccountServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Account Service discovery URI resolution.
 *
 * <p>The resolver bridges Eureka service discovery and the REST client. These
 * tests verify that a discovered service instance URI is converted into the
 * exact Account Service transaction endpoint required by the gateway, including
 * the account id path segment.</p>
 */
class AccountServiceInstanceResolverTest {

    /**
     * Verifies successful Account Service endpoint construction from a
     * discovered Eureka instance.
     *
     * <p>The test mocks {@link DiscoveryClient} and {@link ServiceInstance} so
     * it can assert URI construction without running a real Eureka registry. The
     * resolver should use the configured service id, choose the discovered base
     * URI, and append {@code /accounts/{accountId}/transactions}.</p>
     */
    @Test
    void transactionUri_whenEurekaReturnsAccountServiceInstance_buildsConcreteAccountTransactionEndpoint() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        ServiceInstance serviceInstance = mock(ServiceInstance.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DiscoveryClient> provider = mock(ObjectProvider.class);

        when(provider.getIfAvailable()).thenReturn(discoveryClient);
        when(discoveryClient.getInstances("account-service")).thenReturn(List.of(serviceInstance));
        when(serviceInstance.getUri()).thenReturn(URI.create("http://account-service-host:8081"));

        AccountServiceInstanceResolver resolver = new AccountServiceInstanceResolver(
                new AccountServiceProperties("account-service"),
                provider
        );

        URI uri = resolver.transactionUri("acct-123");

        assertThat(uri).isEqualTo(URI.create("http://account-service-host:8081/accounts/acct-123/transactions"));
    }
}
