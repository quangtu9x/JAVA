package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.infrastructure.errortracking.ErrorLogDto;
import com.td.infrastructure.errortracking.ErrorTrackingService;
import com.td.infrastructure.errortracking.LogType;
import com.td.infrastructure.errortracking.SearchErrorLogsRequest;
import com.td.infrastructure.errortracking.Severity;
import com.td.web.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code addFilters = false} — test này chỉ xác nhận logic controller (map kết quả tra cứu
 * ErrorTrackingService thành response), KHÔNG kiểm tra ràng buộc {@code @PreAuthorize("hasRole('ADMIN')")}
 * (cơ chế phân quyền chung đã được kiểm chứng ở các *PermissionIntegrationTest khác của project).
 */
@WebMvcTest(controllers = ErrorTrackingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ErrorTrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ErrorTrackingService errorTrackingService;

    @Test
    void getByErrorId_shouldReturn200WithDetail_whenFound() throws Exception {
        ErrorLogDto dto = ErrorLogDto.builder()
            .errorId("ERR-abc-123")
            .logType(LogType.BUSINESS)
            .severity(Severity.ERROR)
            .occurredAt(Instant.parse("2026-08-17T00:00:00Z"))
            .httpStatus(500)
            .httpMethod("GET")
            .requestPath("/api/v1/categories")
            .exceptionClass("java.lang.RuntimeException")
            .message("boom")
            .build();
        when(errorTrackingService.findByErrorId("ERR-abc-123")).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/v1/error-logs/ERR-abc-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.data.errorId").value("ERR-abc-123"))
            .andExpect(jsonPath("$.data.logType").value("BUSINESS"))
            .andExpect(jsonPath("$.data.httpStatus").value(500))
            .andExpect(jsonPath("$.data.exceptionClass").value("java.lang.RuntimeException"));
    }

    @Test
    void getByErrorId_shouldReturn404_whenNotFound() throws Exception {
        when(errorTrackingService.findByErrorId("ERR-not-found")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/error-logs/ERR-not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    void search_shouldReturnPagedResults() throws Exception {
        ErrorLogDto dto = ErrorLogDto.builder()
            .errorId("ERR-sys-1")
            .logType(LogType.SYSTEM)
            .severity(Severity.CRITICAL)
            .message("Request xu ly cham: 15000ms")
            .build();
        when(errorTrackingService.search(any(SearchErrorLogsRequest.class), anyInt(), anyInt()))
            .thenReturn(new PaginationResponse<>(List.of(dto), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/error-logs")
                .param("logType", "SYSTEM")
                .param("severity", "CRITICAL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.data[0].errorId").value("ERR-sys-1"))
            .andExpect(jsonPath("$.data[0].logType").value("SYSTEM"));
    }

    @Test
    void search_shouldReturnEmptyPage_whenNoResults() throws Exception {
        when(errorTrackingService.search(any(SearchErrorLogsRequest.class), anyInt(), anyInt()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 20, 0L, 0, true, true));

        mockMvc.perform(get("/api/v1/error-logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
    }
}
