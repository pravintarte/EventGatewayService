package com.eventgateway.api.controller;

import com.eventgateway.api.config.ApplicationConfig;
import com.eventgateway.api.service.HelloService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test for the hello endpoint.
 */
@WebMvcTest(HelloController.class)
@Import({HelloService.class, ApplicationConfig.class})
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Ensures the hello endpoint returns the expected skeleton response.
     *
     * @throws Exception when MockMvc request execution fails
     */
    @Test
    void helloReturnsServiceMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Hello World")))
                .andExpect(jsonPath("$.serviceName").value("event-gateway-api"))
                .andExpect(jsonPath("$.capabilities", hasItem("spring-boot")));
    }
}
