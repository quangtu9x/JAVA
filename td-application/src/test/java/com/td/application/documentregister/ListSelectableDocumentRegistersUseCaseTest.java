package com.td.application.documentregister;

import com.td.domain.documentregister.DocumentRegister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BL-210/211 (Nhập mới VBDen): dropdown "Chọn sổ văn bản" — tương đương
 * {@code qlvb/danhmuc/DanhSachSo} khi trả về NHIỀU sổ.
 */
@ExtendWith(MockitoExtension.class)
class ListSelectableDocumentRegistersUseCaseTest {

    @Mock
    private DocumentRegisterRepository documentRegisterRepository;

    private ListSelectableDocumentRegistersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListSelectableDocumentRegistersUseCase(documentRegisterRepository, com.td.application.sharedcore.UserDelegationTestSupport.emptyResolver());
    }

    @Test
    void execute_blankIdentifier_shouldFail() {
        var result = useCase.execute("", "Văn bản đến");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_noRegisters_shouldReturnEmptyList() {
        when(documentRegisterRepository.findByIdentifierAndDocumentRegisterTypeAndIsLockedFalseAndDeletedOnIsNull(
                "G10", "Văn bản đến")).thenReturn(List.of());

        var result = useCase.execute("G10", "Văn bản đến");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_multipleRegisters_shouldReturnEachWithComputedNextNumber() {
        DocumentRegister register1 = newRegister("Sổ chính", true, 1, "5;3;8");
        DocumentRegister register2 = newRegister("Sổ phụ", false, 7, "");
        when(documentRegisterRepository.findByIdentifierAndDocumentRegisterTypeAndIsLockedFalseAndDeletedOnIsNull(
                "G10", "Văn bản đến")).thenReturn(List.of(register1, register2));

        var result = useCase.execute("G10", "Văn bản đến");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).getCodeStartNumber()).isEqualTo(9); // auto: peek = max+1
        assertThat(result.getData().get(1).getCodeStartNumber()).isEqualTo(7); // manual: giữ nguyên
        // Chỉ TÍNH, không ghi nhận/thay đổi trạng thái sổ.
        assertThat(register1.getCodeStartNumbers()).isEqualTo("5;3;8");
    }

    private DocumentRegister newRegister(String name, boolean autoIncrement, int codeStartNumber, String codeStartNumbers) {
        return new DocumentRegister(name, "Văn bản đến", 2026, "G10", null, null, null, null,
            null, null, null, "Nguyễn Văn A", codeStartNumber, autoIncrement, false, codeStartNumbers);
    }
}
