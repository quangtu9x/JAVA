package com.td.infrastructure.persistence.repository;

import com.td.application.duthao.DuThaoEditHistoryDocumentRepository;
import com.td.domain.duthao.DuThaoEditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Yêu cầu người dùng 27/08/2026 (tạo bảng lưu vết hiệu chỉnh riêng): adapter cho
 * {@code duthao_edit_history_documents} — save APPEND-ONLY; {@code findByParentIdNewestFirst} sắp theo (25/09/2026: đổi sang MỚI NHẤT TRƯỚC — trước đây TĂNG DẦN + FE reverse chỉ đúng khi ≤ 1 trang, quá 20 dòng thì trang 1 là 20 vết CŨ NHẤT)
 * created_on TĂNG DẦN (insert order) khớp response Domino GetRes mà VetHieuChinh.jsx đang reverse.
 * Vì {@code parent_id} là cột typed trên entity, dùng Pageable/Sort thay vì native SQL như bản
 * draft_process (kia phải containment qua JSONB).
 */
@Repository
public class PostgresDuThaoEditHistoryDocumentRepository implements DuThaoEditHistoryDocumentRepository {

    private final DuThaoEditHistoryDocumentJpaRepository jpaRepository;

    public PostgresDuThaoEditHistoryDocumentRepository(DuThaoEditHistoryDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends DuThaoEditHistoryDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Page<DuThaoEditHistoryDocument> findByParentIdNewestFirst(UUID parentId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdOn"));
        // DeletedOnIsNull: AuditableEntity soft-delete — vết không bao giờ bị xoá mềm thực tế nhưng
        // giữ đúng convention đọc mọi bảng khác để nhất quán.
        return jpaRepository.findByParentIdAndDeletedOnIsNull(parentId, sorted);
    }
}
