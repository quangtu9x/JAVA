package com.td.application.duthao;

import com.td.application.common.models.PaginationResponse;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 22/10/2026 — tra cứu nhóm và thống kê dự thảo (thay Domino {@code qlvb/view/query?viewaction=group} và
 * {@code qlvb/search?search_type=reports} + view báo cáo tạo động). FE dùng chung {@code TDTable/GroupTable}:
 * <ul>
 *   <li>{@code ischild != true}: cây nhóm {@code [{key:"1", name, count, children:[{key:"1.1",...}]}]} — key
 *       vị trí kiểu Domino ("1", "1.2", "1.2.3"), lá có {@code children = []}; trả ĐỦ mọi nhóm cấp đầu
 *       (FE tự phân trang khi > 10 dòng).</li>
 *   <li>{@code ischild = true}: dự thảo thuộc nhóm lá {@code navposition}, phân trang theo pageNumber/pageSize.</li>
 * </ul>
 * Phạm vi = dự thảo của tổ chức người đang đăng nhập (như tra cứu "toàn bộ", {@code toanbotochuc}) + bộ lọc
 * {@code search} (CollapseVBduthao, chỉ màn thống kê gửi). Domino là view theo quyền đọc của cả CSDL dự thảo.
 *
 * <p>View nhóm (view_duthao.dxl 1001-1360): nhóm → Năm → Tháng (giảm dần); {@code vwGroup_by_Signer} chỉ dự thảo
 * có người ký và lấy năm/tháng theo {@code create_date}, các view khác theo ngày tạo bản ghi ({@code @Created}).
 * Thống kê ({@code thongke_*}, cột phân loại của thongketheo/ItemsList.jsx — sortorder tăng dần, năm/tháng theo
 * {@code create_date}); "theodonvinguoixuly" thêm {@code col_11..col_20 = {value}} = số dự thảo trạng thái 0..9,
 * "danhgiavanban" thêm {@code col_11..col_14} = số dự thảo đánh giá Tốt/Khá/Trung bình/Kém (draft_rate 0..3) —
 * đúng chỉ số cột của report_columns Domino.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DuThaoGroupViewQuery {

    static final int MAX_DOCUMENTS = 5000;
    static final String NO_VALUE = "Không có";
    private static final String STATS_PREFIX = "thongke_";
    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})");
    private static final Pattern YMD = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})");

    private final ViewQueryService viewQueryService;
    private final com.td.application.documents.DynamicDocumentFieldsService dynamicDocumentFieldsService;

    private enum Order { TEXT, NUMBER_ASC, NUMBER_DESC }

    private record Dimension(Function<Row, List<String>> labels, Order order) {
    }

    private record Row(DuThaoDocument document, Map<String, Object> fields) {
    }

    private record Definition(List<Dimension> dimensions, boolean signerOnly, String countField, int countFrom, int countTo) {
        Definition(List<Dimension> dimensions) {
            this(dimensions, false, null, 0, -1);
        }
    }

    private static final Map<String, Definition> DEFINITIONS = buildDefinitions();

    public static boolean handles(String viewname) {
        return DEFINITIONS.containsKey(normalize(viewname));
    }

    public PaginationResponse<Map<String, Object>> execute(ViewQueryRequest request) {
        Definition definition = DEFINITIONS.get(normalize(request.getViewname()));
        if (definition == null) {
            throw new IllegalArgumentException("View nhóm dự thảo chưa được hỗ trợ: " + request.getViewname());
        }
        Node root = buildTree(definition, viewQueryService.findDuThaoOrgDocuments(request.getSearch(), MAX_DOCUMENTS));
        int pageNumber = Math.max(1, request.getPageNumber());
        int pageSize = Math.min(Math.max(1, request.getPageSize()), 200);

        if (Boolean.TRUE.equals(request.getIschild())) {
            Node node = find(root, request.getNavposition());
            List<DuThaoDocument> members = node == null ? List.of() : node.documents;
            int from = Math.min((pageNumber - 1) * pageSize, members.size());
            int to = Math.min(from + pageSize, members.size());
            List<Map<String, Object>> items = members.subList(from, to).stream()
                .map(document -> viewQueryService.toDuThaoListFields(document, request.getItems()))
                .toList();
            return page(items, pageNumber, pageSize, members.size());
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        int index = 1;
        for (Node child : root.children.values()) {
            groups.add(toMap(child, String.valueOf(index++), definition));
        }
        return page(groups, 1, Math.max(1, groups.size()), groups.size());
    }

    // ===== Cây nhóm =====

    private static final class Node {
        private final String name;
        private final Map<String, Node> children = new LinkedHashMap<>();
        private final List<DuThaoDocument> documents = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();

        private Node(String name) {
            this.name = name;
        }
    }

    private Node buildTree(Definition definition, List<DuThaoDocument> documents) {
        Node root = new Node("");
        for (DuThaoDocument document : documents) {
            Row row = new Row(document, parseFields(document));
            if (definition.signerOnly() && text(row.fields().get("user_signer")).isEmpty()) {
                continue;
            }
            add(root, row, definition.dimensions(), 0);
        }
        sort(root, definition.dimensions(), 0);
        return root;
    }

    // Field đa trị (mảng) vào nhiều nhóm — như separatemultiplevalues Domino.
    private static void add(Node node, Row row, List<Dimension> dimensions, int level) {
        node.rows.add(row);
        if (level == dimensions.size()) {
            node.documents.add(row.document());
            return;
        }
        for (String label : dimensions.get(level).labels().apply(row)) {
            add(node.children.computeIfAbsent(label, Node::new), row, dimensions, level + 1);
        }
    }

    private static void sort(Node node, List<Dimension> dimensions, int level) {
        if (level >= dimensions.size() || node.children.isEmpty()) {
            return;
        }
        List<Node> sorted = new ArrayList<>(node.children.values());
        sorted.sort(comparator(dimensions.get(level).order()));
        node.children.clear();
        sorted.forEach(child -> node.children.put(child.name, child));
        sorted.forEach(child -> sort(child, dimensions, level + 1));
    }

    private static Comparator<Node> comparator(Order order) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        Comparator<Node> noValueLast = Comparator.comparing(node -> NO_VALUE.equals(node.name));
        Comparator<Node> byValue = switch (order) {
            case NUMBER_ASC -> Comparator.comparingInt(node -> number(node.name));
            case NUMBER_DESC -> Comparator.<Node>comparingInt(node -> number(node.name)).reversed();
            case TEXT -> (a, b) -> collator.compare(a.name, b.name);
        };
        return noValueLast.thenComparing(byValue);
    }

    private static Node find(Node root, String navposition) {
        if (navposition == null || navposition.isBlank()) {
            return null;
        }
        Node node = root;
        for (String segment : navposition.trim().split("\\.")) {
            int index = number(segment);
            if (index <= 0) {
                break;
            }
            List<Node> children = new ArrayList<>(node.children.values());
            if (index > children.size()) {
                return null;
            }
            node = children.get(index - 1);
        }
        return node == root ? null : node;
    }

    private static Map<String, Object> toMap(Node node, String key, Definition definition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("name", node.name);
        map.put("count", node.rows.size());
        if (definition.countField() != null) {
            for (int value = definition.countFrom(); value <= definition.countTo(); value++) {
                String expected = String.valueOf(value);
                long count = node.rows.stream()
                    .filter(row -> expected.equals(countValue(row, definition.countField())))
                    .count();
                map.put("col_" + (11 + value - definition.countFrom()), Map.of("value", count));
            }
        }
        List<Map<String, Object>> children = new ArrayList<>();
        int index = 1;
        for (Node child : node.children.values()) {
            children.add(toMap(child, key + "." + index++, definition));
        }
        map.put("children", children);
        return map;
    }

    // status rỗng tính như "0" (Đang soạn thảo) — đúng công thức @If(status="0" | status="";1;0) của Domino.
    private static String countValue(Row row, String field) {
        String value = "status".equals(field) ? text(row.document().getStatus()) : text(row.fields().get(field));
        return value.isEmpty() && "status".equals(field) ? "0" : value;
    }

    // ===== Định nghĩa view =====

    private static Map<String, Definition> buildDefinitions() {
        Dimension signer = field("signer", Order.TEXT);
        Dimension creator = field("creator", Order.TEXT);
        Dimension unit = field("unit_in_charge", Order.TEXT);
        Dimension documentType = field("document_type", Order.TEXT);
        Dimension status = new Dimension(row -> List.of(orNoValue(text(row.document().getStatus()))), Order.TEXT);

        Map<String, Definition> definitions = new LinkedHashMap<>();
        // View nhóm (năm/tháng giảm dần).
        definitions.put("vwgroup_by_signer", new Definition(List.of(signer,
            year(true, Order.NUMBER_DESC), month(true, Order.NUMBER_DESC)), true, null, 0, -1));
        definitions.put("vwgroup_by_creator", new Definition(List.of(creator,
            year(false, Order.NUMBER_DESC), month(false, Order.NUMBER_DESC))));
        definitions.put("vwgroup_by_status", new Definition(List.of(status,
            year(false, Order.NUMBER_DESC), month(false, Order.NUMBER_DESC))));
        definitions.put("vwgroup_by_unit_in_charge", new Definition(List.of(unit,
            year(false, Order.NUMBER_DESC), month(false, Order.NUMBER_DESC))));
        definitions.put("vwgroup_by_document_type", new Definition(List.of(documentType,
            year(false, Order.NUMBER_DESC), month(false, Order.NUMBER_DESC))));
        definitions.put("vwgroup_by_created_date", new Definition(List.of(
            year(false, Order.NUMBER_DESC), month(false, Order.NUMBER_DESC), day(Order.NUMBER_DESC))));
        definitions.put("vwreport_by_unit_in_charge", new Definition(List.of(
            year(false, Order.NUMBER_DESC), month(false, Order.NUMBER_DESC), unit)));
        // Thống kê (sortorder 1 = tăng dần, năm/tháng theo create_date).
        Definition byUnit = new Definition(List.of(unit, year(true, Order.NUMBER_ASC), month(true, Order.NUMBER_ASC)));
        definitions.put(STATS_PREFIX + "donvi", byUnit);
        definitions.put(STATS_PREFIX + "theodonvi", byUnit);
        definitions.put(STATS_PREFIX + "chuyenvien", new Definition(List.of(creator,
            year(true, Order.NUMBER_ASC), month(true, Order.NUMBER_ASC))));
        definitions.put(STATS_PREFIX + "loaivanban", new Definition(List.of(documentType,
            year(true, Order.NUMBER_ASC), month(true, Order.NUMBER_ASC))));
        definitions.put(STATS_PREFIX + "thoigian", new Definition(List.of(
            year(true, Order.NUMBER_ASC), month(true, Order.NUMBER_ASC), unit, documentType)));
        definitions.put(STATS_PREFIX + "theodonvinguoixuly", new Definition(List.of(unit, signer),
            false, "status", 0, 9));
        definitions.put(STATS_PREFIX + "danhgiavanban", new Definition(List.of(creator),
            false, "draft_rate", 0, 3));
        return Map.copyOf(definitions);
    }

    private static Dimension field(String name, Order order) {
        return new Dimension(row -> values(row.fields().get(name)), order);
    }

    // fromCreateDate = @Year(create_date) (field ngày dự thảo, lùi về ngày tạo bản ghi nếu trống); ngược lại @Created.
    private static Dimension year(boolean fromCreateDate, Order order) {
        return new Dimension(row -> datePart(row, fromCreateDate, 0), order);
    }

    private static Dimension month(boolean fromCreateDate, Order order) {
        return new Dimension(row -> datePart(row, fromCreateDate, 1), order);
    }

    private static Dimension day(Order order) {
        return new Dimension(row -> datePart(row, false, 2), order);
    }

    private static List<String> datePart(Row row, boolean fromCreateDate, int part) {
        int[] ymd = fromCreateDate ? parseDate(text(row.fields().get("create_date"))) : null;
        if (ymd == null) {
            LocalDateTime created = row.document().getCreatedOn();
            ymd = created == null ? null : new int[] {created.getYear(), created.getMonthValue(), created.getDayOfMonth()};
        }
        return List.of(ymd == null ? NO_VALUE : String.valueOf(ymd[part]));
    }

    static int[] parseDate(String value) {
        Matcher dmy = DMY.matcher(value);
        if (dmy.find()) {
            return new int[] {Integer.parseInt(dmy.group(3)), Integer.parseInt(dmy.group(2)), Integer.parseInt(dmy.group(1))};
        }
        Matcher ymd = YMD.matcher(value);
        if (ymd.find()) {
            return new int[] {Integer.parseInt(ymd.group(1)), Integer.parseInt(ymd.group(2)), Integer.parseInt(ymd.group(3))};
        }
        return null;
    }

    // ===== Tiện ích =====

    private Map<String, Object> parseFields(DuThaoDocument document) {
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        return fields == null ? Map.of() : fields;
    }

    private static List<String> values(Object value) {
        if (value instanceof List<?> list) {
            List<String> labels = list.stream().map(DuThaoGroupViewQuery::text).filter(s -> !s.isEmpty()).distinct().toList();
            return labels.isEmpty() ? List.of(NO_VALUE) : labels;
        }
        return List.of(orNoValue(text(value)));
    }

    private static String orNoValue(String value) {
        return value.isEmpty() ? NO_VALUE : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
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
