package com.td.application.vbdi;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Payload envelope {@code vbdi.delivery} publish từ {@code AddVbdiSendLogUseCase} — PHẢI khớp
 * field-by-field {@code com.td.worker.tasks.vbdi.VbdiDeliveryPayload} (module {@code td-woker},
 * đọc trực tiếp mã nguồn để lấy đúng tên field, KHÔNG suy đoán từ tài liệu thiết kế cũ). Field bắt
 * buộc phía consumer: {@code sendLogId, documentId, recipientGroup, recipientIdentifier} — còn lại
 * best-effort, thiếu không lỗi (xem {@code VbdiDeliveryTaskHandler.execute}).
 *
 * <p>KHÔNG share class/jar với {@code td-woker} — đây là 1 class RIÊNG ở {@code td-application},
 * chỉ khớp NHAU về hình dạng JSON (giữ đúng "không ràng buộc code" giữa 2 app độc lập, xem
 * {@code td-woker/docs/01-architecture.md}).</p>
 */
@Data
@Builder
public class VbdiDeliveryMessagePayload {

    private String sendId;
    private String sendLogId;
    private String documentId;

    private String senderTenantId;
    private String senderIdentifier;
    private String senderOrganName;
    private String senderReplyRoutingKey;

    private String sendType;
    private String recipientGroup;
    private String recipientIdentifier;
    private String recipientName;
    private String recipientMailin;

    /**
     * Bổ sung 18/09/2026 (hoàn thiện "phát hành vbdi → đến nội bộ"): username "người đại diện nhận"
     * của tổ chức đích ({@code Organization.receiverId} → {@code AppUser.username}), CHỈ resolve khi
     * {@code recipientGroup == internal_unit} — xem {@code AddVbdiSendLogUseCase#resolveReceiverUsername}.
     * {@code null} nếu không áp dụng/không cấu hình — worker ghi field {@code user_receiver} khi có
     * giá trị, bỏ qua khi rỗng (khớp Domino {@code if(!user_receiver.equals(""))}).
     */
    private String recipientReceiverUsername;

    private String statusLabel;
    private String opinion;

    private DocumentSnapshot documentSnapshot;

    /**
     * Bổ sung 07/09/2026 (đối chiếu mẫu Domino thật {@code docs/views_domino/
     * data_vbdi_SendDocument_output.json}: khi SendDocument tạo bản ghi "văn bản đến" bên nhận,
     * Domino COPY NGUYÊN các field mô tả này từ văn bản đi gốc — bản Java trước đó chỉ mang 3 field
     * {@code code}/{@code subject}/{@code promulgationDate}, thiếu hẳn phần còn lại khiến văn bản
     * đến tự tạo trống các thông tin cơ bản khi mở "Chi tiết"). Tên field GIỮ NGUYÊN theo
     * {@code VBDI_FORM_FIELDS} ({@code ThanhHoaDEV/src/utils/global.jsx}, nguồn xác thực nhất —
     * đây CHÍNH LÀ field văn bản đi, không dịch sang tên vbden) — người dùng đã xác nhận trực tiếp
     * chọn phương án "ghi verbatim, không dịch" cho {@code documentReplaceRetrieve} (field duy nhất
     * có nghi vấn ánh xạ tên với vbden, xem docs/architecture/11 addendum 07/09/2026). Field tên file
     * đính kèm gốc ({@code tepbanhanh}/{@code tepkemtheo}) CHƯA đưa vào đây ở lần bổ sung đó — người
     * dùng đã xác nhận để việc riêng sau. <b>Đã làm tiếp ngay sau đó, cùng ngày 07/09/2026</b> — xem
     * javadoc {@link com.td.application.documents.PrepareVbdiDeliveryFilesUseCase}: field
     * {@code files} thay THẲNG 2 field {@code objectKey}/{@code sha256} cũ (2 field đó ĐÃ XÁC NHẬN
     * chưa từng được gán giá trị ở bất kỳ đâu — xem lịch sử git — nên loại bỏ an toàn, không mất dữ
     * liệu thật nào).
     */
    @Data
    @Builder
    public static class DocumentSnapshot {
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
         * Bổ sung 13/09/2026 (port full end-to-end "thay thế/thu hồi", xem javadoc
         * {@code VbdenIncomingDocumentWriter#resolveRevokeFields} td-woker) — = field động
         * {@code replaces_document_id} ghi bởi {@link com.td.application.vbdi.GanVbdiDocumentUseCase}
         * trên văn bản đi B (UUID của văn bản đi A bị thay thế/thu hồi, dạng chuỗi). Worker dùng giá
         * trị này để tìm văn bản đến CŨ (tương ứng A, CÙNG tenant nhận, khớp
         * {@code dynamic_fields.parentid_send = replacesDocumentId}) rồi gán
         * {@code bussiness_doc_type}/{@code documentoutrevoke} lên bản ghi văn bản đến MỚI (tương
         * ứng B) — hoàn thiện gap đã ghi nhận ở addendum 29/08/2026 (4) mục "Không port".
         */
        private String replacesDocumentId;

        /**
         * Tệp đính kèm ĐÃ ĐƯỢC COPY SẴN sang vị trí lưu trữ riêng dùng chung cho lượt gửi (KHÔNG
         * phải đường dẫn gốc của văn bản đi) — xem
         * {@link com.td.application.documents.PrepareVbdiDeliveryFilesUseCase}. Rỗng/null nếu văn
         * bản đi không có tệp nào hoặc bước copy lỗi toàn bộ (best-effort, không chặn việc gửi).
         * Tái dùng THẲNG {@link com.td.application.documents.VbdiDeliveryFileRef} — CÙNG module
         * {@code td-application} (khác {@code td-woker}, module RIÊNG, nên
         * {@code VbdiDeliveryPayload.VbdiDocumentSnapshot} bên đó vẫn phải lặp lại field, không
         * import type này).
         */
        private List<com.td.application.documents.VbdiDeliveryFileRef> files;
    }
}
