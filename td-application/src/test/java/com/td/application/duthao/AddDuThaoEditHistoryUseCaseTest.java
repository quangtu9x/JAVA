package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.domain.duthao.DuThaoDocument;
import com.td.domain.duthao.DuThaoEditHistoryDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 27/08/2026 (port "lưu vết hiệu chỉnh" — tạo bảng lưu vết riêng): test
 * {@link AddDuThaoEditHistoryUseCase} ghi vết vào {@code duthao_edit_history_documents}.
 */
@ExtendWith(MockitoExtension.class)
class AddDuThaoEditHistoryUseCaseTest {

    @Mock
    private DuThaoEditHistoryDocumentRepository editHistoryRepository;
    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentLogService documentLogService;

    private AddDuThaoEditHistoryUseCase useCase;

    private final UUID parentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new AddDuThaoEditHistoryUseCase(
            editHistoryRepository, duThaoDocumentRepository, currentUserProvider, documentLogService);
        // lenient(): không phải test nào cũng chạm tới stamp người dùng
        org.mockito.Mockito.lenient().when(currentUserProvider.currentUsername())
            .thenReturn(Optional.of("demo1"));
    }

    private DuThaoDocument existingDraft(String type) {
        // Id do entity tự sinh; caller dựng request theo đúng id này (khớp pattern
        // LuuDuThaoUseCaseTest — không đụng reflection).
        return new DuThaoDocument("Trich yeu", type, "0", null, null, null, null);
    }

    private AddDuThaoEditHistoryRequest request(UUID id, Map<String, Object> editObj) {
        var request = new AddDuThaoEditHistoryRequest();
        request.setParentId(id);
        request.setCreatorName("Nguyen Van A");
        if (editObj != null) {
            request.setEditObj(editObj);
        }
        return request;
    }

    @Test
    void execute_shouldSaveEntryWithServerStampedUserCreator_andLegacyDisplayFields() {
        var draft = existingDraft("duthao");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        var saved = new DuThaoEditHistoryDocument(draft.getId(), "draft_document", "{}", null, null);
        when(editHistoryRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenReturn(saved);

        Map<String, Object> editObj = new LinkedHashMap<>();
        editObj.put("subject", Map.of("oldvalue", "Cu", "newvalue", "Moi"));

        var result = useCase.execute(request(draft.getId(), editObj));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<com.td.domain.duthao.DuThaoEditHistoryDocument> captor =
            ArgumentCaptor.forClass(com.td.domain.duthao.DuThaoEditHistoryDocument.class);
        verify(editHistoryRepository).save(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.getParentId()).isEqualTo(draft.getId());
        assertThat(entity.getFormEdit()).isEqualTo(AddDuThaoEditHistoryUseCase.DEFAULT_FORM_EDIT);
        // Danh tính thật do server gán (SERVER-AUTHORITATIVE), client chỉ gửi tên hiển thị.
        assertThat(entity.getUserCreator()).isEqualTo("demo1");
        assertThat(entity.getCreatorName()).isEqualTo("Nguyen Van A");
        assertThat(entity.getEditObjJson()).contains("\"subject\"").contains("\"newvalue\":\"Moi\"");
        org.mockito.Mockito.verify(documentLogService)
            .logCreate(org.mockito.ArgumentMatchers.eq(saved.getId()), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void execute_whenParentMissing_shouldFail() {
        when(duThaoDocumentRepository.findById(parentId)).thenReturn(Optional.empty());

        var result = useCase.execute(request(parentId, Map.of("subject", Map.of("oldvalue", "", "newvalue", "x"))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản dự thảo");
        verifyNoInteractions(editHistoryRepository);
    }

    @Test
    void execute_whenParentWrongType_shouldFail() {
        var draft = existingDraft("draft_process");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var result = useCase.execute(request(draft.getId(), Map.of("subject", Map.of("oldvalue", "", "newvalue", "x"))));

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(editHistoryRepository);
    }

    @Test
    void execute_whenEditObjEmpty_shouldFailWithoutSaving() {
        var draft = existingDraft("duthao");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var result = useCase.execute(request(draft.getId(), new LinkedHashMap<>()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("trống");
        verifyNoInteractions(editHistoryRepository);
    }
}
