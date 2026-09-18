package com.td.application.phieutrinhtd;

import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestorePhieuTrinhTdUseCaseTest {

    @Mock
    private PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;

    private RestorePhieuTrinhTdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RestorePhieuTrinhTdUseCase(phieuTrinhTdDocumentRepository);
        lenient().when(phieuTrinhTdDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void execute_withEmptyList_shouldReturnFailure() {
        assertThat(useCase.execute(List.of()).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedDocument_shouldClearDeletedFields() {
        UUID id = UUID.randomUUID();
        PhieuTrinhTdDocument document = new PhieuTrinhTdDocument("Phieu trinh A", PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO, null);
        ReflectionTestUtils.setField(document, "id", id);
        document.markAsDeleted(UUID.randomUUID());
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.of(document));

        var result = useCase.execute(List.of(id));

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.isDeleted()).isFalse();
    }
}
