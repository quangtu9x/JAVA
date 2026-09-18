package com.td.application.vbden;

import com.td.domain.vbden.VbdenUserActionLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 18/09/2026 (yêu cầu người dùng "thêm vết audit của văn bản đến"): đọc lại "Lịch sử thao tác văn
 * bản" theo văn bản đến cha — mirror {@code com.td.application.vbdi.ListVbdiUserActionLogUseCase}.
 *
 * <p><b>Nhóm lại theo {@code username}</b> để khớp shape mà FE ({@code LogUserAction.jsx} phía
 * vbden) render — bảng {@code vbden_user_action_logs} lưu PHẲNG (1 dòng/1 sự kiện) nên use case này
 * tự PIVOT lại khi đọc: gom các dòng cùng {@code username}, mỗi {@code actionType} khác nhau trở
 * thành 1 KEY trong map kết quả, giá trị là chuỗi timestamp nối {@code ";"}.</p>
 *
 * <p>Định dạng timestamp {@code "dd/MM/yyyy hh:mm:ss a"} (12 giờ + AM/PM, {@code Locale.ENGLISH}) —
 * khớp ĐÚNG format {@code LogUserAction.jsx#formatTimeList} đang parse (dùng chung component với
 * vbdi, không cần sửa logic render).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListVbdenUserActionLogUseCase {

    private static final DateTimeFormatter CREATED_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a", Locale.ENGLISH);
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 200;

    private final VbdenUserActionLogRepository vbdenUserActionLogRepository;

    public List<Map<String, Object>> execute(ListVbdenUserActionLogRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return List.of();
        }
        int pageSize = request.getPageSize() == null
            ? DEFAULT_PAGE_SIZE : Math.min(Math.max(request.getPageSize(), 1), MAX_PAGE_SIZE);
        Page<VbdenUserActionLog> page = vbdenUserActionLogRepository.findByParentId(
            request.getDocumentId(), PageRequest.of(0, pageSize));

        Map<String, Map<String, Object>> byUser = new LinkedHashMap<>();
        for (VbdenUserActionLog entry : page.getContent()) {
            String username = entry.getUsername() == null ? "" : entry.getUsername();
            Map<String, Object> row = byUser.computeIfAbsent(username, key -> {
                Map<String, Object> newRow = new LinkedHashMap<>();
                newRow.put("username", entry.getUsername());
                newRow.put("fullname", entry.getFullname());
                newRow.put("unit", entry.getUnit());
                newRow.put("identifier", entry.getIdentifier());
                return newRow;
            });
            String actionType = entry.getActionType();
            if (actionType == null || actionType.isBlank() || entry.getCreatedOn() == null) {
                continue;
            }
            String timestamp = CREATED_FORMATTER.format(entry.getCreatedOn());
            Object existing = row.get(actionType);
            row.put(actionType, existing == null ? timestamp : existing + ";" + timestamp);
        }
        return new ArrayList<>(byUser.values());
    }
}
