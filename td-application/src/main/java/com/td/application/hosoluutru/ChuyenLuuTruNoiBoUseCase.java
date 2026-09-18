package com.td.application.hosoluutru;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
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
 * Port module "Hồ sơ lưu trữ" — nút "Chuyển lưu trữ" NỘI BỘ ({@code ChiTietPage.jsx#ChuyenLuuTru()},
 * dòng 492-514, xem {@code docs/analysis/16-...md} mục 2.4): {@code chuyenluutru: "0" → "1"} +
 * {@code chuyenluutru_date}. KHÁC {@code hosocv.ChuyenLuuTruHoSoUseCase} (tên trùng, ý nghĩa khác —
 * đây là bước "Chờ chuyển lưu trữ" → "Đã lưu trữ chính thức" nội bộ {@code hosoluutru}, không đụng gì
 * tới {@code hosocv}).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChuyenLuuTruNoiBoUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ lưu trữ";
    private static final String GUARD_MESSAGE = "Hồ sơ đã được chuyển lưu trữ chính thức";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

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
        String current = stringField(fields, HoSoLuuTruLifecycleStatus.FIELD_CHUYENLUUTRU);
        if (HoSoLuuTruLifecycleStatus.CHUYENLUUTRU_DA.equals(current)) {
            return Result.failure(GUARD_MESSAGE);
        }

        fields.put(HoSoLuuTruLifecycleStatus.FIELD_CHUYENLUUTRU, HoSoLuuTruLifecycleStatus.CHUYENLUUTRU_DA);
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_CHUYENLUUTRU_DATE, DATE_FORMATTER.format(LocalDate.now()));
        document.replaceDynamicFields(toJson(fields));
        var saved = hoSoLuuTruDocumentRepository.save(document);

        return Result.success(saved.getId());
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
