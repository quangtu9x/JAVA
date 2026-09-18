package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import com.td.domain.hosoluutru.HoSoLuuTruPhieuYeuCauDocument;
import com.td.domain.hosoluutru.HoSoLuuTruProcessDocument;
import com.td.domain.hosoluutru.HoSoLuuTruVanBanDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — xem chi tiết 1 hồ sơ lưu trữ, tương thích
 * {@code qlvb/get?databasetype=hosoluutru&items=getall}, đúng khuôn
 * {@code GetHoSoChiTietUseCase} (hosocv): cộng thêm {@code van_ban_lien_quan} (đọc
 * {@link HoSoLuuTruVanBanDocumentRepository#findByHosoId}), {@code phieu_yeu_cau} (đọc
 * {@link HoSoLuuTruPhieuYeuCauDocumentRepository#findByHosoId}), {@code lich_su_ltls} (đọc
 * {@link HoSoLuuTruProcessDocumentRepository#findByHosoId} — vết LTLS nội bộ) và
 * {@code lich_su_chuyen_luu_tru} (đọc {@code hosocv_process_documents} qua
 * {@code document_profiles_id}, cùng nguồn log tiếp nhận/từ chối — xem tài liệu 16 mục 8.1).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetHoSoLuuTruChiTietUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ lưu trữ";

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;
    private final HoSoLuuTruPhieuYeuCauDocumentRepository hoSoLuuTruPhieuYeuCauDocumentRepository;
    private final HoSoLuuTruProcessDocumentRepository hoSoLuuTruProcessDocumentRepository;
    private final com.td.application.hosocv.HoSoCVProcessDocumentRepository hoSoCVProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<Map<String, Object>> execute(GetHoSoLuuTruChiTietRequest request) {
        UUID id = parseUuid(request == null ? null : request.getId());
        if (id == null) {
            return Result.failure("Thiếu id hồ sơ lưu trữ cần xem");
        }

        HoSoLuuTruDocument document = hoSoLuuTruDocumentRepository.findById(id).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("form", document.getStatus());
        allFields.put("created_by", document.getCreatedBy());
        allFields.put("last_modified_by", document.getLastModifiedBy());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        allFields.putAll(dynamicFields);

        allFields.put("van_ban_lien_quan", collectVanBan(document.getId()));
        allFields.put("phieu_yeu_cau", collectPhieuYeuCau(document.getId()));
        allFields.put("lich_su_ltls", collectLtls(document.getId()));
        allFields.put("lich_su_chuyen_luu_tru", collectChuyenLuuTruHistory(dynamicFields));

        return Result.success(allFields);
    }

    private List<Map<String, Object>> collectVanBan(UUID hosoId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (HoSoLuuTruVanBanDocument vanBan : hoSoLuuTruVanBanDocumentRepository.findByHosoId(hosoId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", vanBan.getId());
            item.put("title", vanBan.getTitle());
            item.putAll(dynamicDocumentFieldsService.parseDynamicFields(vanBan.getDynamicFieldsJson()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> collectPhieuYeuCau(UUID hosoId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (HoSoLuuTruPhieuYeuCauDocument phieu : hoSoLuuTruPhieuYeuCauDocumentRepository.findByHosoId(hosoId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", phieu.getId());
            item.put("title", phieu.getTitle());
            item.putAll(dynamicDocumentFieldsService.parseDynamicFields(phieu.getDynamicFieldsJson()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> collectLtls(UUID hosoId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (HoSoLuuTruProcessDocument process : hoSoLuuTruProcessDocumentRepository.findByHosoId(hosoId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", process.getId());
            item.put("title", process.getTitle());
            item.put("created_on", process.getCreatedOn());
            item.putAll(dynamicDocumentFieldsService.parseDynamicFields(process.getDynamicFieldsJson()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> collectChuyenLuuTruHistory(Map<String, Object> hosoFields) {
        List<Map<String, Object>> result = new ArrayList<>();
        UUID hosocvId = parseUuid(stringField(hosoFields, HoSoLuuTruLifecycleStatus.FIELD_DOCUMENT_PROFILES_ID));
        if (hosocvId == null) {
            return result;
        }
        for (var process : hoSoCVProcessDocumentRepository.findByHoSoCVId(hosocvId)) {
            Map<String, Object> processFields = dynamicDocumentFieldsService.parseDynamicFields(process.getDynamicFieldsJson());
            if (!"chuyenluutru".equals(stringField(processFields, "process_type"))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", process.getId());
            item.put("title", process.getTitle());
            item.put("created_on", process.getCreatedOn());
            item.putAll(processFields);
            result.add(item);
        }
        return result;
    }

    private String stringField(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? "" : value.toString();
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
