package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.hosocv.AddHoSoCVProcessDocumentUseCase;
import com.td.application.hosocv.HoSoCVDocumentRepository;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — thay {@code XuLyHoSoLuuTru.XuLyTraLaiHoSo()}, xem
 * {@code docs/analysis/16-...md} mục 5.3/5.4/5.6/8.2. Bước 2/3 (nhánh từ chối) của cơ chế đồng bộ: kho
 * lưu trữ từ chối tiếp nhận — xoá mềm bản ghi {@code hosoluutru} (đệ quy văn bản con, đúng hành vi
 * {@code Xoa()} thật) + ghi vết + cập nhật ngược {@code storaged="3"} trên {@code hosocv} gốc.
 *
 * <p>Tuân thủ {@code docs/architecture/04-quy-tac-crud-api-shared-core.md}: {@code deletedBy} lấy từ
 * {@link CurrentUserProvider} + {@link AppUserRepository} (KHÔNG {@code UUID.randomUUID()}).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TraLaiHoSoLuuTruUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ lưu trữ";
    private static final String GUARD_MESSAGE = "Hồ sơ không ở trạng thái chờ tiếp nhận";
    private static final String STORAGED_TRA_LAI = "3";
    private static final String FALLBACK_USERNAME = "system";

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;
    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final AddHoSoCVProcessDocumentUseCase addHoSoCVProcessDocumentUseCase;

    public Result<UUID> execute(TiepNhanHoSoLuuTruRequest request) {
        if (request == null || request.getHosoLuuTruId() == null) {
            return Result.failure("Thiếu id hồ sơ lưu trữ");
        }
        var documentOptional = hoSoLuuTruDocumentRepository.findById(request.getHosoLuuTruId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        if (!HoSoLuuTruLifecycleStatus.FORM_CHO_TIEP_NHAN.equals(document.getStatus())) {
            return Result.failure(GUARD_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        UUID deletedBy = resolveCurrentUserId();

        // Xoa de quy van ban con - dung hanh vi that Xoa() (tai lieu 16 muc 5.4).
        for (var vanBan : hoSoLuuTruVanBanDocumentRepository.findByHosoId(document.getId())) {
            vanBan.markAsDeleted(deletedBy);
            hoSoLuuTruVanBanDocumentRepository.save(vanBan);
        }

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        document.markAsDeleted(deletedBy);
        var saved = hoSoLuuTruDocumentRepository.save(document);

        String hosocvIdStr = stringField(fields, HoSoLuuTruLifecycleStatus.FIELD_DOCUMENT_PROFILES_ID);
        appendProcessRecordAndSyncHoSoCV(hosocvIdStr, STORAGED_TRA_LAI, "3", actingUsername, request.getOpinion());

        return Result.success(saved.getId());
    }

    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }

    // Trung lap co chu y voi TiepNhanHoSoLuuTruUseCase (khac gia tri storaged/action) - dung khuon
    // duthao/vbdi da dung (chap nhan trung lap nho de moi use case doc lap, khong ai anh huong ai).
    private void appendProcessRecordAndSyncHoSoCV(
            String hosocvIdStr, String storagedValue, String actionType, String actingUsername, String opinion) {
        if (hosocvIdStr == null || hosocvIdStr.isBlank()) {
            return;
        }
        try {
            UUID hosocvId = UUID.fromString(hosocvIdStr.trim());
            hoSoCVDocumentRepository.findById(hosocvId).ifPresent(hosocv -> {
                Map<String, Object> hosocvFields = new LinkedHashMap<>(
                    dynamicDocumentFieldsService.parseDynamicFields(hosocv.getDynamicFieldsJson()));
                hosocvFields.put("storaged", storagedValue);
                hosocvFields.put("storaged" + actionType + "_date",
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(java.time.LocalDate.now()));
                hosocv.replaceDynamicFields(toJson(hosocvFields));
                hoSoCVDocumentRepository.save(hosocv);
            });
        } catch (IllegalArgumentException ex) {
            // document_profiles_id khong hop le - bo qua dong bo nguoc, khong lam hong thao tac chinh.
        }

        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle("Chuyển lưu trữ hồ sơ công việc");
            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put("hosocv_id", hosocvIdStr);
            processFields.put("process_type", "chuyenluutru");
            processFields.put("action_type", actionType);
            processFields.put("new_value", storagedValue);
            processFields.put("creator", actingUsername);
            if (opinion != null && !opinion.isBlank()) {
                processFields.put("subject", opinion);
            }
            processRequest.setFields(processFields);
            addHoSoCVProcessDocumentUseCase.execute(processRequest);
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
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
