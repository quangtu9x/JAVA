package com.td.infrastructure.persistence.controlplane;

import com.td.domain.dbserver.DbServer;
import com.td.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// BL-117 (bổ sung 19/08/2026): repository control-plane (EntityManagerFactory RIÊNG) — đặt RIÊNG
// PACKAGE, xem lý do đầy đủ ở javadoc CategoryJpaRepository (cùng package này).
public interface DbServerJpaRepository extends BaseRepository<DbServer> {

    // Xem docs/HUONG_DAN_NHAN_BAN_VA_SUA_API.md (mục "Bước 5"): id-lookup luôn viết @Query tường
    // minh trong repo này, tránh nguy cơ Spring Data suy luận sai kiểu id trên repository generic.
    @Query("SELECT s FROM DbServer s WHERE s.id = :id AND s.deletedOn IS NULL")
    Optional<DbServer> findByIdAndDeletedOnIsNull(@Param("id") UUID id);

    boolean existsByHostAndPortAndDeletedOnIsNull(String host, int port);

    List<DbServer> findAllByDeletedOnIsNullOrderByNameAsc();
}
