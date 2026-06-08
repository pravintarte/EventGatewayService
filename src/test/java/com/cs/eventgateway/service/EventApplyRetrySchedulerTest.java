package com.cs.eventgateway.service;

import com.cs.eventgateway.config.tracing.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventApplyRetrySchedulerTest {

    @AfterEach
    void clearMdc() {
        MDC.remove(TraceContext.MDC_TRACE_ID_KEY);
        MDC.remove(TraceContext.MDC_APP_TRACE_ID_KEY);
    }

    @Test
    void retryDueEvents_usesFreshSchedulerTraceAndRestoresPreviousTrace() {
        EventLedgerService eventLedgerService = mock(EventLedgerService.class);
        EventApplyRetryScheduler scheduler = new EventApplyRetryScheduler(eventLedgerService);
        AtomicReference<String> schedulerTraceId = new AtomicReference<>();
        MDC.put(TraceContext.MDC_TRACE_ID_KEY, "http-trace");
        MDC.put(TraceContext.MDC_APP_TRACE_ID_KEY, "http-trace");
        when(eventLedgerService.retryDueEvents()).thenAnswer(invocation -> {
            schedulerTraceId.set(TraceContext.currentOrNewTraceId());
            return 0;
        });

        scheduler.retryDueEvents();

        verify(eventLedgerService).retryDueEvents();
        assertThat(schedulerTraceId.get()).isNotEqualTo("http-trace");
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isEqualTo("http-trace");
        assertThat(MDC.get(TraceContext.MDC_APP_TRACE_ID_KEY)).isEqualTo("http-trace");
    }
}
