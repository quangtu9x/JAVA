package com.td.worker.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chuyển 1 dòng Postgres (JSON từ {@code to_jsonb}) thành document ES, và sinh mapping index theo KIỂU
 * CỘT THẬT (information_schema) thay vì để ES tự đoán:
 * <ul>
 *   <li>ES đoán kiểu theo dòng ĐẦU TIÊN — 1 cột text tình cờ chứa chuỗi giống ngày sẽ bị khoá thành
 *   {@code date}, các dòng sau bị từ chối và kẹt hàng đợi mãi.</li>
 *   <li>Cột {@code jsonb} (dynamic_fields, files, *_obj...) → {@code flattened}: số khoá tuỳ ý không làm
 *   bùng số field của index; giá trị không phải object được bọc {@code {"value": ...}} để luôn cùng dạng.</li>
 * </ul>
 * Cột mới thêm sau khi tạo index: theo dynamic template (chuỗi → text + keyword, object → flattened),
 * tắt đoán ngày/số.
 */
public class SearchDocumentMapper {

    static final String FIELD_SEARCH_TEXT = "search_text";
    static final String FIELD_TENANT = "sync_tenant";
    static final String FIELD_ENTITY = "sync_entity";
    static final String FIELD_SYNCED_AT = "sync_at";
    static final String ANALYZER = "vi_folding";
    static final String FIELD_NORM_DYN = "norm_dyn";
    static final String FIELD_NORM_COL = "norm_col";
    static final String FIELD_DATE_DYN = "date_dyn";
    static final String FIELD_DATE_COL = "date_col";
    static final String FIELD_KEYWORD = "kw";
    /** Nội dung tệp đính kèm đã trích (Tika, chưa OCR) — td-web tìm từ khoá trên trường này khi bật. */
    static final String FIELD_FILES_TEXT = "files_text";
    static final String FIELD_SORT_PROMULGATION = "sort_promulgation_date";
    static final String FIELD_SORT_NUMBER = "sort_document_number";
    private static final int MAX_TOKENS = 200;

    private final ObjectMapper objectMapper;
    private final SearchSyncProperties props;

    public SearchDocumentMapper(ObjectMapper objectMapper, SearchSyncProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
    }

    public ObjectNode toDocument(String tenantId, String entityType, String rowJson, Map<String, String> columnTypes) {
        return toDocument(tenantId, entityType, rowJson, columnTypes, null);
    }

    /** @param filesText nội dung tệp đính kèm đã trích ("tên tệp: nội dung"...) — null nếu không có. */
    public ObjectNode toDocument(String tenantId, String entityType, String rowJson, Map<String, String> columnTypes,
                                 String filesText) {
        JsonNode row;
        try {
            row = objectMapper.readTree(rowJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Dòng dữ liệu không phải JSON hợp lệ", ex);
        }
        ObjectNode doc = objectMapper.createObjectNode();
        Set<String> excluded = Set.copyOf(props.getExcludedColumns());
        StringBuilder text = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> fields = row.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String column = field.getKey();
            if (excluded.contains(column)) {
                continue;
            }
            JsonNode value = field.getValue();
            if (isJsonColumn(columnTypes.get(column)) && !value.isNull() && !value.isObject()) {
                ObjectNode wrapped = objectMapper.createObjectNode();
                wrapped.set("value", value);
                value = wrapped;
            }
            doc.set(column, value);
            appendText(value, text, props.getSearchTextMaxChars());
        }
        doc.put(FIELD_SEARCH_TEXT, text.toString());
        doc.put(FIELD_TENANT, tenantId);
        doc.put(FIELD_ENTITY, entityType);
        doc.put(FIELD_SYNCED_AT, Instant.now().toString());
        addSearchFields(doc, row, columnTypes, excluded);
        if (filesText != null && !filesText.isBlank()) {
            doc.put(FIELD_FILES_TEXT, filesText);
        }
        return doc;
    }

