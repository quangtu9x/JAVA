package com.td.application.duthao;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 08/10/2026: tách tên tệp trong tepbanhanh/tepkemtheo (chuỗi "a;b;" hoặc mảng) giữ thứ tự, loại trùng.
class ChuyenTepVetDuThaoUseCaseTest {

    @Test
    void splitNames_handlesStringArrayAndBlanks() {
        assertThat(ChuyenTepVetDuThaoUseCase.splitNames("a.pdf;b.docx; ;a.pdf;")).containsExactly("a.pdf", "b.docx");
        assertThat(ChuyenTepVetDuThaoUseCase.splitNames(List.of("x.pdf", "", "y.pdf"))).containsExactly("x.pdf", "y.pdf");
        assertThat(ChuyenTepVetDuThaoUseCase.splitNames(null)).isEmpty();
    }

    @Test
    void allowedFields_onlyOfficialFileFields() {
        assertThat(ChuyenTepVetDuThaoUseCase.ALLOWED_FIELDS)
            .containsExactlyInAnyOrder("tepbanhanh", "tepkemtheo", "tepphieutrinh");
    }
}
