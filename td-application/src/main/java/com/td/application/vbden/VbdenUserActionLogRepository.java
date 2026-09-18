package com.td.application.vbden;

import com.td.domain.vbden.VbdenUserActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * 18/09/2026: truy cập bảng vật lý ĐỘC LẬP {@code vbden_user_action_logs} (xem
 * {@link VbdenUserActionLog}) — mirror {@code com.td.application.vbdi.VbdiUserActionLogRepository}.
 * KHÔNG kế thừa {@code IRepository<T>} (đúng tiền lệ — bảng con append-only không cần
 * findById/delete).
 */
public interface VbdenUserActionLogRepository {

    <S extends VbdenUserActionLog> S save(S entity);

    /**
     * Toàn bộ vết thao tác theo văn bản đến cha, MỚI NHẤT TRƯỚC ({@code created_on DESC}).
     * {@link ListVbdenUserActionLogUseCase} tự nhóm lại theo {@code (parentId, username)} khi trả
     * về (khớp shape cũ — 1 dòng/1 user, xem javadoc use case đó).
     */
    Page<VbdenUserActionLog> findByParentId(UUID parentId, Pageable pageable);
}
