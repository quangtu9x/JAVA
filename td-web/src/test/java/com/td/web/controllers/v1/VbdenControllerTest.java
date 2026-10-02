package com.td.web.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.vbden.GetVbdenDetailRequest;
import com.td.application.vbden.ChuyenXuLyVbdenUseCase;
import com.td.application.vbden.GetSoVanBanDenRequest;
import com.td.application.vbden.GetSoVanBanDenUseCase;
import com.td.application.vbden.GetVbdenDetailUseCase;
import com.td.application.vbden.GuiSangVbcddhUseCase;
import com.td.application.vbden.KiemTrungSoDenUseCase;
import com.td.application.vbden.KiemTrungVbdenUseCase;
import com.td.application.vbden.LuuVanBanDenResultDto;
import com.td.application.vbden.LuuVanBanDenUseCase;
import com.td.application.vbden.TiepNhanVbdenRequest;
import com.td.application.vbden.TiepNhanVbdenUseCase;
import com.td.application.vbden.VbdenDuplicateDto;
import com.td.application.processdiagram.ProcessDiagramService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-210 (VBDen Core) — Definition of Done: tiếp nhận (sinh số đến tự động), danh sách theo view,
 * xem chi tiết, quá trình xử lý — tất cả tương thích API legacy {@code qlvb/vbden/*}.
 */
@WebMvcTest(controllers = VbdenController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class VbdenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TiepNhanVbdenUseCase tiepNhanVbdenUseCase;
    @MockBean
    private GetVbdenDetailUseCase getVbdenDetailUseCase;
    @MockBean
    private ViewQueryService viewQueryService;
    @MockBean
    private ChuyenXuLyVbdenUseCase chuyenXuLyVbdenUseCase;
    @MockBean
    private GuiSangVbcddhUseCase guiSangVbcddhUseCase;
    // Bo sung 18/09/2026 - 3 mock nay bi thieu tu truoc (constructor VbdenController da co san 3
    // dependency nay nhung file test chua duoc cap nhat theo, dung bay "WebMvcTest MockBean drift" -
    // phat hien khi chay lai suite de xac minh cutover Thu hoi/Gui xin y kien khong hoi quy).
    @MockBean
    private com.td.application.vbden.ChuyenXuLyVbcddhNhiemVuUseCase chuyenXuLyVbcddhNhiemVuUseCase;
    @MockBean
    private com.td.application.vbden.AddVbdenUserActionLogUseCase addVbdenUserActionLogUseCase;
    @MockBean
    private com.td.application.vbden.ListVbdenUserActionLogUseCase listVbdenUserActionLogUseCase;
    @MockBean
    private KiemTrungVbdenUseCase kiemTrungVbdenUseCase;
    @MockBean
    private KiemTrungSoDenUseCase kiemTrungSoDenUseCase;
    @MockBean
    private GetSoVanBanDenUseCase getSoVanBanDenUseCase;
    @MockBean
    private LuuVanBanDenUseCase luuVanBanDenUseCase;
    @MockBean
    private com.td.application.vbden.ChoYKienXinYKienUseCase choYKienXinYKienUseCase;
    @MockBean
    private com.td.application.vbden.GuiXinYKienUseCase guiXinYKienUseCase;
    @MockBean
    private com.td.application.vbden.GuiXinYKienTuVBdenUseCase guiXinYKienTuVBdenUseCase;
    @MockBean
    private com.td.application.vbden.LichSuXuLyUseCase lichSuXuLyUseCase;
    @MockBean
    private com.td.application.vbden.XuLyLaiUseCase xuLyLaiUseCase;
    @MockBean
    private com.td.application.vbden.ThuHoiVanBanUseCase thuHoiVanBanUseCase;
    @MockBean
    private com.td.application.vbden.LayLaiVanBanUseCase layLaiVanBanUseCase;
    @MockBean
    private com.td.application.vbden.CapNhatKetQuaXuLyUseCase capNhatKetQuaXuLyUseCase;
    @MockBean
    private com.td.application.vbden.VBdenChuyenVanBanPhapQuyUseCase vbdenChuyenVanBanPhapQuyUseCase;
    @MockBean
    private com.td.application.vbden.KetThucXuLyUseCase ketThucXuLyUseCase;
    @MockBean
    private com.td.application.vbden.TaoDuThaoVBdenUseCase taoDuThaoVBdenUseCase;
    @MockBean
    private com.td.application.vbden.TuChoiTiepNhanVbdenUseCase tuChoiTiepNhanVbdenUseCase;
    @MockBean
    private com.td.application.vbden.CapNhatVanBanThayTheThuHoiUseCase capNhatVanBanThayTheThuHoiUseCase;
    @MockBean
    private com.td.application.vbden.AddVbdenEditHistoryUseCase addVbdenEditHistoryUseCase;
    @MockBean
    private com.td.application.vbden.ListVbdenEditHistoryUseCase listVbdenEditHistoryUseCase;
    @MockBean
    private com.td.application.vbden.ListVbdenUpdateHistoryUseCase listVbdenUpdateHistoryUseCase;
    @MockBean
    private com.td.application.vbden.KhongVaoSoVbdenUseCase khongVaoSoVbdenUseCase;
    @MockBean
    private com.td.application.vbden.CapNhatPhoiHopUseCase capNhatPhoiHopUseCase;
    @MockBean
    private com.td.application.vbden.CapNhatDaXemDeBietUseCase capNhatDaXemDeBietUseCase;
    @MockBean
    private com.td.application.vbden.TheoDoiNhiemVuUseCase theoDoiNhiemVuUseCase;
    @MockBean
    private com.td.application.vbden.TheoDoiVanBanDenUseCase theoDoiVanBanDenUseCase;
    @MockBean
    private com.td.application.vbden.GetSoLuongCanXuLyUseCase getSoLuongCanXuLyUseCase;
    @MockBean
    private ProcessDiagramService processDiagramService;
    @MockBean
    private com.td.application.vbden.XoaVanBanDenUseCase xoaVanBanDenUseCase;
    @MockBean
    private com.td.application.vbden.PhucHoiVanBanDenUseCase phucHoiVanBanDenUseCase;
    @MockBean
    private com.td.application.vbden.GetNguoiTraLaiUseCase getNguoiTraLaiUseCase;
    @MockBean
    private com.td.application.vbden.GetNguoiThuHoiUseCase getNguoiThuHoiUseCase;
    @MockBean
    private com.td.application.vbden.TraLaiVanBanUseCase traLaiVanBanUseCase;
    @MockBean
    private com.td.application.vbden.SuaYKienVbdenUseCase suaYKienVbdenUseCase;
    @MockBean
    private com.td.application.vbden.CapNhatYKienVbdenUseCase capNhatYKienVbdenUseCase;
    @MockBean
    private com.td.application.vbden.KySoVbdenUseCase kySoVbdenUseCase;
    @MockBean
    private com.td.application.vbden.GanKemTepVbdenUseCase ganKemTepVbdenUseCase;

    @Test
    void tiepNhan_shouldDelegateToTiepNhanVbdenUseCaseAndReturn201() throws Exception {
        UUID id = UUID.randomUUID();
        when(tiepNhanVbdenUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/vbden/TiepNhan")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("subject", "Van ban den ve viec kiem tra")
                .param("unit_in_charge", "So Noi Vu")
                .param("received_date", "2026-08-04")
                .param("date_fields", "received_date"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(id.toString()));

        ArgumentCaptor<TiepNhanVbdenRequest> captor = ArgumentCaptor.forClass(TiepNhanVbdenRequest.class);
        verify(tiepNhanVbdenUseCase).execute(captor.capture());
        TiepNhanVbdenRequest captured = captor.getValue();
        assertThat(captured.getSubject()).isEqualTo("Van ban den ve viec kiem tra");
        assertThat(captured.getFields()).containsEntry("unit_in_charge", "So Noi Vu");
        assertThat(captured.getFields()).doesNotContainKey("subject");
        assertThat(captured.getDateFields()).isEqualTo("received_date");
    }

    @Test
    void tiepNhan_withDocumentTypeField_shouldKeepItAsDynamicField() throws Exception {
        // Bugfix 24/08/2026: "document_type" la field nghiep vu "Loai van ban" (Cong van/Bao cao/...)
        // - truoc day bi controller xoa nham vi tuong la meta documentType (module discriminator).
        UUID id = UUID.randomUUID();
        when(tiepNhanVbdenUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/vbden/TiepNhan")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("subject", "Van ban den ve viec kiem tra")
                .param("document_type", "Công văn"))
            .andExpect(status().isCreated());

        ArgumentCaptor<TiepNhanVbdenRequest> captor = ArgumentCaptor.forClass(TiepNhanVbdenRequest.class);
        verify(tiepNhanVbdenUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("document_type", "Công văn");
    }

    @Test
    void lichSuCapNhat_shouldReturnPaginationWithItems() throws Exception {
        // Yeu cau nguoi dung 24/09/2026 - xem javadoc ListVbdenUpdateHistoryUseCase: FE doc data.items.
        UUID parentId = UUID.randomUUID();
        Map<String, Object> item = Map.of("action", "capnhat", "created", "24/09/2026 10:00:00");
        when(listVbdenUpdateHistoryUseCase.execute(any())).thenReturn(
            new com.td.application.common.models.PaginationResponse<>(java.util.List.of(item), 1, 20, 1L, 1, true, true));

        mockMvc.perform(post("/api/v1/vbden/LichSuCapNhat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":\"" + parentId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].action").value("capnhat"));
    }

    @Test
    void xemChiTiet_shouldDelegateToGetVbdenDetailUseCase() throws Exception {
        Map<String, Object> fields = Map.of("databasetype", "vbden", "subject", "Van ban den test");
        when(getVbdenDetailUseCase.execute(any())).thenReturn(Result.success(fields));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(UUID.randomUUID().toString());
        request.setItems("getall");

        mockMvc.perform(post("/api/v1/vbden/XemChiTiet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.data.subject").value("Van ban den test"));
    }

    @Test
    void xemChiTiet_whenDocumentNotVbden_shouldReturnErrorStatus() throws Exception {
        when(getVbdenDetailUseCase.execute(any())).thenReturn(Result.failure("Không tìm thấy văn bản đến"));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/vbden/XemChiTiet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    void getView_shouldDelegateToViewQueryServiceWithVbdenDbType() throws Exception {
        when(viewQueryService.execute(eq("vbden"), any()))
            .thenReturn(new PaginationResponse<>(List.of(Map.of("title", "Van ban cho xu ly")), 0, 20, 1L, 1, true, true));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));

        mockMvc.perform(post("/api/v1/vbden/GetView")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("Van ban cho xu ly"));

        verify(viewQueryService).execute(eq("vbden"), any());
    }

    @Test
    void getView_withUnsupportedViewname_shouldReturn400() throws Exception {
        when(viewQueryService.execute(any(), any()))
            .thenThrow(new IllegalArgumentException("Khong ho tro viewname: vwKhongTonTai"));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwKhongTonTai");

        mockMvc.perform(post("/api/v1/vbden/GetView")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void quaTrinhXuLy_shouldDelegateToViewQueryServiceWithVbdenResDbType() throws Exception {
        UUID parentId = UUID.randomUUID();
        when(viewQueryService.execute(eq("vbden_res"), any()))
            .thenReturn(new PaginationResponse<>(List.of(Map.of("title", "Buoc xu ly 1")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(post("/api/v1/vbden/QuaTrinhXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"items\":\"getall\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("Buoc xu ly 1"));

        ArgumentCaptor<ViewQueryRequest> captor = ArgumentCaptor.forClass(ViewQueryRequest.class);
        verify(viewQueryService).execute(eq("vbden_res"), captor.capture());
        assertThat(captor.getValue().getViewname()).isEqualTo("QTXL_by_parentid");
        assertThat(captor.getValue().getKeys()).containsExactly(parentId.toString());
    }

    // Bo sung 02/09/2026 (bao loi nguoi dung: "chua the hien duoc So do xu ly") - endpoint moi doc
    // process_diagrams qua ProcessDiagramService, giu dung shape key "diagram_obj" ma tree_model/
    // sodoxuly.jsx (FE) da doc san tu res.data.diagram_obj (xem javadoc VbdenController#soDoXuLy).
    @Test
    void soDoXuLy_shouldDelegateToProcessDiagramServiceWithVbdenDbType() throws Exception {
        UUID parentId = UUID.randomUUID();
        String diagramJson = "{\"username\":\"root\",\"name\":\"root\",\"children\":[]}";
        when(processDiagramService.getDiagramJson(eq(parentId), eq("vbden"))).thenReturn(diagramJson);

        mockMvc.perform(post("/api/v1/vbden/SoDoXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.diagram_obj").value(diagramJson));

        verify(processDiagramService).getDiagramJson(parentId, "vbden");
    }

    @Test
    void soDoXuLy_whenNoDiagramYet_shouldReturnNullDiagramObj() throws Exception {
        UUID parentId = UUID.randomUUID();
        when(processDiagramService.getDiagramJson(eq(parentId), eq("vbden"))).thenReturn(null);

        mockMvc.perform(post("/api/v1/vbden/SoDoXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.diagram_obj").doesNotExist());
    }

    @Test
    void soDoXuLy_withoutParentId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/SoDoXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void quaTrinhXuLy_withoutParentId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/QuaTrinhXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":\"getall\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void chuyenXuLy_shouldDelegateToChuyenXuLyVbdenUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(chuyenXuLyVbdenUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/ChuyenXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"action\":\"phanxuly\","
                    + "\"userProcess\":\"nguyenvana\",\"opinion\":\"Giao xu ly\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.ChuyenXuLyVbdenRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.ChuyenXuLyVbdenRequest.class);
        verify(chuyenXuLyVbdenUseCase).execute(captor.capture());
        assertThat(captor.getValue().getParentid()).isEqualTo(parentId);
        assertThat(captor.getValue().getAction()).isEqualTo("phanxuly");
    }

    @Test
    void chuyenXuLy_withoutAction_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/ChuyenXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void guiSangVbcddh_shouldDelegateToGuiSangVbcddhUseCaseAndReturn201() throws Exception {
        UUID vbcddhId = UUID.randomUUID();
        when(guiSangVbcddhUseCase.execute(any())).thenReturn(Result.success(vbcddhId));

        UUID sourceId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/GuiSangVBCDDH")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + sourceId + "\",\"thoiHan\":\"2026-08-10\",\"yKien\":\"Can xu ly khan\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(vbcddhId.toString()));
    }

    @Test
    void tiepNhan_withDocumentRegisterId_shouldParseIntoRequestAndExcludeFromFields() throws Exception {
        UUID id = UUID.randomUUID();
        UUID registerId = UUID.randomUUID();
        when(tiepNhanVbdenUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/vbden/TiepNhan")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("subject", "Van ban den vao so")
                .param("document_register_id", registerId.toString()))
            .andExpect(status().isCreated());

        ArgumentCaptor<TiepNhanVbdenRequest> captor = ArgumentCaptor.forClass(TiepNhanVbdenRequest.class);
        verify(tiepNhanVbdenUseCase).execute(captor.capture());
        assertThat(captor.getValue().getDocumentRegisterId()).isEqualTo(registerId);
        assertThat(captor.getValue().getFields()).doesNotContainKey("document_register_id");
    }

    @Test
    void tiepNhan_withInvalidDocumentRegisterId_shouldIgnoreItAndStillSucceed() throws Exception {
        UUID id = UUID.randomUUID();
        when(tiepNhanVbdenUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/vbden/TiepNhan")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("subject", "Van ban den test")
                .param("document_register_id", "khong-phai-uuid"))
            .andExpect(status().isCreated());

        ArgumentCaptor<TiepNhanVbdenRequest> captor = ArgumentCaptor.forClass(TiepNhanVbdenRequest.class);
        verify(tiepNhanVbdenUseCase).execute(captor.capture());
        assertThat(captor.getValue().getDocumentRegisterId()).isNull();
    }

    @Test
    void kiemTrung_shouldDelegateToKiemTrungVbdenUseCase() throws Exception {
        var dto = new VbdenDuplicateDto();
        dto.setCode("123/QD-UBND");
        when(kiemTrungVbdenUseCase.execute(any())).thenReturn(Result.success(List.of(dto)));

        mockMvc.perform(post("/api/v1/vbden/KiemTrung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"G10\",\"promulgation_date\":\"01/01/2026\",\"code\":\"123/QD-UBND\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("123/QD-UBND"));
    }

    @Test
    void kiemTrungSoDen_shouldDelegateToKiemTrungSoDenUseCase() throws Exception {
        when(kiemTrungSoDenUseCase.execute(any())).thenReturn(Result.success(List.of()));

        mockMvc.perform(post("/api/v1/vbden/KiemTrungSoDen")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document_register_id\":\"" + UUID.randomUUID() + "\",\"document_number\":\"5\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    // Slice 9 (BL-210/211): GetSoVanBanDen - man "Vao so van ban den".
    @Test
    void getSoVanBanDen_shouldDelegateToGetSoVanBanDenUseCase() throws Exception {
        when(getSoVanBanDenUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(Map.of("document_number", "5")), 1, 20, 1L, 1, true, true));

        mockMvc.perform(post("/api/v1/vbden/GetSoVanBanDen")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"sovanban\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].document_number").value("5"));

        ArgumentCaptor<GetSoVanBanDenRequest> captor = ArgumentCaptor.forClass(GetSoVanBanDenRequest.class);
        verify(getSoVanBanDenUseCase).execute(captor.capture());
        assertThat(captor.getValue().getViewname()).isEqualTo("sovanban");
    }

    // MVP 21/08/2026: hieu chinh/vao so van ban den DA TON TAI (khac TiepNhan - chi tao moi).
    @Test
    void luu_shouldDelegateToLuuVanBanDenUseCase() throws Exception {
        UUID idvbden = UUID.randomUUID();
        UUID idso = UUID.randomUUID();
        when(luuVanBanDenUseCase.execute(any()))
            .thenReturn(Result.success(new LuuVanBanDenResultDto(idvbden, idso, "lpha")));

        mockMvc.perform(post("/api/v1/vbden/Luu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + idvbden + "\",\"documentRegisterId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.idvbden").value(idvbden.toString()))
            .andExpect(jsonPath("$.data.idso").value(idso.toString()));
    }

    @Test
    void luu_whenUseCaseFails_shouldReturnErrorStatus() throws Exception {
        when(luuVanBanDenUseCase.execute(any())).thenReturn(Result.failure("Không tìm thấy văn bản đến"));

        mockMvc.perform(post("/api/v1/vbden/Luu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false));
    }

    // N08 (31/08/2026) - xem javadoc ChoYKienXinYKienUseCase.
    @Test
    void choYKien_shouldDelegateToChoYKienXinYKienUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(choYKienXinYKienUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/ChoYKien")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"opinion\":\"Dong y\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.ChoYKienXinYKienRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.ChoYKienXinYKienRequest.class);
        verify(choYKienXinYKienUseCase).execute(captor.capture());
        assertThat(captor.getValue().getParentid()).isEqualTo(parentId);
        assertThat(captor.getValue().getOpinion()).isEqualTo("Dong y");
    }

    @Test
    void choYKien_withoutParentid_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/ChoYKien")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"Dong y\"}"))
            .andExpect(status().isBadRequest());
    }

    // N08 (31/08/2026) - xem javadoc GuiXinYKienUseCase.
    @Test
    void guiXinYKien_shouldDelegateToGuiXinYKienUseCaseAndReturn201() throws Exception {
        UUID xinYKienId = UUID.randomUUID();
        when(guiXinYKienUseCase.execute(any())).thenReturn(Result.success(xinYKienId));

        UUID idvbdi = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/GuiXinYKien")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idvbdi\":\"" + idvbdi + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(xinYKienId.toString()));

        ArgumentCaptor<com.td.application.vbden.GuiXinYKienRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.GuiXinYKienRequest.class);
        verify(guiXinYKienUseCase).execute(captor.capture());
        assertThat(captor.getValue().getIdvbdi()).isEqualTo(idvbdi);
        assertThat(captor.getValue().isNoibo()).isFalse();
    }

    @Test
    void guiXinYKien_withoutIdvbdi_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/GuiXinYKien")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // N09 (01/09/2026) - xem javadoc LichSuXuLyUseCase.
    @Test
    void lichSuXuLy_shouldDelegateToLichSuXuLyUseCaseAndReturnFlattenedPage() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID().toString());
        row.put("code", "123/VP");
        when(lichSuXuLyUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(row), 1, 20, 1L, 1, true, true));

        mockMvc.perform(post("/api/v1/vbden/LichSuXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageNumber\":1,\"pageSize\":20}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("123/VP"))
            .andExpect(jsonPath("$.total").value(1));

        verify(lichSuXuLyUseCase).execute(any());
    }

    // N10 (01/09/2026) - xem javadoc XuLyLaiUseCase.
    @Test
    void xuLyLai_shouldDelegateToXuLyLaiUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(xuLyLaiUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/XuLyLai")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"opinion\":\"Can xu ly lai\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.XuLyLaiRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.XuLyLaiRequest.class);
        verify(xuLyLaiUseCase).execute(captor.capture());
        assertThat(captor.getValue().getParentid()).isEqualTo(parentId);
        assertThat(captor.getValue().getOpinion()).isEqualTo("Can xu ly lai");
    }

    @Test
    void xuLyLai_withoutParentid_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/XuLyLai")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"Can xu ly lai\"}"))
            .andExpect(status().isBadRequest());
    }

    // N11 (01/09/2026) - xem javadoc ThuHoiVanBanUseCase.
    @Test
    void thuHoiVanBan_shouldDelegateToThuHoiVanBanUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(thuHoiVanBanUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/ThuHoiVanBan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"opinion\":\"Can thu hoi\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.ThuHoiVanBanRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.ThuHoiVanBanRequest.class);
        verify(thuHoiVanBanUseCase).execute(captor.capture());
        assertThat(captor.getValue().getParentid()).isEqualTo(parentId);
        assertThat(captor.getValue().getOpinion()).isEqualTo("Can thu hoi");
    }

    @Test
    void thuHoiVanBan_withoutParentid_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/ThuHoiVanBan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"Can thu hoi\"}"))
            .andExpect(status().isBadRequest());
    }

    // Bo sung 18/09/2026 (cutover ModalThuHoiVanBan.jsx, nhanh "select") - xem javadoc
    // ThuHoiVanBanUseCase muc "Bo sung 18/09/2026".
    @Test
    void thuHoiVanBan_withTypeSelect_shouldForwardObjFieldsToUseCase() throws Exception {
        UUID childId = UUID.randomUUID();
        when(thuHoiVanBanUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/ThuHoiVanBan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"opinion\":\"Chon thu hoi\","
                    + "\"type\":\"select\",\"userProcessObj\":\"[{\\\"username\\\":\\\"nguyenvana\\\"}]\"}"))
            .andExpect(status().isCreated());

        ArgumentCaptor<com.td.application.vbden.ThuHoiVanBanRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.ThuHoiVanBanRequest.class);
        verify(thuHoiVanBanUseCase).execute(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("select");
        assertThat(captor.getValue().getUserProcessObj()).contains("nguyenvana");
    }

    // Bo sung 18/09/2026 (cutover ModalThuHoiVanBan.jsx) - xem javadoc GetNguoiThuHoiUseCase.
    @Test
    void getNguoiThuHoi_shouldDelegateToUseCaseAndReturn200() throws Exception {
        when(getNguoiThuHoiUseCase.execute(any())).thenReturn(Result.success(Map.of(
            "chutri", List.of(), "phoihop", List.of(), "xemdebiet", List.of(), "theodoi", List.of())));

        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/GetNguoiThuHoi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\"}"))
            .andExpect(status().isOk());

        ArgumentCaptor<com.td.application.vbden.GetNguoiThuHoiRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.GetNguoiThuHoiRequest.class);
        verify(getNguoiThuHoiUseCase).execute(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
    }

    @Test
    void getNguoiThuHoi_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/GetNguoiThuHoi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // Bo sung 18/09/2026 (cutover "Gui xin y kien tu vbden") - xem javadoc GuiXinYKienTuVBdenUseCase.
    @Test
    void guiXinYKienTuVBden_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID xinYKienId = UUID.randomUUID();
        when(guiXinYKienTuVBdenUseCase.execute(any())).thenReturn(Result.success(xinYKienId));

        UUID idvbden = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/GuiXinYKienTuVBden")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idvbden\":\"" + idvbden + "\",\"content\":\"De nghi cho y kien\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(xinYKienId.toString()));

        ArgumentCaptor<com.td.application.vbden.GuiXinYKienTuVBdenRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.GuiXinYKienTuVBdenRequest.class);
        verify(guiXinYKienTuVBdenUseCase).execute(captor.capture());
        assertThat(captor.getValue().getIdvbden()).isEqualTo(idvbden);
        assertThat(captor.getValue().getContent()).isEqualTo("De nghi cho y kien");
    }

    @Test
    void guiXinYKienTuVBden_withoutIdvbden_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/GuiXinYKienTuVBden")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"De nghi cho y kien\"}"))
            .andExpect(status().isBadRequest());
    }

    // N18 (04/09/2026) - xem javadoc LayLaiVanBanUseCase.
    @Test
    void layLaiVanBan_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(layLaiVanBanUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/LayLaiVanBan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"opinion\":\"Lay lai xu ly\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.LayLaiVanBanRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.LayLaiVanBanRequest.class);
        verify(layLaiVanBanUseCase).execute(captor.capture());
        assertThat(captor.getValue().getParentid()).isEqualTo(parentId);
        assertThat(captor.getValue().getOpinion()).isEqualTo("Lay lai xu ly");
    }

    @Test
    void layLaiVanBan_withoutParentid_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/LayLaiVanBan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"Lay lai xu ly\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void layLaiVanBan_withoutOpinion_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/LayLaiVanBan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    // N15 (03/09/2026) - xem javadoc CapNhatKetQuaXuLyUseCase.
    @Test
    void capNhatKetQuaXuLy_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(capNhatKetQuaXuLyUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/CapNhatKetQuaXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"opinion\":\"Da xu ly xong\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.CapNhatKetQuaXuLyRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.CapNhatKetQuaXuLyRequest.class);
        verify(capNhatKetQuaXuLyUseCase).execute(captor.capture());
        assertThat(captor.getValue().getParentid()).isEqualTo(parentId);
        assertThat(captor.getValue().getOpinion()).isEqualTo("Da xu ly xong");
    }

    @Test
    void capNhatKetQuaXuLy_withoutParentid_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/CapNhatKetQuaXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"Da xu ly xong\"}"))
            .andExpect(status().isBadRequest());
    }

    // N16 (03/09/2026) - xem javadoc VBdenChuyenVanBanPhapQuyUseCase.
    @Test
    void chuyenVanBanPhapQuy_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(vbdenChuyenVanBanPhapQuyUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID idqlvb = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/ChuyenVanBanPhapQuy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idqlvb\":\"" + idqlvb + "\",\"mailin\":\"vbqp@example.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.VBdenChuyenVanBanPhapQuyRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.VBdenChuyenVanBanPhapQuyRequest.class);
        verify(vbdenChuyenVanBanPhapQuyUseCase).execute(captor.capture());
        assertThat(captor.getValue().getIdqlvb()).isEqualTo(idqlvb);
        assertThat(captor.getValue().getMailin()).isEqualTo("vbqp@example.com");
    }

    @Test
    void chuyenVanBanPhapQuy_withoutMailin_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/ChuyenVanBanPhapQuy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idqlvb\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    // N17 (04/09/2026) - xem javadoc KetThucXuLyUseCase.
    @Test
    void ketThucXuLy_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(ketThucXuLyUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/KetThucXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"opinion\":\"Da xu ly xong\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.KetThucXuLyRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.KetThucXuLyRequest.class);
        verify(ketThucXuLyUseCase).execute(captor.capture());
        assertThat(captor.getValue().getParentid()).isEqualTo(parentId);
        assertThat(captor.getValue().getOpinion()).isEqualTo("Da xu ly xong");
    }

    @Test
    void ketThucXuLy_withoutParentid_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/KetThucXuLy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"Da xu ly xong\"}"))
            .andExpect(status().isBadRequest());
    }

    // N20 (07/09/2026) - xem javadoc CapNhatPhoiHopUseCase.
    @Test
    void capNhatPhoiHop_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(capNhatPhoiHopUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/CapNhatPhoiHop")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"opinion\":\"Da phoi hop xong\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.CapNhatPhoiHopRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.CapNhatPhoiHopRequest.class);
        verify(capNhatPhoiHopUseCase).execute(captor.capture());
        assertThat(captor.getValue().getParentid()).isEqualTo(parentId);
        assertThat(captor.getValue().getOpinion()).isEqualTo("Da phoi hop xong");
    }

    @Test
    void capNhatPhoiHop_withoutParentid_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/CapNhatPhoiHop")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"Da phoi hop xong\"}"))
            .andExpect(status().isBadRequest());
    }

    // N20 (07/09/2026) - xem javadoc TheoDoiNhiemVuUseCase.
    @Test
    void theoDoiNhiemVu_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(theoDoiNhiemVuUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/TheoDoiNhiemVu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"action\":\"theodoi\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.TheoDoiNhiemVuRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.TheoDoiNhiemVuRequest.class);
        verify(theoDoiNhiemVuUseCase).execute(captor.capture());
        assertThat(captor.getValue().getParentid()).isEqualTo(parentId);
        assertThat(captor.getValue().getAction()).isEqualTo("theodoi");
    }

    @Test
    void theoDoiNhiemVu_withoutAction_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/TheoDoiNhiemVu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    // Bo sung 07/09/2026 (2) - xem javadoc TuChoiTiepNhanVbdenUseCase.
    @Test
    void tuChoiTiepNhan_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID docId = UUID.randomUUID();
        when(tuChoiTiepNhanVbdenUseCase.execute(any())).thenReturn(Result.success(docId));

        mockMvc.perform(post("/api/v1/vbden/TuChoiTiepNhan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + docId + "\",\"opinion\":\"Khong dung tham quyen\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(docId.toString()));

        ArgumentCaptor<com.td.application.vbden.TuChoiTiepNhanVbdenRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.TuChoiTiepNhanVbdenRequest.class);
        verify(tuChoiTiepNhanVbdenUseCase).execute(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(docId);
        assertThat(captor.getValue().getOpinion()).isEqualTo("Khong dung tham quyen");
    }

    @Test
    void tuChoiTiepNhan_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/TuChoiTiepNhan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"Khong dung tham quyen\"}"))
            .andExpect(status().isBadRequest());
    }

    // Bo sung 07/09/2026 (4) - xem javadoc KhongVaoSoVbdenUseCase.
    @Test
    void khongVaoSo_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID docId = UUID.randomUUID();
        when(khongVaoSoVbdenUseCase.execute(any())).thenReturn(Result.success(docId));

        mockMvc.perform(post("/api/v1/vbden/KhongVaoSo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + docId + "\",\"opinion\":\"Vui long xu ly don vi khac\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(docId.toString()));

        ArgumentCaptor<com.td.application.vbden.KhongVaoSoVbdenRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.KhongVaoSoVbdenRequest.class);
        verify(khongVaoSoVbdenUseCase).execute(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(docId);
        assertThat(captor.getValue().getOpinion()).isEqualTo("Vui long xu ly don vi khac");
    }

    @Test
    void khongVaoSo_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/KhongVaoSo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"Vui long xu ly don vi khac\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void taoDuThao_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(taoDuThaoVBdenUseCase.execute(any())).thenReturn(Result.success(childId));

        UUID parentId = UUID.randomUUID();
        UUID resObjId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/vbden/TaoDuThao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentid\":\"" + parentId + "\",\"resObjId\":\"" + resObjId
                    + "\",\"resObjCode\":\"Du thao 01\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbden.TaoDuThaoVBdenRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbden.TaoDuThaoVBdenRequest.class);
        verify(taoDuThaoVBdenUseCase).execute(captor.capture());
        assertThat(captor.getValue().getParentid()).isEqualTo(parentId);
        assertThat(captor.getValue().getResObjId()).isEqualTo(resObjId);
        assertThat(captor.getValue().getResObjCode()).isEqualTo("Du thao 01");
    }

    @Test
    void taoDuThao_withoutParentid_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/TaoDuThao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    // Port SoLuongCanXuLy (09/09/2026, xem javadoc GetSoLuongCanXuLyUseCase) - khong nhan body, uy
    // quyen toan bo cho use case (username lay tu token dang nhap ben trong use case).
    @Test
    void soLuongCanXuLy_shouldDelegateToUseCaseAndReturnFlatCounts() throws Exception {
        when(getSoLuongCanXuLyUseCase.execute()).thenReturn(Map.of("user_process", 3L));

        mockMvc.perform(post("/api/v1/vbden/SoLuongCanXuLy")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user_process").value(3));

        verify(getSoLuongCanXuLyUseCase).execute();
    }
}
