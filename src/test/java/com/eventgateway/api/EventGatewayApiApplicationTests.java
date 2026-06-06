package com.eventgateway.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the complete Spring application context can be created.
 *
 * <p>This smoke test catches missing beans, invalid configuration properties,
 * broken JPA mappings, and incompatible auto-configuration before the
 * application is run manually. Eureka is disabled for this test because context
 * startup should not depend on an external service registry process.</p>
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
class EventGatewayApiApplicationTests {

    /**
     * Loads the full application context using the production component graph.
     *
     * <p>No assertions are required because the test fails if Spring cannot
     * create the context. That includes the Event Ledger repository, controllers,
     * service discovery beans, REST client configuration, and exception
     * handling infrastructure.</p>
     */
    @Test
    void contextLoads_whenApplicationConfigurationIsValid_startsCompleteSpringBootContext() {
    }
}
