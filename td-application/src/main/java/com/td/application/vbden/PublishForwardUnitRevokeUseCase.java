package com.td.application.vbden;

import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import com.td.application.messaging.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bổ sung 08/09/2026 — giai đoạn 2 của {@code vbden.forward_unit} (yêu cầu người dùng: "giai đoạn 1
 * cơ bản đã xong, làm giai đoạn 2 LayLaiVanBanUseCase/ThuHoiVanBanUseCase"). Dùng CHUNG bởi cả
 * {@link LayLaiVanBanUseCase} và {@link ThuHoiVanBanUseCase} (2 nút "Lấy lại VB"/"Thu hồi" nội bộ của
 * vbden — KHÁC "Lấy lại VB" của {@code vbdi}, đã port riêng ở {@code VbdiRevokeTaskHandler}) — cascade
 * revoke sang các bản sao đã "chuyển xử lý" cho đơn vị khác tenant ({@code forwarded_units}, ghi bởi
 * {@code VbdenForwardUnitReplyTaskHandler} khi tenant đích xác nhận đã tạo bản sao).
 *
 * <p><b>Phạm vi CÓ CHỦ ĐÍCH (đơn giản hoá, không suy đoán thêm)</b>: cascade cho TẤT CẢ entry
 * {@code forwarded_units} CHƯA {@code revoked}, KHÔNG lọc thêm theo "ai đã forward"/"có cùng nhánh tổ
 * chức với người thao tác" hay không — vì {@code ChuyenXuLyVbdenUseCase#createProcessingRecord} CHƯA
 * BAO GIỜ ghi {@code unit_process} lên bản ghi {@code vbden_res} lịch sử (chỉ ghi username-level qua 3
 * field {@code _obj}), nên KHÔNG có dữ liệu để truy ngược "lần chuyển xử lý cross-tenant nào do CHÍNH
 * người đang thao tác 'Lấy lại'/'Thu hồi' tạo ra" — khác hẳn cách 2 use case đó lọc revoke USERNAME
 * (dựa vào snapshot {@code _obj} sẵn có). Cả 2 hành động ("Lấy lại VB"/"Thu hồi") đều là hành động có
 * thẩm quyền cao (chỉ người trong {@code author}/người từng giao mới thấy nút) nên cascade revoke
 * TOÀN BỘ forward CÒN HIỆU LỰC là lựa chọn AN TOÀN — tránh để lại bản sao "mồ côi" vẫn tiếp tục được
 * xử lý ở tenant khác sau khi người tạo/điều phối văn bản đã chủ động lấy lại/thu hồi tại tenant gốc.
 * Cùng tinh thần đơn giản hoá đã áp dụng ở {@code VbdiRevokeTaskHandler} (revoke TẤT CẢ bản ghi khớp
 * {@code parentid_send}, không lọc thêm theo người thao tác).</p>
 *
 * <p><b>Idempotent</b>: mỗi entry {@code forwarded_units} được đánh dấu {@code revoked: true} SAU KHI
 * publish thành công — gọi lại (vd "Thu hồi" nhiều lần) sẽ BỎ QUA các entry đã revoked, không publish
 * trùng. Việc publish 1 entry cụ thể lỗi (vd outbox tạm thời lỗi) → giữ NGUYÊN {@code revoked=false}
 * cho entry đó (không đánh dấu nhầm "đã xong"), best-effort — KHÔNG được làm hỏng giao dịch "Lấy lại
 * VB"/"Thu hồi" chính đang chạy.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublishForwardUnitRevokeUseCase {

    private static final String FORWARDED_UNITS_FIELD = "forwarded_units";
    static final String TASK_TYPE = "vbden.forward_unit.revoke";

    private final OutboxPublisherService outboxPublisherService;

    /**
     * Đọc {@code fields.get("forwarded_units")}, publish {@code vbden.forward_unit.revoke} cho MỖI
     * entry CHƯA {@code revoked}, MUTATE {@code fields} IN PLACE (ghi lại {@code forwarded_units} với
     * cờ {@code revoked} cập nhật — caller PHẢI gọi hàm này TRƯỚC khi
     * {@code parent.replaceDynamicFields(...)}/lưu văn bản, để cờ được lưu CÙNG 1 giao dịch với thay
     * đổi routing chính, không cần lưu thêm lần 2).
     *
     * @return số entry vừa được cascade thành công (0 nếu không có {@code forwarded_units} nào, hoặc
     *         tất cả đã {@code revoked} từ trước).
     */
    @SuppressWarnings("unchecked")
    public int cascadeRevoke(
            Map<String, Object> fields, UUID parentDocumentId, String actingUsername, String opinion, String reason) {
        Object raw = fields.get(FORWARDED_UNITS_FIELD);
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            return 0;
        }
        List<Map<String, Object>> updated = new ArrayList<>();
        int cascaded = 0;
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawEntry)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>((Map<String, Object>) rawEntry);
            if (!Boolean.TRUE.equals(entry.get("revoked"))) {
                boolean published = tryPublish(entry, parentDocumentId, actingUsername, opinion, reason);
                if (published) {
                    entry.put("revoked", true);
                    cascaded++;
                }
            }
            updated.add(entry);
        }
        fields.put(FORWARDED_UNITS_FIELD, updated);
        return cascaded;
    }

    private boolean tryPublish(
            Map<String, Object> entry, UUID parentDocumentId, String actingUsername, String opinion, String reason) {
        String tenantId = asString(entry.get("tenantId"));
        String targetDocumentId = asString(entry.get("documentId"));
        if (tenantId == null || tenantId.isBlank() || targetDocumentId == null || targetDocumentId.isBlank()) {
            log.warn("Bo qua 1 entry forwarded_units thieu tenantId/documentId cho van ban {}: {}",
                parentDocumentId, entry);
            return false;
        }
        try {
            var payload = VbdenForwardUnitRevokeMessagePayload.builder()
                .targetDocumentId(targetDocumentId)
                .originDocumentId(parentDocumentId.toString())
                .actingUsername(actingUsername)
                .opinion(opinion)
                .reason(reason)
                .build();
            var unit = new MessageUnitSpec(UUID.randomUUID().toString(), tenantId, payload);
            outboxPublisherService.enqueue("vbden", parentDocumentId,
                new OutboundMessage(TASK_TYPE, parentDocumentId.toString(), List.of(unit)));
            return true;
        } catch (Exception ex) {
            log.warn("Khong the publish vbden.forward_unit.revoke cho van ban {} - don vi tenant {}: {}",
                parentDocumentId, tenantId, ex.getMessage());
            return false;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
