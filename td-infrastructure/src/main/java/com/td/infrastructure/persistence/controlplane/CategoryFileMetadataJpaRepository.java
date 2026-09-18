package com.td.infrastructure.persistence.controlplane;

import com.td.domain.categories.CategoryFileMetadata;
import com.td.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// BL-118: repository control-plane cho tệp đính kèm Category (Banner/Footer/Hướng dẫn sử dụng) —
// cùng lý do đặt package riêng như CategoryJpaRepository/TenantJpaRepository (xem javadoc
// ControlPlaneJpaRepositoriesConfig): @EnableJpaRepositories(basePackages=...) quét NGUYÊN CẢ
// PACKAGE, không phải chỉ đúng những interface được liệt kê.
public interface CategoryFileMetadataJpaRepository extends BaseRepository<CategoryFileMetadata> {

    // BUGFIX (thật, phát hiện lúc khởi động app): KHÔNG được derive query theo tên method có so
    // sánh "id" (vd findByIdAnd...) — field `id` khai báo ở AbstractEntity<T> (generic, type-erased)
    // khiến Spring Data suy ra kiểu "Object" thay vì UUID, ném
    // "Cannot compare left expression of type 'java.lang.Object' with right expression of type
    // 'java.util.UUID'" ngay lúc khởi động. Cùng lý do CategoryJpaRepository.findByIdAndDeletedOnIsNull
    // đã phải dùng @Query tường minh — áp dụng lại đúng convention đó ở đây.
    @Query("SELECT f FROM CategoryFileMetadata f WHERE f.categoryId = :categoryId AND f.deletedOn IS NULL")
    List<CategoryFileMetadata> findAllByCategoryIdAndDeletedOnIsNull(@Param("categoryId") UUID categoryId);

    @Query("SELECT f FROM CategoryFileMetadata f WHERE f.id = :id AND f.categoryId = :categoryId AND f.deletedOn IS NULL")
    Optional<CategoryFileMetadata> findByIdAndCategoryIdAndDeletedOnIsNull(@Param("id") UUID id, @Param("categoryId") UUID categoryId);
}
