package com.td.web.controllers.v1;

import com.td.application.common.models.Result;
import com.td.application.realtime.UserNotificationInboxUseCase;
import com.td.web.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserNotificationsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserNotificationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserNotificationInboxUseCase inboxUseCase;

    @Test
    void list_passesFiltersAndWrapsEnvelope() throws Exception {
        when(inboxUseCase.list(1, 10, true, "vbden")).thenReturn(Result.success(Map.of(
            "items", List.of(Map.of("notification_id", "n1", "action", "chuyenxuly")),
            "total", 1L, "unreadCount", 3L)));

        mockMvc.perform(get("/api/v1/notifications")
                .param("page", "1").param("size", "10").param("unreadOnly", "true").param("databaseType", "vbden"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.data.items[0].action").value("chuyenxuly"))
            .andExpect(jsonPath("$.data.unreadCount").value(3));
    }

    @Test
    void readDocument_readsDocumentIdFromBody() throws Exception {
        when(inboxUseCase.markReadByDocument("doc-1")).thenReturn(Result.success(2));

        mockMvc.perform(post("/api/v1/notifications/read-document")
                .contentType(MediaType.APPLICATION_JSON).content("{\"documentId\":\"doc-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(2));
        verify(inboxUseCase).markReadByDocument("doc-1");
    }

    @Test
    void readAll_withoutBody_marksEverything() throws Exception {
        when(inboxUseCase.markAllRead(null)).thenReturn(Result.success(5));

        mockMvc.perform(post("/api/v1/notifications/read-all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    void markRead_byId() throws Exception {
        UUID id = UUID.randomUUID();
        when(inboxUseCase.markRead(id)).thenReturn(Result.success(1));

        mockMvc.perform(post("/api/v1/notifications/{id}/read", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(1));
    }
}
