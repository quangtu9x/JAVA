package com.td.application.vbcddh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BL-250 (VBCDDH Core) — tương đương {@code CapNhatTinhTrangVBCD.java} thật (form {@code nhiemvu}):
 * mỗi khi 1 nhiệm vụ con của 1 văn bản chỉ đạo ({@code idvanban}) được tạo/cập nhật, tính lại
 * {@code tinhtrang} tổng hợp và các field "người thực hiện/phối hợp" gộp trên chính văn bản gốc.
 *
 * <p>Quy tắc tổng hợp {@code tinhtrang} (đúng nguyên văn Domino thật):
 * <ul>
 *   <li>Không có nhiệm vụ nào "đang"/"đã" → {@code "Chưa thực hiện"}.</li>
 *   <li>Không còn nhiệm vụ "chưa"/"đang" (mọi nhiệm vụ đều "đã") → {@code "Đã hoàn thành"}.</li>
 *   <li>Còn lại → {@code "Đang thực hiện"}.</li>
 * </ul>
 * So khớp chuỗi {@code tinhtrang} theo kiểu chứa-chuỗi con, không phân biệt hoa/thường (đúng
 * {@code .toLowerCase().indexOf(...)} của Domino thật), không phải so khớp tuyệt đối.
 *
 * <p><b>Đơn giản hoá so với Domino thật</b>: KHÔNG replicate {@code addNguoiDaiDien()} (tự thêm
 * "đại diện nhận văn bản" từ {@code db_catalog.nsf} khi đơn vị được chọn qua modal có sẵn người đại
 * diện mặc định) — cơ chế này phụ thuộc dữ liệu tổ chức (catalog riêng) chưa có evidence tương ứng
 * rõ ràng trong data model Spring Boot hiện tại, và không nằm trong 5 hạng mục DoD của BL-250.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VbcddhStatusRollupService {

    public static final String DOCUMENT_TYPE = "vbcddh";
    private static final String FORM_FIELD = "form";
    private static final String FORM_NHIEMVU = "nhiemvu";
    private static final String IDVANBAN_FIELD = "idvanban";
    private static final String TINHTRANG_FIELD = "tinhtrang";
    private static final String CHUA_THUC_HIEN = "Chưa thực hiện";
    private static final String DANG_THUC_HIEN = "Đang thực hiện";
    private static final String DA_HOAN_THANH = "Đã hoàn thành";
    // Domino: 8 field gộp (union, khử trùng lặp) từ mọi nhiệm vụ con về văn bản gốc.
    private static final List<String> AGGREGATE_FIELDS = List.of(
        "donvithuchien", "donvithuchienhide",
        "chuyenvienthuchien", "chuyenvienthuchienhide",
        "donviphoihop", "donviphoihophide",
        "chuyenvienphoihop", "chuyenvienphoihophide");
    private static final int MAX_SIBLINGS = 2000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    /** @param idVanBan UUID của văn bản chỉ đạo gốc (field {@code idvanban} trên các nhiệm vụ con trỏ về đây). */
    public void recomputeParentStatus(UUID idVanBan) {
        if (idVanBan == null) {
            return;
        }
        var parentOptional = vbcddhDocumentRepository.findById(idVanBan);
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()) {
            return;
        }
        var parent = parentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(parent.getDocumentType())) {
            return;
        }

        Pageable pageable = PageRequest.of(0, MAX_SIBLINGS);
        List<VbcddhDocument> siblings = vbcddhDocumentRepository
            .findByFieldValue(DOCUMENT_TYPE, IDVANBAN_FIELD, idVanBan.toString(), pageable)
            .getContent();

        int countChua = 0;
        int countDang = 0;
        int countDa = 0;
        Map<String, Set<String>> aggregates = new LinkedHashMap<>();
        for (String field : AGGREGATE_FIELDS) {
            aggregates.put(field, new LinkedHashSet<>());
        }

        for (VbcddhDocument sibling : siblings) {
            Map<String, Object> siblingFields = dynamicDocumentFieldsService.parseDynamicFields(sibling.getDynamicFieldsJson());
            if (!FORM_NHIEMVU.equalsIgnoreCase(asString(siblingFields.get(FORM_FIELD)))) {
                continue;
            }

            String tinhtrang = asString(siblingFields.get(TINHTRANG_FIELD));
            String lower = tinhtrang == null ? "" : tinhtrang.toLowerCase();
            if (lower.contains("chưa")) {
                countChua++;
            } else if (lower.contains("đang")) {
                countDang++;
            } else if (lower.contains("đã")) {
                countDa++;
            }

            for (String field : AGGREGATE_FIELDS) {
                aggregates.get(field).addAll(splitSemicolon(asString(siblingFields.get(field))));
            }
        }

        if (siblings.isEmpty()) {
            return;
        }

        String tinhtrangMoi;
        if (countDang == 0 && countDa == 0) {
            tinhtrangMoi = CHUA_THUC_HIEN;
        } else if (countChua == 0 && countDang == 0 && countDa > 0) {
            tinhtrangMoi = DA_HOAN_THANH;
        } else {
            tinhtrangMoi = DANG_THUC_HIEN;
        }

        var before = buildSnapshot(parent);
        Map<String, Object> parentFields = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));
        boolean changed = !tinhtrangMoi.equals(asString(parentFields.get(TINHTRANG_FIELD)));
        parentFields.put(TINHTRANG_FIELD, tinhtrangMoi);

        for (String field : AGGREGATE_FIELDS) {
            String joined = String.join(";", aggregates.get(field));
            if (!joined.isEmpty() && !joined.equals(asString(parentFields.get(field)))) {
                changed = true;
            }
            if (!joined.isEmpty()) {
                parentFields.put(field, joined);
            }
        }

        if (!changed) {
            return;
        }

        parent.replaceDynamicFields(toJson(parentFields));
        var saved = vbcddhDocumentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private Map<String, Object> buildSnapshot(VbcddhDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private List<String> splitSemicolon(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String part : value.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
