package com.td.worker.tasks.vbdi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Payload của envelope {@code vbdi.delivery} — đơn vị GỬI → đơn vị NHẬN. Khớp bảng field
 * envelope docs/architecture/11 §4.1 (project tham khảo): message nhẹ, KHÔNG nhúng tệp —
 * chỉ tham chiếu ({@code files[].objectKey}) tới tệp ĐÃ ĐƯỢC COPY SẴN trên MinIO (xem
 * {@code VbdiDocumentSnapshot}).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VbdiDeliveryPayload {

    /** Idempotency key 1 lượt gửi (nhóm nhiều nơi nhận). */
    private String sendId;

    /** id dòng vbdi_send_log_documents phía đơn vị gửi — khoá đối chiếu phản hồi. */
    private String sendLogId;

    private String documentId;

    /** Tenant đơn vị gửi + định danh đối tác (Organization.identifier). */
    private String senderTenantId;
    private String senderIdentifier;
    private String senderOrganName;

    /** Routing key để đơn vị nhận gửi PHẢN HỒI ngược (thay "addresend" của Domino). */
    private String senderReplyRoutingKey;

    /** senddocument/resenddocument/guibosung/... (đúng VbdiSendType của td-web). */
    private String sendType;

    /** coordinator/internal_unit/contact_unit/external_unit (đúng 4 nhóm). */
    private String recipientGroup;

    /** Mã định danh đơn vị nhận (hoặc username nếu coordinator). */
    private String recipientIdentifier;

    private String recipientName;
    private String recipientMailin;

    /**
     * Bổ sung 18/09/2026 (hoàn thiện "phát hành vbdi → đến nội bộ") — username "người đại diện
     * nhận" của tổ chức đích, CHỈ có giá trị khi {@code recipientGroup == internal_unit}. Khớp
     * {@code VbdiDeliveryMessagePayload.recipientReceiverUsername} (td-application). {@code null}
     * nếu không áp dụng/không cấu hình — xem {@link VbdenIncomingDocumentWriter#buildDynamicFieldsJson}.
     */
    private String recipientReceiverUsername;

    /** "Gửi văn bản"/"Gửi lại"/... */
    private String statusLabel;

    private String opinion;

    /** Ảnh tệp (message nhẹ — chỉ tham chiếu, không nhúng nhị phân). */
    private VbdiDocumentSnapshot documentSnapshot = new VbdiDocumentSnapshot();

    /**
     * Bugfix 06/09/2026 (sửa nhầm nhóm — xác nhận lại với người dùng + dữ liệu thật
     * {@code ThanhHoaDEV/src/utils/global.jsx}: {@code contact_unit_rtext = "Danh sách cơ quan,
     * đơn vị thuộc " + CAP} (CAP="Tỉnh") — {@code contact_unit} = CƠ QUAN/ĐƠN VỊ KHÁC THUỘC TỈNH
     * (đúng phạm vi "tạo văn bản đến thật" người dùng yêu cầu), CÒN {@code internal_unit} = đơn vị
     * NỘI BỘ CỦA CHÍNH cơ quan gửi (không có tỉnh nào cả — khác hẳn phạm vi này, giữ nguyên
     * chỉ dedup). Trước đây nhầm coi {@code contact_unit} là "ngoài hệ thống" giống
     * {@code external_unit} — SAI, chỉ {@code external_unit} (cơ quan NGOÀI tỉnh) mới thật sự
     * chưa có gateway. Bỏ điều kiện theo {@code recipientMailin} — quyết định theo NHÓM đơn giản
     * (người dùng đã chốt), một {@code contact_unit} có kèm mailin (kênh liên hệ phụ) vẫn LUÔN thử
     * tạo văn bản thật, không còn rơi vào nhánh chờ.
     */
    public boolean isExternal() {
        return VbdiTaskConstants.GROUP_EXTERNAL_UNIT.equals(recipientGroup);
    }

    /**
     * Field mô tả văn bản đi (bổ sung 07/09/2026, đối chiếu mẫu Domino thật
     * {@code docs/views_domino/data_vbdi_SendDocument_output.json} — xem javadoc
     * {@code AddVbdiSendLogUseCase.publishDeliveryMessage} phía td-application, PHẢI khớp
     * field-by-field với {@code VbdiDeliveryMessagePayload.DocumentSnapshot} bên đó) + danh sách
     * tệp đính kèm (bổ sung tiếp, cùng ngày — xem javadoc {@code files}).
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VbdiDocumentSnapshot {
        private String code;
        private String subject;
        private String promulgationDate;

        private String draftor;
        private String signer;
        private String signerPosition;
        private String creator;
        private String deliveryMethod;
        private String documentType;
        private String sector;
        private String confidentiality;
        private String pageAmount;
        private String priority;
        /** = {@code document_replace_retrieve} bên vbdi — ghi verbatim, KHÔNG dịch (đã chốt với người dùng). */
        private String documentReplaceRetrieve;

        /**
         * Bổ sung 13/09/2026 (port full end-to-end "thay thế/thu hồi") — = field động
         * {@code replaces_document_id} bên vbdi (UUID chuỗi của văn bản đi A bị thay thế/thu hồi) —
         * xem javadoc {@code VbdenIncomingDocumentWriter#resolveRevokeFields}. PHẢI khớp field-by-
         * field {@code VbdiDeliveryMessagePayload.DocumentSnapshot} (td-application).
         */
        private String replacesDocumentId;

        /**
         * Tệp đính kèm ĐÃ ĐƯỢC COPY SẴN (1 lần cho cả lượt gửi, phía td-application —
         * {@code PrepareVbdiDeliveryFilesUseCase}) sang vị trí lưu trữ riêng, KHÔNG phải đường dẫn
         * gốc của văn bản đi (tránh phụ thuộc vòng đời tệp gốc — người gửi xoá/thay thế sau này
         * không ảnh hưởng bản copy này, đã chốt với người dùng). Rỗng/null nếu văn bản đi không có
         * tệp nào hoặc bước copy lỗi toàn bộ (best-effort — xem {@code VbdenIncomingDocumentWriter}).
         */
        private List<VbdiFileRef> files;
    }

    /** = {@code com.td.application.documents.VbdiDeliveryFileRef} - lặp lại field, KHÔNG import (2 module độc lập). */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VbdiFileRef {
        private String objectKey;
        private String bucketName;
        private String originalFilename;
        private Long size;
        private String contentType;
        private String fileExtension;
        /** "tepbanhanh"/"tepkemtheo" (bổ sung 30/09/2026); {@code null} ở message cũ = không phân nhóm. */
        private String attachmentGroup;
    }
}