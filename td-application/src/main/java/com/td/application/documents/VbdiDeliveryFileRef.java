package com.td.application.documents;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bổ sung 07/09/2026 (yêu cầu người dùng: "khi phát hành văn bản đi... không muốn nhân tệp đính
 * kèm... có thể ánh xạ để đọc trực tiếp từ văn bản đi (gốc từ đơn vị gửi) không?"): 1 tệp đã được
 * COPY SERVER-SIDE (MinIO {@code CopyObjectArgs}, không tải byte về) sang 1 vị trí lưu trữ RIÊNG,
 * DÙNG CHUNG cho MỌI nơi nhận của 1 lượt "gửi văn bản" (KHÔNG copy lại theo từng nơi nhận — tránh
 * nhân bản/tăng dung lượng đúng yêu cầu) — xem {@link PrepareVbdiDeliveryFilesUseCase}.
 *
 * <p>{@code objectKey}/{@code bucketName} trỏ tới vị trí MỚI này (KHÔNG phải vị trí gốc của văn bản
 * đi — vị trí gốc có thể bị người gửi xoá/thay thế sau này mà không ảnh hưởng bản copy MỚI này, đúng
 * quyết định đã chốt với người dùng: "chấp nhận copy thay vì tham chiếu thẳng tới bản gốc").</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VbdiDeliveryFileRef {

    private String objectKey;
    private String bucketName;
    private String originalFilename;
    private Long size;
    private String contentType;
    /** Phần mở rộng ngắn (vd "pdf") - khớp {@code FileMetadataEntity#fileExtension}, dùng cho hiển thị. */
    private String fileExtension;

    /** Nhóm hiển thị phía nơi nhận: {@value #GROUP_TEP_BAN_HANH} hoặc {@value #GROUP_TEP_KEM_THEO}. */
    public static final String GROUP_TEP_BAN_HANH = "tepbanhanh";
    public static final String GROUP_TEP_KEM_THEO = "tepkemtheo";

    /**
     * Bổ sung 30/09/2026 (yêu cầu người dùng: văn bản đến nhận được phải chia "Tệp ban hành"/"Tệp kèm
     * theo" như chi tiết văn bản đi; tệp từ "Phát hành kèm tệp văn bản đến" vào "Tệp kèm theo") — tên
     * field động phía nơi nhận mà tệp này thuộc về. Worker ghi tên tệp vào field cùng tên trên văn bản
     * đến vừa tạo. {@code null} (message cũ trước bản vá) = không phân nhóm.
     */
    private String attachmentGroup;
}
