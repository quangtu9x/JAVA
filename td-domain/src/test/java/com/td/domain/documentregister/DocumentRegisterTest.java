package com.td.domain.documentregister;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BL-210/211 (Nhập mới VBDen): {@link DocumentRegister#peekNextNumber()}/
 * {@link DocumentRegister#recordIssuedNumber(int)} — mirror thuật toán Domino thật
 * ({@code LuuVanBanDen.java tang_so_cho_so_van_ban} / {@code DanhSachSo.java findMaxNumber}).
 */
class DocumentRegisterTest {

    @Test
    void peekNextNumber_withEmptyHistory_shouldReturnCodeStartNumber() {
        var register = newRegister(true, 10, "");

        assertThat(register.peekNextNumber()).isEqualTo(10);
    }

    @Test
    void peekNextNumber_withHistory_shouldReturnMaxPlusOne() {
        var register = newRegister(true, 1, "5;3;8;2");

        assertThat(register.peekNextNumber()).isEqualTo(9);
    }

    @Test
    void peekNextNumber_shouldNotMutateState() {
        var register = newRegister(true, 1, "5");

        register.peekNextNumber();

        assertThat(register.getCodeStartNumber()).isEqualTo(1);
        assertThat(register.getCodeStartNumbers()).isEqualTo("5");
    }

    @Test
    void recordIssuedNumber_shouldAppendToHistoryAndAdvanceCounter() {
        var register = newRegister(true, 5, "");

        register.recordIssuedNumber(5);

        assertThat(register.getCodeStartNumber()).isEqualTo(6);
        assertThat(register.getCodeStartNumbers()).isEqualTo("5");
    }

    @Test
    void recordIssuedNumber_calledTwice_shouldAppendBothToHistory() {
        var register = newRegister(true, 1, "");

        register.recordIssuedNumber(1);
        register.recordIssuedNumber(2);

        assertThat(register.getCodeStartNumbers()).isEqualTo("1;2");
        assertThat(register.getCodeStartNumber()).isEqualTo(3);
    }

    @Test
    void recordIssuedNumber_historyOver500_shouldDropOldest() {
        StringBuilder history = new StringBuilder();
        for (int i = 1; i <= 500; i++) {
            history.append(i).append(i < 500 ? ";" : "");
        }
        var register = newRegister(true, 501, history.toString());

        register.recordIssuedNumber(501);

        String[] parts = register.getCodeStartNumbers().split(";");
        assertThat(parts).hasSize(500);
        assertThat(parts[0]).isEqualTo("2");
        assertThat(parts[parts.length - 1]).isEqualTo("501");
    }

    @Test
    void recordIssuedNumber_whenLocked_shouldThrow() {
        var register = newRegister(true, 1, "");
        register.setLocked(true);

        assertThatThrownBy(() -> register.recordIssuedNumber(1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("khoá");
    }

    @Test
    void peekNextNumber_withNonNumericGarbageInHistory_shouldIgnoreThem() {
        var register = newRegister(true, 1, "abc;5;;xyz");

        assertThat(register.peekNextNumber()).isEqualTo(6);
    }

    private DocumentRegister newRegister(boolean autoIncrement, int codeStartNumber, String codeStartNumbers) {
        return new DocumentRegister("Sổ văn bản đến", "Văn bản đến", 2026, "G10", null, null, null, null,
            null, null, null, "Nguyễn Văn A", codeStartNumber, autoIncrement, true, codeStartNumbers);
    }

    // Bổ sung 04/09/2026 (BL-116 tiếp, yêu cầu người dùng: "Lãnh đạo phân công tự điền mặc định khi
    // Nhập mới" — xem javadoc field userAssignerId/userAssignerUsername/assignerPosition).
    @Test
    void constructor16Args_shouldLeaveUserAssignerFieldsNull() {
        var register = newRegister(true, 1, "");

        assertThat(register.getUserAssignerId()).isNull();
        assertThat(register.getUserAssignerUsername()).isNull();
        assertThat(register.getAssignerPosition()).isNull();
    }

    @Test
    void constructor19Args_shouldSetUserAssignerFields() {
        UUID assignerId = UUID.randomUUID();

        var register = new DocumentRegister("Sổ văn bản đến", "Văn bản đến", 2026, "G10", null, null,
            null, null, null, null, null, "Nguyễn Văn A", 1, true, true, "",
            assignerId, "nguyenvana", "Giám đốc");

        assertThat(register.getUserAssignerId()).isEqualTo(assignerId);
        assertThat(register.getUserAssignerUsername()).isEqualTo("nguyenvana");
        assertThat(register.getAssignerPosition()).isEqualTo("Giám đốc");
    }

    @Test
    void update_shouldOverwriteUserAssignerFieldsUnconditionally() {
        UUID oldAssignerId = UUID.randomUUID();
        UUID newAssignerId = UUID.randomUUID();
        var register = new DocumentRegister("Sổ văn bản đến", "Văn bản đến", 2026, "G10", null, null,
            null, null, null, null, null, "Nguyễn Văn A", 1, true, true, "",
            oldAssignerId, "nguyenvana", "Giám đốc");

        register.update("Sổ văn bản đến", "Văn bản đến", 2026, "G10", null, null, null, null, null,
            null, null, "Trần Thị B", 1, true, true, "",
            newAssignerId, "tranthib", "Phó giám đốc");

        assertThat(register.getUserAssignerId()).isEqualTo(newAssignerId);
        assertThat(register.getUserAssignerUsername()).isEqualTo("tranthib");
        assertThat(register.getAssignerPosition()).isEqualTo("Phó giám đốc");
    }

    // Cùng quy ước GHI ĐÈ VÔ ĐIỀU KIỆN với documentType/unitPublish/assigner (KHÔNG null-check) —
    // cho phép xoá "Lãnh đạo phân công" đã chọn về rỗng có chủ đích, khác các field CÓ null-check
    // (name/identifier...) nghĩa là "không đổi".
    @Test
    void update_withNullUserAssignerFields_shouldClearThem() {
        var register = new DocumentRegister("Sổ văn bản đến", "Văn bản đến", 2026, "G10", null, null,
            null, null, null, null, null, "Nguyễn Văn A", 1, true, true, "",
            UUID.randomUUID(), "nguyenvana", "Giám đốc");

        register.update("Sổ văn bản đến", "Văn bản đến", 2026, "G10", null, null, null, null, null,
            null, null, null, 1, true, true, "", null, null, null);

        assertThat(register.getUserAssignerId()).isNull();
        assertThat(register.getUserAssignerUsername()).isNull();
        assertThat(register.getAssignerPosition()).isNull();
    }
}
