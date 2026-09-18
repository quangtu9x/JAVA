package com.td.infrastructure.persistence.controlplane;

import com.td.domain.categories.Category;
import com.td.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

// BL-117: repository control-plane (EntityManagerFactory RIÊNG, xem ControlPlaneDataSourceConfig) —
// đặt RIÊNG PACKAGE (không phải com.td.infrastructure.persistence.repository) vì
// @EnableJpaRepositories(basePackageClasses=...) quét NGUYÊN CẢ PACKAGE chứa class chỉ định, không
// phải chỉ đúng class đó — nếu để chung package với 33 repository nghiệp vụ khác sẽ bị
// ControlPlaneJpaRepositoriesConfig quét trùng, gây lỗi "bean đã được định nghĩa" (đã xảy ra thật,
// xem docs/architecture/06-database-per-tenant-multi-tenant.md).
public interface CategoryJpaRepository extends BaseRepository<Category> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM Category c WHERE c.code = :code AND c.id <> :id AND c.deletedOn IS NULL")
    boolean existsByCodeAndIdNotAndDeletedOnIsNull(@Param("code") String code, @Param("id") UUID id);

    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.deletedOn IS NULL")
    Optional<Category> findByIdAndDeletedOnIsNull(@Param("id") UUID id);
}
