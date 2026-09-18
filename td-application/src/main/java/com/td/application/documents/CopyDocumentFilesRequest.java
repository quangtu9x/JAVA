package com.td.application.documents;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

/**
 * 27/08/2026 (port "Chuyển văn thư" — {@code DraftToDocumentOut.CopyDraftToDocumentOut()} copy
 * tệp {@code duthao_file} -> {@code vbdi_file} thật): request cho {@link CopyDocumentFilesUseCase}
 * — copy tệp của {@code sourceDocumentId} sang {@code targetDocumentId} (document_id MỚI, KHÔNG
 * re-point tệp nguồn — tài liệu nguồn vẫn giữ nguyên tệp của nó).
 *
 * <p><b>Đính chính 27/08/2026 (lần 2, người dùng xác nhận lại)</b>: có 1 lần đổi SAI hướng — lý
 * luận rằng Domino {@code copyAllItems} từ {@code duthao_file} sang {@code vbdi_file} nghĩa là
 * "copy toàn bộ, không lọc theo tên" — nhưng bỏ qua 1 khác biệt gốc rễ giữa 2 mô hình dữ liệu:
 * {@code duthao_file} phía Domino là 1 document VẬT LÝ RIÊNG, khoá theo UNID của dự thảo, VÀ CHỈ
 * chứa đúng bộ tệp CHÍNH THỨC ({@code tepbanhanh}/{@code tepkemtheo}) — tệp đính kèm theo 1 vết xử
 * lý/ý kiến cụ thể (Domino: multipart cùng request {@code draft_process}) KHÔNG bao giờ nằm trong
 * {@code duthao_file}, nên "copy toàn bộ duthao_file" ở Domino ĐÃ TƯƠNG ĐƯƠNG "chỉ copy tệp chính
 * thức" một cách tự nhiên. Ở Java, {@code file_metadata.document_id} = id dự thảo CHA dùng CHUNG
 * cho CẢ 2 loại tệp (tệp chính thức VÀ tệp gắn theo từng vết xử lý qua {@code process_id}, xem DT-007
 * "quản lý tệp đính kèm theo id cha") — nên "copy toàn bộ theo document_id" ở Java KHÔNG còn tương
 * đương nữa, sẽ kéo theo cả tệp không thuộc bộ hồ sơ chính thức. Lọc theo tên trong
 * {@code tepbanhanh}/{@code tepkemtheo} mới là cách đúng để tái tạo lại đúng Ý ĐỊNH thật của Domino
 * trong mô hình dữ liệu khác biệt này — không phải sao chép máy móc cơ chế "copyAllItems".</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopyDocumentFilesRequest {

    private UUID sourceDocumentId;
    private UUID targetDocumentId;

    /** Segment "module" dùng để tách đường dẫn lưu trữ MinIO của bản sao (xem UploadFileUseCaseImpl). */
    private String module;

    /**
     * Lọc theo TÊN GỐC (originalFilename) — chỉ copy tệp có tên nằm trong tập này, bỏ qua tệp khác
     * (vd tệp đính kèm theo 1 vết xử lý/ý kiến cụ thể qua {@code process_id}, không thuộc bộ hồ sơ
     * chính thức của dự thảo — xem javadoc lớp). {@code null}/rỗng = copy TOÀN BỘ tệp của
     * {@code sourceDocumentId} (giữ cho caller khác chưa cần lọc).
     */
    private Set<String> filenames;
}
