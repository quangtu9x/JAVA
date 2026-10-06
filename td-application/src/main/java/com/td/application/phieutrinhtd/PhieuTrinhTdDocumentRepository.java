package com.td.application.phieutrinhtd;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Port module "Phiếu trình thẩm định" (phieutrinhtd) —
 * {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md} mục 6. Bảng vật lý ĐỘC LẬP
 * {@code phieutrinhtd_documents}, đúng tiền lệ {@code VbcddhDocumentRepository} (BL-250) — KHÔNG dùng
 * {@code com.td.application.documents.DocumentRepository}.
 *
 * <p>Mọi method dùng {@code contains-member} (OR-explode {@code ";"}-joined, case-KHÔNG phân biệt hoa
 * thường) thay vì so khớp scalar chính xác — hoạt động đúng cho CẢ field scalar 1 username (vd
 * {@code user_creator}) LẪN field danh sách nhiều username, đúng tiền lệ
 * {@code VbcddhDocumentRepository#findByFormAndAnyFieldContainsMember}.
 *
 * <p><b>Cập nhật 09/09/2026 (đối chiếu DXL thật `docs/views_domino/view_phieutrinh.dxl`, 26 view,
 * xác nhận đây chính là database `phieutrinhtd.nsf`)</b>: tên field khoá thật có tiền tố
 * {@code "user_"} ({@code user_creator}/{@code user_lanhdao1}/{@code user_lanhdao2}/
 * {@code user_lanhdaovanphong}) — bản trước (suy từ FE) thiếu tiền tố này; MỌI view "cá nhân" thật
 * còn có thêm điều kiện {@code storaged != "1"}. Xem javadoc chi tiết từng method + adapter
 * {@code PostgresPhieuTrinhTdDocumentRepository}.
 */
public interface PhieuTrinhTdDocumentRepository extends IRepository<PhieuTrinhTdDocument> {

    /**
     * "Toàn bộ" — OR trên nhiều field quan hệ ({@code user_creator}/{@code user_lanhdao1}/
     * {@code user_lanhdao2}/{@code user_lanhdaovanphong}), KHÔNG lọc theo {@code status} nhưng CÓ lọc
     * {@code storaged != "1"} (đúng {@code vwUser_All}) — tài liệu 20 mục 6.3. Đơn giản hoá CÓ CHỦ Ý so
     * với DXL gốc: KHÔNG gồm {@code user_coordinator}/{@code user_receivers_agree}/
     * {@code user_receivers_refuse} (logic điều kiện phức tạp hơn theo từng {@code trinh<N>_date} —
     * xem tài liệu 20 mục "Cập nhật ... — đối chiếu DXL thật").
     */
    Page<PhieuTrinhTdDocument> findByAnyFieldContainsMember(
        List<String> fieldNames, String member, Pageable pageable);

    /**
     * OR trên nhiều field quan hệ + lọc thêm {@code status IN (...)} + {@code storaged != "1"} — dùng
     * cho các tab 1-field/N-status (dangsoan, trinhduyet, daduyet, khongduyet, trinhthongqua,
     * chothongqua, dathongqua, khongthongqua — tài liệu 20 mục 6.3), khớp đúng
     * {@code vwUser_Creator_0/2/3/4/6_26/7_27}/{@code vwUser_NguoiThongQua_2}.
     */
    Page<PhieuTrinhTdDocument> findByAnyFieldContainsMemberAndStatusIn(
        List<String> fieldNames, String member, List<String> statuses, Pageable pageable);

    /**
     * "Chờ duyệt" — {@code (user_lanhdao1 chứa key AND status='5') OR (user_lanhdao2 chứa key AND
     * status='25')} + {@code storaged != "1"} — khớp đúng {@code vwUser_NguoiDuyet_5_25} (DXL dòng
     * 540-575) — không thể diễn đạt bằng 2 method trên (mỗi field cần khớp 1 status RIÊNG) — tài liệu
     * 20 mục 6.3 dòng "choduyet".
     */
    Page<PhieuTrinhTdDocument> findApproverWaiting(String member, Pageable pageable);

    /**
     * "Chờ cho ý kiến" (tab "thamkhao") — {@code user_coordinator} (danh sách phân tách bởi dấu phẩy
     * ",", chuẩn hoá sang ";" trước khi so khớp) chứa key VÀ {@code user_coordinated} CHƯA chứa key +
     * {@code storaged != "1"} — khớp đúng {@code vwUser_Coordinator_chua} (DXL dòng 893-929).
     *
     * <p><b>Cập nhật 09/09/2026</b>: ĐỔI HẲN cơ chế nền so với bản port đầu (dùng
     * {@code nguoithamkhaohide}/{@code nguoidathamkhaohide}) — 2 field đó KHÔNG có view Domino nào hậu
     * thuẫn trong 26 view thật của {@code phieutrinhtd.nsf} (chỉ có bằng chứng từ 1 modal FE phụ, ít
     * dùng — xem tài liệu 20 mục 2.4). Tên method giữ nguyên (đổi tên sẽ phá vỡ tương thích ngược
     * không cần thiết trong phạm vi sửa lỗi này).
     */
    Page<PhieuTrinhTdDocument> findThamKhaoChoYKien(String member, Pageable pageable);

    /** Thùng rác — {@code deleted_on IS NOT NULL} (soft-delete Java-side, KHÔNG lọc storaged). */
    Page<PhieuTrinhTdDocument> findTrash(Pageable pageable);

    /**
     * 06/10/2026 (cutover FE phiếu trình): truy vấn danh sách theo tiêu chí tổng quát - dùng cho mọi view
     * (xem {@link PhieuTrinhTdViewQuery}); mới tạo nhất trước.
     */
    Page<PhieuTrinhTdDocument> findByCriteria(PhieuTrinhTdListCriteria criteria, Pageable pageable);
}
