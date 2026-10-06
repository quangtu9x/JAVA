package com.td.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 06/10/2026: mọi danh sách vbden (không tự chọn thứ tự) sắp created_on DESC; module khác giữ thứ tự cũ.
class DocumentRepositoryVbdenDefaultOrderTest {

    private static final String LEGACY = " ORDER BY d.created_on ASC NULLS LAST";

    @Test
    void defaultOrderBy_forVbden_isNewestCreatedFirst() {
        assertThat(DocumentRepository.defaultOrderBy("vbden", LEGACY)).isEqualTo(DocumentRepository.NEWEST_CREATED_FIRST);
        assertThat(DocumentRepository.defaultOrderBy(" VBDEN ", LEGACY)).isEqualTo(DocumentRepository.NEWEST_CREATED_FIRST);
    }

    @Test
    void defaultOrderBy_forOtherTypes_keepsLegacyOrder() {
        assertThat(DocumentRepository.defaultOrderBy("vbden_res", LEGACY)).isEqualTo(LEGACY);
        assertThat(DocumentRepository.defaultOrderBy("vbcddh", LEGACY)).isEqualTo(LEGACY);
        assertThat(DocumentRepository.defaultOrderBy(null, LEGACY)).isEqualTo(LEGACY);
    }
}
