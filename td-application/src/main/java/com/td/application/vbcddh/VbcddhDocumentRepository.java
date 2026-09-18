package com.td.application.vbcddh;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.vbcddh.VbcddhDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * BL-250 (VBCDDH Core), bổ sung 08/09/2026 (yêu cầu người dùng "tạo bảng riêng, sử dụng dynamic
 * fields"): port RIÊNG cho bảng vật lý ĐỘC LẬP {@code vbcddh_documents} (xem javadoc
 * {@link VbcddhDocument}) — KHÔNG phải {@code com.td.application.documents.DocumentRepository}
 * (bảng {@code documents} dùng chung cho mọi module khác: vbden/vbdi/hosocv/...), đúng tiền lệ
 * {@code com.td.application.vbdi.VbdiDocumentRepository} (BL-220).
 *
 * <p>Chỉ khai báo ĐÚNG method thực sự được dùng bởi các use case của cụm vbcddh
 * ({@link PhanCongNhiemVuUseCase}/{@link CapNhatTienDoUseCase}/{@link VbcddhStatusRollupService}/
 * {@link ThongKeNhiemVuUseCase}/{@link NhacViecUseCase} + case {@code ViewQueryService}) — không
 * copy nguyên văn toàn bộ surface của {@code DocumentRepository}.
 *
 * <p><b>Cập nhật 08/09/2026 (2)</b> — người dùng cung cấp export DXL THẬT của {@code vbcddh.nsf}
 * (78 view, {@code docs/views_domino/view_vbcddh.dxl}), thay cho suy luận thuần từ tên view/code FE
 * trước đó. Đối chiếu DXL phát hiện method {@code findNhiemVuByDonViThucHien} (bản trước, cùng ngày)
 * SAI: công thức khoá thật của {@code vwNhiem_Vu_By_Donvithuchienhide} là {@code @Unique(@Explode(
 * @Trim(donvithuchienhide) + ";" + @Trim(chuyenvienthuchienhide); ";"))} — UNION 2 field (đơn vị THỰC
 * HIỆN VÀ cá nhân thực hiện), KHÔNG PHẢI chỉ {@code donvithuchienhide} — đã xoá method cũ, thay bằng
 * {@link #findByFormAndAnyFieldContainsMember} tổng quát hơn (đúng khuôn
 * {@code VbdiDocumentRepository#findByAnyFieldContainsMember}).
 */
public interface VbcddhDocumentRepository extends IRepository<VbcddhDocument> {

    /**
     * Cùng ngữ nghĩa {@code DocumentRepository#findByFieldValue} — so khớp field động DẠNG SCALAR
     * bằng JSONB containment ({@code @>}, case-SENSITIVE, đúng giá trị lưu) — dùng cho 2 use case sẵn
     * có ({@link VbcddhStatusRollupService} — {@code idvanban}; {@link ThongKeNhiemVuUseCase}/
     * {@link NhacViecUseCase} — {@code donvithuchienhide}/{@code chuyenvienthuchienhide}, khoá vốn là
     * UUID/identifier nội bộ do chính Java sinh ra, không phải giá trị FE tự viết hoa).
     *
     * <p><b>Sửa 16/09/2026</b> (yêu cầu người dùng port tab "Danh sách nhiệm vụ" ở chi tiết vbden):
     * view {@code vwNhiem_Vu_By_Idvanban} trước đây port QUA method này (chỉ khớp {@code idvanban})
     * — SAI vì dxl xác nhận khoá thật là {@code @Unique(idvanban : idvbdi : idvbden : idduthao)}
     * (UNION 4 field, mỗi field ứng với 1 module gốc khác nhau tạo ra nhiệm vụ). Chuyển sang
     * {@link #findByAnyFieldContainsMember} (OR-containment case-KHÔNG phân biệt hoa/thường trên cả
     * 4 field) — xem javadoc method đó.</p>
     */
    Page<VbcddhDocument> findByFieldValue(String documentType, String fieldName, String fieldValue, Pageable pageable);

    /**
     * 08/09/2026 (2) — lọc {@code form} (field động, KHÔNG phải {@code documentType} cột tĩnh) +
     * {@code extraPredicateSql} tuỳ chọn, KHÔNG có khoá field nào — dùng cho view "thùng rác"
     * {@code vwNhiem_Vu_Da_Xoa}/{@code vwVan_Ban_Da_Xoa} (dxl: {@code Form = "nhiemvu_Deleted"}/
     * {@code Form = "vanban_Deleted"}, không điều kiện nào khác).
     */
    Page<VbcddhDocument> findByForm(String documentType, String form, String extraPredicateSql, Pageable pageable);

    /**
     * 08/09/2026 (2) — lọc {@code form} + 1 field khoá SCALAR khớp CHÍNH XÁC (case-SENSITIVE, đúng
     * {@code DocumentRepository#findByFieldValue} nhưng có thêm điều kiện {@code form}) — dùng cho
     * {@code vwTien_Do_Nhiem_Vu_By_Idnhiemvu} (dxl: {@code Form = "tiendonhiemvu" & idnhiemvu != ""},
     * documentType={@code vbcddh_res}, khoá {@code idnhiemvu} = UUID nội bộ do Java sinh, không cần
     * case-insensitive).
     */
    Page<VbcddhDocument> findByFormAndFieldValue(
        String documentType, String form, String fieldName, String fieldValue, Pageable pageable);

    /**
     * 08/09/2026 (2) — lọc {@code form} + OR-containment (case-KHÔNG phân biệt hoa/thường, tương
     * đương {@code @Explode(field;";")} rồi so khớp) trên N field, + {@code extraPredicateSql} tuỳ
     * chọn (hằng số nội bộ CỐ ĐỊNH, KHÔNG BAO GIỜ ghép trực tiếp từ input client — đúng nguyên tắc
     * {@code VbdiDocumentOutFilter#extraPredicateSql}). Dùng cho:
     * <ul>
     *   <li>Nhóm {@code vwNhiem_Vu_By_Donvithuchienhide*} (24 view, {@code form=nhiemvu}, khoá UNION
     *   {@code donvithuchienhide}+{@code chuyenvienthuchienhide} — dxl công thức thật, xem javadoc lớp).</li>
     *   <li>Nhóm {@code vwVan_Ban_Ca_Nhan_Co_Quan*} (6 view, {@code form=vanban}, khoá UNION 6 field
     *   {@code user_creator}/{@code nguoitheodoihide}/{@code nguoiphutrachhide}/{@code nguoichidaohide}/
     *   {@code user_nguoichuyencddh}(exploded)/{@code coquanid}).</li>
     *   <li>{@code vwNhiem_Vu_Co_Quan} (1 view, {@code form=nhiemvu}, khoá 1 field {@code coquanid}
     *   — dùng list 1 phần tử, tương đương so khớp scalar case-insensitive).</li>
     * </ul>
     *
     * <p>Case-insensitive vì FE ({@code NhiemVuItemsList.jsx}/{@code ItemsList.jsx}) LUÔN
     * {@code .toUpperCase()} khoá trước khi gửi, trong khi field lưu trên document giữ NGUYÊN case
     * gốc — Domino view lookup mặc định vốn không phân biệt hoa/thường.
     */
    Page<VbcddhDocument> findByFormAndAnyFieldContainsMember(
        String documentType, String form, List<String> fieldNames, String member,
        String extraPredicateSql, Pageable pageable);

    /**
     * 08/09/2026 (2) — GIỐNG {@link #findByFormAndAnyFieldContainsMember} nhưng thêm điều kiện khoá
     * 2 CẤP thật của nhóm {@code vwNhiem_Vu_Ca_Nhan_Co_Quan*} (dxl cột {@code $7}):
     * {@code loaiNhiemVu} phải khớp {@code CASE WHEN idvanban != '' THEN 'nvtuqlvb' ELSE
     * 'nvtheochidao' END}, HOẶC {@code loaiNhiemVu = "all"} (dxl: {@code @Unique(key : "all")} — khoá
     * "all" khớp MỌI document bất kể phân loại nvtuqlvb/nvtheochidao). Field union danh tính (cấp 1)
     * giống hệt {@code vwVan_Ban_Ca_Nhan_Co_Quan} (6 field, xem javadoc method kia) — KHÁC
     * {@code vwNhiem_Vu_By_Donvithuchienhide} (chỉ 2 field, không có khái niệm {@code loaiNhiemVu}).
     */
    Page<VbcddhDocument> findByFormAndAnyFieldContainsMemberAndLoaiNhiemVu(
        String documentType, String form, List<String> fieldNames, String member,
        String loaiNhiemVu, String extraPredicateSql, Pageable pageable);

    /**
     * 16/09/2026 — GIỐNG {@link #findByFormAndAnyFieldContainsMember} (OR-containment case-KHÔNG
     * phân biệt hoa/thường trên N field, bọc {@code ";"} tránh khớp nhầm chuỗi con) nhưng KHÔNG lọc
     * {@code form} — đúng {@link #findByFieldValue} hiện tại (không lọc form) mà method này thay thế
     * cho view {@code vwNhiem_Vu_By_Idvanban}: dxl xác nhận {@code SELECT (Form = "nhiemvu" |
     * Form = "nhiemvu_chuaduyet") & (idvanban != "" | idvbdi != "" | idvbden != "" | idduthao != "")}
     * — 2 form khác nhau nên KHÔNG dùng {@link #findByFormAndAnyFieldContainsMember} (chỉ nhận 1
     * form); đơn giản hoá bỏ điều kiện form (đúng tiền lệ {@link #findByFieldValue} đã chấp nhận từ
     * trước, không siết chặt thêm ngoài phạm vi bugfix này). Field khoá: {@code idvanban} (văn bản
     * qlvb gốc)/{@code idvbdi} (văn bản đi)/{@code idvbden} (văn bản đến — xem
     * {@code GuiSangVBCDDH.java} dòng 336/457-485/887: {@code docnhiemvu.replaceItemValue("idvbden",
     * docDocument.getUniversalID())})/{@code idduthao} (dự thảo) — nhiệm vụ có thể phát sinh từ BẤT
     * KỲ module nào trong 4 module này.
     */
    Page<VbcddhDocument> findByAnyFieldContainsMember(
        String documentType, List<String> fieldNames, String member, Pageable pageable);
}
