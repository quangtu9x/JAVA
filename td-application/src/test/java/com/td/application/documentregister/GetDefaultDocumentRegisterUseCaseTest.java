package com.td.application.documentregister;

import com.td.domain.documentregister.DocumentRegister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BL-210/211 (Nhập mới VBDen): tương thích {@code qlvb/danhmuc/DanhSachSo} — lấy sổ mặc định của
 * 1 đơn vị + loại sổ để tự điền form "Nhập mới".
 */
@ExtendWith(MockitoExtension.class)
class GetDefaultDocumentRegisterUseCaseTest {

    @Mock
    private DocumentRegisterRepository documentRegisterRepository;

    private GetDefaultDocumentRegisterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetDefaultDocumentRegisterUseCase(documentRegisterRepository, com.td.application.sharedcore.UserDelegationTestSupport.emptyResolver());
    }

    @Test
    void execute_blankIdentifier_shouldFail() {
        var result = useCase.execute("", "Văn bản đến");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_notFound_shouldFail() {
        when(documentRegisterRepository.findByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
                "G10", "Văn bản đến")).thenReturn(Optional.empty());

        var result = useCase.execute("G10", "Văn bản đến");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_lockedRegister_shouldFail() {
        DocumentRegister register = newRegister(true, 10, "");
        register.setLocked(true);
        when(documentRegisterRepository.findByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
                "G10", "Văn bản đến")).thenReturn(Optional.of(register));

        var result = useCase.execute("G10", "Văn bản đến");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_autoIncrement_shouldReturnComputedNextNumber() {
        DocumentRegister register = newRegister(true, 1, "5;3;8");
        when(documentRegisterRepository.findByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
                "G10", "Văn bản đến")).thenReturn(Optional.of(register));

        var result = useCase.execute("G10", "Văn bản đến");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getCodeStartNumber()).isEqualTo(9);
        // Chỉ TÍNH, không ghi nhận/thay đổi trạng thái sổ (peek, không phải issue thật).
        assertThat(register.getCodeStartNumbers()).isEqualTo("5;3;8");
    }

    @Test
    void execute_manualNumbering_shouldReturnRawStoredNumberUnchanged() {
        DocumentRegister register = newRegister(false, 7, "");
        when(documentRegisterRepository.findByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
                "G10", "Văn bản đến")).thenReturn(Optional.of(register));

        var result = useCase.execute("G10", "Văn bản đến");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().isAutoIncrement()).isFalse();
        assertThat(result.getData().getCodeStartNumber()).isEqualTo(7);
    }

    private DocumentRegister newRegister(boolean autoIncrement, int codeStartNumber, String codeStartNumbers) {
        return new DocumentRegister("Sổ văn bản đến", "Văn bản đến", 2026, "G10", null, null, null, null,
            null, null, null, "Nguyễn Văn A", codeStartNumber, autoIncrement, true, codeStartNumbers);
    }
}
