package com.td.web.controllers.v1;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.application.documents.CreateDocumentUseCase;
import com.td.application.documents.DeleteDocumentUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.GetDeletedDocumentsUseCase;
import com.td.application.documents.GetDocumentLogsUseCase;
import com.td.application.documents.GetDocumentSearchStatusUseCase;
import com.td.application.documents.GetDocumentUseCase;
import com.td.application.documents.GetDynamicDocumentRequest;
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
import com.td.application.documents.ChuKySoDto;
import com.td.application.documents.VerifyFileSignatureUseCase;
import com.td.application.documents.RecordFileAccessUseCase;
import com.td.application.documents.ListFileAccessLogUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.web.config.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-101 (Dynamic Data Engine) — Definition of Done (2)/(3): xác thực contract HTTP của
 * {@code POST /api/v1/documents/add} (form-data, tương thích {@code qlvb/add}) và
 * {@code POST /api/v1/documents/{id}/get} (tương thích {@code qlvb/get}, {@code items="getall"}).
 */
@WebMvcTest(controllers = DocumentsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DynamicDocumentEndpointsTest {

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
    void postFormData_addDynamicDocument_shouldReturn201WithId() throws Exception {
        UUID id = UUID.randomUUID();
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/documents/add")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("title", "Van ban den test BL-101")
                .param("received_date", "2026-08-04")
                .param("so_tien_number", "150000")
                .param("process_obj", "{\"step\":1}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.code").value(201))
            .andExpect(jsonPath("$.data").value(id.toString()));

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsOnlyKeys("received_date", "so_tien_number", "process_obj");
    }

    @Test
    void postFormData_addDynamicDocument_shouldExcludeMetaKeysFromDynamicFields() throws Exception {
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/documents/add")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("title", "Van ban den test")
                .param("documentType", "VBDEN")
                .param("status", "DRAFT")
                .param("date_fields", "custom_date")
                .param("custom_date", "2026-08-04"))
            .andExpect(status().isCreated());

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase).execute(captor.capture());
        AddDynamicDocumentRequest captured = captor.getValue();

        assertThat(captured.getTitle()).isEqualTo("Van ban den test");
        assertThat(captured.getDocumentType()).isEqualTo("VBDEN");
        assertThat(captured.getDateFields()).isEqualTo("custom_date");
        assertThat(captured.getFields()).containsOnlyKeys("custom_date");
    }

    @Test
    void postJson_getDynamicDocument_withGetAll_shouldReturnAllFields() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", id);
        allFields.put("subject", "Van ban den test BL-101");
        allFields.put("received_date", "2026-08-04");
        allFields.put("so_tien_number", 150000.0);
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(allFields));

        GetDynamicDocumentRequest request = new GetDynamicDocumentRequest();
        request.setItems("getall");

        mockMvc.perform(post("/api/v1/documents/{id}/get", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.data.subject").value("Van ban den test BL-101"))
            .andExpect(jsonPath("$.data.received_date").value("2026-08-04"))
            .andExpect(jsonPath("$.data.so_tien_number").value(150000.0));

        verify(getDynamicDocumentUseCase).execute(
            argThat((GetDynamicDocumentRequest req) -> req.getId().equals(id) && "getall".equals(req.getItems())));
    }

    @Test
    void postJson_getDynamicDocument_withoutBody_shouldDefaultToGetAll() throws Exception {
        UUID id = UUID.randomUUID();
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(Map.of("id", id)));

        mockMvc.perform(post("/api/v1/documents/{id}/get", id))
            .andExpect(status().isOk());

        verify(getDynamicDocumentUseCase).execute(argThat((GetDynamicDocumentRequest req) -> "getall".equals(req.getItems())));
    }

    /**
     * BL-221 (VBDi — Ký số & Gửi đi) — DoD (2): xác thực chữ ký số, tổng quát cho mọi documentType
     * (không riêng vbdi — đúng thiết kế databasetype-agnostic thật của TDAttachment).
     */
    @Test
    void postJson_verifySignature_shouldReturnSignatureList() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        var signature = ChuKySoDto.builder()
            .name("NGUYEN VAN A")
            .time("05/08/2026 10:00:00")
            .integrityValid(true)
            .coversWholeDocument(true)
            .build();
        when(verifyFileSignatureUseCase.execute(any())).thenReturn(Result.success(List.of(signature)));

        mockMvc.perform(post("/api/v1/documents/{documentId}/files/{fileId}/verify-signature", documentId, fileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("NGUYEN VAN A"))
            .andExpect(jsonPath("$.data[0].integrity_valid").value(true));

        verify(verifyFileSignatureUseCase).execute(argThat(req ->
            req.getDocumentId().equals(documentId) && req.getFileId().equals(fileId)));
    }

    @Test
    void postJson_verifySignature_withoutSignature_shouldReturnFailureStatus() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(verifyFileSignatureUseCase.execute(any())).thenReturn(Result.failure("Không tìm thấy chữ ký số trong file"));

        mockMvc.perform(post("/api/v1/documents/{documentId}/files/{fileId}/verify-signature", documentId, fileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false));
    }
}
