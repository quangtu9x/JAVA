package com.td.application.duthao;

import com.td.domain.duthao.DraftProcessDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * DT-007, yêu cầu người dùng 25/08/2026 ("tách draft_process sang bảng riêng"): port truy cập
 * bảng vật lý ĐỘC LẬP {@code draft_process_documents} (xem {@link DraftProcessDocument}) — tách
 * khỏi {@link DuThaoDocumentRepository} vốn giờ CHỈ phục vụ bảng {@code duthao_documents}
 * (document chính {@code document_type='duthao'}).
 */
public interface DraftProcessDocumentRepository {

    <S extends DraftProcessDocument> S save(S entity);

    /** 03/10/2026: xoá vết / xoá tệp của vết quá trình dự thảo (XoaVetDuThaoUseCase, XoaTepVetDuThaoUseCase). */
    java.util.Optional<DraftProcessDocument> findById(java.util.UUID id);

    /**
     * DT-007 (26/08/2026, port {@code vwGroup_process_by_draft_id} — tab "Quá trình xử lý",
     * {@code duthaofile.jsx}): lịch sử xử lý theo dự thảo cha, khoá field động {@code parentid}
     * (UUID dự thảo, dạng chuỗi — {@code XuLyDuThaoUseCase#createDraftProcessRecord}/
     * {@code XinYKienPhoiHopUseCase#createOpinionRecord}). Bảng CHỈ chứa
     * {@code document_type='draft_process'} duy nhất (ép cứng ở {@code AddDraftProcessDocumentUseCase})
     * nên KHÔNG cần tham số documentType như {@code DocumentRepository#findByParentId}. Sắp xếp MỚI
     * NHẤT TRƯỚC ({@code created_on DESC}) — đúng {@code orderBy: ["createdOn DESC"]} Domino gốc
     * FE đang gửi, khác chiều ASC mặc định của {@code DocumentRepository#findByParentId}.
     */
    Page<DraftProcessDocument> findByParentId(String parentId, Pageable pageable);
}