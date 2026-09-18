package com.td.application.vbdi;

import com.td.domain.vbdi.VbdiUserActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * 30/08/2026 (port {@code qlvb/vbdi?...&action=loguseraction|getloguseraction} sang Java): truy cập
 * bảng vật lý ĐỘC LẬP {@code vbdi_user_action_logs} (xem {@link VbdiUserActionLog}) — KHÔNG kế thừa
 * {@code IRepository<T>} (đúng tiền lệ {@link VbdiSendLogDocumentRepository}: bảng con append-only
 * không cần findById/delete).
 */
public interface VbdiUserActionLogRepository {

    <S extends VbdiUserActionLog> S save(S entity);

    /**
     * Toàn bộ vết thao tác theo văn bản đi cha, MỚI NHẤT TRƯỚC ({@code created_on DESC}) — đúng
     * chiều {@link VbdiSendLogDocumentRepository#findByParentId}.
     * {@link ListVbdiUserActionLogUseCase} tự nhóm lại theo {@code (parentId, username)} khi trả
     * về (khớp shape Domino cũ — 1 dòng/1 user, xem javadoc use case đó).
     */
    Page<VbdiUserActionLog> findByParentId(UUID parentId, Pageable pageable);
}
