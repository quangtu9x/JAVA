package com.td.application.documents;

import com.td.domain.documents.FileAccessLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 09/09/2026 (2) — đọc lại "Lịch sử xem/tải tệp đính kèm" theo văn bản (mọi module) từ
 * {@code file_access_logs}. KHÁC hẳn {@code ListVbdiUserActionLogUseCase} (pivot theo username) —
 * ở đây MỖI DÒNG đã là 1 tổ hợp (tệp, người dùng) DUY NHẤT sẵn (model UPSERT, xem javadoc
 * {@link FileAccessLog}) nên trả THẲNG, không cần gộp/pivot gì thêm — FE nhóm theo người dùng là
 * MIỄN PHÍ (đã đúng 1 dòng/người dùng).
 *
 * <p><b>Parse {@code dynamicFieldsJson} qua {@link DynamicDocumentFieldsService}</b> — DÙNG LẠI
 * ĐÚNG service chung mọi entity có cột {@code dynamic_fields} (vd {@code GetVbdiDetailUseCase}),
 * KHÔNG tự viết {@code ObjectMapper} riêng ở đây (tránh lặp lại logic parse JSON mỗi use case, xem
 * quy ước ARCHITECTURE.md § Best Practices "Dynamic fields / JSONB") — trả field {@code "actions"}
 * dạng {@code List<String>} (vd {@code ["view","download"]}) cho FE render nhiều tag/dòng thay vì 1
 * chuỗi.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListFileAccessLogUseCase {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a", Locale.ENGLISH);
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 200;
    private static final String ACTIONS_FIELD = "actions";

    private final FileAccessLogRepository fileAccessLogRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public List<Map<String, Object>> execute(ListFileAccessLogRequest request) {
        if (request == null || request.getModule() == null || request.getModule().isBlank()
                || request.getDocumentId() == null) {
            return List.of();
        }
        int pageNumber = request.getPageNumber() == null ? 0 : Math.max(request.getPageNumber() - 1, 0);
        int pageSize = request.getPageSize() == null
            ? DEFAULT_PAGE_SIZE : Math.min(Math.max(request.getPageSize(), 1), MAX_PAGE_SIZE);
        Page<FileAccessLog> page = fileAccessLogRepository.findByModuleAndDocumentId(
            request.getModule().trim(), request.getDocumentId(), PageRequest.of(pageNumber, pageSize));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (FileAccessLog entry : page.getContent()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fileId", entry.getFileId());
            row.put("fileName", entry.getFileName());
            row.put("actions", extractActions(entry.getDynamicFieldsJson()));
            row.put("username", entry.getUsername());
            row.put("fullname", entry.getFullname());
            row.put("unit", entry.getUnit());
            row.put("firstAccessedOn", format(entry.getFirstAccessedOn()));
            row.put("lastAccessedOn", format(entry.getLastAccessedOn()));
            row.put("accessCount", entry.getAccessCount());
            rows.add(row);
        }
        return rows;
    }

    private String format(java.time.LocalDateTime value) {
        return value == null ? null : TIMESTAMP_FORMAT.format(value);
    }

    private List<String> extractActions(String dynamicFieldsJson) {
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(dynamicFieldsJson);
        Object rawActions = dynamicFields.get(ACTIONS_FIELD);
        if (!(rawActions instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<String> actions = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                actions.add(item.toString());
            }
        }
        return actions;
    }
}
