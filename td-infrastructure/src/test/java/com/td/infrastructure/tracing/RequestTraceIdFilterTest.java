package com.td.infrastructure.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestTraceIdFilterTest {

    private final RequestTraceIdFilter filter = new RequestTraceIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_shouldGenerateTraceId_whenNoIncomingHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        AtomicReference<String> traceIdSeenDuringChain = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            traceIdSeenDuringChain.set(MDC.get(RequestTraceIdFilter.TRACE_ID_MDC_KEY));
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(traceIdSeenDuringChain.get()).isNotBlank();
        verify(request).setAttribute(RequestTraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, traceIdSeenDuringChain.get());
        verify(response).setHeader(RequestTraceIdFilter.TRACE_ID_HEADER, traceIdSeenDuringChain.get());
        assertThat(MDC.get(RequestTraceIdFilter.TRACE_ID_MDC_KEY))
            .as("MDC phai duoc clear sau request, tranh ro ri sang request khac dung chung thread")
            .isNull();
    }

    @Test
    void doFilter_shouldReuseIncomingTraceIdHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getHeader(RequestTraceIdFilter.TRACE_ID_HEADER)).thenReturn("existing-trace-id");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(response).setHeader(RequestTraceIdFilter.TRACE_ID_HEADER, "existing-trace-id");
    }

    @Test
    void doFilter_shouldClearMdc_evenWhenChainThrows() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, (req, res) -> {
            throw new RuntimeException("loi gia lap trong controller");
        }));

        assertThat(MDC.get(RequestTraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}
