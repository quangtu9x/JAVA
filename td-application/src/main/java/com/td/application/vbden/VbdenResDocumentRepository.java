package com.td.application.vbden;

import com.td.domain.vbden.VbdenResDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * BL-210 (VBDen Core) — DoD (4), yêu cầu người dùng 05/09/2026 ("tách vbden_res sang bảng riêng"):
 * port truy cập bảng vật lý ĐỘC LẬP {@code vbden_res_documents} (xem {@link VbdenResDocument}) —
 * tách khỏi {@code DocumentRepository} vốn giờ CHỈ phục vụ bảng {@code documents} (văn bản đến CHÍNH
 * {@code document_type='vbden'} và mọi document_type khác chưa tách riêng).
 */
public interface VbdenResDocumentRepository {

    <S extends VbdenResDocument> S save(S entity);

    /**
     * Bổ sung 15/09/2026 (chức năng "Sửa ý kiến" — {@code SuaYkien.jsx}, xem javadoc
     * {@code SuaYKienVbdenUseCase}) — tìm 1 bản ghi vbden_res THEO ID CỦA CHÍNH NÓ (khác
     * {@link #findByParentId}/{@link #findByFieldValue}, vốn khoá theo field động). Đây là ĐIỂM DUY
     * NHẤT hiện có đọc lại 1 bản ghi vbden_res để SỬA (ngoại lệ có chủ đích với thiết kế append-only
     * đã ghi ở javadoc {@link VbdenResDocument} — chỉ field {@code opinion} được phép sửa, xem use
     * case).
     */
    Optional<VbdenResDocument> findById(UUID id);

    /**
     * Tương đương view Domino {@code QTXL_by_parentid} — lịch sử xử lý theo văn bản đến cha, khoá
     * field động {@code parentid} (UUID văn bản cha, dạng chuỗi). Bảng CHỈ chứa
     * {@code document_type='vbden_res'} duy nhất (ép cứng ở {@code AddVbdenResDocumentUseCase}) nên
     * KHÔNG cần tham số documentType như {@code DocumentRepository#findByParentId}. Sắp xếp CŨ→MỚI
     * ({@code created_on ASC}) — GIỮ NGUYÊN đúng thứ tự {@code DocumentRepository#findByParentId} cũ
     * đang dùng (khác {@code DraftProcessDocumentRepository#findByParentId}, vốn DESC), tránh đổi thứ
     * tự hiển thị đã ship của {@code QuaTrinhXuLy}/{@code ThuHoiVanBanUseCase}.
     */
    Page<VbdenResDocument> findByParentId(String parentId, Pageable pageable);

    /**
     * Tương đương view Domino {@code QTXL_by_username} — toàn bộ bản ghi quá trình xử lý (xuyên suốt
     * MỌI văn bản cha) mà field động {@code fieldName} khớp {@code fieldValue} (vd.
     * {@code nguoi_thuc_hien}, xem {@code LichSuXuLyUseCase}). Tương đương
     * {@code DocumentRepository#findByFieldValue} nhưng không cần tham số documentType (lý do như
     * {@link #findByParentId}). Sắp xếp CŨ→MỚI ({@code created_on ASC}), giữ nguyên hành vi cũ.
     */
    Page<VbdenResDocument> findByFieldValue(String fieldName, String fieldValue, Pageable pageable);
}
