package com.td.infrastructure.tonghop;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.application.tonghop.ProvinceDates;
import com.td.application.tonghop.ProvinceDocumentStates;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chiếu 1 văn bản (cột + {@code dynamic_fields}) của DB tenant thành 1 dòng {@code province_documents}.
 * Quy tắc đối chiếu trên dữ liệu thật (h05_01_sokhcn, 12/10/2026) — xem docs/analysis/26.
 */
final class ProvinceDocumentProjector {

    /** Field động cần đọc — truy vấn chỉ lấy đúng các key này (tránh kéo log/files nặng). */
    static final String[] VBDEN_KEYS = {
        "identifier", "identifier_creator", "unit_process", "contact_unit_identifier", "internal",
        "document_internet", "method", "delivery_method", "is_revoke", "sender_identifier", "unit_in_charge",
        "unit_in_charge_identifier", "code", "document_number", "document_type", "signer", "priority",
        "received_date", "promulgation_date", "deadline_date", "send_log_id", "subject"};

    static final String[] VBDI_KEYS = {
        "unit_publish_identifier", "unit_publish", "code_full", "code", "document_number", "document_type",
        "signer", "priority", "promulgation_date", "deadline_date", "send_agent", "delivery_method", "dakyso",
        "dakycoquan"};

    private static final Set<String> VBDI_ISSUED_STATUSES = Set.of("ISSUED", "2");

    private ProvinceDocumentProjector() {
    }

    static ProvinceDocumentRow vbden(String tenantId, UUID id, String title, String status, JsonNode f,
                                     OffsetDateTime createdOn, OffsetDateTime modifiedOn, boolean deleted,
                                     Map<String, String> orgNames, LocalDate today) {
        ProvinceDocumentRow row = base(tenantId, id, ProvinceDocumentStates.KIND_VBDEN, createdOn, modifiedOn, deleted);
        boolean internal = ProvinceText.isFlag(f, "internal");
        String registered = ProvinceText.text(f, "identifier");
        String owner = firstNonBlank(
            registered,
            internal ? ProvinceText.text(f, "unit_process") : null,
            ProvinceText.text(f, "contact_unit_identifier"),
            ProvinceText.text(f, "identifier_creator"));
        row.setOwnerIdentifier(owner);
        row.setOwnerName(owner == null ? null : orgNames.get(owner));
        row.setCode(firstNonBlank(ProvinceText.text(f, "code"), ProvinceText.text(f, "document_number")));
        row.setTitle(firstNonBlank(title, ProvinceText.text(f, "subject")));
        row.setDocumentTypeName(ProvinceText.text(f, "document_type"));
        String agencyIdentifier = firstNonBlank(ProvinceText.text(f, "sender_identifier"),
            ProvinceText.text(f, "unit_in_charge_identifier"));
        row.setAgencyIdentifier(agencyIdentifier);
        row.setAgencyName(firstNonBlank(ProvinceText.text(f, "unit_in_charge"),
            agencyIdentifier == null ? null : orgNames.get(agencyIdentifier)));
        row.setSigner(ProvinceText.text(f, "signer"));
        row.setPriority(ProvinceText.text(f, "priority"));
        LocalDate received = ProvinceDates.parse(ProvinceText.text(f, "received_date"));
        row.setDocDate(received != null ? received : createdOn == null ? null : createdOn.toLocalDate());
        row.setPromulgationDate(ProvinceDates.parse(ProvinceText.text(f, "promulgation_date")));
        row.setDeadlineDate(ProvinceDates.parse(ProvinceText.text(f, "deadline_date")));
        row.setStatusCode(status);
        row.setProcessState(vbdenProcessState(status, registered));
        row.setOverdue(isVbdenOverdue(status, row.getProcessState(), row.getDeadlineDate(), today));

        String internet = ProvinceText.text(f, "document_internet");
        row.setInternal(internal);
        row.setNational(!internal && "2".equals(internet));
        row.setNetwork(!internal && "1".equals(internet));
        String method = ProvinceText.normalize(firstNonBlank(ProvinceText.text(f, "method"), ProvinceText.text(f, "delivery_method")));
        row.setPaper(!row.isInternal() && !row.isNetwork() && !row.isNational() && method.contains("giay"));
        row.setRevoked(ProvinceText.isFlag(f, "is_revoke"));
        row.setSourceSendLogId(ProvinceText.text(f, "send_log_id"));
        row.setSearchText(ProvinceText.joinNormalized(row.getCode(), row.getTitle(), row.getAgencyName(), row.getSigner(),
            row.getOwnerName(), row.getDocumentTypeName()));
        return row;
    }

