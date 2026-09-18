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
}
