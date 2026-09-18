package com.td.application.vbdi;

import com.td.domain.vbdi.VbdiUserActionLog;
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
 * 30/08/2026 (port {@code qlvb/vbdi?...&action=getloguseraction} sang Java): đọc lại "Lịch sử thao
 * tác văn bản" theo văn bản đi cha — port
 * {@code vn.com.tandan.xsp.services.vbdi.LogUserAction.java#getLogUserAction} (Domino đọc view
 * {@code user_action_id} theo {@code document_id}, trả TOÀN BỘ document log dạng phẳng-mọi-field).
 *
 * <p><b>Nhóm lại theo {@code username}</b> để khớp ĐÚNG shape Domino gốc mà FE ({@code LogUserAction.jsx})
 * đang render — Domino trả 1 document/1 user với mỗi {@code action_type} là 1 field chứa danh sách
 * timestamp nối {@code ";"}; bảng Java {@code vbdi_user_action_logs} lưu PHẲNG (1 dòng/1 sự kiện, xem
 * javadoc {@code VbdiUserActionLog}) nên use case này tự PIVOT lại khi đọc: gom các dòng cùng
 * {@code username}, mỗi {@code actionType} khác nhau trở thành 1 KEY trong map kết quả, giá trị là
 * chuỗi timestamp nối {@code ";"} — ĐÚNG shape {@code record[actionType]} mà
 * {@code LogUserAction.jsx#renderActionTags} đang đọc, không cần sửa logic render/cột hiện có.</p>
 *
 * <p>Định dạng timestamp {@code "dd/MM/yyyy hh:mm:ss a"} (12 giờ + AM/PM, {@code Locale.ENGLISH} để
 * ra đúng chữ "AM"/"PM" — không phải "SA"/"CH" tiếng Việt) — khớp ĐÚNG format Domino gốc
 * {@code ControllModal.jsx#logUserAction} từng gửi ({@code dayjs().format("DD/MM/YYYY HH:mm:ss A")})
 * mà {@code LogUserAction.jsx#formatTimeList} đang parse lại bằng cùng pattern — giữ nguyên để
 * KHÔNG phải sửa hàm parse đó cho nhánh Java.</p>
 *
 * <p>Domino gốc KHÔNG phân trang (trả hết) — ở đây vẫn nhận {@code pageSize} nhưng mặc định/giới
 * hạn RỘNG (200, xem {@link ListVbdiUserActionLogRequest}) để giữ đúng hành vi thực tế (số thao
 * tác/văn bản hiếm khi vượt ngưỡng) nhưng có chặn trên an toàn.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListVbdiUserActionLogUseCase {

    private static final DateTimeFormatter CREATED_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a", Locale.ENGLISH);
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 200;

    private final VbdiUserActionLogRepository vbdiUserActionLogRepository;

    public List<Map<String, Object>> execute(ListVbdiUserActionLogRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return List.of();
        }
        int pageSize = request.getPageSize() == null
            ? DEFAULT_PAGE_SIZE : Math.min(Math.max(request.getPageSize(), 1), MAX_PAGE_SIZE);
        Page<VbdiUserActionLog> page = vbdiUserActionLogRepository.findByParentId(
            request.getDocumentId(), PageRequest.of(0, pageSize));

        Map<String, Map<String, Object>> byUser = new LinkedHashMap<>();
        for (VbdiUserActionLog entry : page.getContent()) {
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
