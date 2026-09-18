package com.td.infrastructure.errortracking;

import com.td.infrastructure.security.CurrentUserContext;
import com.td.infrastructure.security.CurrentUserContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrorTrackingServiceTest {

    @Mock
    private ErrorLogJdbcRepository errorLogRepository;

    @Mock
    private CurrentUserContextResolver currentUserContextResolver;

    private ErrorTrackingProperties properties;
    private ErrorTrackingService service;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        properties = new ErrorTrackingProperties();
        service = new ErrorTrackingService(errorLogRepository, properties, currentUserContextResolver);

        // lenient(): khong phai test nao cung di toi buildEntity() (vd. khi enabled=false hoac
        // findByErrorId khong dung request) - tranh Mockito bao "UnnecessaryStubbing".
        request = mock(HttpServletRequest.class);
        lenient().when(request.getMethod()).thenReturn("GET");
        lenient().when(request.getRequestURI()).thenReturn("/api/v1/categories/123");
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    // ── captureError (BUSINESS) ─────────────────────────────────────────────

    @Test
    void captureError_shouldReturnNonBlankErrorId_startingWithErrPrefix() {
        when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.empty());

        String errorId = service.captureError(request, HttpStatus.INTERNAL_SERVER_ERROR, new RuntimeException("boom"));

        assertThat(errorId).isNotBlank().startsWith("ERR-");
    }

    @Test
    void captureError_shouldPersistEntityWithSanitizedContext_andSeverityWarnFor4xx() {
        CurrentUserContext ctx = CurrentUserContext.builder().username("nguyenvana").build();
        when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.of(ctx));

        String errorId = service.captureError(request, HttpStatus.BAD_REQUEST,
            new IllegalArgumentException("Sai du lieu voi password=abc123"));

        verify(errorLogRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
            entity != null
                && errorId.equals(entity.getErrorId())
                && entity.getLogType() == LogType.BUSINESS
                && entity.getSeverity() == Severity.WARN
                && Integer.valueOf(400).equals(entity.getHttpStatus())
                && "GET".equals(entity.getHttpMethod())
                && "/api/v1/categories/123".equals(entity.getRequestPath())
                && "nguyenvana".equals(entity.getUsername())
                && entity.getMessage() != null
                && !entity.getMessage().contains("abc123")
        ));
    }

    @Test
    void captureError_shouldClassifySeverityError_for5xx() {
        when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.empty());

        service.captureError(request, HttpStatus.INTERNAL_SERVER_ERROR, new RuntimeException("boom"));

        verify(errorLogRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
            entity.getSeverity() == Severity.ERROR && entity.getLogType() == LogType.BUSINESS));
    }

    @Test
    void captureError_shouldCaptureAndSanitizeRequestBody_whenContentCachingWrapperPresent() throws Exception {
        when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.empty());

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/api/v1/categories");
        String rawBody = "{\"name\":\"abc\",\"password\":\"SuperSecret123\"}";
        mockRequest.setContent(rawBody.getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(mockRequest);
        wrapper.getInputStream().readAllBytes(); // gia lap Jackson da doc body (nhu khi that bai deserialize)

        service.captureError(wrapper, HttpStatus.BAD_REQUEST, new RuntimeException("boom"));

        verify(errorLogRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
            entity.getRequestBody() != null
                && entity.getRequestBody().contains("\"name\":\"abc\"")
                && !entity.getRequestBody().contains("SuperSecret123")));
    }

    @Test
    void captureError_shouldHaveNullRequestBody_whenRequestNotWrapped() {
        when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.empty());

        service.captureError(request, HttpStatus.BAD_REQUEST, new RuntimeException("boom"));

        verify(errorLogRepository).save(org.mockito.ArgumentMatchers.argThat(entity -> entity.getRequestBody() == null));
    }

    @Test
    void captureError_shouldNeverThrow_whenRepositorySaveFails() {
        when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new RuntimeException("DB Error Tracking down")).when(errorLogRepository).save(any());

        String errorId = service.captureError(request, HttpStatus.INTERNAL_SERVER_ERROR, new RuntimeException("boom"));

        assertThat(errorId).isNotBlank();
    }

    @Test
    void captureError_shouldSkipPersistence_whenDisabled() {
        properties.setEnabled(false);

        String errorId = service.captureError(request, HttpStatus.INTERNAL_SERVER_ERROR, new RuntimeException("boom"));

        assertThat(errorId).isNotBlank();
        verify(errorLogRepository, never()).save(any());
    }

    @Test
    void captureError_shouldNeverThrow_whenCurrentUserResolverFails() {
        when(currentUserContextResolver.resolveCurrentUserContext()).thenThrow(new RuntimeException("cache down"));

        String errorId = service.captureError(request, HttpStatus.INTERNAL_SERVER_ERROR, new RuntimeException("boom"));

        assertThat(errorId).isNotBlank();
    }

    // ── captureSlowRequest (SYSTEM) ──────────────────────────────────────────

    @Test
    void captureSlowRequest_shouldUseSeverityWarn_belowCriticalThreshold() {
        lenient().when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.empty());

        String errorId = service.captureSlowRequest(request, HttpStatus.OK, 5000L);

        assertThat(errorId).startsWith("ERR-");
        verify(errorLogRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
            entity.getLogType() == LogType.SYSTEM
                && entity.getSeverity() == Severity.WARN
                && entity.getDurationMs() == 5000L
                && entity.getExceptionClass() == null));
    }

    @Test
    void captureSlowRequest_shouldUseSeverityCritical_aboveCriticalThreshold() {
        lenient().when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.empty());

        service.captureSlowRequest(request, HttpStatus.OK, 15_000L);

        verify(errorLogRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
            entity.getSeverity() == Severity.CRITICAL && entity.getLogType() == LogType.SYSTEM));
    }

    @Test
    void captureSlowRequest_shouldSkipPersistence_whenDisabled() {
        properties.setEnabled(false);

        service.captureSlowRequest(request, HttpStatus.OK, 5000L);

        verify(errorLogRepository, never()).save(any());
    }

    // ── captureSystemEvent (SYSTEM, khong gan request) ──────────────────────

    @Test
    void captureSystemEvent_shouldPersistWithoutHttpContext() {
        String errorId = service.captureSystemEvent("Phat hien deadlock", Severity.CRITICAL, "thread dump...");

        assertThat(errorId).startsWith("ERR-");
        verify(errorLogRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
            entity.getLogType() == LogType.SYSTEM
                && entity.getSeverity() == Severity.CRITICAL
                && entity.getHttpStatus() == null
                && entity.getHttpMethod() == null
                && "Phat hien deadlock".equals(entity.getMessage())));
    }

    // ── findByErrorId / search ───────────────────────────────────────────────

    @Test
    void findByErrorId_shouldReturnEmpty_whenBlank() {
        assertThat(service.findByErrorId(" ")).isEmpty();
        assertThat(service.findByErrorId(null)).isEmpty();
    }

    @Test
    void findByErrorId_shouldMapEntityToDto() {
        ErrorLogEntity entity = ErrorLogEntity.builder()
            .errorId("ERR-abc")
            .logType(LogType.BUSINESS)
            .severity(Severity.ERROR)
            .httpStatus(500)
            .exceptionClass("java.lang.RuntimeException")
            .build();
        when(errorLogRepository.findById("ERR-abc")).thenReturn(Optional.of(entity));

        Optional<ErrorLogDto> result = service.findByErrorId("ERR-abc");

        assertThat(result).isPresent();
        assertThat(result.get().getErrorId()).isEqualTo("ERR-abc");
        assertThat(result.get().getExceptionClass()).isEqualTo("java.lang.RuntimeException");
        assertThat(result.get().getLogType()).isEqualTo(LogType.BUSINESS);
    }

    @Test
    void findByErrorId_shouldReturnEmpty_whenRepositoryThrows() {
        when(errorLogRepository.findById("ERR-abc")).thenThrow(new RuntimeException("DB down"));

        assertThat(service.findByErrorId("ERR-abc")).isEmpty();
    }

    @Test
    void search_shouldReturnMappedPage() {
        ErrorLogEntity entity = ErrorLogEntity.builder().errorId("ERR-1").logType(LogType.SYSTEM).severity(Severity.WARN).build();
        SearchErrorLogsRequest searchRequest = SearchErrorLogsRequest.builder().logType(LogType.SYSTEM).build();
        when(errorLogRepository.count(searchRequest)).thenReturn(1L);
        when(errorLogRepository.search(searchRequest, 0, 20)).thenReturn(List.of(entity));

        var result = service.search(searchRequest, 0, 20);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getErrorId()).isEqualTo("ERR-1");
        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void search_shouldReturnEmptyPage_whenRepositoryThrows() {
        SearchErrorLogsRequest searchRequest = SearchErrorLogsRequest.builder().build();
        when(errorLogRepository.count(searchRequest)).thenThrow(new RuntimeException("DB down"));

        var result = service.search(searchRequest, 0, 20);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalItems()).isZero();
    }
}
