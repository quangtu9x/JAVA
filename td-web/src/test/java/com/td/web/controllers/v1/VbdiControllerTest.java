package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.vbdi.BanHanhVbdiUseCase;
import com.td.application.vbdi.GuiEmailVbdiUseCase;
import com.td.application.vbdi.GetVbdiDetailUseCase;
import com.td.application.vbdi.KySoVbdiUseCase;
import com.td.application.vbdi.GanKemTepVbdiUseCase;
import com.td.application.vbdi.ListVbdiSendLogUseCase;
import com.td.application.vbdi.SendVbdiDocumentUseCase;
import com.td.application.vbdi.HieuChinhVbdiUseCase;
import com.td.application.vbdi.AddVbdiEditHistoryUseCase;
import com.td.application.vbdi.ListVbdiEditHistoryUseCase;
import com.td.application.vbdi.SoanThaoVbdiUseCase;
import com.td.application.vbdi.TrinhKyVbdiUseCase;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.web.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-220 (VBDi Core) — Definition of Done: soạn thảo, trình ký, ban hành (sinh số đi), danh sách
 * theo view — tất cả tương thích khuôn dạng API {@code /api/v1/vbdi/*}.
 */
@WebMvcTest(controllers = VbdiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class VbdiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SoanThaoVbdiUseCase soanThaoVbdiUseCase;
    @MockBean
    private HieuChinhVbdiUseCase hieuChinhVbdiUseCase;
    // Bo sung 13/09/2026 (pre-existing gap phat hien khi rieng port thay the/thu hoi vbden - vbdiController
    // da them tham so constructor nay tu 11/09/2026 nhung test file chua duoc cap nhat theo, khien
    // TOAN BO shared WebMvcTest context (WebSliceTestApplication, dung chung cho moi *ControllerTest)
    // fail load - vo tinh chan luon viec kiem chung thay doi khac trong cung file nay).
    @MockBean
    private com.td.application.vbdi.CapNhatVanBanDiUseCase capNhatVanBanDiUseCase;
    @MockBean
    private AddVbdiEditHistoryUseCase addVbdiEditHistoryUseCase;
    @MockBean
    private ListVbdiEditHistoryUseCase listVbdiEditHistoryUseCase;
    @MockBean
    private TrinhKyVbdiUseCase trinhKyVbdiUseCase;
    @MockBean
    private BanHanhVbdiUseCase banHanhVbdiUseCase;
    @MockBean
    private KySoVbdiUseCase kySoVbdiUseCase;
    @MockBean
    private GanKemTepVbdiUseCase ganKemTepVbdiUseCase;
    @MockBean
    private GuiEmailVbdiUseCase guiEmailVbdiUseCase;
    @MockBean
    private GetVbdiDetailUseCase getVbdiDetailUseCase;
    @MockBean
    private ViewQueryService viewQueryService;
    @MockBean
    private SendVbdiDocumentUseCase sendVbdiDocumentUseCase;
    @MockBean
    private ListVbdiSendLogUseCase listVbdiSendLogUseCase;
    @MockBean
    private com.td.application.vbdi.GanVbdiDocumentUseCase ganVbdiDocumentUseCase;
    @MockBean
    private com.td.application.vbdi.AddVbdiUserActionLogUseCase addVbdiUserActionLogUseCase;
    @MockBean
    private com.td.application.vbdi.ListVbdiUserActionLogUseCase listVbdiUserActionLogUseCase;
    @MockBean
    private com.td.application.vbdi.AddVbdiSeenDocumentUseCase addVbdiSeenDocumentUseCase;
    @MockBean
    private com.td.application.vbdi.AddVbdiImportantDocumentUseCase addVbdiImportantDocumentUseCase;
    @MockBean
    private com.td.application.vbdi.ChuyenXemDeBietVbdiUseCase chuyenXemDeBietVbdiUseCase;
    // Bo sung 13/09/2026 - cung nhom pre-existing gap voi CapNhatVanBanDiUseCase o tren (VbdiController
    // da co san cac tham so nay tu truoc, test file chua duoc cap nhat theo).
    @MockBean
    private com.td.application.vbdi.DeleteVbdiDocumentUseCase deleteVbdiDocumentUseCase;
    @MockBean
    private com.td.application.vbdi.GanVanBanLienQuanUseCase ganVanBanLienQuanUseCase;
    @MockBean
    private com.td.application.vbdi.RestoreVbdiDocumentUseCase restoreVbdiDocumentUseCase;
    @MockBean
    private com.td.application.vbdi.TraLoiVanBanDenUseCase traLoiVanBanDenUseCase;
    @MockBean
    private com.td.application.vbdi.XoaTraLoiVanBanDenUseCase xoaTraLoiVanBanDenUseCase;
    @MockBean
    private com.td.application.vbdi.XoaVanBanLienQuanUseCase xoaVanBanLienQuanUseCase;

    @Test
    void soanThao_shouldDelegateToSoanThaoVbdiUseCaseAndReturn201() throws Exception {
        UUID id = UUID.randomUUID();
        when(soanThaoVbdiUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/vbdi/SoanThao")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("subject", "Cong van ve viec kiem tra")
                .param("unit_in_charge", "So Noi Vu"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(id.toString()));

        ArgumentCaptor<com.td.application.vbdi.SoanThaoVbdiRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.SoanThaoVbdiRequest.class);
        verify(soanThaoVbdiUseCase).execute(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Cong van ve viec kiem tra");
        assertThat(captor.getValue().getFields()).containsEntry("unit_in_charge", "So Noi Vu");
    }

    @Test
    void soanThao_withDocumentTypeField_shouldKeepItAsDynamicField() throws Exception {
        // Bugfix 24/08/2026 (dong bo VbdenController): "document_type" la field nghiep vu "Loai van
        // ban" (Cong van/Bao cao/...), khong phai meta module discriminator - khong duoc strip.
        UUID id = UUID.randomUUID();
        when(soanThaoVbdiUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/vbdi/SoanThao")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("subject", "Cong van ve viec kiem tra")
                .param("document_type", "Công văn"))
            .andExpect(status().isCreated());

        ArgumentCaptor<com.td.application.vbdi.SoanThaoVbdiRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.SoanThaoVbdiRequest.class);
        verify(soanThaoVbdiUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("document_type", "Công văn");
    }

    @Test
    void trinhKy_shouldDelegateToTrinhKyVbdiUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(trinhKyVbdiUseCase.execute(any())).thenReturn(Result.success(childId));

        mockMvc.perform(post("/api/v1/vbdi/TrinhKy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"nguoiKy\":\"lanhdao01\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));
    }

    @Test
    void trinhKy_withoutNguoiKy_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/TrinhKy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void banHanh_shouldDelegateToBanHanhVbdiUseCaseAndReturn201() throws Exception {
        when(banHanhVbdiUseCase.execute(any())).thenReturn(Result.success("5/2026-VBD"));

        mockMvc.perform(post("/api/v1/vbdi/BanHanh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value("5/2026-VBD"));
    }

    @Test
    void banHanh_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/BanHanh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void kySo_shouldDelegateToKySoVbdiUseCaseAndReturn201() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(kySoVbdiUseCase.execute(any())).thenReturn(Result.success(fileId));

        MockMultipartFile file = new MockMultipartFile("file", "vbdi_signed.pdf", "application/pdf", "pdf-bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/vbdi/KySo")
                .file(file)
                .param("id", UUID.randomUUID().toString())
                .param("coQuan", "true"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(fileId.toString()));

        ArgumentCaptor<com.td.application.vbdi.KySoVbdiRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.KySoVbdiRequest.class);
        verify(kySoVbdiUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFileName()).isEqualTo("vbdi_signed.pdf");
        assertThat(captor.getValue().isCoQuan()).isTrue();
    }

    @Test
    void guiEmail_shouldDelegateToGuiEmailVbdiUseCaseAndReturn200() throws Exception {
        when(guiEmailVbdiUseCase.execute(any())).thenReturn(Result.success(null));

        mockMvc.perform(post("/api/v1/vbdi/GuiEmail")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"toEmails\":[\"a@example.com\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true));
    }

    @Test
    void guiEmail_withoutToEmails_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/GuiEmail")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"toEmails\":[]}"))
            .andExpect(status().isBadRequest());
    }

    // Tách bảng riêng vbdi_documents (yêu cầu người dùng) — "xem chi tiết" phải đọc bảng này qua
    // GetVbdiDetailUseCase, KHÔNG còn qua GET /api/v1/documents/{id} generic nữa.
    @Test
    void xemChiTiet_shouldDelegateToGetVbdiDetailUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(getVbdiDetailUseCase.execute(any()))
            .thenReturn(Result.success(Map.of("id", id.toString(), "title", "Van ban di")));

        mockMvc.perform(post("/api/v1/vbdi/XemChiTiet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Van ban di"));

        ArgumentCaptor<com.td.application.vbdi.GetVbdiDetailRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.GetVbdiDetailRequest.class);
        verify(getVbdiDetailUseCase).execute(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id.toString());
    }

    @Test
    void xemChiTiet_withoutId_shouldDelegateAndReturnFailurePayload() throws Exception {
        when(getVbdiDetailUseCase.execute(any())).thenReturn(Result.failure("Thiếu id văn bản đi cần xem"));

        mockMvc.perform(post("/api/v1/vbdi/XemChiTiet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false));

        verify(getVbdiDetailUseCase).execute(any());
    }

    @Test
    void getView_shouldDelegateToViewQueryServiceWithVbdiDbType() throws Exception {
        when(viewQueryService.execute(eq("vbdi"), any()))
            .thenReturn(new PaginationResponse<>(List.of(Map.of("title", "Van ban cho ban hanh")), 1, 20, 1L, 1, true, true));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwvandi_chobanhanh");

        mockMvc.perform(post("/api/v1/vbdi/GetView")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"vwvandi_chobanhanh\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("Van ban cho ban hanh"));

        verify(viewQueryService).execute(eq("vbdi"), any());
    }

    @Test
    void getView_withUnsupportedViewname_shouldReturn400() throws Exception {
        when(viewQueryService.execute(any(), any()))
            .thenThrow(new IllegalArgumentException("Khong ho tro viewname: vwKhongTonTai"));

        mockMvc.perform(post("/api/v1/vbdi/GetView")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"vwKhongTonTai\"}"))
            .andExpect(status().isBadRequest());
    }

    // Yeu cau nguoi dung 28/08/2026 ("Nhat ky gui nhan", tach bang rieng vbdi_send_log_documents).
    @Test
    void sendDocument_shouldDelegateToSendVbdiDocumentUseCaseAndReturn201() throws Exception {
        when(sendVbdiDocumentUseCase.execute(any())).thenReturn(Result.success(2));

        mockMvc.perform(post("/api/v1/vbdi/SendDocument")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\","
                    + "\"coordinator\":[{\"username\":\"hoangnt.skhcn\",\"name\":\"Nong Thanh Hoang\"}]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(2));

        ArgumentCaptor<com.td.application.vbdi.SendVbdiDocumentRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.SendVbdiDocumentRequest.class);
        verify(sendVbdiDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getCoordinator()).hasSize(1);
        assertThat(captor.getValue().getCoordinator().get(0).getUsername()).isEqualTo("hoangnt.skhcn");
    }

    @Test
    void sendDocument_withoutDocumentId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/SendDocument")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sendLog_shouldDelegateToListVbdiSendLogUseCaseAndReturn200() throws Exception {
        when(listVbdiSendLogUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(Map.of("status", "Gửi văn bản")), 1, 20, 1L, 1, true, true));

        mockMvc.perform(post("/api/v1/vbdi/SendLog")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].status").value("Gửi văn bản"));
    }

    @Test
    void sendLog_withoutDocumentId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/SendLog")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // 29/08/2026 (port "Gắn VB đi" — GanVbdiDocumentUseCase).
    @Test
    void ganVanBanDi_shouldDelegateToGanVbdiDocumentUseCaseAndReturn200() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID replacesId = UUID.randomUUID();
        when(ganVbdiDocumentUseCase.execute(any())).thenReturn(Result.success(documentId));

        mockMvc.perform(post("/api/v1/vbdi/GanVanBanDi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + documentId + "\",\"replacesDocumentId\":\"" + replacesId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(documentId.toString()));

        ArgumentCaptor<com.td.application.vbdi.GanVbdiDocumentRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.GanVbdiDocumentRequest.class);
        verify(ganVbdiDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getReplacesDocumentId()).isEqualTo(replacesId);
    }

    @Test
    void ganVanBanDi_withoutDocumentId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/GanVanBanDi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // 30/08/2026 (port "Lịch sử thao tác văn bản" — loguseraction/getloguseraction).
    @Test
    void logUserAction_shouldDelegateToAddVbdiUserActionLogUseCaseAndReturn201() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(addVbdiUserActionLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbdi/LogUserAction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + documentId + "\",\"actionType\":\"guivanban\"}"))
            .andExpect(status().isCreated());

        ArgumentCaptor<com.td.application.vbdi.AddVbdiUserActionLogRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.AddVbdiUserActionLogRequest.class);
        verify(addVbdiUserActionLogUseCase).execute(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo("guivanban");
    }

    @Test
    void logUserAction_withoutActionType_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/LogUserAction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getLogUserAction_shouldDelegateToListVbdiUserActionLogUseCaseAndReturn200() throws Exception {
        when(listVbdiUserActionLogUseCase.execute(any()))
            .thenReturn(List.of(Map.of("username", "xuanlt.skhcn", "guivanban", "30/08/2026 10:00:00 AM")));

        mockMvc.perform(post("/api/v1/vbdi/GetLogUserAction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].username").value("xuanlt.skhcn"));
    }

    @Test
    void getLogUserAction_withoutDocumentId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/GetLogUserAction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
}
