package com.td.application.nhatkyhethong;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.edithistory.EditHistoryDocument;
import lombok.Data;
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
import java.util.Set;

/**
 * 30/10/2026 (port "Nhật ký quản trị" - nhatkyhethong/nhatkyhieuchinh/nhomquyen|nguoidung|cauhinh): thay Domino
 * qlvb/search {form:"log", form_edit} bằng đọc {@code edit_history_documents}. Chỉ quản trị.
 * Trả {@code edit_obj} dạng chuỗi dễ đọc ("field: cũ -> mới; ...") cho cột "Nội dung thay đổi".
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NhatKyQuanTriUseCase {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "CVQUANTRI", "CVQUANTRIDV");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final NhatKyQuanTriRepository repository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;

    @Data
    public static class Request {
        private String formEdit;
        private String keyword;
        /** [từ ngày, đến ngày] dạng dd/MM/yyyy (RangePicker FE). */
        private List<String> createdDate = new ArrayList<>();
        private int pageNumber = 1;
        private int pageSize = 20;
    }

    public Result<PaginationResponse<Map<String, Object>>> execute(Request request) {
        if (currentUserProvider.currentRoles().stream().noneMatch(ADMIN_ROLES::contains)) {
            return Result.failure("Bạn không có quyền xem nhật ký quản trị");
        }
        int pageNumber = Math.max(1, request.getPageNumber());
        int pageSize = Math.min(Math.max(1, request.getPageSize()), 1000);
        List<String> range = request.getCreatedDate() == null ? List.of() : request.getCreatedDate();
        LocalDate from = range.isEmpty() ? null : parseDate(range.get(0));
        LocalDate to = range.size() < 2 ? null : parseDate(range.get(1));
        var page = repository.search(request.getFormEdit(), request.getKeyword(), from, to,
            PageRequest.of(pageNumber - 1, pageSize));
        List<Map<String, Object>> items = page.getContent().stream().map(this::toRow).toList();
        return Result.success(new PaginationResponse<>(items, pageNumber, pageSize, page.getTotalElements(),
            page.getTotalPages(), page.isFirst(), page.isLast()));
    }

    private Map<String, Object> toRow(EditHistoryDocument history) {
        Map<String, Object> row = new LinkedHashMap<>();
        Map<String, Object> changes = dynamicDocumentFieldsService.parseDynamicFields(history.getEditObjJson());
        row.put("id", history.getId());
        row.put("parentid", history.getParentId());
        row.put("form", "log");
        row.put("form_edit", history.getFormEdit());
        row.put("databasetype", history.getDatabaseType());
        row.put("creator", history.getCreatorName() == null || history.getCreatorName().isBlank()
            ? history.getUserCreator() : history.getCreatorName());
        row.put("user_creator", history.getUserCreator());
        row.put("edit_obj", describe(changes));
        row.put("edit_changes", changes);
        String created = history.getCreatedOn() == null ? "" : history.getCreatedOn().format(DATE_TIME);
        row.put("created", created);
        row.put("create_date", created);
        return row;
    }

    private static String describe(Map<String, Object> changes) {
        List<String> parts = new ArrayList<>();
        changes.forEach((field, value) -> {
            if (value instanceof Map<?, ?> change) {
                parts.add(field + ": " + text(change.get("oldvalue")) + " -> " + text(change.get("newvalue")));
            } else {
                parts.add(field + ": " + text(value));
            }
        });
        return String.join("; ", parts);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim(), DATE);
        } catch (Exception ex) {
            return null;
        }
    }
}
