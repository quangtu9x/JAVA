package com.td.application.hosoluutru;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — "Chuyển lưu trữ lịch sử" (LTLS) chờ duyệt:
 * {@code chuyenluutrutinh: "" → "2"}, xem {@code docs/analysis/16-...md} mục 2.4/7 #2. Guard: hồ sơ
 * phải đã {@code chuyenluutru="1"} (đã lưu trữ chính thức nội bộ) và {@code storage_period} phải
 * {@code "Vĩnh viễn"} (đúng {@code ButtonActions.jsx:350-387}).
 *
 * <p><b>Chỉ port chiều này</b> — duyệt/từ chối LTLS ({@code "1"}/{@code "3"}) KHÔNG có bằng chứng FE/BE
 * (xem tài liệu 16 mục 7 #2), không port.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChuyenLuuTruLichSuUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ lưu trữ";
    private static final String GUARD_MESSAGE =
        "Hồ sơ chưa đủ điều kiện chuyển lưu trữ lịch sử (phải đã lưu trữ chính thức, thời hạn bảo quản Vĩnh viễn)";
    private static final String STORAGE_PERIOD_VINH_VIEN = "vĩnh viễn";
    private static final String FALLBACK_USERNAME = "system";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AddHoSoLuuTruProcessDocumentUseCase addHoSoLuuTruProcessDocumentUseCase;

    public Result<UUID> execute(HoSoLuuTruLifecycleActionRequest request) {
        if (request == null || request.getHosoLuuTruId() == null) {
            return Result.failure("Thiếu id hồ sơ lưu trữ");
        }
        var documentOptional = hoSoLuuTruDocumentRepository.findById(request.getHosoLuuTruId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        boolean isChuyenLuuTru = HoSoLuuTruLifecycleStatus.CHUYENLUUTRU_DA.equals(
            stringField(fields, HoSoLuuTruLifecycleStatus.FIELD_CHUYENLUUTRU));
        boolean isVinhVien = STORAGE_PERIOD_VINH_VIEN.equalsIgnoreCase(
            stringField(fields, "storage_period").trim());
        if (!isChuyenLuuTru || !isVinhVien) {
            return Result.failure(GUARD_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_CHUYENLUUTRUTINH, HoSoLuuTruLifecycleStatus.CHUYENLUUTRUTINH_CHO_DUYET);
        fields.put("chuyenluutrutinh2_date", DATE_FORMATTER.format(LocalDate.now()));
        document.replaceDynamicFields(toJson(fields));
        var saved = hoSoLuuTruDocumentRepository.save(document);

        appendProcessRecord(saved.getId(), HoSoLuuTruLifecycleStatus.CHUYENLUUTRUTINH_CHO_DUYET, actingUsername, request.getOpinion());

        return Result.success(saved.getId());
    }

    private void appendProcessRecord(UUID hosoId, String actionType, String actingUsername, String opinion) {
        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle("Chuyển lưu trữ lịch sử");
            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put(HoSoLuuTruLifecycleStatus.FIELD_HOSO_ID, hosoId.toString());
            processFields.put("process_type", "chuyenltls");
            processFields.put("action_type", actionType);
            processFields.put("creator", actingUsername);
            processFields.put(HoSoLuuTruLifecycleStatus.FIELD_USER_CREATOR, actingUsername);
            if (opinion != null && !opinion.isBlank()) {
                processFields.put(HoSoLuuTruLifecycleStatus.FIELD_SUBJECT, opinion);
            }
            processRequest.setFields(processFields);
            addHoSoLuuTruProcessDocumentUseCase.execute(processRequest);
        } catch (Exception ignored) {
            // Vet xu ly la audit phu - loi ghi vet KHONG duoc lam hong thao tac chinh.
        }
    }

    private String stringField(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? "" : value.toString();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
