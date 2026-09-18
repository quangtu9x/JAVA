package com.td.application.vbden;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Port của {@code TDDocumentUlties.tinhSoNgayConLai}/{@code xacDinhTrangThai} — xem javadoc
 * {@link VbdenStatusService} về nguồn Domino và caveat biên chưa xác nhận.
 */
class VbdenStatusServiceTest {

    private final VbdenStatusService service = new VbdenStatusService();

    @Test
    void computeRemainingDays_withNullDeadline_shouldReturnNull() {
        assertThat(service.computeRemainingDays(null, LocalDate.of(2026, 8, 20))).isNull();
    }

    @Test
    void computeRemainingDays_shouldMatchDeadlineMinusToday() {
        LocalDate today = LocalDate.of(2026, 8, 20);

        assertThat(service.computeRemainingDays(LocalDate.of(2026, 8, 25), today)).isEqualTo(5);
        assertThat(service.computeRemainingDays(LocalDate.of(2026, 8, 20), today)).isEqualTo(0);
        assertThat(service.computeRemainingDays(LocalDate.of(2026, 8, 15), today)).isEqualTo(-5);
    }

    @Test
    void determineStatus_overdueWithPendingAssignee_shouldReturn1() {
        assertThat(service.determineStatus(-1, true, false))
            .isEqualTo(VbdenStatusService.STATUS_QUA_HAN_DANG_XU_LY);
    }

    @Test
    void determineStatus_onTimeWithPendingAssignee_shouldReturn2() {
        assertThat(service.determineStatus(0, true, false))
            .isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
        assertThat(service.determineStatus(3, true, false))
            .isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
    }

    @Test
    void determineStatus_overdueAndProcessed_shouldReturn3() {
        assertThat(service.determineStatus(-1, false, true))
            .isEqualTo(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY);
    }

    @Test
    void determineStatus_onTimeAndProcessed_shouldReturn4() {
        assertThat(service.determineStatus(0, false, true))
            .isEqualTo(VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY);
    }

    @Test
    void determineStatus_noAssigneeAndNotProcessed_shouldNotBeTreatedAsCompleted() {
        // Domino: user_process/user_processing rong nhung user_processed cung rong -> chua co ai
        // xu ly bao gio, khong phai "da xu ly xong" -> van roi vao nhanh qua han/con han dang xu ly.
        assertThat(service.determineStatus(-1, false, false))
            .isEqualTo(VbdenStatusService.STATUS_QUA_HAN_DANG_XU_LY);
        assertThat(service.determineStatus(0, false, false))
            .isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
    }
}
