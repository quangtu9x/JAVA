package com.td.application.hosoluutru;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 30/10/2026: danh sách văn bản con ({@code vanban_by_hoso_id}) và vết quá trình
 * ({@code vwQuaTrinhChuyenLTLS_Document_Profiles_ID}/{@code vwQuaTrinh_Document_Profiles_ID}) của 1 hồ
 * sơ lưu trữ — thay {@code qlvb/view/query}. Số bản ghi mỗi hồ sơ nhỏ nên lọc từ khoá (không dấu) +
 * phân trang trong bộ nhớ.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DanhSachTaiLieuLuuTruUseCase {

    private static final List<String> VAN_BAN_KEYWORD_FIELDS =
        List.of("code", "subject", "unit_in_charge", "signer", "note");
    private static final String LOAI_CHUYEN_LTLS = "chuyenltls";
    private static final java.time.format.DateTimeFormatter CREATE_DATE_FORMATTER =
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;
    private final HoSoLuuTruProcessDocumentRepository hoSoLuuTruProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final XemTaiLieuLuuTruUseCase xemTaiLieuLuuTruUseCase;

    public PaginationResponse<Map<String, Object>> danhSachVanBan(DanhSachTaiLieuLuuTruRequest request) {
        int pageNumber = request == null || request.getPageNumber() == null || request.getPageNumber() < 1
            ? 1 : request.getPageNumber();
        int pageSize = request == null || request.getPageSize() == null || request.getPageSize() < 1
            ? 20 : Math.min(request.getPageSize(), 10000);
        UUID hosoId = parseUuid(request == null ? null : request.getHosoId());
        if (hosoId == null) {
            return new PaginationResponse<>(List.of(), pageNumber, pageSize, 0, 0, true, true);
        }
        String keyword = normalize(request.getKeyword());

        List<Map<String, Object>> all = new ArrayList<>();
        for (var vanBan : hoSoLuuTruVanBanDocumentRepository.findByHosoId(hosoId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", vanBan.getId());
            item.put("title", vanBan.getTitle());
            item.put("created_on", vanBan.getCreatedOn());
            item.putAll(dynamicDocumentFieldsService.parseDynamicFields(vanBan.getDynamicFieldsJson()));
            if (!keyword.isEmpty() && !matchesKeyword(item, keyword)) {
                continue;
            }
            all.add(item);
        }
        // Mới tạo trước (đúng orderBy createdOn DESC của FE cũ).
        all.sort(Comparator.comparing(
            (Map<String, Object> m) -> String.valueOf(m.get("created_on") == null ? "" : m.get("created_on")))
            .reversed());

        int total = all.size();
        int from = Math.min((pageNumber - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> page = new ArrayList<>(all.subList(from, to));
        // Cột "Tệp đính kèm" (ListIconFiles) đọc files theo file_id — chỉ tra tệp cho trang hiện tại.
        for (Map<String, Object> item : page) {
            Object id = item.get("id");
            if (id instanceof UUID uuid) {
                item.put("files", xemTaiLieuLuuTruUseCase.listFiles(uuid));
            }
        }
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return new PaginationResponse<>(page, pageNumber, pageSize, total, totalPages,
            pageNumber <= 1, pageNumber >= totalPages);
    }

    public Result<List<Map<String, Object>>> quaTrinh(DanhSachTaiLieuLuuTruRequest request) {
        UUID hosoId = parseUuid(request == null ? null : request.getHosoId());
        if (hosoId == null) {
            return Result.success(List.of());
        }
        boolean chuyenLtls = request.getLoai() == null || LOAI_CHUYEN_LTLS.equalsIgnoreCase(request.getLoai().trim());
        String keyword = normalize(request.getKeyword());
        List<Map<String, Object>> result = new ArrayList<>();
        for (var process : hoSoLuuTruProcessDocumentRepository.findByHosoId(hosoId)) {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(process.getDynamicFieldsJson());
            boolean isLtls = LOAI_CHUYEN_LTLS.equalsIgnoreCase(String.valueOf(fields.getOrDefault("process_type", "")));
            if (isLtls != chuyenLtls) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", process.getId());
            item.put("title", process.getTitle());
            item.put("created_on", process.getCreatedOn());
            item.putAll(fields);
            // Cột "Thời gian" FE đọc create_date — vết Java không ghi field này, lấy từ created_on.
            if (process.getCreatedOn() != null
                    && String.valueOf(item.getOrDefault("create_date", "")).isBlank()) {
                item.put("create_date", process.getCreatedOn().format(CREATE_DATE_FORMATTER));
            }
            if (!keyword.isEmpty() && !normalize(process.getTitle() + " " + fields.getOrDefault("subject", ""))
                    .contains(keyword)) {
                continue;
            }
            result.add(item);
        }
        result.sort(Comparator.comparing(
            (Map<String, Object> m) -> String.valueOf(m.get("created_on") == null ? "" : m.get("created_on")))
            .reversed());
        return Result.success(result);
    }

    private boolean matchesKeyword(Map<String, Object> item, String keyword) {
        if (normalize(String.valueOf(item.getOrDefault("title", ""))).contains(keyword)) {
            return true;
        }
        for (String field : VAN_BAN_KEYWORD_FIELDS) {
            Object value = item.get(field);
            if (value != null && normalize(value.toString()).contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // Chữ thường + bỏ dấu (đ→d) — cùng quy tắc td_search_norm/SearchTextSql.
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.trim().toLowerCase(Locale.ROOT).replace('đ', 'd');
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
