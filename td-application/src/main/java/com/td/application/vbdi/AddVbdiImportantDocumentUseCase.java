package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 21/09/2026 (yêu cầu người dùng "kiểm tra port qlvb/vbdi/addimportant?databasetype=vbdi"): port
 * {@code DocumentOutProcesses.java#HandleAddImportant()} (Domino, {@code BE_THANHHOA}) — đánh dấu
 * "Văn bản đi quan trọng" (gắn dấu sao) là 1 flag RIÊNG CHO TỪNG NGƯỜI DÙNG trên 1 văn bản đi CHUNG,
 * không phải flag toàn văn bản.
 *
 * <p><b>Cơ chế Domino gốc (đã xác nhận qua đọc code, không suy đoán)</b>: field multivalue
 * {@code MarkImportant} trên CHÍNH văn bản đi, chứa danh sách username đã "gắn sao" văn bản
 * đó — {@code action="add"} thêm username vào danh sách (nếu chưa có), {@code action="delete"} loại
 * bỏ (nếu có). Tab "Gắn dấu sao" ở Domino là 1 view categorize theo field này, lookup theo key
 * {@code $username$} (macro thay bằng username người gọi) — tương đương SQL {@code LIKE} theo thành
 * viên trên chuỗi phân tách hiện có, đã port sẵn từ trước ở {@code ViewQueryService
 * #queryVbdiPrivateImportant}/{@code VbdiDocumentRepository#findByFieldContainsMemberAnySeparator}.
 * Use case này port CHIỀU GHI còn thiếu.</p>
 *
 * <p><b>Bugfix 21/09/2026 (2, người dùng báo "ở các danh sách chưa thể hiện được đã gắn hay chưa")
 * </b>: bản đầu dùng ĐÚNG hoa/thường Domino gốc làm khoá JSONB ({@code "MarkImportant"}) — PHÁT HIỆN
 * việc này làm field bị RỚT KHỎI mọi response GetView có {@code items} liệt kê tường minh (không phải
 * {@code "getall"}), vì {@link com.td.application.common.dynamicfields.ItemsFilter
 * #splitSemicolonList} LUÔN hạ thường tên field được yêu cầu trước khi so khớp
 * {@code containsKey} (case-sensitive) với {@code allFields} — yêu cầu {@code "MarkImportant"} bị hạ
 * thành {@code "markimportant"}, không khớp khoá thật {@code "MarkImportant"} (hoa), field biến mất
 * âm thầm ở các danh sách dùng {@code ITEMS_LISTVIEW} (vd {@code chobanhanh}, không dùng
 * {@code "getall"} như tab {@code xuly}). ĐỔI khoá field sang CHỮ THƯỜNG
 * {@value #FIELD_MARK_IMPORTANT} — khớp quy ước chung của {@code dynamic_fields} (hầu hết field khác
 * đều lowercase, xem {@code DynamicDocumentFieldsService#buildDynamicFieldsJson}) và miễn nhiễm với
 * bug hạ-thường-rồi-so-khớp của {@code ItemsFilter} vì khoá đã sẵn là chữ thường. Vì
 * {@code vbdi_documents} CHƯA có dữ liệu lịch sử import (BL-305 chưa chạy — xem
 * {@link AddVbdiImportantDocumentUseCase} lịch sử trước đó/memory dự án), đổi khoá KHÔNG cần migrate
 * dữ liệu cũ — chỉ cần đồng bộ 2 chỗ ĐỌC ({@code ViewQueryService#queryVbdiPrivateImportant}) VÀ GHI
 * (lớp này) cùng lúc.</p>
 *
 * <p><b>Domino gốc có {@code mark_important_log} (item JSON dự định ghi ai/khi nào gắn sao) nhưng
 * đường ghi bị comment-out từ trước</b> (chỉ đọc, không bao giờ ghi thêm) — tức Domino gốc CŨNG chưa
 * bao giờ có lịch sử gắn sao thật, chỉ có danh sách thành viên hiện tại. Use case này giữ đúng hành
 * vi đó (không port audit trail cho hành động gắn sao) — nếu sau này cần "ai gắn sao lúc nào" thì bổ
 * sung field riêng theo mẫu {@code log_seen_document_detail} của {@link AddVbdiSeenDocumentUseCase},
 * không lẫn vào field liệt kê thành viên.</p>
 *
 * <p><b>Định danh SERVER-AUTHORITATIVE</b>: {@code username} lấy từ {@link CurrentUserProvider} —
 * KHÔNG nhận từ client, tránh gắn/gỡ sao giả mạo hộ người khác.</p>
 *
 * <p><b>Guard</b>: văn bản phải tồn tại, đúng {@code documentType="vbdi"}, chưa xoá mềm — cùng
 * nguyên tắc {@link AddVbdiSeenDocumentUseCase}. KHÔNG guard trạng thái ISSUED (Domino gốc không yêu
 * cầu). Idempotent: gọi {@code add} khi đã có trong danh sách hoặc {@code delete} khi chưa có đều
 * không lỗi, chỉ không đổi gì thêm.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddVbdiImportantDocumentUseCase {

    private static final String FALLBACK_USERNAME = "system";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String ACTION_ADD = "add";
    private static final String ACTION_DELETE = "delete";

    static final String FIELD_MARK_IMPORTANT = "markimportant";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;

    public Result<UUID> execute(AddVbdiImportantDocumentRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return Result.failure("Thiếu documentId cần đánh dấu quan trọng");
        }
        boolean add;
        if (ACTION_ADD.equalsIgnoreCase(request.getAction())) {
            add = true;
        } else if (ACTION_DELETE.equalsIgnoreCase(request.getAction())) {
            add = false;
        } else {
            return Result.failure("action khong hop le (chi chap nhan 'add' hoac 'delete')");
        }

        var document = vbdiDocumentRepository.findById(request.getDocumentId()).orElse(null);
        if (document == null || document.isDeleted()
                || !SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String username = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        if (add) {
            addMember(fields, FIELD_MARK_IMPORTANT, username);
        } else {
            removeMember(fields, FIELD_MARK_IMPORTANT, username);
        }

        document.replaceDynamicFields(toJson(fields));
        var saved = vbdiDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(),
            Map.of("username", username, "action", request.getAction()),
            Map.of("username", username, FIELD_MARK_IMPORTANT, fields.getOrDefault(FIELD_MARK_IMPORTANT, "")));
        log.info("Cap nhat van ban di quan trong {} - {} boi {}", saved.getId(), request.getAction(), username);
        return Result.success(saved.getId());
    }

    private void addMember(Map<String, Object> fields, String fieldName, String username) {
        List<String> members = splitMembers(fields.get(fieldName));
        if (members.stream().noneMatch(item -> item.equalsIgnoreCase(username))) {
            members.add(username);
        }
        fields.put(fieldName, String.join(";", members));
    }

    private void removeMember(Map<String, Object> fields, String fieldName, String username) {
        List<String> members = splitMembers(fields.get(fieldName));
        if (members.isEmpty()) {
            return;
        }
        List<String> filtered = members.stream()
            .filter(item -> !item.equalsIgnoreCase(username))
            .collect(Collectors.toList());
        fields.put(fieldName, String.join(";", filtered));
    }

    /** Tách chuỗi {@code "a;b;c"} thành danh sách, bỏ khoảng trắng/phần tử rỗng — an toàn với null. */
    private List<String> splitMembers(Object rawValue) {
        String text = rawValue == null ? "" : String.valueOf(rawValue);
        if (text.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(text.split(";"))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
