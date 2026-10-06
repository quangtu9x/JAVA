package com.td.application.hosocv;

import com.td.application.documentlinks.InMemoryDocumentLinkRepository;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.duthao.DuThaoDocumentRepository;
import com.td.application.phieutrinhtd.PhieuTrinhTdDocumentRepository;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.documentlinks.DocumentLinkType;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.duthao.DuThaoDocument;
import com.td.domain.hosocv.HoSoCVDocument;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-240 (HoSoCV Core) — gắn / gỡ văn bản vào hồ sơ qua {@link GanKemVanBanUseCase}. Từ 06/10/2026 liên
 * kết lưu ở bảng {@code document_links} (docs/architecture/document-links.md) - test kiểm tra dòng liên
 * kết (fake {@link InMemoryDocumentLinkRepository}) và việc tra tồn tại ĐÚNG bảng vật lý theo actionName
 * (bug tra sai bảng cũ: tra dự thảo/văn bản đi qua documentRepository luôn rỗng).
 */
@ExtendWith(MockitoExtension.class)
class GanKemVanBanUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;

    private final InMemoryDocumentLinkRepository linkRepository = new InMemoryDocumentLinkRepository();
    private GanKemVanBanUseCase useCase;
    private HoSoCVDocument hosocv;

    @BeforeEach
    void setUp() {
        useCase = new GanKemVanBanUseCase(
            hoSoCVDocumentRepository, InMemoryDocumentLinkRepository.newService(linkRepository),
            documentRepository, duThaoDocumentRepository, vbdiDocumentRepository, phieuTrinhTdDocumentRepository,
            new DynamicDocumentFieldsService());
        hosocv = withId(new HoSoCVDocument("Ho so", "hosocv", "0", null, null, null, null), UUID.randomUUID());
        lenient().when(hoSoCVDocumentRepository.findById(hosocv.getId())).thenReturn(Optional.of(hosocv));
    }

    @Test
    void execute_withUnknownActionName_shouldReturnFailure() {
        var result = useCase.execute(request("khongtontai", null, UUID.randomUUID()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(linkRepository.activeRows()).isEmpty();
    }

    @Test
    void execute_withUnknownHoSoCV_shouldReturnFailure() {
        var request = request("ganvanbandi", null, UUID.randomUUID());
        request.setHosocvId(UUID.randomUUID());
        when(hoSoCVDocumentRepository.findById(request.getHosocvId())).thenReturn(Optional.empty());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withNonHoSoCVDocument_shouldReturnFailure() {
        HoSoCVDocument vanBanKhac = withId(
            new HoSoCVDocument("VB khac", "hosocv_vbkhac", "0", null, null, null, null), UUID.randomUUID());
        when(hoSoCVDocumentRepository.findById(vanBanKhac.getId())).thenReturn(Optional.of(vanBanKhac));
        var request = request("ganvanbandi", null, UUID.randomUUID());
        request.setHosocvId(vanBanKhac.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_ganVanBanDi_shouldResolveThroughVbdiRepositoryAndLink() {
        VbdiDocument vbdi = vbdi(UUID.randomUUID(), "{}");

        var result = useCase.execute(request("ganvanbandi", null, vbdi.getId()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(links()).containsExactly("hoso_vbdi:" + vbdi.getId());
        verify(documentRepository, never()).findById(any());
    }

    @Test
    void execute_ganVanBanDen_twice_shouldNotDuplicate_andRemoveShouldSoftDelete() {
        UUID vbdenId = UUID.randomUUID();
        when(documentRepository.findById(vbdenId)).thenReturn(Optional.of(
            withId(new BusinessDocument("VB den", "vbden", "0", null, null, null, null), vbdenId)));

        useCase.execute(request("ganvanbanden", null, vbdenId));
        useCase.execute(request("ganvanbanden", null, vbdenId));
        assertThat(links()).containsExactly("hoso_vbden:" + vbdenId);

        useCase.execute(request("ganvanbanden", "remove", vbdenId));
        assertThat(links()).isEmpty();
    }

    @Test
    void execute_ganVanBanDenXuLy_shouldUseHandlingLinkType() {
        UUID vbdenId = UUID.randomUUID();
        when(documentRepository.findById(vbdenId)).thenReturn(Optional.of(
            withId(new BusinessDocument("VB den", "vbden", "0", null, null, null, null), vbdenId)));

        useCase.execute(request("ganvanbandenxuly", null, vbdenId));

        assertThat(links()).containsExactly("hoso_vbden_xuly:" + vbdenId);
    }

    // Dự thảo đã phát hành: văn bản đi (liên kết duthao_vbdi HOẶC field document_out_id) cũng vào tab
    // "Văn bản phát hành"; gỡ dự thảo gỡ luôn.
    @Test
    void execute_ganKemDuThao_shouldAlsoLinkIssuedVbdi_andRemoveBoth() {
        VbdiDocument issuedByLink = vbdi(UUID.randomUUID(), "{}");
        VbdiDocument issuedByField = vbdi(UUID.randomUUID(), "{}");
        DuThaoDocument duThao = duThao(UUID.randomUUID(), "{\"document_out_id\":\"" + issuedByField.getId() + "\"}");
        linkRepository.seed(DocumentLinkType.DUTHAO_VBDI, duThao.getId(), issuedByLink.getId());

        useCase.execute(request("gankemduthao", null, duThao.getId()));

        assertThat(links()).containsExactlyInAnyOrder(
            "duthao_vbdi:" + issuedByLink.getId(),
            "hoso_duthao:" + duThao.getId(),
            "hoso_vbdi_phathanh:" + issuedByLink.getId(),
            "hoso_vbdi_phathanh:" + issuedByField.getId());

        useCase.execute(request("gankemduthao", "remove", duThao.getId()));

        assertThat(links()).containsExactly("duthao_vbdi:" + issuedByLink.getId());
    }

    // Liên kết không còn ghi vào dynamic_fields của văn bản lẫn hồ sơ.
    @Test
    void execute_shouldNotRewriteDynamicFields() {
        DuThaoDocument duThao = duThao(UUID.randomUUID(), "{}");

        useCase.execute(request("gankemduthao", null, duThao.getId()));

        verify(duThaoDocumentRepository, never()).save(any());
        verify(hoSoCVDocumentRepository, never()).save(any());
        assertThat(duThao.getDynamicFieldsJson()).isEqualTo("{}");
    }

    @Test
    void execute_ganKemPhieuTrinh_shouldResolveThroughPhieuTrinhTdRepository() {
        UUID phieuTrinhId = UUID.randomUUID();
        when(phieuTrinhTdDocumentRepository.findById(phieuTrinhId)).thenReturn(Optional.of(
            withId(new PhieuTrinhTdDocument("Phieu trinh", "0", null), phieuTrinhId)));

        useCase.execute(request("gankemphieutrinh", null, phieuTrinhId));

        assertThat(links()).containsExactly("hoso_phieutrinh:" + phieuTrinhId);
        verify(documentRepository, never()).findById(any());
    }

    @Test
    void execute_withUnknownVanBanId_shouldSkipGracefully() {
        UUID unknownId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(unknownId)).thenReturn(Optional.empty());

        var result = useCase.execute(request("ganvanbandi", null, unknownId));

        assertThat(result.isSuccess()).isTrue();
        assertThat(links()).isEmpty();
    }

    private List<String> links() {
        return linkRepository.activeRows().stream()
            .map(link -> link.getLinkType() + ":" + link.getTargetId())
            .toList();
    }

    private GanKemVanBanRequest request(String actionName, String action, UUID vanBanId) {
        var request = new GanKemVanBanRequest();
        request.setHosocvId(hosocv.getId());
        request.setVanBanIds(List.of(vanBanId));
        request.setActionName(actionName);
        request.setAction(action);
        return request;
    }

    private VbdiDocument vbdi(UUID id, String dynamicFieldsJson) {
        VbdiDocument document = withId(new VbdiDocument("VB di", "vbdi", "0", null, null, null, null), id);
        document.replaceDynamicFields(dynamicFieldsJson);
        lenient().when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.of(document));
        return document;
    }

    private DuThaoDocument duThao(UUID id, String dynamicFieldsJson) {
        DuThaoDocument document = withId(new DuThaoDocument("Du thao", "duthao", "0", null, null, null, null), id);
        document.replaceDynamicFields(dynamicFieldsJson);
        lenient().when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.of(document));
        return document;
    }

    private static <T> T withId(T document, UUID id) {
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
