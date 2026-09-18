package com.td.application.vbdi;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.UserDisplayInfoService;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 09/09/2026 (port hợp nhất Domino {@code qlvb/add&actionname=addlogseendocument} +
 * {@code qlvb/vbdi/seendocumentout} — xem javadoc {@link AddVbdiSeenDocumentUseCase}): test đánh
 * dấu "đã xem" trên {@code log_seen_document}/{@code coordinator_hide_seen_rtext}/
 * {@code coordinator_hide_not_seen_rtext}/{@code userxemdebiet} (4 field dạng chuỗi username nối
 * {@code ";"}).
 */
@ExtendWith(MockitoExtension.class)
class AddVbdiSeenDocumentUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserDisplayInfoService userDisplayInfoService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private AddVbdiSeenDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddVbdiSeenDocumentUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService,
            currentUserProvider, userDisplayInfoService);
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nhanvien01"));
        // Mac dinh khong tim thay ho so (fallback ve username/don vi rong) - test rieng cho truong
        // hop CO ho so o duoi.
        lenient().when(userDisplayInfoService.resolve("nhanvien01"))
            .thenReturn(new UserDisplayInfoService.UserDisplayInfo("nhanvien01", ""));
    }

    private VbdiDocument vbdiDocument() {
        return new VbdiDocument("Van ban di", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
    }

    private AddVbdiSeenDocumentRequest request(UUID documentId) {
        var request = new AddVbdiSeenDocumentRequest();
        request.setDocumentId(documentId);
        return request;
    }

    @Test
    void execute_firstTimeViewing_shouldAppendSeenAndCleanUpPendingLists() {
        var document = vbdiDocument();
        document.replaceDynamicFields(
            "{\"coordinator_hide_not_seen_rtext\":\"nhanvien01;nhanvien02\","
                + "\"userxemdebiet\":\"nhanvien01;nhanvien02\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(String.valueOf(fields.get("log_seen_document"))).isEqualTo("nhanvien01");
        assertThat(String.valueOf(fields.get("coordinator_hide_seen_rtext"))).isEqualTo("nhanvien01");
        assertThat(String.valueOf(fields.get("coordinator_hide_not_seen_rtext"))).isEqualTo("nhanvien02");
        assertThat(String.valueOf(fields.get("userxemdebiet"))).isEqualTo("nhanvien02");
        verify(documentCacheService).evict(document.getId());
        verify(documentCacheService).evictAllListCaches();
        // 09/09/2026 (2, nguoi dung bao chua hien thi duoc "ngay gio"/"don vi"): khong co ho so
        // AppUser (mock tra Optional.empty()) -> fallback name=username, unit rong, van phai co
        // "created".
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detail = (List<Map<String, Object>>) fields.get("log_seen_document_detail");
        assertThat(detail).hasSize(1);
        assertThat(detail.get(0).get("username")).isEqualTo("nhanvien01");
        assertThat(detail.get(0).get("name")).isEqualTo("nhanvien01");
        assertThat(detail.get(0).get("unit")).isEqualTo("");
        assertThat(String.valueOf(detail.get(0).get("created"))).isNotBlank();
        // 09/09/2026 (3, yeu cau them "ngay gio xem lan cuoi"): lan xem dau tien -> created == lastSeen.
        assertThat(detail.get(0).get("lastSeen")).isEqualTo(detail.get(0).get("created"));
    }

    @Test
    void execute_firstTimeViewing_withKnownAppUser_shouldRecordFullNameAndUnitInDetail() {
        var document = vbdiDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(userDisplayInfoService.resolve("nhanvien01"))
            .thenReturn(new UserDisplayInfoService.UserDisplayInfo("Nguyen Van A", "So KH&CN / Phong Tong hop"));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detail = (List<Map<String, Object>>) fields.get("log_seen_document_detail");
        assertThat(detail).hasSize(1);
        assertThat(detail.get(0).get("name")).isEqualTo("Nguyen Van A");
        assertThat(detail.get(0).get("unit")).isEqualTo("So KH&CN / Phong Tong hop");
        assertThat(detail.get(0).get("lastSeen")).isNotNull();
    }

    @Test
    void execute_whenAlreadySeen_shouldNotDuplicateInLogSeenDocument() {
        var document = vbdiDocument();
        document.replaceDynamicFields(
            "{\"log_seen_document\":\"nhanvien01\",\"coordinator_hide_seen_rtext\":\"nhanvien01\","
                + "\"log_seen_document_detail\":[{\"username\":\"nhanvien01\",\"name\":\"nhanvien01\","
                + "\"unit\":\"\",\"created\":\"09/09/2026 10:00:00\",\"lastSeen\":\"09/09/2026 10:00:00\"}]}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(String.valueOf(fields.get("log_seen_document"))).isEqualTo("nhanvien01");
        assertThat(String.valueOf(fields.get("coordinator_hide_seen_rtext"))).isEqualTo("nhanvien01");
        // Da xem truoc do -> KHONG them 1 phan tu moi vao log_seen_document_detail (giu nguyen 1)...
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detail = (List<Map<String, Object>>) fields.get("log_seen_document_detail");
        assertThat(detail).hasSize(1);
        // ...nhung "created" (lan xem dau) giu nguyen, con "lastSeen" (09/09/2026, 3) PHAI duoc lam moi.
        assertThat(detail.get(0).get("created")).isEqualTo("09/09/2026 10:00:00");
        assertThat(detail.get(0).get("lastSeen")).isNotEqualTo("09/09/2026 10:00:00");
    }

    @Test
    void execute_whenDocumentMissing_shouldFail() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var result = useCase.execute(request(id));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi");
    }

    @Test
    void execute_whenDocumentDeleted_shouldFail() {
        var document = vbdiDocument();
        document.markAsDeleted(UUID.randomUUID());
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withoutDocumentId_shouldFail() {
        var result = useCase.execute(new AddVbdiSeenDocumentRequest());

        assertThat(result.isSuccess()).isFalse();
    }
}
