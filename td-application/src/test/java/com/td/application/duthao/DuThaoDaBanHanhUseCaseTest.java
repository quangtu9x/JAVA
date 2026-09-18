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
 * button của văn bản đi"): test {@link DuThaoDaBanHanhUseCase} — đối chiếu
 * {@code XuLyDuThao#ChuyenTrangThaiDuThaoDaBanHanh()}.
 */
@ExtendWith(MockitoExtension.class)
class DuThaoDaBanHanhUseCaseTest {

    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private DuThaoDaBanHanhUseCase newUseCase() {
        return new DuThaoDaBanHanhUseCase(
            duThaoDocumentRepository, dynamicDocumentFieldsService, documentLogService, documentCacheService);
    }

    private DuThaoDocument newDraft() {
        DuThaoDocument draft = new DuThaoDocument("Du thao", "duthao", DuThaoStatus.PUBLISHED, null, null, null, null);
        draft.replaceDynamicFields("{}");
        return draft;
    }

    @Test
    void execute_shouldSetDocumentOutFieldsAndFinalizeStatus() {
        var draft = newDraft();
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new DuThaoDaBanHanhRequest();
        request.setId(draft.getId());
        UUID vbdiId = UUID.randomUUID();
        request.setVbdiId(vbdiId);
        request.setVbdiCode("123/QĐ-UBND");

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.DOCUMENT_OUT_FINALIZED);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(fields).containsEntry("documentout_id", vbdiId.toString());
        assertThat(fields).containsEntry("documentout_code", "123/QĐ-UBND");
        assertThat(fields).containsEntry("status_pre", DuThaoStatus.PUBLISHED);
    }

    @Test
    void execute_whenDraftNotFound_shouldFail() {
        UUID id = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new DuThaoDaBanHanhRequest();
        request.setId(id);
        request.setVbdiId(UUID.randomUUID());

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
    }
}
