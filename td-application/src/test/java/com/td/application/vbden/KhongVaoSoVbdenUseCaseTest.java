package com.td.application.vbden;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
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
import static org.mockito.Mockito.when;

/**
 * Bổ sung 07/09/2026 (4) — xem javadoc {@link KhongVaoSoVbdenUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class KhongVaoSoVbdenUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private KhongVaoSoVbdenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new KhongVaoSoVbdenUseCase(
            documentRepository, dynamicDocumentFieldsService, currentUserProvider,
            resolveIdentifierByLevelUseCase, documentLogService, documentCacheService);
        org.mockito.Mockito.lenient().when(documentRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BusinessDocument newDocument(String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", "vbden", "2", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    @Test
    void execute_shouldRemoveIdentifierFromAllFourFieldsAndAddToDiscard() {
        BusinessDocument document = newDocument("""
            {"contact_unit_identifier":["H05.02"],"unit_process":["H05.02"],
             "unit_coordinate":["H05.02","H05.03"],"unit_to_read":["H05.02"]}
            """);
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu02"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05.02"));

        var request = new KhongVaoSoVbdenRequest();
        request.setId(document.getId());
        request.setOpinion("Khong dung tham quyen");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("contact_unit_identifier")).isEqualTo(List.of());
        assertThat(saved.get("unit_process")).isEqualTo(List.of());
        assertThat(saved.get("unit_coordinate")).isEqualTo(List.of("H05.03"));
        assertThat(saved.get("unit_to_read")).isEqualTo(List.of());
        assertThat(saved.get("unit_discard")).isEqualTo(List.of("H05.02"));
        assertThat(saved.get("user_discard")).isEqualTo(List.of("vanthu02"));
        // Domino that KHONG dat 2 field nay (khac TuChoiTiepNhanVbdenUseCase).
        assertThat(saved.get("is_document_refuse")).isNull();
        assertThat(saved.get("nghiepvu")).isNull();
    }

    @Test
    void execute_unknownDocument_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new KhongVaoSoVbdenRequest();
        request.setId(id);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument document = newDocument("{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new KhongVaoSoVbdenRequest();
        request.setId(document.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    /** Khong resolve duoc identifier - van thanh cong, chi bo qua buoc loai khoi 4 field. */
    @Test
    void execute_withoutIdentifier_shouldStillSucceedWithoutTouchingFields() {
        BusinessDocument document = newDocument("{\"unit_process\":[\"H05.02\"]}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu02"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        var request = new KhongVaoSoVbdenRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("unit_process")).isEqualTo(List.of("H05.02"));
        assertThat(saved.get("unit_discard")).isNull();
        assertThat(saved.get("user_discard")).isEqualTo(List.of("vanthu02"));
    }
}
