package com.td.application.documents;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Tham số cho {@link PrepareVbdiDeliveryFilesUseCase} — xem javadoc interface đó.
 */
@Data
public class PrepareVbdiDeliveryFilesRequest {

    /** Id văn bản đi (nguồn) cần copy tệp đính kèm sang vị trí lưu trữ dùng chung cho lượt gửi. */
    private UUID documentId;

    /** Mã định danh đơn vị gửi ({@code unit_publish_identifier}) - dùng làm segment đường dẫn. */
    private String senderIdentifier;

    /**
     * Bổ sung 21/09/2026 (yêu cầu người dùng "nếu check vào phát hành kèm văn bản đến thì khi gửi
     * phải gửi tất cả những file đính kèm của văn bản đến theo"): id CÁC văn bản đến (module
     * {@code vbden}) mà tệp đính kèm của chúng CŨNG phải copy vào CÙNG lượt gửi — port
     * {@code TDUlties.phat_hanh_kem_van_ban_den()} (Domino, gọi khi field {@code phathanhkemvbden}
     * trên văn bản đi = "1"). Nguồn: field {@code documentin_obj} (JSON array {@code {id,...}}) trên
     * chính văn bản đi — xem {@code SendVbdiDocumentUseCase#resolvePhatHanhKemVbdenIds}. Rỗng/{@code
     * null} nếu không bật "Phát hành kèm văn bản đến" — hành vi giữ nguyên như trước (chỉ copy tệp
     * của chính văn bản đi).
     */
    private List<UUID> additionalSourceDocumentIds;

    /**
     * Bổ sung 30/09/2026 (xem {@link VbdiDeliveryFileRef#getAttachmentGroup()}): tên tệp trong field
     * {@code tepkemtheo} của CHÍNH văn bản đi. Tệp của văn bản đi có tên nằm trong danh sách này →
     * "Tệp kèm theo", còn lại → "Tệp ban hành" (khớp cách {@code filesVBdi.jsx} chia nhóm). Tệp từ
     * {@link #additionalSourceDocumentIds} LUÔN là "Tệp kèm theo".
     */
    private List<String> attachedFilenames;
}
