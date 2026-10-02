package com.td.infrastructure.persistence.repository;

import com.td.application.vbdi.VbdiEditHistoryDocumentRepository;
import com.td.domain.vbdi.VbdiEditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Yêu cầu người dùng 28/08/2026 (tạo bảng lưu vết hiệu chỉnh riêng cho vbdi): adapter cho
 * {@code vbdi_edit_history_documents} — save APPEND-ONLY; {@code findByParentIdNewestFirst} sắp theo (25/09/2026: đổi sang MỚI NHẤT TRƯỚC — trước đây TĂNG DẦN + FE reverse chỉ đúng khi ≤ 1 trang, quá 20 dòng thì trang 1 là 20 vết CŨ NHẤT)
 * created_on TĂNG DẦN (insert order) khớp response Domino GetRes mà VetHieuChinh.jsx đang reverse —
 * mirror {@code PostgresDuThaoEditHistoryDocumentRepository}.
 */
@Repository
public class PostgresVbdiEditHistoryDocumentRepository implements VbdiEditHistoryDocumentRepository {

    private final VbdiEditHistoryDocumentJpaRepository jpaRepository;

    public PostgresVbdiEditHistoryDocumentRepository(VbdiEditHistoryDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends VbdiEditHistoryDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Page<VbdiEditHistoryDocument> findByParentIdNewestFirst(java.util.UUID parentId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdOn"));
        return jpaRepository.findByParentIdAndDeletedOnIsNull(parentId, sorted);
    }
}
