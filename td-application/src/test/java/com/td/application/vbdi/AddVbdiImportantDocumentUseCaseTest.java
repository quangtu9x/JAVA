package com.td.application.vbdi;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 21/09/2026 (port {@code DocumentOutProcesses.java#HandleAddImportant()} — xem javadoc
 * {@link AddVbdiImportantDocumentUseCase}): test gắn/bỏ gắn sao "Văn bản đi quan trọng" trên field
 * {@code markimportant} (chuỗi username nối {@code ";"}, đúng khuôn field đã đọc từ trước ở
 * {@code PostgresVbdiDocumentRepository#findByFieldContainsMemberAnySeparator}).
 */
@ExtendWith(MockitoExtension.class)
class AddVbdiImportantDocumentUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private AddVbdiImportantDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddVbdiImportantDocumentUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService,
            currentUserProvider);
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nhanvien01"));
    }

    private VbdiDocument vbdiDocument() {
        return new VbdiDocument("Van ban di", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
    }

    private AddVbdiImportantDocumentRequest request(UUID documentId, String action) {
        var request = new AddVbdiImportantDocumentRequest();
        request.setDocumentId(documentId);
        request.setAction(action);
        return request;
    }

    @Test
    void execute_add_shouldAppendUsernameToMarkImportant() {
        var document = vbdiDocument();
        document.replaceDynamicFields("{\"markimportant\":\"nhanvien02\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId(), "add"));

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(String.valueOf(fields.get("markimportant"))).isEqualTo("nhanvien02;nhanvien01");
        verify(documentCacheService).evict(document.getId());
        verify(documentCacheService).evictAllListCaches();
    }

    @Test
    void execute_addWhenAlreadyMarked_shouldNotDuplicate() {
        var document = vbdiDocument();
        document.replaceDynamicFields("{\"markimportant\":\"nhanvien01;nhanvien02\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId(), "add"));

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(String.valueOf(fields.get("markimportant"))).isEqualTo("nhanvien01;nhanvien02");
    }

    @Test
    void execute_delete_shouldRemoveUsernameButKeepOthers() {
        var document = vbdiDocument();
        document.replaceDynamicFields("{\"markimportant\":\"nhanvien01;nhanvien02\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId(), "delete"));

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(String.valueOf(fields.get("markimportant"))).isEqualTo("nhanvien02");
    }

    @Test
    void execute_deleteWhenNotMarked_shouldBeNoOpNotError() {
        var document = vbdiDocument();
        document.replaceDynamicFields("{\"markimportant\":\"nhanvien02\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId(), "delete"));

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(String.valueOf(fields.get("markimportant"))).isEqualTo("nhanvien02");
    }

    @Test
    void execute_withInvalidAction_shouldFail() {
        var result = useCase.execute(request(UUID.randomUUID(), "toggle"));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_whenDocumentMissing_shouldFail() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var result = useCase.execute(request(id, "add"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi");
    }

    @Test
    void execute_whenDocumentDeleted_shouldFail() {
        var document = vbdiDocument();
        document.markAsDeleted(UUID.randomUUID());
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId(), "add"));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withoutDocumentId_shouldFail() {
        var result = useCase.execute(request(null, "add"));

        assertThat(result.isSuccess()).isFalse();
    }
}
