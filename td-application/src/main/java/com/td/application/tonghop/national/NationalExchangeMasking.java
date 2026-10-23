package com.td.application.tonghop.national;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Áp quyền/phạm vi lên dữ liệu trả về màn Trục quốc gia (người dùng chốt 22/10/2026):
 * <ul>
 *   <li>Thiếu quyền {@link NationalExchangeAccessPolicy#AUDIT}: vẫn thấy trạng thái, mã lỗi, nhóm lỗi; ẩn mô tả lỗi
 *       chi tiết (thông điệp SDK/stack), dòng "lần thao tác Trục", người thao tác quản trị, dynamic fields kỹ thuật.</li>
 *   <li>Phạm vi đơn vị: gói đến chia nhiều tenant nhận chỉ giữ nơi nhận + sự kiện của tenant mình.</li>
 * </ul>
 */
final class NationalExchangeMasking {

    private NationalExchangeMasking() {
    }

    static void maskItem(NationalExchangeItem item) {
        item.setErrorDetail(null);
    }

    static void maskDetail(NationalExchangeDetail detail) {
        if (detail.getExchange() != null) {
            maskItem(detail.getExchange());
        }
        detail.setSuccessDetail(null);
        detail.setDynamicFields(Map.of());
        detail.setAttempts(List.of());
        detail.getRecipients().forEach(r -> r.setErrorDetail(null));
        detail.getEvents().forEach(e -> {
            e.setDetails(null);
            e.setActorName(null);
        });
        detail.getOperations().forEach(o -> {
            o.setRequestedBy(null);
            o.setRequestedByName(null);
            o.setErrorDetail(null);
        });
    }

    /** Gói có thuộc tenant không: tenant gửi (master) hoặc 1 trong các tenant nhận. */
    static boolean belongsTo(NationalExchangeDetail detail, String tenantId) {
        if (tenantId == null) {
            return false;
        }
        if (detail.getExchange() != null && tenantId.equals(detail.getExchange().getTenantId())) {
            return true;
        }
        return detail.getRecipients().stream().anyMatch(r -> tenantId.equals(r.getTenantId()));
    }

    /**
     * Gói đến bàn giao cho nhiều tenant: tenant đơn vị chỉ thấy phần của mình. Nơi nhận ngoài tỉnh (tenant rỗng —
     * chiều đi) giữ nguyên vì cả gói thuộc tenant gửi.
     */
    static void restrictToTenant(NationalExchangeDetail detail, String tenantId) {
        detail.setRecipients(detail.getRecipients().stream()
            .filter(r -> r.getTenantId() == null || tenantId.equals(r.getTenantId()))
            .toList());
        detail.setEvents(detail.getEvents().stream()
            .filter(e -> e.getAgencyCode() == null || e.getTenantId() == null || Objects.equals(tenantId, e.getTenantId()))
            .toList());
        NationalExchangeItem exchange = detail.getExchange();
        if (exchange != null && NationalExchangeStates.DIRECTION_INBOUND.equals(exchange.getDirection())) {
            // Danh sách mã nơi nhận/số đếm của cả gói lộ đơn vị khác → thay bằng phần của tenant mình.
            exchange.setRecipientCodes(String.join(";", detail.getRecipients().stream()
                .map(NationalExchangeDetail.Recipient::getAgencyCode).toList()));
            exchange.setRecipientCount(detail.getRecipients().size());
            exchange.setAcknowledgedCount((int) detail.getRecipients().stream()
                .filter(r -> "RECEIVED".equals(r.getDeliveryStatus()) || "SENT".equals(r.getDeliveryStatus())).count());
        }
    }
}
