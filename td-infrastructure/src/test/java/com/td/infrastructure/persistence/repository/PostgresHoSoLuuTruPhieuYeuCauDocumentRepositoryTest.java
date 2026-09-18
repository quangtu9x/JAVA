package com.td.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bugfix 09/09/2026 (báo lỗi người dùng — ERR-8de8584a: {@code UnsupportedOperationException} tại
 * {@code Unpaged.getPageSize()}, lộ ra khi {@code GetSoLuongCanXuLyUseCase} lần đầu gọi tới view
 * {@code phieuyeucau_quahan_by_identifier} — {@code ViewQueryService#queryPhieuYeuCauQuaHan} truyền
 * {@code Pageable.unpaged()} có chủ đích (lọc quá hạn trên TOÀN BỘ kết quả trước khi tự phân trang
 * lại) nhưng {@code findByCriteria} trước đó gọi thẳng {@code pageable.getPageSize()}/
 * {@code getOffset()} — 2 method KHÔNG được hỗ trợ bởi {@code Pageable.unpaged()} theo đúng thiết kế
 * Spring Data (ném exception thay vì trả giá trị mặc định)). Test dùng {@link EntityManager}/
 * {@link Query} mock (không cần DB thật) — tiêm qua {@link ReflectionTestUtils} vì field
 * {@code @PersistenceContext} là private, không có constructor/setter công khai.
 */
@ExtendWith(MockitoExtension.class)
class PostgresHoSoLuuTruPhieuYeuCauDocumentRepositoryTest {

    @Mock
    private HoSoLuuTruPhieuYeuCauDocumentJpaRepository jpaRepository;
    @Mock
    private EntityManager entityManager;

    private PostgresHoSoLuuTruPhieuYeuCauDocumentRepository newRepository() {
        var repository = new PostgresHoSoLuuTruPhieuYeuCauDocumentRepository(jpaRepository);
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
        return repository;
    }

    private Query stubCountAndSelectQueries(long total) {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(total);
        Query selectQuery = mock(Query.class);
        when(selectQuery.getResultList()).thenReturn(List.of());
        when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
        when(entityManager.createNativeQuery(anyString(), eq(
            com.td.domain.hosoluutru.HoSoLuuTruPhieuYeuCauDocument.class))).thenReturn(selectQuery);
        return selectQuery;
    }

    @Test
    void findByCriteria_withUnpagedPageable_shouldNotThrowAndUseSafetyCapInsteadOfPageSize() {
        Query selectQuery = stubCountAndSelectQueries(0L);

        var result = newRepository().findByCriteria(
            List.of("identifier"), "H05.01", Map.of(), Pageable.unpaged());

        assertThat(result.getContent()).isEmpty();
        // MAX_ROWS (private, xem lop) = 2000 - dung cap an toan thay vi getPageSize() (nem exception).
        verify(selectQuery).setParameter("limit", 2000);
        verify(selectQuery, never()).setParameter(eq("offset"), any());
    }

    @Test
    void findByCriteria_withPagedPageable_shouldKeepUsingPageSizeAndOffset() {
        Query selectQuery = stubCountAndSelectQueries(0L);

        newRepository().findByCriteria(
            List.of("identifier"), "H05.01", Map.of(), PageRequest.of(2, 10));

        verify(selectQuery).setParameter("limit", 10);
        verify(selectQuery).setParameter("offset", 20L);
    }
}
