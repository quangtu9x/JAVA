package com.td.application.vbden;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BL-210/211 (Nhập mới VBDen) — tương thích {@code qlvb/vbden/KiemTrungSoDen} (Domino thật).
 */
@ExtendWith(MockitoExtension.class)
class KiemTrungSoDenUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    private KiemTrungSoDenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new KiemTrungSoDenUseCase(
            documentRepository, dynamicDocumentFieldsService, currentUserProvider, appUserRepository);
    }

    @Test
    void execute_missingParams_shouldReturnEmptyWithoutQuerying() {
        var request = new KiemTrungSoDenRequest();

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_currentUserWithoutIdentifier_shouldReturnEmpty() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new KiemTrungSoDenRequest();
        request.setDocumentRegisterId(UUID.randomUUID());
        request.setDocumentNumber("5");

        var result = useCase.execute(request);

        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_matchingNumberAndOwnIdentifier_shouldReturnDuplicate() {
        stubCurrentUserIdentifier("G10");
        UUID registerId = UUID.randomUUID();
        BusinessDocument doc = new BusinessDocument("Van ban trung so", "vbden", "0", null, null, null, null);
        when(documentRepository.findByFieldValue(
                org.mockito.ArgumentMatchers.eq("vbden"),
                org.mockito.ArgumentMatchers.eq("document_register_id"),
                org.mockito.ArgumentMatchers.eq(registerId.toString()),
                any()))
            .thenReturn(new PageImpl<>(List.of(doc)));
        when(dynamicDocumentFieldsService.parseDynamicFields(any())).thenReturn(Map.of(
            "document_number", "5", "identifier_creator", "G10", "code", "01/VB"
        ));

        var request = new KiemTrungSoDenRequest();
        request.setDocumentRegisterId(registerId);
        request.setDocumentNumber("5");

        var result = useCase.execute(request);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getCode()).isEqualTo("01/VB");
    }

    @Test
    void execute_documentNumberStoredAsDouble_shouldMatchAndReturnPlainTextAndTitleSubject() {
        // 05/10/2026: document_number lưu dạng số trong JSONB -> Double 92.0; trích yếu ở cột title.
        stubCurrentUserIdentifier("G10");
        BusinessDocument doc = new BusinessDocument("Trich yeu o cot title", "vbden", "0", null, null, null, null);
        when(documentRepository.findByFieldValue(any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(doc)));
        when(dynamicDocumentFieldsService.parseDynamicFields(any())).thenReturn(Map.of(
            "document_number", 92.0d, "identifier_creator", "G10"
        ));

        var request = new KiemTrungSoDenRequest();
        request.setDocumentRegisterId(UUID.randomUUID());
        request.setDocumentNumber("92");

        var result = useCase.execute(request);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getDocumentNumber()).isEqualTo("92");
        assertThat(result.getData().get(0).getSubject()).isEqualTo("Trich yeu o cot title");
    }

    @Test
    void execute_matchingNumberButDifferentIdentifier_shouldNotMatch() {
        stubCurrentUserIdentifier("G10");
        UUID registerId = UUID.randomUUID();
        BusinessDocument doc = new BusinessDocument("Van ban don vi khac", "vbden", "0", null, null, null, null);
        when(documentRepository.findByFieldValue(any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(doc)));
        when(dynamicDocumentFieldsService.parseDynamicFields(any())).thenReturn(Map.of(
            "document_number", "5", "identifier_creator", "G20"
        ));

        var request = new KiemTrungSoDenRequest();
        request.setDocumentRegisterId(registerId);
        request.setDocumentNumber("5");

        var result = useCase.execute(request);

        assertThat(result.getData()).isEmpty();
    }

    private void stubCurrentUserIdentifier(String identifier) {
        String username = "nhanvien01";
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of(username));
        AppUser user = new AppUser("kc-" + username, username, "Nguyen Van A", null, null, null, true,
            null, 0, null, null, null, null, null, null, identifier, false, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull(username)).thenReturn(Optional.of(user));
    }
}
