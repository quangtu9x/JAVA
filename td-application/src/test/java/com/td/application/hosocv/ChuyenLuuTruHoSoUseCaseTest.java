package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosocv.HoSoCVDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3: {@link ChuyenLuuTruHoSoUseCase}. Guard đúng điều kiện nút
 * "Chuyển lưu trữ" ({@code ButtonActions.jsx}): {@code status="1"} + {@code trinhlanhdaoduyet="1"}
 * + {@code category} đã chọn + {@code storaged} chưa chuyển.
 */
@ExtendWith(MockitoExtension.class)
class ChuyenLuuTruHoSoUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AddHoSoCVProcessDocumentUseCase addHoSoCVProcessDocumentUseCase;
    @Mock
    private com.td.application.hosoluutru.TaoHoSoLuuTruTuHoSoCVUseCase taoHoSoLuuTruTuHoSoCVUseCase;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private ChuyenLuuTruHoSoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChuyenLuuTruHoSoUseCase(
            hoSoCVDocumentRepository, dynamicDocumentFieldsService, documentCacheService,
            documentLogService, currentUserProvider, addHoSoCVProcessDocumentUseCase,
            taoHoSoLuuTruTuHoSoCVUseCase);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        lenient().when(addHoSoCVProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(taoHoSoLuuTruTuHoSoCVUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_whenNotEnded_shouldReturnFailure() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "0",
            "{\"trinhlanhdaoduyet\":\"1\",\"category\":\"Hanh chinh\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThat(useCase.execute(request(document.getId())).isSuccess()).isFalse();
    }

    @Test
    void execute_whenNotApproved_shouldReturnFailure() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "1", "{\"category\":\"Hanh chinh\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThat(useCase.execute(request(document.getId())).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCategory_shouldReturnFailure() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "1", "{\"trinhlanhdaoduyet\":\"1\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThat(useCase.execute(request(document.getId())).isSuccess()).isFalse();
    }

    @Test
    void execute_alreadyStoraged_shouldReturnFailure() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "1",
            "{\"trinhlanhdaoduyet\":\"1\",\"category\":\"Hanh chinh\",\"storaged\":\"2\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThat(useCase.execute(request(document.getId())).isSuccess()).isFalse();
    }

    @Test
    void execute_whenAllConditionsMet_shouldSetStoragedTo2() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "1",
            "{\"trinhlanhdaoduyet\":\"1\",\"category\":\"Hanh chinh\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields.get("storaged")).isEqualTo(HoSoCVLifecycleStatus.STORAGED_DA_CHUYEN_CHO_TIEP_NHAN);
        assertThat(fields.get("storaged_date")).isNotNull();
    }

    // 08/09/2026 (port module hosoluutru): TaoHoSoLuuTruTuHoSoCVUseCase phai duoc goi TRUOC KHI luu
    // storaged="2" - neu that bai (vd. khong tim thay danh muc), khong duoc luu gi ben hosocv.
    @Test
    void execute_whenTaoHoSoLuuTruFails_shouldNotSaveHoSoCVAndReturnFailure() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "1",
            "{\"trinhlanhdaoduyet\":\"1\",\"category\":\"Hanh chinh\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(taoHoSoLuuTruTuHoSoCVUseCase.execute(any())).thenReturn(Result.failure("Không tìm thấy danh mục hồ sơ lưu trữ"));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isFalse();
        org.mockito.Mockito.verify(hoSoCVDocumentRepository, org.mockito.Mockito.never()).save(any());
    }

    private ChuyenLuuTruHoSoRequest request(UUID id) {
        var request = new ChuyenLuuTruHoSoRequest();
        request.setHosocvId(id);
        request.setDmhsId(UUID.randomUUID());
        return request;
    }

    private HoSoCVDocument newDocument(UUID id, String status, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Test", "hosocv", status, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
