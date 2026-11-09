package com.td.application.edithistory;

import com.td.domain.edithistory.EditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 30/10/2026: truy cập {@code edit_history_documents} (vết hiệu chỉnh dùng chung, xem
 * {@link EditHistoryDocument}).
 */
public interface EditHistoryDocumentRepository {

    <S extends EditHistoryDocument> S save(S entity);

    /** Vết hiệu chỉnh theo bản ghi cha — MỚI NHẤT TRƯỚC (created_on DESC). */
    Page<EditHistoryDocument> findByParentIdNewestFirst(String parentId, Pageable pageable);
}
