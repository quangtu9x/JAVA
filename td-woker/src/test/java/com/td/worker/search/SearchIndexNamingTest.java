package com.td.worker.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchIndexNamingTest {

    @Test
    void alias_shouldBeLowercaseAndPrefixed() {
        assertThat(SearchIndexNaming.alias("HSCV", "H05_01", "vbdi")).isEqualTo("hscv-h05_01-vbdi");
    }

    @Test
    void alias_shouldReplaceCharactersEsRejects() {
        assertThat(SearchIndexNaming.alias("hscv", "So KH&CN", "documents")).isEqualTo("hscv-so_kh_cn-documents");
    }

    @Test
    void physical_shouldAppendMappingVersion() {
        assertThat(SearchIndexNaming.physical("hscv-h05-vbdi", 3)).isEqualTo("hscv-h05-vbdi-v3");
    }

    @Test
    void requireSqlIdentifier_shouldRejectInjection() {
        assertThat(SearchIndexNaming.requireSqlIdentifier("vbdi_documents")).isEqualTo("vbdi_documents");
        assertThatThrownBy(() -> SearchIndexNaming.requireSqlIdentifier("documents; drop table x"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SearchIndexNaming.requireSqlIdentifier("Documents"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
