package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.application.documents.CreateDocumentUseCase;
import com.td.application.documents.DeleteDocumentUseCase;
import com.td.application.documents.DeleteFileUseCase;
import com.td.application.documents.RenameFileUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogDto;
import com.td.application.documents.GetDeletedDocumentsUseCase;
import com.td.application.documents.GetDocumentLogsUseCase;
import com.td.application.documents.GetDocumentUseCase;
import com.td.application.documents.GetDynamicDocumentUseCase;
import com.td.application.documents.GetFileUseCase;
import com.td.application.documents.HardDeleteDocumentUseCase;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.documents.SearchDocumentsUseCase;
import com.td.application.documents.UpdateDocumentUseCase;
import com.td.application.documents.UpdateDocumentWithFileUseCase;
import com.td.application.documents.UploadFileUseCase;
import com.td.application.documents.VerifyFileSignatureUseCase;
import com.td.application.documents.RecordFileAccessUseCase;
import com.td.application.documents.ListFileAccessLogUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.web.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-103 (Audit Log) — Definition of Done (5): API tra cứu log theo documentId.
 */
@WebMvcTest(controllers = DocumentsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DocumentLogsEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateDocumentUseCase createDocumentUseCase;
    @MockBean
    private AddDynamicDocumentUseCase addDynamicDocumentUseCase;
    @MockBean
    private GetDynamicDocumentUseCase getDynamicDocumentUseCase;
    @MockBean
    private UpdateDocumentUseCase updateDocumentUseCase;
    @MockBean
    private DeleteDocumentUseCase deleteDocumentUseCase;
    @MockBean
    private HardDeleteDocumentUseCase hardDeleteDocumentUseCase;
    @MockBean
    private GetDocumentUseCase getDocumentUseCase;
    @MockBean
    private SearchDocumentsUseCase searchDocumentsUseCase;
    @MockBean
    private GetDeletedDocumentsUseCase getDeletedDocumentsUseCase;
    @MockBean
    private GetDocumentLogsUseCase getDocumentLogsUseCase;
    @MockBean
    private UploadFileUseCase uploadFileUseCase;
    @MockBean
    private GetFileUseCase getFileUseCase;
    @MockBean
    private ListDocumentFilesUseCase listDocumentFilesUseCase;
    @MockBean
    private DeleteFileUseCase deleteFileUseCase;
    @MockBean
    private RenameFileUseCase renameFileUseCase;
    @MockBean
    private UpdateDocumentWithFileUseCase updateDocumentWithFileUseCase;
    @MockBean
    private VerifyFileSignatureUseCase verifyFileSignatureUseCase;
    @MockBean
    private MinioService minioService;
    @MockBean
    private DocumentCacheService documentCacheService;
    // Bo sung 18/09/2026 - 2 mock nay bi thieu tu truoc (constructor DocumentsController da co san
    // nhung file test chua duoc cap nhat theo, dung bay "WebMvcTest MockBean drift").
    @MockBean
    private RecordFileAccessUseCase recordFileAccessUseCase;
    @MockBean
    private ListFileAccessLogUseCase listFileAccessLogUseCase;

    @Test
    void getDocumentLogs_shouldReturn200WithEnvelope() throws Exception {
        UUID documentId = UUID.randomUUID();

        DocumentLogDto createLog = new DocumentLogDto();
        createLog.setId(UUID.randomUUID());
        createLog.setDocumentId(documentId);
        createLog.setLogType("create");
        createLog.setLogData(Map.of("newValue", Map.of("title", "Van ban A")));
        createLog.setCreatedBy("alice");
        createLog.setCreatedAt(LocalDateTime.now());

        when(getDocumentLogsUseCase.execute(eq(documentId), eq(0), eq(20)))
            .thenReturn(new PaginationResponse<>(List.of(createLog), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/documents/{id}/logs", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.data[0].logType").value("create"))
            .andExpect(jsonPath("$.data[0].createdBy").value("alice"));

        verify(getDocumentLogsUseCase).execute(eq(documentId), eq(0), eq(20));
    }

    @Test
    void getDocumentLogs_shouldPassCustomPagination() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(getDocumentLogsUseCase.execute(any(), any(Integer.class), any(Integer.class)))
            .thenReturn(new PaginationResponse<>(List.of(), 2, 5, 0L, 0, false, true));

        mockMvc.perform(get("/api/v1/documents/{id}/logs", documentId)
                .param("pageNumber", "2")
                .param("pageSize", "5"))
            .andExpect(status().isOk());

        verify(getDocumentLogsUseCase).execute(eq(documentId), eq(2), eq(5));
    }
}
