package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.application.documents.CreateDocumentRequest;
import com.td.application.documents.CreateDocumentUseCase;
import com.td.application.documents.DeleteDocumentUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentDto;
import com.td.application.documents.GetDeletedDocumentsUseCase;
import com.td.application.documents.GetDocumentLogsUseCase;
import com.td.application.documents.GetDocumentSearchStatusUseCase;
import com.td.application.documents.GetDocumentUseCase;
import com.td.application.documents.GetDynamicDocumentUseCase;
import com.td.application.documents.GetFileUseCase;
import com.td.application.documents.HardDeleteDocumentUseCase;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.documents.DeleteFileUseCase;
import com.td.application.documents.RenameFileUseCase;
import com.td.application.documents.ReindexDocumentSearchUseCase;
import com.td.application.documents.SearchDocumentsElasticUseCase;
import com.td.application.documents.SearchDocumentsUseCase;
import com.td.application.documents.SyncDocumentSearchByIdUseCase;
import com.td.application.documents.UpdateDocumentUseCase;
import com.td.application.documents.UpdateDocumentWithFileUseCase;
import com.td.application.documents.UploadFileUseCase;
import com.td.application.documents.VerifyFileSignatureUseCase;
import com.td.application.documents.RecordFileAccessUseCase;
import com.td.application.documents.ListFileAccessLogUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.web.config.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-005 (4/4) — Method check: mỗi kiểu phương thức FE đang dùng (GET, POST JSON, POST
 * multipart/form-data) phải có ít nhất 1 endpoint hoạt động đúng qua {@link DocumentsController} —
 * controller duy nhất hiện có cả 3 kiểu trong 1 nơi (list = GET, create = POST JSON,
 * upload file = POST multipart/form-data, giống {@code requestPOST_FORM} trong {@code baseAPI.js}).
 */
@WebMvcTest(controllers = DocumentsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FeCompatibilityMethodPatternTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private SearchDocumentsElasticUseCase searchDocumentsElasticUseCase;
    @MockBean
    private GetDocumentSearchStatusUseCase getDocumentSearchStatusUseCase;
    @MockBean
    private ReindexDocumentSearchUseCase reindexDocumentSearchUseCase;
    @MockBean
    private SyncDocumentSearchByIdUseCase syncDocumentSearchByIdUseCase;
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
    // Bo sung 18/09/2026 - xem ghi chu trong DocumentLogsEndpointTest (bay "WebMvcTest MockBean drift").
    @MockBean
    private RecordFileAccessUseCase recordFileAccessUseCase;
    @MockBean
    private ListFileAccessLogUseCase listFileAccessLogUseCase;

    @Test
    void get_listDocuments_shouldReturn200WithEnvelope() throws Exception {
        when(documentCacheService.getList(any())).thenReturn(null);
        when(searchDocumentsUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.<DocumentDto>of(), 0, 10, 0L, 0, true, true));
        when(listDocumentFilesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 100, 0L, 0, true, true));

        mockMvc.perform(get("/api/v1/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void post_createDocument_shouldReturn201WithEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(createDocumentUseCase.execute(any())).thenReturn(Result.success(id));

        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setTitle("Van ban test BL-005");

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.code").value(201))
            .andExpect(jsonPath("$.data").value(id.toString()));
    }

    @Test
    void postFormData_uploadFile_shouldReturn201WithEnvelope() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(uploadFileUseCase.execute(any())).thenReturn(Result.success(fileId));

        MockMultipartFile file = new MockMultipartFile(
            "file", "vanban.pdf", MediaType.APPLICATION_PDF_VALUE, "noi dung test".getBytes());

        // Giống hệt requestPOST_FORM trong baseAPI.js: Content-Type multipart/form-data
        mockMvc.perform(multipart("/api/v1/documents/{documentId}/files", documentId)
                .file(file))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.code").value(201))
            .andExpect(jsonPath("$.data").value(fileId.toString()));
    }
}
