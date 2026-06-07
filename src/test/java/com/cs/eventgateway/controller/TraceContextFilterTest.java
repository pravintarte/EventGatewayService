package com.cs.eventgateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "event-gateway.retry.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.zipkin.autoconfigure.ZipkinAutoConfiguration"
})
@AutoConfigureMockMvc
class TraceContextFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void request_whenTraceHeaderIsMissing_generatesTraceHeader() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", not(blankOrNullString())));
    }

    @Test
    void request_whenTraceHeaderIsPresent_echoesTraceHeader() throws Exception {
        mockMvc.perform(get("/health").header("X-Trace-Id", "trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-123"));
    }
}
