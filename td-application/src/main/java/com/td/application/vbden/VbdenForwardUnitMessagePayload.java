package com.td.application.vbden;

import com.td.application.documents.VbdiDeliveryFileRef;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Payload envelope {@code vbden.forward_unit} — publish từ {@link ChuyenXuLyVbdenUseCase} khi 1 đơn
 * vị được giao chủ trì ({@code unit_process}) thuộc TENANT KHÁC (đơn vị con có văn thư/CSDL riêng —
 * đúng {@code Organization.linkedTenantId} khác tenant hiện tại). Port {@code DocumentInProcess.java
 * #copyDocumentProcessToDocumentIn()} (Domino, form mail-in {@code document_process_unit}, xem
 * {@code docs/agent_domino/io/BeforeMailin.ja}) — CHƯA từng có tương đương Java trước bản này (đã rà
 * soát toàn bộ {@code JAVA/}, xác nhận với người dùng 08/09/2026).
 *
 * <p><b>Đơn giản hoá CÓ CHỦ ĐÍCH so với Domino gốc</b> (ghi rõ, không suy đoán thêm phạm vi): Domino
 * copy TOÀN BỘ field của văn bản gốc (kể cả tệp đính kèm, {@code fieldsToUpdate} 10 field dạng
 * "user_" / "unit_") sang bản sao. Bản Java lần port đầu này KHÔNG copy tệp đính kèm, KHÔNG đồng bộ
 * user_process/user_coordinate/user_to_read/user_track sang bản sao (đơn vị nhận tự phân công lại từ
 * đầu trên bản sao của họ, không kế thừa phân công của đơn vị gốc). Ghi nhận đây là gap CÓ CHỦ ĐÍCH,
 * mở rộng được sau nếu người dùng xác nhận cần.
 *
 * <p><b>Bugfix 08/09/2026 (2) — người dùng test thật: "văn thư/người đại diện nhận đang không thấy
 * văn bản ở bất kỳ đâu"</b>: bản đầu KHÔNG set {@code identifier_creator} trên bản sao — field DUY
 * NHẤT mà MỌI view org-scoped của vbden ({@code chophanxuly}/{@code cuatochuc}/
 * {@code phaixulyCuatochuc}... xem {@code ViewQueryService}) dùng để lọc "văn bản CỦA tổ chức tôi"
 * (qua {@code resolveCurrentIdentifier()}) — thiếu field này, bản sao tồn tại trong DB nhưng KHÔNG
 * view nào khớp được, "vô hình" với mọi người ở tenant đích. {@code VbdenForwardUnitTaskHandler}
 * (td-woker) giờ LUÔN set {@code identifier_creator = targetIdentifier} khi INSERT (giá trị MỚI,
 * không forward từ payload — đại diện "bản sao này giờ thuộc tổ chức đích", không phải origin).
 * Bổ sung thêm 3 field hiển thị/lọc còn thiếu (đọc LIVE từ {@code dynamic_fields} văn bản gốc, cùng
 * cách {@code AddVbdiSendLogUseCase} đọc snapshot cho vbdi): {@code unitInCharge}/
 * {@code unitInChargeIdentifier} (cơ quan BAN HÀNH gốc bên ngoài — vd "Bộ Tài nguyên và Môi trường",
 * KHÔNG phải đơn vị đang chuyển tiếp — forward NGUYÊN VẸN để văn thư đích biết văn bản từ đâu tới) và
 * {@code deadlineDate} (hạn xử lý — cần để đúng vào {@code phaixulyCuatochucFilter}/
 * {@code quahanCuatochucFilter} sau này).</p>
 *
 * <p>KHÔNG share class/jar với {@code td-woker} — chỉ khớp NHAU về hình dạng JSON, xem
 * {@code VbdenForwardUnitPayload} phía {@code td-woker}.
 */
@Data
@Builder
public class VbdenForwardUnitMessagePayload {

    /** id văn bản đến GỐC (tenant hiện tại) đang bị "phân xử lý" cho đơn vị khác tenant. */
    private String originDocumentId;
    private String originTenantId;

    /** Định danh + tên đơn vị NHẬN xử lý (Organization.identifier/name, tenant khác). */
    private String targetIdentifier;
    private String targetName;

    /** Người thực hiện "chuyển xử lý" (hiển thị "Người gửi" trên bản sao). */
    private String actingUsername;
    private String opinion;

    private String subject;
    private String code;
    private String promulgationDate;

    /** Bổ sung 08/09/2026 (2, xem javadoc lớp): forward NGUYÊN VẸN từ văn bản gốc. */
    private String deadlineDate;
    private String unitInCharge;
    private String unitInChargeIdentifier;

    /**
     * Bổ sung 08/09/2026 (3, người dùng: "thiếu files nữa, nhưng không phải nhân ra mà đọc chung
     * với đơn vị chuyển xử lý luôn"): tham chiếu THẲNG {@code objectKey}/{@code bucketName} HIỆN CÓ
     * của văn bản gốc — KHÔNG copy MinIO (khác hẳn quyết định của vbdi/{@code
     * PrepareVbdiDeliveryFilesUseCase}, xem javadoc {@link com.td.application.documents
     * .GetDocumentFileRefsUseCase}) — tenant đích tạo 1 dòng {@code file_metadata} RIÊNG trỏ CÙNG
     * object vật lý (không nhân bản dung lượng, đúng yêu cầu người dùng).
     */
    private List<VbdiDeliveryFileRef> files;
}
