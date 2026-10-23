package com.td.application.common.models;

import com.td.application.documents.AddDynamicDocumentRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 19/10/2026 — lỗi "value too long" của PostgreSQL thành câu người dùng hiểu được. */
class ApiResponseFriendlyMessageTest {

    @Test
    void error_withPostgresValueTooLong_shouldBecomeFriendlyMessageWithLimit() {
        ApiResponse<Void> response = ApiResponse.error(
            "Tạo văn bản thất bại: could not execute statement [ERROR: value too long for type character varying(300)]");

        assertThat(response.getMessage()).isEqualTo(
            "Dữ liệu nhập vượt quá 300 ký tự cho phép (vd Trích yếu tối đa 300 ký tự) — vui lòng rút gọn.");
    }

    @Test
    void error_otherMessages_shouldBeUnchanged() {
        assertThat(ApiResponse.error("Không tìm thấy dự thảo").getMessage()).isEqualTo("Không tìm thấy dự thảo");
        assertThat(ApiResponse.friendlyMessage(null)).isNull();
    }

    @Test
    void labelTitle_shouldTruncateOnlyWhenOverColumnLength() {
        assertThat(AddDynamicDocumentRequest.labelTitle("ngắn")).isEqualTo("ngắn");
        String longText = "a".repeat(350);
        assertThat(AddDynamicDocumentRequest.labelTitle(longText)).hasSize(300).endsWith("…");
    }
}
