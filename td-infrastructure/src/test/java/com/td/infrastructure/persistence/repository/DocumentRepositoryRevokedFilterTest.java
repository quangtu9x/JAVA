package com.td.infrastructure.persistence.repository;

import com.td.application.documents.VbdenOrgViewFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 30/09/2026 (báo lỗi người dùng: vbdi "Lấy lại văn bản" — văn bản đến vẫn hiện ở
 * {@code vbden/xuly/dencoquan}): mọi truy vấn DANH SÁCH phải loại {@code is_revoke="1"} (tương đương
 * Domino đổi Form sang {@code DocumentIn_Deleted}), trừ view "Bị thu hồi" và tra cứu chi tiết.
 */
class DocumentRepositoryRevokedFilterTest {

    private static final String NOT_REVOKED_SQL = "COALESCE(d.dynamic_fields->>'is_revoke', '') NOT IN (:notRevoked_0)";

    private final PostgresDocumentRepository repository = new PostgresDocumentRepository(null);
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(entityManager.createNativeQuery(anyString(), any(Class.class))).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        when(query.getResultList()).thenReturn(List.of());
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
    }

    private String capturedSql() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, atLeastOnce()).createNativeQuery(captor.capture(), any(Class.class));
        return captor.getValue();
    }

    private VbdenOrgViewFilter.VbdenOrgViewFilterBuilder orgFilter() {
        return VbdenOrgViewFilter.builder().orgFieldNames(List.of("identifier_creator")).orgValue("H05.01");
    }

    @Test
    void findByVbdenOrgView_default_shouldExcludeRevoked() {
        repository.findByVbdenOrgView("vbden", orgFilter().build(), PageRequest.of(0, 10));
        assertThat(capturedSql()).contains(NOT_REVOKED_SQL);
    }

    @Test
    void findByVbdenOrgView_revokedTrashView_shouldNotExcludeRevoked() {
        var filter = orgFilter().includeFieldValues(Map.of("reply", List.of("1"), "is_revoke", List.of("1"))).build();
        repository.findByVbdenOrgView("vbden_so", filter, PageRequest.of(0, 10));
        assertThat(capturedSql()).doesNotContain(NOT_REVOKED_SQL);
    }

    @Test
    void findByVbdenOrgView_includeRevoked_shouldNotExcludeRevoked() {
        repository.findByVbdenOrgView("vbden_so", orgFilter().includeRevoked(true).build(), PageRequest.of(0, 1));
        assertThat(capturedSql()).doesNotContain(NOT_REVOKED_SQL);
    }

    @Test
    void findByAnyFieldContains_shouldExcludeRevoked() {
        repository.findByAnyFieldContains("vbden", List.of("user_process"), "nguyenvana", PageRequest.of(0, 10));
        assertThat(capturedSql()).contains(NOT_REVOKED_SQL);
    }

    @Test
    void findByFieldValue_businessLookup_shouldNotExcludeRevoked() {
        repository.findByFieldValue("vbden_so", "parentid", "abc", PageRequest.of(0, 10));
        assertThat(capturedSql()).doesNotContain(NOT_REVOKED_SQL);
    }
}
