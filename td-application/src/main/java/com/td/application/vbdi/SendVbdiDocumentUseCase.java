package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.PrepareVbdiDeliveryFilesRequest;
import com.td.application.documents.PrepareVbdiDeliveryFilesUseCase;
import com.td.application.documents.VbdiDeliveryFileRef;
import com.td.application.sharedcore.OrganizationAgencyResolver;
import com.td.application.sharedcore.OrganizationRepository;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Yêu cầu người dùng 28/08/2026 ("tách lưu Nhật ký gửi nhận ra bảng vật lý khác"), tham khảo
 * {@code docs/analysis/07-phan-tich-port-senddocument-vbdi-qlvb.md}: "Gửi văn bản đi" tới nơi nhận —
 * port ĐÚNG PHẦN LÕI đã có hạ tầng để làm chắc chắn (ghi Nhật ký gửi nhận, dedup theo identifier,
 * guard trạng thái) — port {@code SendDocument.java} (Domino, 1798 dòng) TOÀN BỘ side-effect ngoài
 * (thông báo realtime, gửi mail-in/liên thông quốc gia thật, reader ACL) là việc RIÊNG, cần quyết
 * định hạ tầng/gateway chưa có cơ sở để tự chốt (xem tài liệu phân tích 07 §7) — 3 quyết định ĐÃ
 * CHỐT ở lần port này (bảo thủ, có thể mở rộng sau, không phá vỡ dữ liệu đã ghi):
 * <ol>
 *   <li><b>Thông báo realtime</b>: KHÔNG tích hợp — không có service {@code sendSocketNotSave}
 *   tương đương nào tồn tại trong hạ tầng Java hiện tại (đã rà soát toàn bộ
 *   {@code com.td.infrastructure}/{@code com.td.application} khi port session này, không tìm thấy).
 *   Người nhận vẫn thấy được lịch sử qua {@link ListVbdiSendLogUseCase} (đọc lại), chỉ thiếu đẩy
 *   tin nhắn tức thời.</li>
 *   <li><b>Gửi mail-in/liên thông quốc gia</b>: CHỈ ghi nhận log (field {@code mailin} lưu lại mã,
 *   {@code recipient_group=external_unit}) — KHÔNG gọi gateway ngoài thật (cùng quyết định đã áp
 *   dụng cho {@link GuiEmailVbdiUseCase}: chưa có SMTP relay/gateway liên thông thật cấu hình trong
 *   môi trường Java, giả lập tích hợp sẽ SAI hơn là không làm).</li>
 *   <li><b>Reader ACL</b>: KHÔNG cấp quyền đọc kiểu Domino {@code createDocumentReaderAuthor} — hệ
 *   thống Java hiện tại CHƯA có mô hình ACL per-document ngang Domino Readers field (đã xác nhận
 *   qua toàn bộ session port BL-220/BL-221/BL-230, không có field/service tương đương) — tự chế 1 mô
 *   hình ACL mới không có cơ sở nghiệp vụ xác nhận rủi ro hơn là để trống và ghi nhận gap này.</li>
 * </ol>
 *
 * <p><b>Guard</b>: văn bản phải ISSUED (đã ban hành) — đúng phân tích 07 §6 ("đi sau
 * Ban hành/ISSUED"), value ADD giữa Soạn thảo/Trình ký/Ban hành hiện có. <b>Dedup</b>: với
 * {@link VbdiSendType#SEND_DOCUMENT} (gửi lần đầu, mặc định khi không truyền {@code sendType}), bỏ
 * qua (không ghi vết mới) nơi nhận ĐÃ có ít nhất 1 vết gửi trước đó cho văn bản này (khớp §2 điểm 2
 * tài liệu phân tích — "dedup theo identifier"); {@code resenddocument}/{@code guibosung}/
 * {@code guilaitoanbo} LUÔN ghi vết mới (đại diện 1 hành động gửi lại/bổ sung rõ ràng, không phải
 * gửi trùng lặp ngoài ý muốn).</p>
 *
 * <p>29/08/2026 (yêu cầu người dùng "Thu hồi toàn bộ/thu hồi theo đơn vị phân tích và thực hiện port
 * luôn"): lớp này CŨNG xử lý 3 sendType "thu hồi" ({@link VbdiSendType#THU_HOI_VAN_BAN}/
 * {@link VbdiSendType#LAY_LAI_VAN_BAN}/{@link VbdiSendType#THU_HOI_TOAN_BO}) — port
 * {@code RevokeDocumentOut.java}/{@code RevokeAllDocumentOut.java} (Domino, 2 action RIÊNG
 * {@code revokedocumentout}/{@code Allrevokedocumentout}). KHÔNG tách use case riêng vì cơ chế ghi
 * log Y HỆT "gửi lại": guard ISSUED, LUÔN ghi (không dedup), 1 dòng/1 nơi nhận — Domino gốc cũng chỉ
 * khác NHÃN hiển thị + xử lý field {@code *_rtext}/reader-ACL cấp document (KHÔNG port, xem gap dưới
 * đây) so với gửi thường, không khác thuật toán cốt lõi. <b>Khác biệt CHỦ ĐÍCH với Domino gốc</b>
 * (bảng {@code vbdi_send_log_documents} APPEND-ONLY, không có update-tại-chỗ như Domino
 * {@code replaceItemValue} trên phần tử JSON log): Domino ĐỔI {@code status} của phần tử log đã có
 * thành "Thu hồi"/"Lấy lại" (ghi đè); ở đây ghi THÊM 1 dòng MỚI mang nhãn đó — lịch sử đầy đủ hơn
 * (không mất dấu vết đã từng gửi), tra cứu "trạng thái hiện tại" = dòng MỚI NHẤT theo
 * {@code recipient_identifier} (đã áp dụng cách này cho FE, xem {@code fetchVbdiCurrentRecipientsJava}
 * — {@code vbdiApi.js}).</p>
 *
 * <p><b>3 gap KHÔNG port (ngoài phạm vi, giống 3 gap "gửi văn bản" ở trên, KHÔNG có cơ sở hạ tầng
 * xác nhận)</b>: (1) xoá quyền reader/author phía Domino ({@code removeDocumentReaderAuthor}) — hệ
 * Java chưa có ACL per-document; (2) tác động cross-module sang văn bản đến nội bộ tương ứng
 * ({@code RemoveVBDENNOIBO_byDV}/{@code RemoveVBDENNOIBOALL} — xoá quyền xử lý/đánh dấu Deleted bên
 * {@code vbden}) — thuộc module KHÁC, cần phân tích riêng nếu cần; (3) gửi document "feedback" thông
 * báo thu hồi qua Domino Router cho {@code contact_unit}/{@code external_unit} — cùng quyết định đã
 * chốt cho gateway mail-in ở "gửi văn bản" (chỉ ghi log, không gọi gateway thật).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SendVbdiDocumentUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String NOT_ISSUED_MESSAGE = "Văn bản chưa ban hành, không thể gửi";
    private static final String NO_RECIPIENT_MESSAGE = "Chưa chọn nơi nhận";

    private static final Map<String, String> STATUS_LABELS = Map.of(
        VbdiSendType.SEND_DOCUMENT, "Gửi văn bản",
        VbdiSendType.RESEND_DOCUMENT, "Gửi lại",
        VbdiSendType.GUI_BO_SUNG, "Gửi bổ sung",
        VbdiSendType.GUI_LAI_TOAN_BO, "Gửi lại toàn bộ",
        // 29/08/2026 (port "Thu hồi toàn bộ"/"thu hồi theo đơn vị" — RevokeDocumentOut.java/
        // RevokeAllDocumentOut.java, xem javadoc VbdiSendType): tái dùng NGUYÊN cơ chế ghi log của
        // lớp này (guard ISSUED, LUÔN ghi không dedup — giống resenddocument/guibosung) thay vì viết
        // use case riêng, vì Domino gốc cũng chỉ khác NHÃN hiển thị, không khác thuật toán ghi.
        VbdiSendType.THU_HOI_VAN_BAN, "Thu hồi ngay",
        VbdiSendType.LAY_LAI_VAN_BAN, "Lấy lại ngay",
        VbdiSendType.THU_HOI_TOAN_BO, "Lấy lại",
        // 29/08/2026 (port "gửi văn bản thay thế" — SendDocument.java actionname=UnitRevoke, xem
        // javadoc VbdiSendType#UNIT_REVOKE): nhãn gốc Domino có lỗi đánh máy "Gửi gửi văn bản thay
        // thế/ thu hồi" (dòng ~783, action = "Gửi gửi văn bản..." — lặp từ "Gửi") — SỬA lại 1 từ
        // "Gửi" khi port (không phải hành vi nghiệp vụ, chỉ là lỗi chính tả hiển thị, không có lý do
        // giữ nguyên lỗi).
        VbdiSendType.UNIT_REVOKE, "Gửi văn bản thay thế/thu hồi");

    /**
     * 29/08/2026 (người dùng báo "thao tác gửi văn bản chưa thấy đổi trạng thái văn bản"): 4
     * sendType thuộc họ "GỬI" thật sự (port {@code SendDocument.java}) — CHỈ các sendType này mới
     * đánh dấu {@value #SEND_AGENT_FIELD}="1" (xem javadoc field đó). KHÔNG gồm 3 sendType "thu hồi"
     * ({@link VbdiSendType#THU_HOI_VAN_BAN}/{@link VbdiSendType#LAY_LAI_VAN_BAN}/
     * {@link VbdiSendType#THU_HOI_TOAN_BO}) — Domino gốc set field này TRONG {@code SendDocument.java}
     * (dòng ~494), KHÔNG có trong {@code RevokeDocumentOut.java}/{@code RevokeAllDocumentOut.java}
     * (2 file RIÊNG, đã xác nhận qua rà soát — xem addendum 07 (3)/(4)).
     *
     * <p>30/08/2026 (sửa lỗi thật: người dùng test lại sau rebuild vẫn thấy "0" — vì văn bản đó ĐÃ
     * gửi 1 lần TRƯỚC KHI có bản vá này, gửi lại lần 2 bị dedup 100% recipient (written=0) nhưng
     * VẪN "thành công" — điều kiện CŨ {@code written > 0} bỏ sót đúng ca này): Domino gốc set
     * {@code send_agent="1"} VÔ ĐIỀU KIỆN miễn luồng gửi chạy TỚI CUỐI method (dòng ~494, nằm sau
     * TOÀN BỘ nhánh xử lý, không phụ thuộc có ghi được dòng log MỚI nào hay không) — KHÔNG gate theo
     * "written &gt; 0" như bản vá lần đầu (sai). Sửa: set bất cứ khi nào {@code execute()} không rơi
     * vào 2 nhánh THẤT BẠI (không tìm thấy văn bản/chưa ISSUED/chưa chọn nơi nhận nào).</p>
     */
    private static final Set<String> SEND_FAMILY_TYPES = Set.of(
        VbdiSendType.SEND_DOCUMENT, VbdiSendType.RESEND_DOCUMENT, VbdiSendType.GUI_BO_SUNG,
        VbdiSendType.GUI_LAI_TOAN_BO, VbdiSendType.UNIT_REVOKE);

    /**
     * 29/08/2026 (cùng phát hiện trên): field ĐỘNG (không phải cột riêng — dùng chung Dynamic Data
     * Engine BL-101 sẵn có, giống {@code document_replace_retrieve}) đánh dấu văn bản ĐÃ được gửi
     * tới ít nhất 1 nơi nhận — port {@code SendDocument.java} dòng ~494
     * ({@code docDocument.replaceItemValue("send_agent", "1")}, set UNCONDITIONAL mỗi lần gửi thành
     * công, không phụ thuộc nhóm nào). Field này đã được {@code ViewQueryService} DÙNG SẴN từ trước
     * (case {@code VIEW_VBDI_WAITING_TO_SEND}/{@code VIEW_VBDI_SENT} lọc theo
     * {@code send_agent='0'}/{@code '1'}) nhưng KHÔNG có nơi nào GHI field này ở phía Java cho tới
     * bản vá này — văn bản gửi qua Java trước đây MÃI MÃI nằm ở tab "Chưa gửi" dù đã gửi thành
     * công. CHỈ set khi {@code sendType} thuộc {@link #SEND_FAMILY_TYPES} — KHÔNG yêu cầu có dòng
     * log MỚI nào được ghi (xem đính chính 30/08/2026 trên {@link #SEND_FAMILY_TYPES}: gửi lại 1
     * văn bản đã gửi trước đó dedup hết 100% recipient (0 dòng mới) vẫn phải set field này, đúng
     * Domino set vô điều kiện miễn không rơi vào 2 nhánh thất bại của {@code execute()}).
     */
    private static final String SEND_AGENT_FIELD = "send_agent";
    /** {@code vbdi_documents.dynamic_fields} - đơn vị ban hành (xem {@code AddVbdiSendLogUseCase}). */
    private static final String UNIT_PUBLISH_IDENTIFIER_FIELD = "unit_publish_identifier";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final VbdiSendLogDocumentRepository vbdiSendLogDocumentRepository;
    private final AddVbdiSendLogUseCase addVbdiSendLogUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final PrepareVbdiDeliveryFilesUseCase prepareVbdiDeliveryFilesUseCase;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAgencyResolver organizationAgencyResolver;

    public Result<Integer> execute(SendVbdiDocumentRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return Result.failure("Thiếu documentId cần gửi");
        }
        VbdiDocument document = vbdiDocumentRepository.findById(request.getDocumentId()).orElse(null);
        if (document == null || !SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())
                || document.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (!VbdiStatus.ISSUED.equals(document.getStatus())) {
            return Result.failure(NOT_ISSUED_MESSAGE);
        }

        String sendType = request.getSendType() == null || request.getSendType().isBlank()
            ? VbdiSendType.SEND_DOCUMENT : request.getSendType().trim();
        String statusLabel = STATUS_LABELS.getOrDefault(sendType, sendType);
        String content = request.getOpinion() == null || request.getOpinion().isBlank()
            ? null : request.getOpinion().trim();

        // Bo sung 07/09/2026 (yeu cau nguoi dung: "khong muon nhan tep dinh kem... anh xa doc truc
        // tiep... khi phat hanh se gui them 'files'"): copy DUNG 1 LAN cho CA LUOT gui (khong phai
        // theo tung nhom/tung noi nhan) - xem javadoc prepareDeliveryFiles(). Chi can khi co it nhat
        // 1 nguoi nhan contact_unit (nhom DUY NHAT dang tao van ban den that o td-woker hien nay -
        // xem VbdiDeliveryTaskHandler) - tranh copy vo ich khi chi gui coordinator/internal_unit.
        List<VbdiDeliveryFileRef> deliveryFiles = isEmpty(request.getContactUnit())
            ? List.of() : prepareDeliveryFiles(document);

        int written = 0;
        written += sendToGroup(request.getDocumentId(), VbdiSendRecipientGroup.COORDINATOR,
            request.getCoordinator(), sendType, statusLabel, content, deliveryFiles);
        written += sendToGroup(request.getDocumentId(), VbdiSendRecipientGroup.INTERNAL_UNIT,
            request.getInternalUnit(), sendType, statusLabel, content, deliveryFiles);
        written += sendToGroup(request.getDocumentId(), VbdiSendRecipientGroup.CONTACT_UNIT,
            request.getContactUnit(), sendType, statusLabel, content, deliveryFiles);
        written += sendToGroup(request.getDocumentId(), VbdiSendRecipientGroup.EXTERNAL_UNIT,
            request.getExternalUnit(), sendType, statusLabel, content, deliveryFiles);

        if (written == 0 && isEveryGroupEmpty(request)) {
            return Result.failure(NO_RECIPIENT_MESSAGE);
        }
        if (SEND_FAMILY_TYPES.contains(sendType)) {
            markSendAgent(document);
        }
        return Result.success(written);
    }

    /**
     * Bổ sung 07/09/2026 — copy tệp đính kèm của văn bản đi sang vị trí lưu trữ riêng dùng CHUNG cho
     * cả lượt gửi (xem {@link PrepareVbdiDeliveryFilesUseCase}), gọi ĐÚNG 1 LẦN tại đây (không phải
     * trong {@link #sendToGroup} — nơi lặp theo từng nơi nhận). Best-effort ở CẤP LƯỢT GỬI: lỗi toàn
     * bộ bước này (vd MinIO tạm thời không khả dụng) KHÔNG được chặn việc "gửi văn bản" (side-effect
     * phụ, giống publish outbox message ở {@code AddVbdiSendLogUseCase}) — trả về rỗng, văn bản đến
     * vẫn được tạo, chỉ thiếu tệp đính kèm (có thể bổ sung thủ công sau), CÒN HƠN không gửi được văn
     * bản.
     */
    private List<VbdiDeliveryFileRef> prepareDeliveryFiles(VbdiDocument document) {
        try {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
            Object senderIdentifier = fields.get(UNIT_PUBLISH_IDENTIFIER_FIELD);

            var request = new PrepareVbdiDeliveryFilesRequest();
            request.setDocumentId(document.getId());
            request.setSenderIdentifier(senderIdentifier == null ? null : senderIdentifier.toString());

            var result = prepareVbdiDeliveryFilesUseCase.execute(request);
            if (!result.isSuccess()) {
                log.error("Chuan bi tep dinh kem cho luot gui van ban {} that bai: {} - van ban den se "
                    + "duoc tao KHONG co tep dinh kem", document.getId(), result.getError());
                return List.of();
            }
            return result.getData() == null ? List.of() : result.getData();
        } catch (Exception ex) {
            log.error("Chuan bi tep dinh kem cho luot gui van ban {} loi ngoai y muon - van ban den se "
                + "duoc tao KHONG co tep dinh kem", document.getId(), ex);
            return List.of();
        }
    }

    private void markSendAgent(VbdiDocument document) {
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        if ("1".equals(String.valueOf(fields.get(SEND_AGENT_FIELD)))) {
            return; // Da danh dau tu lan gui truoc - khong ghi/save thua.
        }
        fields.put(SEND_AGENT_FIELD, "1");
        document.replaceDynamicFields(toJson(fields));
        vbdiDocumentRepository.save(document);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private int sendToGroup(java.util.UUID documentId, String group, List<VbdiSendRecipientRequest> recipients,
            String sendType, String statusLabel, String content, List<VbdiDeliveryFileRef> deliveryFiles) {
        if (recipients == null || recipients.isEmpty()) {
            return 0;
        }
        int written = 0;
        for (VbdiSendRecipientRequest recipient : recipients) {
            if (recipient == null) {
                continue;
            }
            String identifier = firstNonBlank(recipient.getIdentifier(), recipient.getUsername());
            if (identifier == null) {
                log.warn("Bo qua nguoi nhan khong co identifier/username (group={})", group);
                continue;
            }
            String effectiveGroup = VbdiSendRecipientGroup.CONTACT_UNIT.equals(group)
                ? reclassifyIfOwnAgency(identifier) : group;
            boolean isInitialSend = VbdiSendType.SEND_DOCUMENT.equals(sendType);
            if (isInitialSend && vbdiSendLogDocumentRepository
                    .existsByParentIdAndRecipientGroupAndRecipientIdentifier(documentId, effectiveGroup, identifier)) {
                // Da gui truoc do - khong ghi trung vet "Gui van ban" ban dau (dedup, xem javadoc lop).
                continue;
            }

            var logRequest = new AddVbdiSendLogRequest();
            logRequest.setParentId(documentId);
            logRequest.setSendType(sendType);
            logRequest.setRecipientGroup(effectiveGroup);
            logRequest.setRecipientIdentifier(identifier);
            logRequest.setRecipientName(recipient.getName());
            logRequest.setMailin(recipient.getMailin());
            logRequest.setStatus(statusLabel);
            logRequest.setContent(content);
            // Chi nhom contact_unit thuc su dung (td-woker chi tao van ban den that cho nhom nay) -
            // nhung van truyen cho ca 3 nhom con lai vo hai (VbdiDeliveryPayload phia worker se don
            // gian bo qua field nay ngoai nhanh contact_unit, xem VbdenIncomingDocumentWriter).
            logRequest.setFiles(deliveryFiles);

            Result<java.util.UUID> result = addVbdiSendLogUseCase.execute(logRequest);
            if (result.isSuccess()) {
                written++;
            } else {
                log.warn("Ghi vet gui nhan that bai (group={}, identifier={}): {}", effectiveGroup, identifier, result.getError());
            }
        }
        return written;
    }

    /**
     * Bổ sung 18/09/2026 (yêu cầu người dùng "hoàn thiện phát hành đi khi chọn Đơn vị nội bộ, có
     * trường hợp chọn đơn vị nội bộ ở Cơ quan, đơn vị thuộc Tỉnh"): tab "Cơ quan, đơn vị thuộc Tỉnh"
     * ({@code contact_unit}) CỐ Ý hiển thị cây TOÀN TỈNH — bao gồm cả chính cơ quan người gửi và
     * phòng/ban con của họ (khớp Domino gốc {@code CoCauToChuc.java}, không loại trừ chính mình —
     * xem {@code GetLegacyOrganizationTreeUseCase} javadoc bugfix 07/09/2026). Người dùng có thể lỡ
     * tick chính đơn vị mình ở tab này thay vì tab hẹp "Đơn vị nội bộ".
     *
     * <p>Domino gốc tự phát hiện case này ngay tại {@code SendDocument.java}
     * ({@code copy_Document_Vbden_NoiBo_CAY}, dòng 1359-1627) bằng heuristic riêng của nó (mailin
     * rỗng + độ sâu identifier) rồi xử lý y hệt {@code internal_unit} thật. Heuristic đó KHÔNG còn
     * khớp dữ liệu Java (cây tổ chức Java mô tả đầy đủ phòng/ban cho MỌI cơ quan, không riêng cơ
     * quan mình) — thay bằng cách chuẩn hơn, đã có sẵn cho mục đích tương tự
     * ({@code GetLegacyOrganizationTreeUseCase#resolveCurrentUserAgencyId}, dùng cho picker
     * "Chuyển xử lý"): so khớp tổ tiên "agency" (Sở/cơ quan) gần nhất của tổ chức được chọn với
     * agency của CHÍNH người gửi hiện tại — xem {@link OrganizationAgencyResolver}. Khớp → coi như
     * {@code internal_unit} cho TOÀN BỘ phần còn lại của luồng (dedup, {@code recipient_group} lưu
     * DB, tenant đích, tạo văn bản đến — xem {@code AddVbdiSendLogUseCase}/
     * {@code VbdenIncomingDocumentWriter} phía {@code td-woker}).
     *
     * <p>Không resolve được ở BẤT KỲ bước nào (tổ chức không tồn tại/đã xoá, hoặc không xác định
     * được agency của người gửi hiện tại) → giữ nguyên {@code contact_unit} — an toàn hơn đoán bừa,
     * cùng triết lý fail-open đã dùng ở {@link OrganizationAgencyResolver}.
     */
    private String reclassifyIfOwnAgency(String recipientIdentifier) {
        var recipientOrg = organizationRepository.findByIdentifierAndDeletedOnIsNull(recipientIdentifier);
        if (recipientOrg.isEmpty()) {
            return VbdiSendRecipientGroup.CONTACT_UNIT;
        }
        var recipientAgencyId = organizationAgencyResolver.resolveAgencyId(recipientOrg.get().getId());
        var senderAgencyId = organizationAgencyResolver.resolveCurrentUserAgencyId();
        boolean isOwnAgency = recipientAgencyId.isPresent() && senderAgencyId.isPresent()
            && recipientAgencyId.get().equals(senderAgencyId.get());
        return isOwnAgency ? VbdiSendRecipientGroup.INTERNAL_UNIT : VbdiSendRecipientGroup.CONTACT_UNIT;
    }

    private boolean isEveryGroupEmpty(SendVbdiDocumentRequest request) {
        return isEmpty(request.getCoordinator()) && isEmpty(request.getInternalUnit())
            && isEmpty(request.getContactUnit()) && isEmpty(request.getExternalUnit());
    }

    private boolean isEmpty(List<VbdiSendRecipientRequest> list) {
        return list == null || list.isEmpty();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
