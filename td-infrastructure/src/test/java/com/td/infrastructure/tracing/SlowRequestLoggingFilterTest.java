package com.td.infrastructure.tracing;

import com.td.infrastructure.errortracking.ErrorTrackingProperties;
import com.td.infrastructure.errortracking.ErrorTrackingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlowRequestLoggingFilterTest {

    private final SlowRequestLoggingFilter filter = new SlowRequestLoggingFilter();
    private ErrorTrackingService errorTrackingService;
    private ErrorTrackingProperties properties;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        errorTrackingService = mock(ErrorTrackingService.class);
        properties = new ErrorTrackingProperties();
        filter.setErrorTrackingService(errorTrackingService);
        filter.setErrorTrackingProperties(properties);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);
    }

    @Test
    void doFilter_shouldNotCapture_whenRequestFasterThanThreshold() throws Exception {
        properties.getSlowRequest().setWarnThresholdMs(3000);
        FilterChain chain = (req, res) -> { /* nhanh - khong sleep */ };

        filter.doFilter(request, response, chain);

        verify(errorTrackingService, never()).captureSlowRequest(any(), any(), anyLong());
    }

    @Test
    void doFilter_shouldCapture_whenRequestSlowerThanThreshold() throws Exception {
        properties.getSlowRequest().setWarnThresholdMs(0); // moi request deu tinh la "cham" de test khong can sleep that
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        verify(errorTrackingService).captureSlowRequest(eq(request), eq(HttpStatus.OK), anyLong());
    }

    @Test
    void doFilter_shouldSkip_whenErrorTrackingServiceAbsent() throws Exception {
        filter.setErrorTrackingService(null);
        properties.getSlowRequest().setWarnThresholdMs(0);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldSkip_whenSlowRequestDetectionDisabled() throws Exception {
        properties.getSlowRequest().setEnabled(false);
        properties.getSlowRequest().setWarnThresholdMs(0);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(errorTrackingService, never()).captureSlowRequest(any(), any(), anyLong());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldNotCapture_whenErrorTrackingDisabledGlobally() throws Exception {
        properties.setEnabled(false);
        properties.getSlowRequest().setWarnThresholdMs(0);
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        verify(errorTrackingService, never()).captureSlowRequest(any(), any(), anyLong());
    }

    @Test
    void doFilter_shouldStillCapture_evenWhenChainThrows() {
        properties.getSlowRequest().setWarnThresholdMs(0);

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, (req, res) -> {
            throw new RuntimeException("loi gia lap trong controller");
        }));

        verify(errorTrackingService).captureSlowRequest(eq(request), any(), anyLong());
    }
}
