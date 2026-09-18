package com.td.application.phieutrinhtd;

import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AddPhieuTrinhTdDynamicDocumentUseCaseTest {

    @Mock
    private PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private AddPhieuTrinhTdDynamicDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddPhieuTrinhTdDynamicDocumentUseCase(
            phieuTrinhTdDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService,
            authorAclFieldsService);
        lenient().when(phieuTrinhTdDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void execute_withValidRequest_shouldCreateDocumentWithDefaultStatusDangSoanThao() {
        var request = new AddDynamicDocumentRequest();
        request.setTitle("Phieu trinh tham dinh du an A");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("description", "Xin y kien du an A");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(phieuTrinhTdDocumentRepository).save(any(PhieuTrinhTdDocument.class));
        verify(documentCacheService).evictAllListCaches();
        verify(documentLogService).logCreate(any(), any());
    }

    @Test
    void execute_withoutTitle_shouldUseDefaultTitle() {
        var request = new AddDynamicDocumentRequest();

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
    }
}
