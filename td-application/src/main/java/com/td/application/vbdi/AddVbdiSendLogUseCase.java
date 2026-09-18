package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import com.td.application.messaging.OutboxPublisherService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.OrganizationRepository;
import com.td.domain.sharedcore.Organization;
import com.td.domain.vbdi.VbdiDocument;
import com.td.domain.vbdi.VbdiSendLogDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("tách lưu Nhật ký gửi nhận ra bảng vật lý khác như làm với
 * draft_process"): writer CẤP THẤP DUY NHẤT của bảng vật lý ĐỘC LẬP {@code vbdi_send_log_documents}
 * (xem {@link VbdiSendLogDocument}) — ghi ĐÚNG 1 dòng cho 1 nơi nhận. {@link SendVbdiDocumentUseCase}
 * (business action "gửi văn bản", DoD tài liệu phân tích 07) gọi lặp lại use case này cho từng nơi
 * nhận trong 4 nhóm — đúng khuôn {@code AddDraftProcessDocumentUseCase} (writer chung, caller
 * nghiệp vụ lặp gọi cho mỗi action/mỗi nơi nhận).
 *
 * <p>Guard: văn bản đi cha ({@code parentId}) phải tồn tại trong {@code vbdi_documents}
 * ({@code document_type='vbdi'}, chưa xoá mềm) — cùng nguyên tắc {@code AddDuThaoEditHistoryUseCase}
 * guard dự thảo cha. KHÔNG guard trạng thái ISSUED ở tầng này (đó là quy tắc nghiệp vụ của hành động
 * "gửi văn bản" cụ thể, không phải bất biến của "ghi 1 dòng log" — xem guard đó ở
 * {@link SendVbdiDocumentUseCase}, giữ writer cấp thấp này tái dùng được cho ngữ cảnh khác nếu phát
 * sinh sau này).</p>
 *
 * <p>Danh tính người thực hiện SERVER-AUTHORITATIVE: {@code userCreator} = username đăng nhập qua
 * {@link CurrentUserProvider} (fallback "system"), client KHÔNG tự đặt được — cùng nguyên tắc
 * {@code AddDuThaoEditHistoryUseCase#stampUserCreator}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddVbdiSendLogUseCase {

    private static final String FALLBACK_USERNAME = "system";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";

    /** {@code vbdi_documents.dynamic_fields} - đơn vị ban hành (xem ViewQueryService/BanHanhVbdiUseCase). */
    private static final String UNIT_PUBLISH_IDENTIFIER_FIELD = "unit_publish_identifier";
    private static final String UNIT_PUBLISH_FIELD = "unit_publish";
    private static final String DOCUMENT_NUMBER_FIELD = "document_number";
    /** = "Ký hiệu" ({@code VBDI_FORM_FIELDS} #1) - CHỈ ký hiệu (vd "QĐ-UBND"), KHÔNG có số. */
    private static final String CODE_FIELD = "code";
    /**
     * FE tự tính qua {@code TDSoKyHieu()} (ThanhHoaDEV/src/app/pages/vbdi/Components/
     * ControllModal.jsx) mỗi lần lưu có đủ {@code document_number}+{@code code}: {@code document_number
     * + number_other + ("/" hoặc "-" tuỳ khối) + code} — vd "123/QĐ-UBND". Đây MỚI là giá trị ĐẦY ĐỦ
     * dùng hiển thị (field-mapping/02-vbdi-field-mapping.md field #2, đánh dấu bắt buộc;
     * {@code ColumnsVBdi.jsx#record.code_full || record.code}) — xem {@link #resolveCode}.
     */
    private static final String CODE_FULL_FIELD = "code_full";
    private static final String PROMULGATION_DATE_FIELD = "promulgation_date";
    // Bo sung 07/09/2026 (doi chieu docs/views_domino/data_vbdi_SendDocument_output.json + xac
    // nhan ten qua VBDI_FORM_FIELDS, ThanhHoaDEV/src/utils/global.jsx) - xem javadoc
    // publishDeliveryMessage() va VbdiDeliveryMessagePayload.DocumentSnapshot.
    private static final String DRAFTOR_FIELD = "draftor";
    private static final String SIGNER_FIELD = "signer";
    private static final String SIGNER_POSITION_FIELD = "signer_position";
    private static final String CREATOR_FIELD = "creator";
    private static final String DELIVERY_METHOD_FIELD = "delivery_method";
    private static final String DOCUMENT_TYPE_FIELD = "document_type";
    private static final String SECTOR_FIELD = "sector";
    private static final String CONFIDENTIALITY_FIELD = "confidentiality";
    private static final String PAGE_AMOUNT_FIELD = "page_amount";
    private static final String PRIORITY_FIELD = "priority";
    private static final String DOCUMENT_REPLACE_RETRIEVE_FIELD = "document_replace_retrieve";
    // Bo sung 13/09/2026 (yeu cau nguoi dung "port full end-to-end nhanh thay the/thu hoi", xem
    // javadoc VbdenIncomingDocumentWriter#resolveRevokeFields, td-woker): field ghi boi
    // GanVbdiDocumentUseCase (lien ket B->A) - PHAI forward sang ban ghi vbden tao o ben nhan de
    // VbdenIncomingDocumentWriter tim duoc van ban den CU (tuong ung A, cung tenant, khop
    // parentid_send=replacesDocumentId) roi gan bussiness_doc_type/documentoutrevoke cho ban ghi
    // MOI - hoan thien noi tiep phan "CHUA port" da ghi nhan o addendum 29/08/2026 (4) muc 4.
    private static final String REPLACES_DOCUMENT_ID_FIELD = "replaces_document_id";

    /** taskType envelope publish tới RabbitMQ/Kafka - PHẢI khớp {@code VbdiTaskConstants.TASK_DELIVERY} (td-woker). */
    private static final String TASK_TYPE_VBDI_DELIVERY = "vbdi.delivery";

    /**
     * Bổ sung 08/09/2026 (người dùng báo lỗi thật: bấm "Lấy lại VB" — log td-worker báo DONE nhưng
     * văn bản đến của đơn vị VẪN còn nguyên trong danh sách): TRƯỚC bản vá này, {@code publishDeliveryMessage}
     * publish CẢ 3 sendType "thu hồi" ({@link #REVOKE_FAMILY_SEND_TYPES}) qua CÙNG taskType
     * {@code vbdi.delivery} như 1 lượt GỬI MỚI — SAI hoàn toàn: Domino
     * {@code RevokeDocumentOut.java}/{@code RevokeAllDocumentOut.java} gửi mail form
     * {@code feedback_DocumentOut} (xử lý ở {@code StatusProcess.ExecuteVBDIBussiness() ->
     * ActionRevokeDocument()} — đọc lại {@code docs/agent_domino/io/BeforeMailin.ja/StatusProcess.java}),
     * KHÔNG PHẢI form {@code documentin} như {@code SendDocument.java} — nghĩa là phía nơi nhận phải
     * TÌM văn bản đến ĐÃ CÓ (theo id văn bản đi gốc) để đánh dấu "Đã lấy lại"/ẩn khỏi danh sách, KHÔNG
     * được tạo thêm 1 văn bản đến MỚI như 1 lượt gửi bình thường. taskType RIÊNG này khớp
     * {@code VbdiTaskConstants.TASK_REVOKE} (td-woker, route sang {@code VbdiRevokeTaskHandler} MỚI —
     * port đúng {@code ActionRevokeDocument}), PHÂN BIỆT với {@link #TASK_TYPE_VBDI_DELIVERY}.
     */
    private static final String TASK_TYPE_VBDI_REVOKE = "vbdi.revoke";

    /**
     * 3 sendType thuộc họ "THU HỒI/LẤY LẠI" ({@code RevokeDocumentOut.java}/
     * {@code RevokeAllDocumentOut.java} Domino, xem javadoc {@link VbdiSendType}) — publish
     * {@link #TASK_TYPE_VBDI_REVOKE} thay vì {@link #TASK_TYPE_VBDI_DELIVERY} (xem javadoc field đó).
     * KHÔNG gồm {@link VbdiSendType#UNIT_REVOKE} ("gửi văn bản thay thế/thu hồi") — action đó publish
     * {@code SendDocument.java} THẬT (tạo văn bản đến MỚI thông báo thay thế), khác hẳn về bản chất
     * dù tên có chữ "revoke" — vẫn thuộc họ GỬI (xem {@code SendVbdiDocumentUseCase#SEND_FAMILY_TYPES}).
     */
    private static final Set<String> REVOKE_FAMILY_SEND_TYPES = Set.of(
        VbdiSendType.THU_HOI_VAN_BAN, VbdiSendType.LAY_LAI_VAN_BAN, VbdiSendType.THU_HOI_TOAN_BO);

    private final VbdiSendLogDocumentRepository vbdiSendLogDocumentRepository;
    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final OrganizationRepository organizationRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final OutboxPublisherService outboxPublisherService;

    public Result<UUID> execute(AddVbdiSendLogRequest request) {
        if (request == null || request.getParentId() == null) {
            return Result.failure("Thiếu id văn bản đi cần ghi vết gửi nhận");
        }
        var parent = vbdiDocumentRepository.findById(request.getParentId()).orElse(null);
        if (parent == null || !SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(parent.getDocumentType())
                || parent.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String sendType = request.getSendType() == null || request.getSendType().isBlank()
            ? VbdiSendType.SEND_DOCUMENT : request.getSendType().trim();

        var entity = new VbdiSendLogDocument(
            request.getParentId(),
            sendType,
            request.getRecipientGroup(),
            request.getRecipientIdentifier(),
            request.getRecipientName(),
            request.getMailin(),
            request.getStatus(),
            request.getContent(),
            currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME));

        var saved = vbdiSendLogDocumentRepository.save(entity);
        documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
        publishDeliveryMessage(saved, parent, request.getFiles());
        return Result.success(saved.getId());
    }

    /**
     * Yêu cầu người dùng 04/09/2026 ("hoàn thiện Gửi văn bản để publish RabbitMQ/Kafka... code
     * trước luôn"): publish envelope {@code vbdi.delivery} tới {@link OutboxPublisherService} —
     * TRONG CÙNG transaction ghi {@code vbdi_send_log_documents} ở trên (outbox pattern, xem
     * javadoc lớp {@link OutboxPublisherService}). Áp dụng cho CẢ 4 nhóm nơi nhận (quyết định đã
     * chốt với người dùng — xem addendum {@code docs/architecture/11}); với
     * {@code external_unit} (cơ quan ngoài tỉnh, chưa có tenant đích thật/chưa có gateway liên
     * thông quốc gia), dùng tenant CỦA CHÍNH ĐƠN VỊ GỬI làm placeholder — an toàn vì
     * {@code VbdiDeliveryPayload.isExternal()} phía {@code td-woker} trả về sớm, không đụng DB
     * tenant nào theo giá trị này.
     *
     * <p><b>Bugfix 06/09/2026</b> (nhầm nhóm ban đầu — xác nhận lại qua
     * {@code ThanhHoaDEV/src/utils/global.jsx}: {@code contact_unit_rtext = "Danh sách cơ quan,
     * đơn vị thuộc " + CAP}): {@code contact_unit} (cơ quan/đơn vị KHÁC thuộc tỉnh) MỚI là nhóm cần
     * tra tenant đích THẬT (trước đó nhầm coi là placeholder giống {@code external_unit}) —
     * {@code internal_unit} (đơn vị nội bộ CỦA CHÍNH cơ quan gửi, không "thuộc tỉnh") KHÔNG cần
     * tenant đích khác — xem {@link #resolveTargetTenantId}.
     *
     * <p>Lỗi ở bước này (publish thất bại/thiếu cấu hình...) KHÔNG được làm hỏng việc ghi Nhật ký
     * gửi nhận (đã lưu DB xong ở trên) — publish là side-effect BEST-EFFORT, giống các side-effect
     * khác của module này (thông báo realtime, gateway mail-in — xem javadoc lớp
     * {@link SendVbdiDocumentUseCase}).</p>
     *
     * <p><b>Bugfix 07/09/2026</b> (người dùng gửi mẫu Domino thật {@code docs/views_domino/
     * data_vbdi_SendDocument_output.json}, phát hiện "dữ liệu vẫn chưa khớp hoàn toàn với DOMINO
     * cũ" ở văn bản đến tự tạo): trước đó {@code documentSnapshot} CHỈ mang 3 field ({@code code},
     * {@code subject}, {@code promulgationDate}) — thiếu {@code draftor}/{@code signer}/
     * {@code signer_position}/{@code creator}/{@code delivery_method}/{@code document_type}/
     * {@code sector}/{@code confidentiality}/{@code page_amount}/{@code priority}/
     * {@code document_replace_retrieve} mà Domino thật COPY nguyên từ văn bản đi sang bản ghi văn
     * bản đến khi SendDocument. Đối chiếu {@code VBDI_FORM_FIELDS} ({@code ThanhHoaDEV/src/utils/
     * global.jsx}, nguồn xác thực nhất) xác nhận đây CHÍNH LÀ tên field văn bản đi hiện đã lưu đúng
     * trên {@code parent.dynamic_fields} — chỉ thiếu bước đọc ra và forward. Đã bổ sung đọc thêm 11
     * field này (xem hằng số ở đầu lớp) vào {@code documentSnapshot}, giữ NGUYÊN tên
     * {@code document_replace_retrieve} (không dịch sang tên vbden — người dùng đã chọn phương án
     * này vì bản thân form vbden dùng 3 tên khác nhau không rõ ánh xạ, xem docs/architecture/11
     * addendum 07/09/2026). Tên file đính kèm gốc ({@code tepbanhanh}/{@code tepkemtheo}) CHƯA đưa
     * vào ở lần bổ sung đó — người dùng xác nhận để việc riêng sau.</p>
     *
     * <p><b>Bổ sung tiếp 07/09/2026 (cùng ngày, sau khi hỏi lại người dùng)</b>: {@code files} nhận
     * THẲNG từ {@code request.getFiles()} (đã copy sẵn 1 LẦN cho cả lượt gửi bởi
     * {@code SendVbdiDocumentUseCase} — xem javadoc {@code PrepareVbdiDeliveryFilesUseCase}), KHÔNG
     * tự đọc lại {@code file_metadata} ở đây — writer cấp thấp này chỉ ghi vết + publish, không tự
     * quyết định logic file (giữ đúng nguyên tắc 1 nơi quyết định, tránh N lần copy cho N nơi nhận).</p>
     */
    private void publishDeliveryMessage(VbdiSendLogDocument saved, VbdiDocument parent,
            List<com.td.application.documents.VbdiDeliveryFileRef> files) {
        try {
            String senderTenantId = currentTenantProvider.currentTenantId().orElse(null);
            String targetTenantId = resolveTargetTenantId(saved.getRecipientGroup(),
                saved.getRecipientIdentifier(), senderTenantId);
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
            String senderIdentifier = asString(fields.get(UNIT_PUBLISH_IDENTIFIER_FIELD));

            var payload = VbdiDeliveryMessagePayload.builder()
                .sendId(UUID.randomUUID().toString())
                .sendLogId(saved.getId().toString())
                .documentId(saved.getParentId().toString())
                .senderTenantId(senderTenantId)
                .senderIdentifier(senderIdentifier)
                .senderOrganName(asString(fields.get(UNIT_PUBLISH_FIELD)))
                .senderReplyRoutingKey(senderIdentifier == null ? null : "vbdi.reply." + senderIdentifier)
                .sendType(saved.getSendType())
                .recipientGroup(saved.getRecipientGroup())
                .recipientIdentifier(saved.getRecipientIdentifier())
                .recipientName(saved.getRecipientName())
                .recipientMailin(saved.getMailin())
                .recipientReceiverUsername(resolveReceiverUsername(saved))
                .statusLabel(saved.getStatus())
                .opinion(saved.getContent())
                .documentSnapshot(VbdiDeliveryMessagePayload.DocumentSnapshot.builder()
                    .code(resolveCode(fields))
                    .subject(parent.getTitle())
                    .promulgationDate(asString(fields.get(PROMULGATION_DATE_FIELD)))
                    // Bo sung 07/09/2026 - xem javadoc phuong thuc nay + VbdiDeliveryMessagePayload.DocumentSnapshot.
                    .draftor(asString(fields.get(DRAFTOR_FIELD)))
                    .signer(asString(fields.get(SIGNER_FIELD)))
                    .signerPosition(asString(fields.get(SIGNER_POSITION_FIELD)))
                    .creator(asString(fields.get(CREATOR_FIELD)))
                    .deliveryMethod(asString(fields.get(DELIVERY_METHOD_FIELD)))
                    .documentType(asString(fields.get(DOCUMENT_TYPE_FIELD)))
                    .sector(asString(fields.get(SECTOR_FIELD)))
                    .confidentiality(asString(fields.get(CONFIDENTIALITY_FIELD)))
                    .pageAmount(asString(fields.get(PAGE_AMOUNT_FIELD)))
                    .priority(asString(fields.get(PRIORITY_FIELD)))
                    .documentReplaceRetrieve(asString(fields.get(DOCUMENT_REPLACE_RETRIEVE_FIELD)))
                    .replacesDocumentId(asString(fields.get(REPLACES_DOCUMENT_ID_FIELD)))
                    .files(files)
                    .build())
                .build();

            var unit = new MessageUnitSpec(saved.getId().toString(), targetTenantId, payload);
            // Bo sung 08/09/2026 (xem javadoc TASK_TYPE_VBDI_REVOKE/REVOKE_FAMILY_SEND_TYPES): "thu
            // hoi"/"lay lai" KHONG phai 1 luot gui moi - phai route sang handler RIENG o phia nhan,
            // khong duoc tao them van ban den moi.
            String taskType = REVOKE_FAMILY_SEND_TYPES.contains(saved.getSendType())
                ? TASK_TYPE_VBDI_REVOKE : TASK_TYPE_VBDI_DELIVERY;
            outboxPublisherService.enqueue("vbdi", saved.getId(),
                new OutboundMessage(taskType, saved.getId().toString(), List.of(unit)));
        } catch (Exception ex) {
            log.error("Khong the enqueue outbox 'vbdi.delivery' cho sendLog {} - Nhat ky gui nhan " +
                "van da ghi DB thanh cong, chi thieu buoc publish message (best-effort)", saved.getId(), ex);
        }
    }

    /**
     * Bugfix 07/09/2026 (người dùng phát hiện: "code_full hoặc document_number và code để gán vào
     * 'code' vì ở văn bản đi có tách code là ký hiệu văn bản và document_number là số văn bản,
     * code_full là đầy đủ, nếu lấy mình document_number sẽ thiếu thông tin"). Trước đó
     * {@code documentSnapshot.code} lấy THẲNG {@code document_number} (vd chỉ "123") — thiếu hẳn ký
     * hiệu ("/QĐ-UBND"), khiến văn bản đến tự tạo hiển thị số văn bản trống nghĩa. Thứ tự ưu tiên:
     * <ol>
     *   <li>{@code code_full} nếu có — giá trị FE ĐÃ tính đúng qua {@code TDSoKyHieu()} lúc lưu văn
     *       bản đi (cộng cả {@code number_other} và đúng dấu phân cách theo khối tỉnh/khối khác mà
     *       Worker không có ngữ cảnh để tính lại chính xác).</li>
     *   <li>Ghép {@code document_number + "/" + code} nếu CẢ HAI có nhưng {@code code_full} lại
     *       thiếu (dữ liệu cũ/thiếu đồng bộ) — xấp xỉ hợp lý cho khối tỉnh (dấu phân cách phổ biến
     *       nhất), chấp nhận có thể lệch dấu phân cách với khối khác thay vì mất hẳn ký hiệu.</li>
     *   <li>Fallback về 1 trong 2 field còn lại (document_number hoặc code) nếu chỉ có 1 field —
     *       vẫn còn hơn để trống hoàn toàn.</li>
     * </ol>
     */
    private String resolveCode(Map<String, Object> fields) {
        String codeFull = asString(fields.get(CODE_FULL_FIELD));
        if (codeFull != null && !codeFull.isBlank()) {
            return codeFull;
        }
        String documentNumber = asString(fields.get(DOCUMENT_NUMBER_FIELD));
        String code = asString(fields.get(CODE_FIELD));
        boolean hasDocumentNumber = documentNumber != null && !documentNumber.isBlank();
        boolean hasCode = code != null && !code.isBlank();
        if (hasDocumentNumber && hasCode) {
            return documentNumber + "/" + code;
        }
        return hasDocumentNumber ? documentNumber : code;
    }

    /**
     * {@code coordinator}/{@code contact_unit}: tra {@code Organization.linkedTenantId} theo
     * {@code recipientIdentifier} (tenant thật của đơn vị nhận, xem docs/architecture/11 §5.2) —
     * fallback tenant hiện tại nếu chưa liên kết (vẫn cùng 1 tiến trình phục vụ mọi tenant, xem
     * docs/architecture/06). {@code internal_unit}/{@code external_unit}: LUÔN dùng tenant hiện tại
     * (placeholder).
     *
     * <p>Bugfix 06/09/2026 (đã đổi từ {@code internal_unit} sang {@code contact_unit} — xem
     * javadoc {@link #publishDeliveryMessage}): {@code contact_unit} = cơ quan/đơn vị KHÁC thuộc
     * tỉnh, CÓ tenant riêng thật (cần tra {@code linkedTenantId}); {@code internal_unit} = đơn vị
     * nội bộ CỦA CHÍNH cơ quan gửi (KHÔNG có tenant khác — placeholder tenant hiện tại là đúng, dù
     * "khác tenant" không áp dụng cho nhóm này theo đúng nghĩa của nó).
     */
    private String resolveTargetTenantId(String recipientGroup, String recipientIdentifier, String senderTenantId) {
        boolean hasRealTenantGroup = VbdiSendRecipientGroup.COORDINATOR.equals(recipientGroup)
            || VbdiSendRecipientGroup.CONTACT_UNIT.equals(recipientGroup);
        if (hasRealTenantGroup && recipientIdentifier != null) {
            var linked = organizationRepository.findByIdentifierAndDeletedOnIsNull(recipientIdentifier)
                .map(org -> org.getLinkedTenantId())
                .filter(id -> id != null && !id.isBlank());
            if (linked.isPresent()) {
                return linked.get();
            }
        }
        return senderTenantId;
    }

    /**
     * Bổ sung 18/09/2026 (hoàn thiện "phát hành vbdi → đến nội bộ"): CHỈ nhóm {@code internal_unit}
     * (thật hoặc reclassify bởi {@code SendVbdiDocumentUseCase#reclassifyIfOwnAgency}) cần username
     * "người đại diện nhận" của tổ chức đích — khớp Domino {@code SendDocument.java}
     * ({@code copy_Document_Vbden_NoiBo}/{@code _CAY}: tra {@code dbqlvb.getDocumentByUNID(id)} lấy
     * field {@code user_receiver}). Java đã có sẵn field tương đương
     * ({@code Organization.receiverId} → {@code AppUser.username}, xem
     * {@code GetLegacyOrganizationTreeUseCase#resolveReceiverUsername}) — lặp lại 2 bước tra cứu ở
     * đây (không tách use case riêng chỉ cho 3 dòng). Trả {@code null} nếu không phải
     * {@code internal_unit}, tổ chức không tồn tại, hoặc không cấu hình người đại diện — worker phía
     * nhận bỏ qua field {@code user_receiver} khi rỗng, KHÔNG lỗi (khớp Domino
     * {@code if(!user_receiver.equals(""))}).
     */
    private String resolveReceiverUsername(VbdiSendLogDocument saved) {
        if (!VbdiSendRecipientGroup.INTERNAL_UNIT.equals(saved.getRecipientGroup())) {
            return null;
        }
        Organization org = organizationRepository
            .findByIdentifierAndDeletedOnIsNull(saved.getRecipientIdentifier()).orElse(null);
        if (org == null || org.getReceiverId() == null || org.getReceiverId().isBlank()) {
            return null;
        }
        try {
            UUID receiverId = UUID.fromString(org.getReceiverId().trim());
            return appUserRepository.findByIdAndDeletedOnIsNull(receiverId)
                .map(user -> user.getUsername()).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> buildSnapshot(VbdiSendLogDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("parentId", document.getParentId());
        snapshot.put("sendType", document.getSendType());
        snapshot.put("recipientGroup", document.getRecipientGroup());
        snapshot.put("recipientIdentifier", document.getRecipientIdentifier());
        snapshot.put("status", document.getStatus());
        snapshot.put("userCreator", document.getUserCreator());
        return snapshot;
    }
}
