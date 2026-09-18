package com.td.application.duthao;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DuThaoDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương ứng ở
 * button của văn bản đi"): test {@link DuThaoVanThuKhongBanHanhUseCase} — đối chiếu
 * {@code XuLyDuThao#ChuyenTrangThaiDuThaoVanThuXoa()}.
 */
@ExtendWith(MockitoExtension.class)
class DuThaoVanThuKhongBanHanhUseCaseTest {

    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private DuThaoVanThuKhongBanHanhUseCase newUseCase() {
        return new DuThaoVanThuKhongBanHanhUseCase(
            duThaoDocumentRepository, dynamicDocumentFieldsService, documentLogService, documentCacheService);
    }

    @Test
    void execute_shouldSetXoaVanBanDiReasonAndFinalizeStatus() {
        DuThaoDocument draft = new DuThaoDocument("Du thao", "duthao", DuThaoStatus.PUBLISHED, null, null, null, null);
        draft.replaceDynamicFields("{}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new DuThaoVanThuKhongBanHanhRequest();
        request.setId(draft.getId());

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.DOCUMENT_OUT_FINALIZED);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(fields.get("xoavanbandi").toString()).startsWith("Văn thư không ban hành ");
    }

    @Test
    void execute_whenDraftNotFound_shouldFail() {
        UUID id = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new DuThaoVanThuKhongBanHanhRequest();
        request.setId(id);

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
    }
}
