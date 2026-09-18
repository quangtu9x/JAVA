package com.td.application.vbdi;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.domain.vbdi.VbdiDocument;
import com.td.domain.vbdi.VbdiEditHistoryDocument;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 28/08/2026 ("ghi log chỉnh sửa thì tạo ra bảng riêng tham khảo thêm như dự
 * thảo đã làm"): test {@link AddVbdiEditHistoryUseCase} — mirror
 * {@code AddDuThaoEditHistoryUseCaseTest}.
 */
@ExtendWith(MockitoExtension.class)
class AddVbdiEditHistoryUseCaseTest {

    @Mock
    private VbdiEditHistoryDocumentRepository editHistoryRepository;
    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentLogService documentLogService;

    private AddVbdiEditHistoryUseCase useCase;

    private final UUID parentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new AddVbdiEditHistoryUseCase(
            editHistoryRepository, vbdiDocumentRepository, currentUserProvider, documentLogService);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo1"));
    }

    private VbdiDocument existingDocument(String documentType) {
        return new VbdiDocument("Cong van", documentType, VbdiStatus.DRAFT, null, null, null, null);
    }

    private AddVbdiEditHistoryRequest request(UUID id, Map<String, Object> editObj) {
        var request = new AddVbdiEditHistoryRequest();
        request.setParentId(id);
        request.setCreatorName("Nguyen Van A");
        if (editObj != null) {
            request.setEditObj(editObj);
        }
        return request;
    }

    @Test
    void execute_shouldSaveEntryWithServerStampedUserCreator() {
        var document = existingDocument("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiEditHistoryDocument(document.getId(), "vbdi", "{}", null, null);
        when(editHistoryRepository.save(any())).thenReturn(saved);

        Map<String, Object> editObj = new LinkedHashMap<>();
        editObj.put("subject", Map.of("oldvalue", "Cu", "newvalue", "Moi"));

        var result = useCase.execute(request(document.getId(), editObj));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<VbdiEditHistoryDocument> captor = ArgumentCaptor.forClass(VbdiEditHistoryDocument.class);
        verify(editHistoryRepository).save(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.getParentId()).isEqualTo(document.getId());
        assertThat(entity.getFormEdit()).isEqualTo(AddVbdiEditHistoryUseCase.DEFAULT_FORM_EDIT);
        // Danh tinh that do server gan (SERVER-AUTHORITATIVE), client chi gui ten hien thi.
        assertThat(entity.getUserCreator()).isEqualTo("demo1");
        assertThat(entity.getCreatorName()).isEqualTo("Nguyen Van A");
        assertThat(entity.getEditObjJson()).contains("\"subject\"").contains("\"newvalue\":\"Moi\"");
        verify(documentLogService).logCreate(eq(saved.getId()), anyMap());
    }

    @Test
    void execute_whenParentMissing_shouldFail() {
        when(vbdiDocumentRepository.findById(parentId)).thenReturn(Optional.empty());

        var result = useCase.execute(request(parentId, Map.of("subject", Map.of("oldvalue", "", "newvalue", "x"))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi");
        verifyNoInteractions(editHistoryRepository);
    }

    @Test
    void execute_whenParentWrongType_shouldFail() {
        var document = existingDocument("vbdi_res");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId(), Map.of("subject", Map.of("oldvalue", "", "newvalue", "x"))));

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(editHistoryRepository);
    }

    @Test
    void execute_whenEditObjEmpty_shouldFailWithoutSaving() {
        var document = existingDocument("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId(), new LinkedHashMap<>()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("trống");
        verifyNoInteractions(editHistoryRepository);
    }
}
