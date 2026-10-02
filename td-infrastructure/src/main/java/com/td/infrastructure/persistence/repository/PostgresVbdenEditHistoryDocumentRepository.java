package com.td.infrastructure.persistence.repository;

import com.td.application.vbden.VbdenEditHistoryDocumentRepository;
import com.td.domain.vbden.VbdenEditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Yêu cầu người dùng 12/09/2026 (tạo bảng lưu vết hiệu chỉnh riêng cho vbden): adapter cho
 * {@code vbden_edit_history_documents} — save APPEND-ONLY; {@code findByParentIdNewestFirst} sắp theo (25/09/2026: đổi sang MỚI NHẤT TRƯỚC — trước đây TĂNG DẦN + FE reverse chỉ đúng khi ≤ 1 trang, quá 20 dòng thì trang 1 là 20 vết CŨ NHẤT)
 * created_on TĂNG DẦN (insert order) khớp response Domino GetRes mà VetHieuChinh.jsx đang reverse —
 * mirror {@code PostgresVbdiEditHistoryDocumentRepository}.
 */
@Repository
public class PostgresVbdenEditHistoryDocumentRepository implements VbdenEditHistoryDocumentRepository {

    private final VbdenEditHistoryDocumentJpaRepository jpaRepository;

    public PostgresVbdenEditHistoryDocumentRepository(VbdenEditHistoryDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends VbdenEditHistoryDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Page<VbdenEditHistoryDocument> findByParentIdNewestFirst(java.util.UUID parentId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdOn"));
        return jpaRepository.findByParentIdAndDeletedOnIsNull(parentId, sorted);
    }
}
