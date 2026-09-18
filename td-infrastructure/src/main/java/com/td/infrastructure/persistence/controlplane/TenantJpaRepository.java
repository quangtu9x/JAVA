package com.td.infrastructure.persistence.controlplane;

import com.td.domain.tenant.Tenant;
import com.td.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// BL-117: repository control-plane (EntityManagerFactory RIÊNG, xem ControlPlaneDataSourceConfig) —
// đặt RIÊNG PACKAGE, xem lý do đầy đủ ở javadoc CategoryJpaRepository (cùng package này).
public interface TenantJpaRepository extends BaseRepository<Tenant> {

    // Xem docs/HUONG_DAN_NHAN_BAN_VA_SUA_API.md (mục "Bước 5"): id-lookup luôn viết @Query tường
    // minh trong repo này, tránh nguy cơ Spring Data suy luận sai kiểu id trên repository generic.
    @Query("SELECT t FROM Tenant t WHERE t.id = :id AND t.deletedOn IS NULL")
    Optional<Tenant> findByIdAndDeletedOnIsNull(@Param("id") UUID id);

    Optional<Tenant> findByTenantIdAndDeletedOnIsNull(String tenantId);

    boolean existsByTenantIdAndDeletedOnIsNull(String tenantId);

    boolean existsByDatabaseNameAndDeletedOnIsNull(String databaseName);

    List<Tenant> findAllByDeletedOnIsNull();
}
