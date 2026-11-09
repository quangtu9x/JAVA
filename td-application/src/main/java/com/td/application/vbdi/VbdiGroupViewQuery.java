package com.td.application.vbdi;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 30/10/2026 — tra cứu nhóm văn bản đi (thay Domino {@code qlvb/view/query?databasetype=vbdi&viewaction=group},
 * FE {@code TDTable/GroupTable} ở {@code vbdi/tracuu/theo_nguoi_ky} và {@code theo_don_vi_soan}). Cùng khuôn
 * {@code DuThaoGroupViewQuery}:
 * <ul>
 *   <li>{@code ischild != true}: danh sách nhóm {@code [{key:"1", name, count, children:[]}]} (1 cấp như dxl).</li>
 *   <li>{@code ischild = true}: văn bản thuộc nhóm {@code navposition}, phân trang pageNumber/pageSize.</li>
 * </ul>
 * View (view_vanbandi.dxl 80-132): {@code vwGroup_by_Signer} nhóm theo {@code signer},
 * {@code vwGroup_by_Unit_In_Charge} theo {@code unit_in_charge} — chỉ văn bản có giá trị field đó. Phạm vi =
 * văn bản đi đã ban hành của tổ chức người đang đăng nhập (Domino: quyền đọc cả CSDL).
 * Chỉ xử lý khi request có {@code ischild} (GroupTable luôn gửi) — {@code vwGroup_by_Unit_In_Charge} còn được
 * danh sách phẳng "Đơn vị soạn" gọi qua ViewQueryService.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VbdiGroupViewQuery {

    static final int MAX_DOCUMENTS = 5000;
    private static final Map<String, String> GROUP_FIELDS = Map.of(
        "vwgroup_by_signer", "signer",
        "vwgroup_by_unit_in_charge", "unit_in_charge");

    private final ViewQueryService viewQueryService;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public static boolean handles(ViewQueryRequest request) {
        return request != null && request.getIschild() != null && GROUP_FIELDS.containsKey(normalize(request.getViewname()));
    }

    public PaginationResponse<Map<String, Object>> execute(ViewQueryRequest request) {
        String field = GROUP_FIELDS.get(normalize(request.getViewname()));
        if (field == null) {
            throw new IllegalArgumentException("View nhóm văn bản đi chưa được hỗ trợ: " + request.getViewname());
        }
        Map<String, List<VbdiDocument>> groups = new LinkedHashMap<>();
        for (VbdiDocument document : viewQueryService.findVbdiOrgIssuedDocuments(MAX_DOCUMENTS)) {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
            Object raw = fields == null ? null : fields.get(field);
            List<String> labels = labels(raw);
            for (String label : labels) {
                groups.computeIfAbsent(label, k -> new ArrayList<>()).add(document);
            }
        }
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        List<String> names = new ArrayList<>(groups.keySet());
        names.sort(collator::compare);

        int pageNumber = Math.max(1, request.getPageNumber());
        int pageSize = Math.min(Math.max(1, request.getPageSize()), 200);
        if (Boolean.TRUE.equals(request.getIschild())) {
            List<VbdiDocument> members = List.of();
            String navposition = request.getNavposition();
            if (navposition != null && !navposition.isBlank()) {
                int index = number(navposition.trim().split("\\.")[0]);
                if (index >= 1 && index <= names.size()) {
                    members = groups.get(names.get(index - 1));
                }
            }
            int from = Math.min((pageNumber - 1) * pageSize, members.size());
            int to = Math.min(from + pageSize, members.size());
            List<Map<String, Object>> items = members.subList(from, to).stream()
                .map(document -> viewQueryService.toVbdiListFields(document, request.getItems()))
                .toList();
            return page(items, pageNumber, pageSize, members.size());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 1;
        for (String name : names) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", String.valueOf(index++));
            row.put("name", name);
            row.put("count", groups.get(name).size());
            row.put("children", List.of());
            rows.add(row);
        }
        return page(rows, 1, Math.max(1, rows.size()), rows.size());
    }

    // Field đa trị vào nhiều nhóm (separatemultiplevalues); bỏ văn bản không có giá trị (dxl: field != "").
    private static List<String> labels(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(VbdiGroupViewQuery::text).filter(s -> !s.isEmpty()).distinct().toList();
        }
        String text = text(value);
        return text.isEmpty() ? List.of() : List.of(text);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String normalize(String viewname) {
        return viewname == null ? "" : viewname.trim().toLowerCase(Locale.ROOT);
    }

    private static PaginationResponse<Map<String, Object>> page(
            List<Map<String, Object>> items, int pageNumber, int pageSize, int total) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PaginationResponse<>(items, pageNumber, pageSize, total, totalPages,
            pageNumber <= 1, pageNumber >= totalPages);
    }
}
