package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 — request cho {@code POST /api/v1/vbdi/SendDocument}: "gửi văn bản
 * đi" tới nơi nhận, port DoD lõi (ghi Nhật ký gửi nhận) của
 * {@code docs/analysis/07-phan-tich-port-senddocument-vbdi-qlvb.md} — tương thích Ý NGHĨA
 * (KHÔNG tương thích byte-for-byte payload) với {@code POST qlvb/vbdi?databasetype=vbdi&action=
 * senddocument|resenddocument&id=<UNID>}.
 *
 * <p>4 nhóm nơi nhận CỨNG đúng Domino thật (xem {@link VbdiSendRecipientGroup}) — field rỗng
 * (danh sách rỗng) nghĩa là KHÔNG gửi tới nhóm đó, không phải lỗi.</p>
 */
@Data
public class SendVbdiDocumentRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;

    /** Mặc định {@link VbdiSendType#SEND_DOCUMENT} nếu bỏ trống. */
    private String sendType;

    /** Ý kiến/nội dung kèm khi gửi — ghi vào {@code content} của mỗi vết. */
    private String opinion;

    /** Cá nhân/phê duyệt. */
    private List<VbdiSendRecipientRequest> coordinator = new ArrayList<>();

    /** Đơn vị nội bộ. */
    private List<VbdiSendRecipientRequest> internalUnit = new ArrayList<>();

    /** Cơ quan liên hệ. */
    private List<VbdiSendRecipientRequest> contactUnit = new ArrayList<>();

    /** Cơ quan ngoài/liên thông quốc gia. */
    private List<VbdiSendRecipientRequest> externalUnit = new ArrayList<>();
}
