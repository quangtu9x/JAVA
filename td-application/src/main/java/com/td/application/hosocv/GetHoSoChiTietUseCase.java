package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.duthao.DuThaoDocumentRepository;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.hosocv.HoSoCVDocument;
import com.td.domain.hosocv.HoSoCVProcessDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G1 ({@code docs/analysis/08-...md} mục 7.2 G1;
 * {@code docs/analysis/13-...md} Phần 2.3): xem chi tiết 1 hồ sơ công việc — tương thích API
 * legacy {@code qlvb/get?databasetype=hosocv&items=getall} (đúng khuôn
 * {@code GetDuThaoDetailUseCase}), CỘNG THÊM 2 phần response con FE thật cần
 * ({@code ChiTietPage.jsx}):
 * <ul>
 *   <li>{@code van_ban_lien_quan}: danh sách văn bản đã gắn (đọc field
 *   {@code duthao_attached_id}/{@code document_out_attached_id}/{@code document_in_attached_id}/
 *   {@code submission_dossier_attached_id} — ghi bởi {@link GanKemVanBanUseCase} — dispatch đúng
 *   repository theo loại văn bản, giống {@code ACTION_DOCUMENT_TYPE_MAP} ở đó, tránh lặp lại bug
 *   tra sai bảng).</li>
 *   <li>{@code lich_su_xu_ly}: toàn bộ vết {@code hosocv_process_documents}
 *   ({@link HoSoCVProcessDocumentRepository#findByHoSoCVId}) — tương đương {@code quatrinhduyet}/
 *   {@code storag_log}/ý kiến phối hợp gộp 1 danh sách, FE tự nhóm theo {@code process_type} nếu
 *   cần (khác Domino tách 3 view riêng — đơn giản hoá có chủ ý, tránh 3 lời gọi API cho 1 tab).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetHoSoChiTietUseCase {

    public static final String DOCUMENT_TYPE = TaoHoSoCVUseCase.DOCUMENT_TYPE;
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ công việc";
    private static final String TYPE_DUTHAO = "duthao";
    private static final String TYPE_VBDI = "vbdi";

    // field tren hoso (ghi boi GanKemVanBanUseCase) -> loai van ban - dung de dispatch dung repository.
    private static final Map<String, String> ATTACHED_FIELD_TYPE_MAP = Map.of(
        "duthao_attached_id", TYPE_DUTHAO,
        "document_out_attached_id", TYPE_VBDI,
        "document_in_attached_id", "vbden",
        "submission_dossier_attached_id", "phieutrinh"
    );

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final HoSoCVProcessDocumentRepository hoSoCVProcessDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    // 02/10/2026: van_ban_lien_quan gom thêm "văn bản khác" của hồ sơ - xem LuuVanBanKhacUseCase.
    private final HoSoVanBanKhacFinder hoSoVanBanKhacFinder;

    public Result<Map<String, Object>> execute(GetHoSoChiTietRequest request) {
        UUID id = parseUuid(request == null ? null : request.getId());
        if (id == null) {
            return Result.failure("Thiếu id hồ sơ công việc cần xem");
        }

        HoSoCVDocument document = hoSoCVDocumentRepository.findById(id).orElse(null);
        if (document == null || document.isDeleted() || !DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("created_by", document.getCreatedBy());
        allFields.put("last_modified_by", document.getLastModifiedBy());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        allFields.putAll(dynamicFields);

        List<Map<String, Object>> vanBanLienQuan = collectLinkedVanBan(dynamicFields);
        hoSoVanBanKhacFinder.findByHoSo(document.getId()).forEach(vanBanKhac -> vanBanLienQuan.add(
            vanBanSummary(vanBanKhac.getDocumentType(), vanBanKhac.getId(), vanBanKhac.getTitle(), vanBanKhac.getStatus())));
        allFields.put("van_ban_lien_quan", vanBanLienQuan);
        allFields.put("lich_su_xu_ly", collectProcessHistory(document.getId()));

        return Result.success(allFields);
    }

    private List<Map<String, Object>> collectLinkedVanBan(Map<String, Object> hosoFields) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : ATTACHED_FIELD_TYPE_MAP.entrySet()) {
            String fieldName = entry.getKey();
            String vanBanType = entry.getValue();
            for (String idStr : toSemicolonList(hosoFields.get(fieldName))) {
                UUID vanBanId = parseUuid(idStr);
                if (vanBanId == null) {
                    continue;
                }
                resolveVanBanSummary(vanBanType, vanBanId).ifPresent(result::add);
            }
        }
        return result;
    }

    private java.util.Optional<Map<String, Object>> resolveVanBanSummary(String vanBanType, UUID vanBanId) {
        return switch (vanBanType) {
            case TYPE_DUTHAO -> duThaoDocumentRepository.findById(vanBanId)
                .filter(d -> !d.isDeleted())
                .map(d -> vanBanSummary(vanBanType, d.getId(), d.getTitle(), d.getStatus()));
            case TYPE_VBDI -> vbdiDocumentRepository.findById(vanBanId)
                .filter(d -> !d.isDeleted())
                .map(d -> vanBanSummary(vanBanType, d.getId(), d.getTitle(), d.getStatus()));
            default -> documentRepository.findById(vanBanId)
                .filter(d -> !d.isDeleted())
                .map(d -> vanBanSummary(d.getDocumentType(), d.getId(), d.getTitle(), d.getStatus()));
        };
    }

    private Map<String, Object> vanBanSummary(String documentType, UUID id, String title, String status) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("databasetype", documentType);
        summary.put("document_id", id);
        summary.put("title", title);
        summary.put("status", status);
        return summary;
    }

    private List<Map<String, Object>> collectProcessHistory(UUID hosocvId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (HoSoCVProcessDocument process : hoSoCVProcessDocumentRepository.findByHoSoCVId(hosocvId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", process.getId());
            item.put("title", process.getTitle());
            item.put("created_on", process.getCreatedOn());
            item.putAll(dynamicDocumentFieldsService.parseDynamicFields(process.getDynamicFieldsJson()));
            result.add(item);
        }
        return result;
    }

    private List<String> toSemicolonList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        } else if (value instanceof String str && !str.isBlank()) {
            for (String part : str.split(";")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
