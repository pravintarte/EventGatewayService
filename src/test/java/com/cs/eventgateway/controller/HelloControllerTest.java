package com.cs.eventgateway.controller;

import java.time.Clock;

import com.cs.eventgateway.service.HelloService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test for the legacy hello endpoint.
 *
 * <p>The Event Ledger endpoints are covered separately. This test keeps the
 * original skeleton endpoint protected so the existing service metadata contract
 * does not regress while new ledger functionality is added.</p>
 */
@WebMvcTest(HelloController.class)
@Import({HelloService.class, HelloControllerTest.ClockTestConfig.class})
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Ensures the hello endpoint returns the expected skeleton response.
     *
     * <p>The test runs through Spring MVC using {@link MockMvc}, verifying that
     * request mapping, dependency injection, JSON serialization, and the service
     * metadata payload all work together in the web slice.</p>
     *
     * @throws Exception when MockMvc request execution fails
     */
    @Test
    void hello_whenEndpointIsCalled_returnsServiceMetadataAndBaselineCapabilities() throws Exception {
        mockMvc.perform(get("/api/v1/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Hello World")))
                .andExpect(jsonPath("$.serviceName").value("event-gateway-api"))
                .andExpect(jsonPath("$.capabilities", hasItem("spring-boot")));
    }

    @TestConfiguration
    static class ClockTestConfig {

        /**
         * Supplies a UTC clock for the web-slice test context.
         *
         * @return system UTC clock used by {@link HelloService}
         */
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