    static ProvinceDocumentRow vbdi(String tenantId, UUID id, String title, String status, JsonNode f,
                                    OffsetDateTime createdOn, OffsetDateTime modifiedOn, boolean deleted,
                                    Map<String, String> orgNames) {
        // Chỉ văn bản ĐÃ BAN HÀNH mới thuộc phạm vi toàn tỉnh; dự thảo/bị trả lại coi như không tồn tại.
        boolean issued = status != null && VBDI_ISSUED_STATUSES.contains(status);
        ProvinceDocumentRow row = base(tenantId, id, ProvinceDocumentStates.KIND_VBDI, createdOn, modifiedOn,
            deleted || !issued);
        String owner = ProvinceText.text(f, "unit_publish_identifier");
        row.setOwnerIdentifier(owner);
        String ownerName = firstNonBlank(owner == null ? null : orgNames.get(owner), ProvinceText.text(f, "unit_publish"));
        row.setOwnerName(ownerName);
        row.setCode(firstNonBlank(ProvinceText.text(f, "code_full"), ProvinceText.text(f, "code"),
            ProvinceText.text(f, "document_number")));
        row.setTitle(title);
        row.setDocumentTypeName(ProvinceText.text(f, "document_type"));
        row.setAgencyIdentifier(owner);
        row.setAgencyName(ownerName);
        row.setSigner(ProvinceText.text(f, "signer"));
        row.setPriority(ProvinceText.text(f, "priority"));
        LocalDate promulgation = ProvinceDates.parse(ProvinceText.text(f, "promulgation_date"));
        row.setPromulgationDate(promulgation);
        row.setDocDate(promulgation != null ? promulgation : createdOn == null ? null : createdOn.toLocalDate());
        row.setDeadlineDate(ProvinceDates.parse(ProvinceText.text(f, "deadline_date")));
        row.setStatusCode(status);
        row.setProcessState(ProvinceText.isFlag(f, "send_agent") ? ProvinceDocumentStates.DA_GUI : ProvinceDocumentStates.CHUA_GUI);
        row.setSigned(ProvinceText.isFlag(f, "dakyso") || ProvinceText.isFlag(f, "dakycoquan"));
        row.setPaper(ProvinceText.normalize(ProvinceText.text(f, "delivery_method")).contains("giay"));
        // is_internal/is_network/is_national của vbdi suy từ nơi nhận thật — tính lại sau khi đồng bộ lượt gửi.
        row.setSearchText(ProvinceText.joinNormalized(row.getCode(), row.getTitle(), row.getAgencyName(), row.getSigner(),
            row.getDocumentTypeName()));
        return row;
    }

    static String vbdenProcessState(String status, String registeredIdentifier) {
        if (status != null) {
            switch (status) {
                case "5":
                    return ProvinceDocumentStates.TU_CHOI;
                case "3", "4":
                    return ProvinceDocumentStates.DA_XU_LY;
                case "1", "2":
                    return ProvinceDocumentStates.DANG_XU_LY;
                default:
                    break;
            }
        }
        return registeredIdentifier == null ? ProvinceDocumentStates.CHO_VAO_SO : ProvinceDocumentStates.CHUA_XU_LY;
    }

    static boolean isVbdenOverdue(String status, String processState, LocalDate deadline, LocalDate today) {
        if ("1".equals(status) || "3".equals(status)) {
            return true;
        }
        if (deadline == null || ProvinceDocumentStates.DA_XU_LY.equals(processState)
            || ProvinceDocumentStates.TU_CHOI.equals(processState)) {
            return false;
        }
        return deadline.isBefore(today);
    }

    private static ProvinceDocumentRow base(String tenantId, UUID id, String kind, OffsetDateTime createdOn,
                                            OffsetDateTime modifiedOn, boolean deleted) {
        ProvinceDocumentRow row = new ProvinceDocumentRow();
        row.setTenantId(tenantId);
        row.setDocumentId(id);
        row.setDocKind(kind);
        row.setCreatedOn(createdOn);
        row.setSourceModifiedOn(modifiedOn);
        row.setDeleted(deleted);
        return row;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
