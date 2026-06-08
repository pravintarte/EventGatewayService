package com.cs.eventgateway.controller;

import com.cs.eventgateway.config.tracing.TraceContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.matchesPattern;
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
    void request_whenTraceHeaderIsMissing_generatesFreshTraceForEachRequest() throws Exception {
        MvcResult first = mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(first.getResponse().getHeader("X-Trace-Id"))
                .isNotBlank()
                .isNotEqualTo(second.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void request_whenStaleTraceExistsAndHeaderIsMissing_doesNotReuseStaleTrace() throws Exception {
        MDC.put(TraceContext.MDC_TRACE_ID_KEY, "stale-trace");
        MDC.put(TraceContext.MDC_APP_TRACE_ID_KEY, "stale-trace");

        try {
            mockMvc.perform(get("/health"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Trace-Id", not("stale-trace")));
        } finally {
            MDC.remove(TraceContext.MDC_TRACE_ID_KEY);
            MDC.remove(TraceContext.MDC_APP_TRACE_ID_KEY);
        }
    }

    @Test
    void request_whenTraceHeaderIsPresent_usesZipkinTraceIdForResponseHeader() throws Exception {
        MDC.put(TraceContext.MDC_TRACE_ID_KEY, "zipkin-trace");

        try {
            mockMvc.perform(get("/health").header("X-Trace-Id", "app-trace"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Trace-Id", not("app-trace")))
                    .andExpect(header().string("X-Trace-Id", matchesPattern("[a-f0-9]{16}|[a-f0-9]{32}")));

            assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isEqualTo("zipkin-trace");
        } finally {
            MDC.remove(TraceContext.MDC_TRACE_ID_KEY);
            MDC.remove(TraceContext.MDC_APP_TRACE_ID_KEY);
        }
    }

    @Test
    void traceScope_preservesZipkinTraceIdAndRestoresAppTraceId() {
        MDC.put(TraceContext.MDC_TRACE_ID_KEY, "zipkin-trace");
        MDC.put(TraceContext.MDC_APP_TRACE_ID_KEY, "previous-app-trace");

        try (TraceContext.TraceScope ignored = TraceContext.startTrace("current-app-trace")) {
            assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isEqualTo("zipkin-trace");
            assertThat(MDC.get(TraceContext.MDC_APP_TRACE_ID_KEY)).isEqualTo("current-app-trace");
        }

        assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isEqualTo("zipkin-trace");
        assertThat(MDC.get(TraceContext.MDC_APP_TRACE_ID_KEY)).isEqualTo("previous-app-trace");

        MDC.remove(TraceContext.MDC_TRACE_ID_KEY);
        MDC.remove(TraceContext.MDC_APP_TRACE_ID_KEY);
    }

    @Test
    void request_whenTraceHeaderIsPresent_doesNotLetCustomHeaderOverrideZipkinTrace() throws Exception {
        mockMvc.perform(get("/health").header("X-Trace-Id", "trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", not("trace-123")))
                .andExpect(header().string("X-Trace-Id", matchesPattern("[a-f0-9]{16}|[a-f0-9]{32}")));
    }
}
