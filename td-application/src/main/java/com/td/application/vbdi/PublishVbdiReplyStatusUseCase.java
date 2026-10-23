package com.td.application.vbdi;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import com.td.application.messaging.OutboxPublisherService;
import com.td.application.sharedcore.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bổ sung 07/09/2026 (yêu cầu người dùng: "hoàn thiện chức năng gửi văn bản trạng thái... khi đơn
 * vị nhận được văn bản, văn thư vào sổ, phân xử lý... sẽ gửi trạng thái phản hồi cho đơn vị gửi và
 * lưu vào nhật ký gửi nhận của đơn vị", đối chiếu Domino thật {@code ChuyenXuLy.java} gọi
 * {@code StatusCode}/{@code ResponseStatus}, {@code docs/agent_domino/io}): publish envelope
 * {@code vbdi.reply} NGƯỢC (đơn vị NHẬN → đơn vị GỬI) khi 1 văn bản đến TỰ TẠO qua giao (có
 * {@code send_log_id} trong {@code dynamic_fields} — xem {@code VbdenIncomingDocumentWriter},
 * td-woker) có hành động nghiệp vụ đáng thông báo — hiện gọi từ 2 nơi:
 * <ul>
 *   <li>{@code LuuVanBanDenUseCase} khi "vào sổ" (có chọn {@code documentRegisterId}) — STATUS_03
 *       "Đã tiếp nhận".</li>
 *   <li>{@code ChuyenXuLyVbdenUseCase} khi phân công/chuyển xử lý (có người mới được giao chủ trì)
 *       — STATUS_04 "Phân công xử lý", kèm STATUS_05 "Đang xử lý" khi đây là lần đầu văn bản được
 *       phân công (status trước đó = "0", đúng {@code status.equals("0")} của {@code ChuyenXuLy.java}
 *       thật).</li>
 *   <li>{@code KetThucXuLyUseCase} khi 1 người "Kết thúc xử lý" phần việc của họ — STATUS_06
 *       "Hoàn thành" (đúng {@code CapNhatVanBanDen.java} thật, case {@code ketthucxuly}: bắn VÔ
 *       ĐIỀU KIỆN mỗi lần thành công, không gác theo "văn bản đã hết người xử lý chưa").</li>
 * </ul>
 * <p><b>Bổ sung 07/09/2026 (2)</b>: đã "làm nốt" STATUS_05/06 theo yêu cầu người dùng. STATUS_02
 * "Từ chối tiếp nhận" VẪN CHƯA áp dụng — đối chiếu lại Domino thật xác nhận mã này thực ra được
 * publish từ {@code vn.com.tandan.xsp.services.vbdi.ReplyDocumentOut.java} (module {@code vbdi} —
 * "văn bản đi", KHÔNG phải luồng "Không vào sổ" của {@code vbden} như suy đoán ban đầu), và "Không
 * vào sổ" phía Java CHƯA được port (FE {@code VaoSoNhieu.jsx} vẫn gọi thẳng Domino
 * {@code qlvb/vbden/CapNhatVanBanDen?action=khongvaoso}) — chưa có điểm gọi nào để hook vào, ghi
 * nhận gap chờ port flow đó trước (xem {@code docs/architecture/11} addendum 07/09/2026 (5)).
 *
 * <p><b>Guard bắt buộc</b>: văn bản đến KHÔNG có {@code send_log_id} (vd tạo thủ công/nhận qua
 * đường khác, không phải giao qua {@code contact_unit}) → KHÔNG có gì để phản hồi, bỏ qua NGAY
 * (không log lỗi — đây là trường hợp BÌNH THƯỜNG, đa số văn bản đến KHÔNG tới từ giao liên thông).
 * Publish là side-effect BEST-EFFORT (giống {@code AddVbdiSendLogUseCase.publishDeliveryMessage}) —
 * lỗi ở bước này (tra tenant thất bại, outbox lỗi...) KHÔNG được làm hỏng hành động nghiệp vụ CHÍNH
 * (vào sổ/phân xử lý đã lưu DB xong), chỉ log cảnh báo.</p>
 *
 * <p><b>{@code targetTenantId}</b>: tra qua {@code Organization.linkedTenantId} theo
 * {@code sender_identifier} lưu sẵn trong {@code dynamic_fields} — ĐÚNG cơ chế
 * {@code AddVbdiSendLogUseCase#resolveTargetTenantId} đã dùng cho chiều GỬI, áp dụng NGƯỢC lại cho
 * chiều PHẢN HỒI này.</p>
 *
 * <p><b>Bugfix 07/09/2026 (5) — báo lỗi người dùng: publish "im lặng" (không log gì), Worker không
 * hề nhận được message nào, văn bản đi không thấy vết phản hồi</b>: quyết định trước đó ("KHÔNG
 * fallback về tenant hiện tại khi org chưa link tenant") là SAI cho trường hợp đơn vị GỬI thuộc
 * nhóm {@code internal_unit} (đơn vị nội bộ CÙNG tenant với đơn vị nhận — tổ chức TỒN TẠI trong
 * {@code organizations} nhưng KHÔNG có {@code linked_tenant_id} vì không cần, đúng
 * {@code AddVbdiSendLogUseCase#resolveTargetTenantId} đã xác nhận cho chiều GỬI: "{@code
 * internal_unit}... KHÔNG có tenant khác — placeholder tenant hiện tại là đúng"). Ở CHIỀU PHẢN HỒI
 * này, "tenant hiện tại" (nơi văn thư đang thao tác "vào sổ"/"Từ chối"/"Trả lại") CHÍNH LÀ tenant
 * của đơn vị gửi khi 2 bên nội bộ cùng tenant — publish nhầm KHÔNG xảy ra như lo ngại ban đầu, mà
 * NGƯỢC LẠI: bỏ qua fallback khiến những phản hồi này KHÔNG BAO GIỜ publish được, dù hành động
 * nghiệp vụ chính (vào sổ/từ chối/trả lại) đã lưu DB thành công. Nay fallback về
 * {@link CurrentTenantProvider#currentTenantId()} khi tổ chức TỒN TẠI nhưng không có
 * {@code linked_tenant_id} — CHỈ khi KHÔNG tìm thấy tổ chức nào khớp {@code sender_identifier} mới
 * thực sự bỏ qua (dữ liệu thiếu/sai, không có gì để suy luận thêm).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublishVbdiReplyStatusUseCase {

    private static final String SEND_LOG_ID_FIELD = "send_log_id";
    private static final String SENDER_IDENTIFIER_FIELD = "sender_identifier";
    private static final String PARENT_ID_SEND_FIELD = "parentid_send";
    private static final String CONTACT_UNIT_IDENTIFIER_FIELD = "contact_unit_identifier";
    private static final String TASK_TYPE_VBDI_REPLY = "vbdi.reply";

    private final OrganizationRepository organizationRepository;
    private final OutboxPublisherService outboxPublisherService;
    private final CurrentTenantProvider currentTenantProvider;
    private final PublishNationalStatusUseCase publishNationalStatusUseCase;

    public void execute(PublishVbdiReplyStatusRequest request) {
        try {
            Map<String, Object> fields = request.getDynamicFields();
            if (fields == null) {
                return;
            }
            // 14/10/2026: văn bản đến TỪ TRỤC QUỐC GIA (send_log_id "national:...") — phản hồi qua TD Gateway (gói
            // trạng thái eDXML), KHÔNG qua vbdi.reply nội tỉnh (đơn vị gửi ngoài tỉnh không có trong bảng tổ chức —
            // trước đây rơi vào nhánh "không tìm thấy tổ chức" và không bao giờ phản hồi).
            if (PublishNationalStatusUseCase.isNationalIncoming(fields)) {
                publishNationalStatusUseCase.execute(request);
                return;
            }
            String sendLogId = asString(fields.get(SEND_LOG_ID_FIELD));
            String senderIdentifier = asString(fields.get(SENDER_IDENTIFIER_FIELD));
            if (sendLogId == null || sendLogId.isBlank() || senderIdentifier == null || senderIdentifier.isBlank()) {
                log.debug("Bo qua phan hoi trang thai '{}' - van ban khong co send_log_id/sender_identifier "
                    + "(khong tao qua giao lien thong, binh thuong)", request.getStatusCode());
                return; // Van ban den KHONG tao qua giao lien thong - khong co gi de phan hoi (binh thuong).
            }

            var orgOptional = organizationRepository.findByIdentifierAndDeletedOnIsNull(senderIdentifier);
            if (orgOptional.isEmpty()) {
                log.warn("Khong publish duoc phan hoi trang thai '{}' cho sendLog={} - khong tim thay "
                        + "to chuc voi identifier={}",
                    request.getStatusCode(), sendLogId, senderIdentifier);
                return;
            }
            // 12/10/2026: bên gửi là đơn vị/phòng con (không khai báo linked_tenant_id) -> tenant của tổ
            // tiên gần nhất (OrganizationTenantLookup); chỉ khi cả chuỗi không khai báo mới fallback bên dưới.
            String targetTenantId = com.td.application.sharedcore.OrganizationTenantLookup
                .resolveLinkedTenantId(organizationRepository, orgOptional.get()).orElse(null);
            if (targetTenantId == null || targetTenantId.isBlank()) {
                // Bugfix 07/09/2026 (5, bao loi nguoi dung: publish "im lang" khong log gi, td-woker
                // khong nhan duoc message nao) - doi chieu lai AddVbdiSendLogUseCase#resolveTargetTenantId
                // (chieu GUI): to chuc TON TAI nhung KHONG co linkedTenantId rieng dung nghia
                // "internal_unit" (don vi noi bo CUNG tenant voi nguoi gui - khong co tenant khac de
                // tra) - PHAI fallback ve tenant HIEN TAI (chinh la tenant cua nguoi gui THAT trong
                // truong hop nay, vi don vi nhan/gui noi bo cung 1 tenant), KHONG duoc bo qua im lang
                // nhu truoc day (quyet dinh cu SAI - chi dung cho truong hop "internal_unit" o chieu
                // GUI, khong ap dung duoc y nguyen o chieu PHAN HOI vi "tenant hien tai" o day la don
                // vi NHAN, dung LA tenant can gui toi khi ca 2 cung 1 tenant).
                targetTenantId = currentTenantProvider.currentTenantId().orElse(null);
                if (targetTenantId == null || targetTenantId.isBlank()) {
                    log.warn("Khong publish duoc phan hoi trang thai '{}' cho sendLog={} - to chuc "
                            + "identifier={} khong co linked_tenant_id VA khong xac dinh duoc tenant "
                            + "hien tai de fallback",
                        request.getStatusCode(), sendLogId, senderIdentifier);
                    return;
                }
                log.debug("To chuc identifier={} khong co linked_tenant_id rieng - fallback ve tenant "
                    + "hien tai ({}) de publish phan hoi (dung nghia 'internal_unit', cung tenant voi "
                    + "nguoi gui)", senderIdentifier, targetTenantId);
            }

            var payload = VbdiReplyMessagePayload.builder()
                .sendLogId(sendLogId)
                .documentId(asString(fields.get(PARENT_ID_SEND_FIELD)))
                .statusCode(request.getStatusCode())
                .statusLabel(request.getStatusLabel())
                .statusDetails(request.getStatusDetails())
                .recipientIdentifier(asString(fields.get(CONTACT_UNIT_IDENTIFIER_FIELD)))
                .actorDisplayName(request.getActorDisplayName())
                .build();

            var unit = new MessageUnitSpec(UUID.randomUUID().toString(), targetTenantId, payload);
            outboxPublisherService.enqueue("vbdi_reply", UUID.randomUUID(),
                new OutboundMessage(TASK_TYPE_VBDI_REPLY, sendLogId, List.of(unit)));
        } catch (Exception ex) {
            log.error("Khong the enqueue outbox 'vbdi.reply' (statusCode={}) - hanh dong nghiep vu "
                + "chinh van da luu DB thanh cong, chi thieu buoc phan hoi (best-effort)",
                request.getStatusCode(), ex);
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