    /**
     * 17/10/2026 (tìm kiếm nâng cao đọc ES — docs/analysis/27 §6): các trường CHUẨN HOÁ để td-web lọc đúng như SQL
     * (ViewQueryService) mà không phụ thuộc dạng lưu của từng dòng:
     * <ul>
     *   <li>{@code norm_dyn.<key>} / {@code norm_col.<cột>}: mảng token chữ thường (giá trị nguyên vẹn + từng phần tách
     *   theo {@code ;}/{@code ,} + biến thể bỏ khoảng trắng; mảng JSON → từng phần tử) — thay cho so bằng /
     *   {@code @>} / "member" {@code ';x;'} của SQL.</li>
     *   <li>{@code date_dyn.<key>} / {@code date_col.<cột>}: {@code yyyy-MM-dd} (từ {@code dd/MM/yyyy...} hoặc ISO) —
     *   so khoảng ngày theo thứ tự chữ.</li>
     *   <li>{@code kw.<key>}: chữ của các cột tìm từ khoá ({@link SearchSyncProperties#getKeywordFields()}),
     *   {@code subject}/{@code title} gộp cả cột {@code title}.</li>
     *   <li>{@code sort_promulgation_date}, {@code sort_document_number}: thứ tự danh sách VB đi.</li>
     * </ul>
     */
    private void addSearchFields(ObjectNode doc, JsonNode row, Map<String, String> columnTypes, Set<String> excluded) {
        JsonNode dynamic = row.path("dynamic_fields");
        ObjectNode normDyn = doc.putObject(FIELD_NORM_DYN);
        ObjectNode dateDyn = doc.putObject(FIELD_DATE_DYN);
        if (dynamic.isObject()) {
            dynamic.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                var tokens = normTokens(entry.getValue());
                if (!tokens.isEmpty()) {
                    var array = normDyn.putArray(key);
                    tokens.forEach(array::add);
                }
                String iso = isoDate(entry.getValue());
                if (iso != null) {
                    dateDyn.put(key, iso);
                }
            });
        }
        ObjectNode normCol = doc.putObject(FIELD_NORM_COL);
        ObjectNode dateCol = doc.putObject(FIELD_DATE_COL);
        row.fields().forEachRemaining(entry -> {
            String column = entry.getKey();
            String type = columnTypes.get(column);
            if (excluded.contains(column) || isJsonColumn(type) || entry.getValue().isContainerNode()) {
                return;
            }
            if (type != null && (type.startsWith("timestamp") || type.equals("date"))) {
                String iso = isoDate(entry.getValue());
                if (iso != null) {
                    dateCol.put(column, iso);
                }
                return;
            }
            var tokens = normTokens(entry.getValue());
            if (!tokens.isEmpty()) {
                var array = normCol.putArray(column);
                tokens.forEach(array::add);
            }
        });

        ObjectNode kw = doc.putObject(FIELD_KEYWORD);
        String titleColumn = row.path("title").isTextual() ? row.path("title").asText() : "";
        for (String key : props.getKeywordFields()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            StringBuilder value = new StringBuilder(textOf(dynamic.path(key.trim())));
            if ("subject".equals(key.trim()) || "title".equals(key.trim())) {
                if (!titleColumn.isBlank()) {
                    value.append(value.isEmpty() ? "" : " ").append(titleColumn);
                }
            }
            if (!value.isEmpty()) {
                kw.put(key.trim(), value.toString());
            }
        }

        String promulgation = isoDate(dynamic.path("promulgation_date"));
        if (promulgation != null) {
            doc.put(FIELD_SORT_PROMULGATION, promulgation);
        }
        Long number = leadingNumber(dynamic.path("document_number"));
        if (number != null) {
            doc.put(FIELD_SORT_NUMBER, number);
        }
    }

    /** Token chuẩn hoá của 1 giá trị (xem {@link #addSearchFields}). */
    static java.util.List<String> normTokens(JsonNode value) {
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        collectTokens(value, tokens);
        return tokens.stream().limit(MAX_TOKENS).toList();
    }

    private static void collectTokens(JsonNode value, java.util.Set<String> tokens) {
        if (value == null || value.isNull() || value.isMissingNode() || value.isObject()) {
            return;
        }
        if (value.isArray()) {
            value.forEach(element -> collectTokens(element, tokens));
            return;
        }
        String raw = value.asText();
        if (value.isNumber() && value.canConvertToLong() && value.asDouble() == Math.rint(value.asDouble())) {
            tokens.add(String.valueOf(value.asLong())); // 3.0 → cả "3" và "3.0"
        }
        String whole = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (whole.isEmpty()) {
            return;
        }
        tokens.add(whole);
        for (String part : whole.split("[;,]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
                String compact = trimmed.replaceAll("\\s+", "");
                if (!compact.isEmpty()) {
                    tokens.add(compact);
                }
            }
        }
    }

    private static final java.util.regex.Pattern DMY = java.util.regex.Pattern.compile("^\\s*(\\d{2})/(\\d{2})/(\\d{4})");
    private static final java.util.regex.Pattern YMD = java.util.regex.Pattern.compile("^\\s*(\\d{4})-(\\d{2})-(\\d{2})");

    /** {@code dd/MM/yyyy[...]} hoặc {@code yyyy-MM-dd[...]} → {@code yyyy-MM-dd}; khác → null. */
    static String isoDate(JsonNode value) {
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        var dmy = DMY.matcher(text);
        if (dmy.find()) {
            return dmy.group(3) + "-" + dmy.group(2) + "-" + dmy.group(1);
        }
        var ymd = YMD.matcher(text);
        if (ymd.find()) {
            return ymd.group(1) + "-" + ymd.group(2) + "-" + ymd.group(3);
        }
        return null;
    }

    private static Long leadingNumber(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode() || value.isContainerNode()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        var matcher = java.util.regex.Pattern.compile("^\\s*(\\d{1,18})").matcher(value.asText());
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static String textOf(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode() || value.isObject()) {
            return "";
        }
        if (value.isArray()) {
            StringBuilder joined = new StringBuilder();
            value.forEach(element -> {
                String part = textOf(element);
                if (!part.isEmpty()) {
                    joined.append(joined.isEmpty() ? "" : " ").append(part);
                }
            });
            return joined.toString();
        }
        return value.asText().trim();
    }

    /** Body tạo index (settings + mappings). */
    public ObjectNode indexDefinition(Map<String, String> columnTypes) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode index = root.putObject("settings").putObject("index");
        index.put("number_of_shards", props.getNumberOfShards());
        index.put("number_of_replicas", props.getNumberOfReplicas());
        index.put("refresh_interval", props.getRefreshInterval());
        ObjectNode mappingSettings = index.putObject("mapping");
        mappingSettings.putObject("total_fields").put("limit", props.getTotalFieldsLimit());
        // Giá trị sai kiểu (vd số/ngày lỗi) bị bỏ qua ở field đó thay vì từ chối cả document.
        mappingSettings.put("ignore_malformed", true);
        ObjectNode analysis = index.putObject("analysis");
        ObjectNode filter = analysis.putObject("filter");
        filter.putObject("vi_ascii").put("type", "asciifolding").put("preserve_original", true);
        ObjectNode analyzer = analysis.putObject("analyzer").putObject(ANALYZER);
        analyzer.put("type", "custom").put("tokenizer", "standard");
        analyzer.putArray("filter").add("lowercase").add("vi_ascii");
        ObjectNode normalizer = analysis.putObject("normalizer").putObject("lowercase_folding");
        normalizer.put("type", "custom");
        normalizer.putArray("filter").add("lowercase").add("asciifolding");

        ObjectNode mappings = root.putObject("mappings");
        mappings.put("date_detection", false);
        mappings.put("numeric_detection", false);
        var templates = mappings.putArray("dynamic_templates");
        templates.addObject().putObject("objects_as_flattened")
            .put("match_mapping_type", "object")
            .putObject("mapping").put("type", "flattened");
        templates.addObject().putObject("strings_as_text")
            .put("match_mapping_type", "string")
            .set("mapping", textMapping());

        ObjectNode properties = mappings.putObject("properties");
        for (Map.Entry<String, String> column : columnTypes.entrySet()) {
            if (props.getExcludedColumns().contains(column.getKey())) {
                continue;
            }
            properties.set(column.getKey(), mappingFor(column.getKey(), column.getValue()));
        }
        properties.putObject("id").put("type", "keyword");
        properties.putObject(FIELD_SEARCH_TEXT).put("type", "text").put("analyzer", ANALYZER);
        properties.putObject(FIELD_TENANT).put("type", "keyword");
        properties.putObject(FIELD_ENTITY).put("type", "keyword");
        properties.putObject(FIELD_SYNCED_AT).put("type", "date");
        // Trường chuẩn hoá cho tìm kiếm nâng cao (addSearchFields). ignore_above: giá trị dài (nội dung) không
        // dùng để lọc so bằng, tránh vượt giới hạn 32766 byte của keyword.
        for (String field : List.of(FIELD_NORM_DYN, FIELD_NORM_COL, FIELD_DATE_DYN, FIELD_DATE_COL)) {
            properties.putObject(field).put("type", "flattened").put("ignore_above", 1024);
        }
        properties.putObject(FIELD_KEYWORD).put("type", "object").put("dynamic", true);
        properties.putObject(FIELD_FILES_TEXT).put("type", "text").put("analyzer", ANALYZER);
        properties.putObject(FIELD_SORT_PROMULGATION).put("type", "date").put("format", "yyyy-MM-dd");
        properties.putObject(FIELD_SORT_NUMBER).put("type", "long");
        return root;
    }

    private ObjectNode mappingFor(String column, String udtName) {
        String type = udtName == null ? "" : udtName.toLowerCase();
        ObjectNode mapping = objectMapper.createObjectNode();
        if ("id".equals(column) || type.equals("uuid") || type.equals("_uuid")) {
            return mapping.put("type", "keyword");
        }
        if (isJsonColumn(type)) {
            return mapping.put("type", "flattened");
        }
        switch (stripArray(type)) {
            case "timestamp", "timestamptz", "date" -> {
                return mapping.put("type", "date").put("format", "strict_date_optional_time||epoch_millis");
            }
            case "int2", "int4" -> {
                return mapping.put("type", "integer");
            }
            case "int8" -> {
                return mapping.put("type", "long");
            }
            case "float4", "float8", "numeric" -> {
                return mapping.put("type", "double");
            }
            case "bool" -> {
                return mapping.put("type", "boolean");
            }
            case "bytea" -> {
                return mapping.put("type", "binary");
            }
            default -> {
                return textMapping();
            }
        }
    }

    private ObjectNode textMapping() {
        ObjectNode mapping = objectMapper.createObjectNode();
        mapping.put("type", "text").put("analyzer", ANALYZER);
        mapping.putObject("fields").putObject("keyword")
            .put("type", "keyword").put("ignore_above", 512).put("normalizer", "lowercase_folding");
        return mapping;
    }

    static boolean isJsonColumn(String udtName) {
        return udtName != null && (udtName.equalsIgnoreCase("jsonb") || udtName.equalsIgnoreCase("json"));
    }

    private static String stripArray(String udtName) {
        return udtName.startsWith("_") ? udtName.substring(1) : udtName;
    }

    private static void appendText(JsonNode value, StringBuilder text, int maxChars) {
        if (text.length() >= maxChars || value == null || value.isNull()) {
            return;
        }
        if (value.isTextual()) {
            String s = value.asText();
            if (!s.isBlank() && !looksLikeId(s)) {
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append(s, 0, Math.min(s.length(), maxChars - text.length()));
            }
        } else if (value.isContainerNode()) {
            Collection<JsonNode> children = new java.util.ArrayList<>();
            value.elements().forEachRemaining(children::add);
            for (JsonNode child : children) {
                appendText(child, text, maxChars);
            }
        }
    }

    // Bỏ UUID khỏi search_text (không ai tìm theo chữ, chỉ làm phình index).
    private static boolean looksLikeId(String s) {
        return s.length() == 36 && s.charAt(8) == '-' && s.charAt(13) == '-' && s.charAt(18) == '-' && s.charAt(23) == '-';
    }
}
