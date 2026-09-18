package com.td.application.hosocv;

import com.td.application.common.interfaces.IRepository;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.domain.hosocv.HoSoCVDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Mở rộng BL-240 (HoSoCV Core), yêu cầu người dùng ("tạo bảng mới, sử dụng jsonb (dynamic field)
 * như các module đã thực hiện"): port RIÊNG cho bảng vật lý ĐỘC LẬP {@code hosocv_documents} (xem
 * javadoc {@link HoSoCVDocument}) — KHÔNG phải {@code com.td.application.documents.DocumentRepository}
 * (bảng {@code documents} dùng chung cho các module chưa tách bảng riêng: vbden/phieutrinh/...).
 *
 * <p>Chỉ khai báo ĐÚNG method thực sự dùng bởi use case + {@code ViewQueryService} của hosocv,
 * đúng nguyên tắc đã áp dụng cho {@code DuThaoDocumentRepository}.
 */
public interface HoSoCVDocumentRepository extends IRepository<HoSoCVDocument> {

    /**
     * Field đơn (scalar, vd. {@code user_creator}/{@code user_process_handler}/
     * {@code user_approved_by}/{@code user_leader_in_charge}) — OR containment (JSON-array hoặc
     * scalar-equality, cùng ngữ nghĩa {@code DocumentRepository#findByAnyFieldContains}).
     */
    Page<HoSoCVDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value, Pageable pageable);

    /** Cùng ngữ nghĩa {@code DuThaoDocumentRepository#findByVbdenOrgView} — tổ hợp khoá + status/field IN-NOT_IN. */
    Page<HoSoCVDocument> findByVbdenOrgView(String documentType, VbdenOrgViewFilter filter, Pageable pageable);

    /**
     * {@code vwUser_Coordinated_Status} (khoá FE {@code <username>_chua}/{@code <username>_da}):
     * field {@code user_coordinator}/{@code user_coordinated} lưu dạng chuỗi {@code ";"}-joined
     * (không phải JSON array) — so khớp THEO THÀNH VIÊN (LIKE neo {@code ";"}), khác containment/
     * scalar-equality của {@link #findByAnyFieldContains}. {@code excludeListField} truyền
     * {@code null} khi không cần loại trừ (nhánh {@code _da}: chỉ cần thành viên của
     * {@code user_coordinated}, không cần loại trừ field nào khác).
     */
    Page<HoSoCVDocument> findByListFieldMemberExcludingOther(
            String documentType, String includeListField, String excludeListField, String value,
            Pageable pageable);

    /** {@code vwTrash} — hồ sơ đã xoá mềm ({@code deleted_on IS NOT NULL}). */
    Page<HoSoCVDocument> findDeleted(String documentType, Pageable pageable);

    /**
     * {@code vwUser_Process_Handler_Den_Han_Nop_Luu_Tru}/{@code vwOverdue_Processing}/
     * {@code vwUser_Overdue_Processing} — đơn giản hoá CÓ CHỦ Ý (đúng tinh thần BL-240 gốc): field
     * ngày ({@code dateFieldName}, lưu {@code dd/MM/yyyy} — cùng quy ước {@code deadline_date} của
     * vbden) so với hôm nay ({@code strictlyBefore=true}: quá hạn — {@code <} hôm nay;
     * {@code strictlyBefore=false}: đến hạn — {@code <=} hôm nay), kết hợp khoá cá nhân
     * ({@code keyFieldName}/{@code keyValue}, {@code null}/rỗng = bỏ qua — dùng cho biến thể thống
     * kê "toàn cơ quan") + điều kiện field động bằng-chính-xác tuỳ chọn (vd. {@code storaged="0"}).
     */
    Page<HoSoCVDocument> findByFieldValueDueDate(
            String documentType, String keyFieldName, String keyValue,
            String dateFieldName, boolean strictlyBefore,
            Map<String, String> extraDynamicFieldEquals, Pageable pageable);
}
