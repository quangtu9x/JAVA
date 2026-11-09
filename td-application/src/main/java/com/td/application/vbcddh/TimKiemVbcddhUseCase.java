package com.td.application.vbcddh;

import com.td.application.common.models.PaginationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 30/10/2026 (cutover FE vbcddh khỏi Domino {@code qlvb/search?databasetype=vbcddh}): tìm kiếm nâng cao
 * văn bản CĐĐH / nhiệm vụ (ItemsList/NhiemVuItemsList/báo cáo theo đơn vị). Riêng module vbcddh (không
 * dùng controller tìm kiếm chung). Quy tắc dịch {@code search}:
 * <ul>
 *   <li>{@code form} → lọc form; {@code keyword} → từ khoá không dấu trên trích yếu/nội dung/số ký hiệu
 *   (+ {@code columns} nếu có); {@code author} (ACL Domino) bỏ qua — dữ liệu đã tách DB theo đơn vị.</li>
 *   <li>field {@code *_date} dạng [từ, đến] → khoảng ngày; {@code cohan} 1/0 → có/không hạn xử lý;
 *   {@code loainhiemvu} nvtuqlvb/nvtheochidao → theo idvanban; còn lại → field chứa giá trị.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimKiemVbcddhUseCase {

    private static final Pattern FIELD_NAME = Pattern.compile("^[a-z0-9_]{1,64}$");
    private static final Set<String> IGNORED_KEYS = Set.of(
        "form", "keyword", "author", "chonngaynhanh", "chonngaynhanh_loai", "cohan", "loainhiemvu", "databasetype");
    private static final List<String> DEFAULT_KEYWORD_FIELDS = List.of("trichyeu", "noidung", "sokyhieu");
    private static final String CO_HAN = "COALESCE(d.dynamic_fields->>'hanxuly_date','') != ''";
    private static final String KHONG_HAN = "COALESCE(d.dynamic_fields->>'hanxuly_date','') = ''";
    private static final String NV_TU_QLVB = "COALESCE(d.dynamic_fields->>'idvanban','') != ''";
    private static final String NV_THEO_CHI_DAO = "COALESCE(d.dynamic_fields->>'idvanban','') = ''";

    private final VbcddhViewQuery vbcddhViewQuery;

    public PaginationResponse<Map<String, Object>> execute(TimKiemVbcddhRequest request) {
        TimKiemVbcddhRequest safe = request == null ? new TimKiemVbcddhRequest() : request;
        Map<String, Object> search = safe.getSearch() == null ? Map.of() : safe.getSearch();

        List<String> forms = new ArrayList<>();
        String form = asString(search.get("form"));
        forms.add(form.isBlank() ? "nhiemvu" : form);

        String keyword = asString(search.get("keyword"));
        if (keyword.isBlank()) {
            keyword = asString(safe.getKeyword());
        }
        List<String> keywordFields = new ArrayList<>(DEFAULT_KEYWORD_FIELDS);
        if (safe.getColumns() != null) {
            for (String column : safe.getColumns()) {
                String name = column == null ? "" : column.trim().toLowerCase();
                if (FIELD_NAME.matcher(name).matches() && !keywordFields.contains(name)) {
                    keywordFields.add(name);
                }
            }
        }

        Map<String, String> contains = new LinkedHashMap<>();
        Map<String, String[]> dateRanges = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : search.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase();
            if (IGNORED_KEYS.contains(name) || !FIELD_NAME.matcher(name).matches() || entry.getValue() == null) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Collection<?> collection) {
                List<String> parts = collection.stream().map(TimKiemVbcddhUseCase::asString).toList();
                if (name.endsWith("_date") && parts.size() >= 2) {
                    dateRanges.put(name, new String[] {parts.get(0), parts.get(1)});
                } else if (!parts.isEmpty()) {
                    contains.put(name, parts.get(0));
                }
                continue;
            }
            String text = asString(value);
            if (!text.isBlank()) {
                contains.put(name, text);
            }
        }

        List<String> extras = new ArrayList<>();
        String coHan = asString(search.get("cohan"));
        if ("1".equals(coHan)) {
            extras.add(CO_HAN);
        } else if ("0".equals(coHan)) {
            extras.add(KHONG_HAN);
        }
        String loai = asString(search.get("loainhiemvu"));
        if ("nvtuqlvb".equalsIgnoreCase(loai)) {
            extras.add(NV_TU_QLVB);
        } else if ("nvtheochidao".equalsIgnoreCase(loai)) {
            extras.add(NV_THEO_CHI_DAO);
        }

        VbcddhListCriteria criteria = VbcddhListCriteria.builder()
            .documentType("vbcddh")
            .forms(forms)
            .contains(contains)
            .dateRanges(dateRanges)
            .keyword(keyword.isBlank() ? null : keyword)
            .keywordFields(keywordFields)
            .extraPredicateSql(extras.isEmpty() ? null : String.join(" AND ", extras))
            .build();
        Pageable pageable = PageRequest.of(Math.max(1, safe.getPageNumber()) - 1,
            Math.min(Math.max(1, safe.getPageSize()), VbcddhViewQuery.MAX_PAGE_SIZE));
        return vbcddhViewQuery.query(criteria, safe.getItems(), pageable);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
