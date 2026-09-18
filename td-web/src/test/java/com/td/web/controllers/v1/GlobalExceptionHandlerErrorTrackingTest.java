package com.td.web.controllers.v1;

import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.infrastructure.errortracking.ErrorTrackingService;
import com.td.web.config.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Xác nhận {@link GlobalExceptionHandler} tích hợp Error Tracking đúng: khi
 * {@link ErrorTrackingService} có mặt trong context, Error ID sinh ra được đưa vào
 * {@code ApiResponse.errorId}. Trường hợp bean vắng mặt (đa số {@code @WebMvcTest} khác trong
 * project không import bean này) đã được bảo vệ hồi quy bởi chính các test đó tiếp tục pass mà
 * không cần sửa gì (xem {@code GlobalExceptionHandler.track()} — trả {@code null} êm khi thiếu bean).
 */
@WebMvcTest(controllers = ViewQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerErrorTrackingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ViewQueryService viewQueryService;

    @MockBean
    private ErrorTrackingService errorTrackingService;

    @Test
    void handledException_shouldIncludeErrorIdFromErrorTrackingService() throws Exception {
        when(errorTrackingService.captureError(any(), eq(HttpStatus.BAD_REQUEST), any()))
            .thenReturn("ERR-test-1234");
        when(viewQueryService.execute(any(), any()))
            .thenThrow(new IllegalArgumentException("Khong ho tro viewname: vwKhongTonTai"));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwKhongTonTai");

        mockMvc.perform(post("/api/v1/qlvb/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.errorId").value("ERR-test-1234"));

        verify(errorTrackingService).captureError(any(), eq(HttpStatus.BAD_REQUEST), any());
    }
}
