package com.td.application.dbserver;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-117 (bổ sung 19/08/2026): xoá mềm (markAsDeleted — AuditableEntity/ISoftDelete), KHÔNG dùng
// IRepository.delete(...) (hard-delete vật lý, chỉ tồn tại để thoả interface, xem PostgresTenantRepository
// tương tự chưa từng gọi tới). KHÔNG gán deletedBy (currentUserId) — control-plane entity (Category/
// Tenant) đứng NGOÀI mọi tenant, không có TenantContext/AppUserRepository (tenant-scoped) đáng tin cậy
// để tra cứu tại đây, cùng lý do CreateTenantUseCase/... chưa bao giờ gán createdBy.
//
// KHÔNG như Tenant (không hard/soft-delete gì cả, chỉ activate/deactivate — vì Tenant gắn với 1
// database Postgres THẬT đã tạo), DbServer chỉ là gợi ý host/port, không có FK/quan hệ tới Tenant đã
// tạo trước đó (xem javadoc DbServer.java) — xoá mềm ở đây an toàn, không ảnh hưởng dữ liệu khác.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("controlPlaneTransactionManager")
public class DeleteDbServerUseCase {

    private final DbServerRepository dbServerRepository;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = dbServerRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy máy chủ CSDL với ID: " + id);
            }
            var entity = opt.get();
            entity.markAsDeleted(null);
            dbServerRepository.save(entity);
            return Result.success(id);
        } catch (Exception ex) {
            log.error("Xoá máy chủ CSDL thất bại", ex);
            return Result.failure("Xoá máy chủ CSDL thất bại: " + ex.getMessage());
        }
    }
}
