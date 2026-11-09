package com.td.application.hosoluutru;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 30/10/2026: tìm kiếm nâng cao hồ sơ lưu trữ (màn Tìm kiếm + ItemsList hồ sơ lưu trữ khi có điều kiện
 * nâng cao) — thay {@code qlvb/search?databasetype=hosoluutru}. Đọc ĐÚNG các ô form
 * {@code CollapseHoSoLuuTru.jsx}; field lạ bị bỏ qua (không suy diễn theo hậu tố như TDQuery Domino).
 * Phạm vi = {@code keys} (identifier/tài khoản) giống các view {@code hoso_*_by_identifier}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimKiemHoSoLuuTruUseCase {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> CONTAINS_FIELDS = List.of(
        "process_handler", "unit_process_handler", "code", "title", "first_topic", "second_topic",
        "storage_period", "category");
    private static final List<String> EXACT_FIELDS = List.of("chuyenluutru");
    private static final List<String> KEYWORD_FIELDS = List.of(
        "code", "title", "category", "first_topic", "second_topic", "process_handler", "note");
    private static final List<String> SCOPE_FIELDS = List.of(
        HoSoLuuTruLifecycleStatus.FIELD_IDENTIFIER, HoSoLuuTruLifecycleStatus.FIELD_USER_CREATOR);

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public PaginationResponse<Map<String, Object>> execute(TimKiemHoSoLuuTruRequest request) {
        int pageNumber = request == null || request.getPageNumber() == null || request.getPageNumber() < 1
            ? 1 : request.getPageNumber();
        int pageSize = request == null || request.getPageSize() == null || request.getPageSize() < 1
            ? 20 : Math.min(request.getPageSize(), 10000);
        String scopeValue = request == null || request.getKeys() == null ? "" : request.getKeys().stream()
            .filter(key -> key != null && !key.isBlank()).findFirst().orElse("");
        if (scopeValue.isBlank()) {
            return new PaginationResponse<>(List.of(), pageNumber, pageSize, 0, 0, true, true);
        }
        Map<String, Object> search = request.getSearch() == null ? Map.of() : request.getSearch();

        Map<String, String> containsFields = new LinkedHashMap<>();
        for (String field : CONTAINS_FIELDS) {
            String value = scalar(search.get(field));
            if (!value.isBlank()) {
                containsFields.put(field, value);
            }
        }
        Map<String, String> exactFields = new LinkedHashMap<>();
        for (String field : EXACT_FIELDS) {
            String value = scalar(search.get(field));
            if (!value.isBlank()) {
                exactFields.put(field, value);
            }
        }
        String keyword = request.getKeyword() != null && !request.getKeyword().isBlank()
            ? request.getKeyword() : scalar(search.get("keyword"));

        LocalDate dateFrom = null;
        LocalDate dateTo = null;
        if (search.get("create_date") instanceof List<?> range && !range.isEmpty()) {
            dateFrom = parseDate(range.get(0));
            dateTo = range.size() > 1 ? parseDate(range.get(1)) : null;
        }

        var page = hoSoLuuTruDocumentRepository.search(
            "hosoluutru", SCOPE_FIELDS, scopeValue.trim(), containsFields, exactFields,
            keyword, KEYWORD_FIELDS, "create_date", dateFrom, dateTo,
            PageRequest.of(pageNumber - 1, pageSize));

        List<Map<String, Object>> items = new ArrayList<>();
        for (var document : page.getContent()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", document.getId());
            item.put("title", document.getTitle());
            item.put("form", document.getStatus());
            item.put("created_on", document.getCreatedOn());
            item.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
            items.add(item);
        }
        return new PaginationResponse<>(items, pageNumber, pageSize, page.getTotalElements(),
            page.getTotalPages(), page.isFirst(), page.isLast());
    }

    // Ô TDSelect có thể trả {value,label} thay vì chuỗi.
    private String scalar(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            Object inner = map.get("value");
            return inner == null ? "" : inner.toString().trim();
        }
        if (value instanceof List<?> || value instanceof Boolean) {
            return "";
        }
        return value.toString().trim();
    }

    private LocalDate parseDate(Object value) {
        String text = scalar(value);
        if (text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 10), DATE_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }
}
