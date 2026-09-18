package com.td.application.vbden;

import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListFilesForDocumentsUseCase;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-210/211 (VBDen — Sổ văn bản đến) — Slice 9: {@code GetSoVanBanDenUseCase} — kiến trúc
 * 2-document ({@code vbden_so} query + join live về {@code vbden} gốc qua {@code parentid}), đúng
 * Domino thật (đối chiếu lại sau phát hiện của người dùng — xem javadoc lớp use case).
 */
@ExtendWith(MockitoExtension.class)
class GetSoVanBanDenUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private ListFilesForDocumentsUseCase listFilesForDocumentsUseCase;

    private GetSoVanBanDenUseCase useCase;

    @BeforeEach
    void setUp() {
        // 31/08/2026 ("o danh sach chua thay hien thi tep dinh kem"): request.items mac dinh la
        // "getall" (GetSoVanBanDenRequest) nen HAU HET test se kich hoat collectFilesByParentId - stub
        // mac dinh rong (lenient, khong lam cac test khong quan tam "files" bi NPE); test rieng ben
        // duoi ghi de lai - dung tien le GetVbdenDetailUseCaseTest/GetVbdiDetailUseCaseTest.
        lenient().when(listFilesForDocumentsUseCase.execute(any())).thenReturn(Map.of());
        useCase = new GetSoVanBanDenUseCase(
            documentRepository, new DynamicDocumentFieldsService(), resolveIdentifierByLevelUseCase,
            listFilesForDocumentsUseCase);
    }

    @Test
    void execute_whenCurrentUserHasNoIdentifier_shouldReturnEmptyPageWithoutQuerying() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        var result = useCase.execute(new GetSoVanBanDenRequest());

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findByVbdenOrgView(any(), any(), any());
    }

    @Test
    void execute_sovanban_shouldQueryVbdenSoByIdentifierCreatorSortedByDocumentNumberDescending() {
        stubCurrentIdentifier("PHONG_TH");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var request = new GetSoVanBanDenRequest();
        request.setViewname("sovanban");

        useCase.execute(request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("identifier_creator");
        assertThat(filter.getOrgValue()).isEqualTo("PHONG_TH");
        assertThat(filter.getRequireNonEmptyFieldName()).isNull();
        assertThat(filter.getPrimarySortField()).isEqualTo("document_number");
        assertThat(filter.getPrimarySortType()).isEqualTo("integer");
        assertThat(filter.isSortDescending()).isTrue();
        // Bo sung 08/09/2026 (nguoi dung yeu cau "lay lai tan goc"): ban ghi vbden_so da bi
        // VbdiRevokeTaskHandler danh dau reply="1" phai bien mat khoi danh sach "So van ban den".
        assertThat(filter.getExcludeFieldValues()).containsEntry("reply", List.of("1"));
    }

    @Test
    void execute_sovanban_withSortUp_shouldSortAscending() {
        stubCurrentIdentifier("PHONG_TH");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var request = new GetSoVanBanDenRequest();
        request.setViewname("sovanban");
        request.setSort("document_number_up");

        useCase.execute(request);

        assertThat(filterCaptor.getValue().isSortDescending()).isFalse();
    }

    @Test
    void execute_sovanbanSxNgayDen_shouldSortByReceivedDateThenDocumentNumberDescending() {
        stubCurrentIdentifier("PHONG_TH");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var request = new GetSoVanBanDenRequest();
        request.setViewname("sovanban_sx_ngayden");

        useCase.execute(request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getPrimarySortField()).isEqualTo("received_date");
        assertThat(filter.getPrimarySortType()).isEqualTo("date");
        assertThat(filter.getSecondarySortField()).isEqualTo("document_number");
        assertThat(filter.getSecondarySortType()).isEqualTo("integer");
    }

    @Test
    void execute_homqua_shouldFilterCreatedOnToYesterday() {
        stubCurrentIdentifier("PHONG_TH");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var request = new GetSoVanBanDenRequest();
        request.setViewname("homqua");

        useCase.execute(request);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertThat(filterCaptor.getValue().getMinCreatedOn()).isEqualTo(yesterday);
        assertThat(filterCaptor.getValue().getMaxCreatedOn()).isEqualTo(yesterday);
    }

    @Test
    void execute_trongngay_shouldFilterCreatedOnToToday() {
        stubCurrentIdentifier("PHONG_TH");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var request = new GetSoVanBanDenRequest();
        request.setViewname("trongngay");

        useCase.execute(request);

        LocalDate today = LocalDate.now();
        assertThat(filterCaptor.getValue().getMinCreatedOn()).isEqualTo(today);
        assertThat(filterCaptor.getValue().getMaxCreatedOn()).isEqualTo(today);
    }

    @Test
    void execute_trongtuan_shouldFilterCreatedOnToMondayThroughSundayOfCurrentWeek() {
        stubCurrentIdentifier("PHONG_TH");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var request = new GetSoVanBanDenRequest();
        request.setViewname("trongtuan");

        useCase.execute(request);

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        assertThat(filterCaptor.getValue().getMinCreatedOn()).isEqualTo(monday);
        assertThat(filterCaptor.getValue().getMaxCreatedOn()).isEqualTo(sunday);
    }

    @Test
    void execute_withIddanhmucso_shouldFilterByDocumentRegisterIdRegardlessOfViewname() {
        stubCurrentIdentifier("PHONG_TH");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var request = new GetSoVanBanDenRequest();
        request.setViewname("sovanban_sx_ngayden");
        request.setIddanhmucso("11111111-1111-1111-1111-111111111111");

        useCase.execute(request);

        assertThat(filterCaptor.getValue().getIncludeFieldValues())
            .containsEntry("document_register_id", List.of("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void execute_withUnsupportedViewname_shouldThrow() {
        stubCurrentIdentifier("PHONG_TH");

        var request = new GetSoVanBanDenRequest();
        request.setViewname("sovanban_by_idvbden");

        assertThatThrownBy(() -> useCase.execute(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_shouldJoinRegisterEntryWithLiveParentDocumentFields() {
        stubCurrentIdentifier("PHONG_TH");
        UUID parentId = UUID.randomUUID();
        BusinessDocument parent = newDocument("Van ban goc", "vbden", "{\"subject\":\"Trich yeu goc\"}");
        // BusinessDocument.getId() luon random khi tao qua constructor - dung Mockito lenient khong
        // can, chi can findById tra dung parent bat ke id thuc te vi test set parentid = toString().
        BusinessDocument entry = newDocument("So 5", "vbden_so",
            "{\"parentid\":\"" + parentId + "\",\"document_number\":\"5\"}");
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));
        when(documentRepository.findById(parentId)).thenReturn(Optional.of(parent));

        var request = new GetSoVanBanDenRequest();
        request.setViewname("sovanban");
        request.setItems("getall");

        var result = useCase.execute(request);

        var row = result.getItems().get(0);
        assertThat(row).containsEntry("title", "Van ban goc");
        assertThat(row).containsEntry("subject", "Trich yeu goc");
        assertThat(row).containsEntry("document_number", "5");
        assertThat(row).containsEntry("id", entry.getId());
        // Bugfix 24/08/2026: key doi ten sang "databasetype" - xem javadoc GetVbdenDetailUseCaseTest
        // (cung nguyen nhan: tranh dung ten voi field nghiep vu "document_type" "Loai van ban").
        assertThat(row).containsEntry("databasetype", "vbden");
    }

    // "o danh sach chua thay hien thi tep dinh kem" 31/08/2026: "files" phai xuat hien, gan theo id
    // VAN BAN GOC (parentId), goi listFilesForDocumentsUseCase 1 LAN cho CA TRANG (khong phai tung
    // dong) - xem javadoc ListFilesForDocumentsUseCase.
    @Test
    void execute_withFiles_shouldCollectFilesByParentDocumentIdInOneBatchCall() {
        stubCurrentIdentifier("PHONG_TH");
        UUID parentId = UUID.randomUUID();
        BusinessDocument parent = newDocument("Van ban goc", "vbden", "{}");
        BusinessDocument entry = newDocument("So 5", "vbden_so",
            "{\"parentid\":\"" + parentId + "\",\"document_number\":\"5\"}");
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));
        when(documentRepository.findById(parentId)).thenReturn(Optional.of(parent));
        UUID fileId = UUID.randomUUID();
        var fileDto = FileDto.builder().fileId(fileId).originalFileName("phu_luc.pdf").fileSize(2048L).build();
        when(listFilesForDocumentsUseCase.execute(List.of(parentId))).thenReturn(Map.of(parentId, List.of(fileDto)));

        var request = new GetSoVanBanDenRequest();
        request.setItems("getall");
        var result = useCase.execute(request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.getItems().get(0).get("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0)).containsEntry("file_id", fileId).containsEntry("name", "phu_luc.pdf");
        verify(listFilesForDocumentsUseCase, org.mockito.Mockito.times(1)).execute(any());
    }

    @Test
    void execute_whenParentDocumentNotFound_shouldStillReturnRegisterEntryFieldsWithoutThrowing() {
        stubCurrentIdentifier("PHONG_TH");
        UUID parentId = UUID.randomUUID();
        BusinessDocument entry = newDocument("So 6", "vbden_so",
            "{\"parentid\":\"" + parentId + "\",\"document_number\":\"6\"}");
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new GetSoVanBanDenRequest();
        request.setItems("getall");

        var result = useCase.execute(request);

        assertThat(result.getItems().get(0)).containsEntry("document_number", "6");
    }

    // Bugfix 22/08/2026 (bao loi nguoi dung: cot "trich yeu" trong danh sach vbden trong): van ban
    // tao qua TiepNhanVbdenUseCase chi co "title" (cot entity), khong co "subject" trong
    // dynamic_fields - phai fallback ve title.
    @Test
    void execute_whenParentHasNoSubjectInDynamicFields_shouldFallBackToTitle() {
        stubCurrentIdentifier("PHONG_TH");
        UUID parentId = UUID.randomUUID();
        BusinessDocument parent = newDocument("Van ban goc khong co subject", "vbden", "{}");
        BusinessDocument entry = newDocument("So 7", "vbden_so",
            "{\"parentid\":\"" + parentId + "\",\"document_number\":\"7\"}");
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));
        when(documentRepository.findById(parentId)).thenReturn(Optional.of(parent));

        var request = new GetSoVanBanDenRequest();
        request.setItems("getall");

        var result = useCase.execute(request);

        assertThat(result.getItems().get(0)).containsEntry("subject", "Van ban goc khong co subject");
    }

    private void stubCurrentIdentifier(String identifier) {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(identifier));
    }

    private BusinessDocument newDocument(String title, String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument(title, documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
