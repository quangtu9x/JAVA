package com.td.application.hosoluutru;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosoluutru.HoSoLuuTruVanBanDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — thêm/sửa thủ công 1 văn bản gắn vào hồ sơ lưu trữ (đúng
 * {@code VanBanModal.jsx}, xem {@code docs/analysis/16-...md} mục 6.2). Khác
 * {@link TaoHoSoLuuTruTuHoSoCVUseCase#copyVanBanTuVbden}/{@code copyVanBanTuVbdi} (copy tự động khi
 * chuyển lưu trữ) — đây là nhập tay, không liên kết {@code document_in_id}/{@code document_out_id}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ThemVanBanLuuTruUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(ThemVanBanLuuTruRequest request) {
        if (request == null || request.getHosoId() == null) {
            return Result.failure("Thiếu id hồ sơ lưu trữ");
        }
        Map<String, String> fields = request.getFields() == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getFields());
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_HOSO_ID, request.getHosoId().toString());
        String title = fields.getOrDefault("subject", "Văn bản liên quan");

        if (request.getId() == null) {
            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                fields, request.getDateFields(), request.getNumberFields(), request.getRichtextFields());
            var vanBan = new HoSoLuuTruVanBanDocument(
                title, TaoHoSoLuuTruTuHoSoCVUseCase.VAN_BAN_DOCUMENT_TYPE, null, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);
            var saved = hoSoLuuTruVanBanDocumentRepository.save(vanBan);
            return Result.success(saved.getId());
        }

        HoSoLuuTruVanBanDocument document = hoSoLuuTruVanBanDocumentRepository.findById(request.getId()).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        Map<String, Object> existing = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        Map<String, Object> incoming = dynamicDocumentFieldsService.parseDynamicFields(
            dynamicDocumentFieldsService.buildDynamicFieldsJson(
                fields, request.getDateFields(), request.getNumberFields(), request.getRichtextFields()));
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(incoming);
        document.replaceDynamicFields(toJson(merged));
        if (!title.isBlank()) {
            document.update(title, null, null, null, null, null, null);
        }
        var saved = hoSoLuuTruVanBanDocumentRepository.save(document);
        return Result.success(saved.getId());
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
