package com.td.infrastructure.persistence.repository;

import com.td.application.vbdi.VbdiStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 01/10/2026: văn bản đi CHƯA vào sổ (DRAFT — chocapkyso/chobanhanh/chocapkysomc) sắp theo ngày tạo
 * mới nhất; trạng thái khác giữ luật 28/09 (ngày ban hành → số → last_modified_on).
 */
class PostgresVbdiDocumentOrderByTest {

    @Test
    void orderByForStatus_draft_shouldSortByCreatedOnDesc() {
        assertThat(PostgresVbdiDocumentRepository.orderByForStatus(VbdiStatus.DRAFT))
            .isEqualTo(" ORDER BY d.created_on DESC NULLS LAST, d.last_modified_on DESC NULLS LAST");
    }

    @Test
    void orderByForStatus_issued_shouldKeepPromulgationDateThenNumber() {
        String orderBy = PostgresVbdiDocumentRepository.orderByForStatus(VbdiStatus.ISSUED);
        assertThat(orderBy).contains("promulgation_date").contains("document_number");
        assertThat(orderBy.indexOf("promulgation_date")).isLessThan(orderBy.indexOf("document_number"));
    }

    @Test
    void orderByForStatus_null_shouldKeepIssuedOrder() {
        assertThat(PostgresVbdiDocumentRepository.orderByForStatus(null)).contains("promulgation_date");
    }
}
