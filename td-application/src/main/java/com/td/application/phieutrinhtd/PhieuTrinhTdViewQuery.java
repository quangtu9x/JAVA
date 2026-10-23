package com.td.application.phieutrinhtd;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documentlinks.DocumentLinkService;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.domain.documentlinks.DocumentLinkType;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.td.application.phieutrinhtd.PhieuTrinhTdListCriteria.CoordinatedStatus;
import static com.td.application.phieutrinhtd.PhieuTrinhTdListCriteria.MemberRule;
import static com.td.domain.phieutrinhtd.PhieuTrinhTdDocument.*;

/**
 * 06/10/2026 (cutover FE phiếu trình) - danh sách phiếu trình cho {@code phieutrinhtd/GetView}. Nhận ĐÚNG
 * tên view Domino FE đang gửi ({@code docs/views_domino/view_phieutrinh.dxl}, 26 view) để FE chỉ đổi
 * endpoint, và giữ tương thích tên view Java cũ ({@code phieutrinhtd_*}, port 09/09/2026).
 *
 * <p>Khoá {@code $username$} / thiếu khoá = người đang đăng nhập. Mọi view cá nhân (trừ thùng rác) chỉ
 * lấy phiếu trình chưa kết thúc ({@code storaged != "1"}) như DXL; {@code vwStoraged} ngược lại. Có hỗ
 * trợ từ khoá ({@code keyword} + {@code columns}). Mới tạo nhất trước.</p>
 *
 * <p>Tách khỏi {@code ViewQueryService} (rẽ nhánh theo dbType trước switch chung) vì nhiều tên view
 * Domino TRÙNG với module khác ({@code vwUser_Creator}, {@code vwUser_All}, {@code vwTrash}...).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhieuTrinhTdViewQuery {

    public static final String DB_TYPE = "phieutrinhtd";
    public static final String RES_DB_TYPE = "phieutrinhtd_res";
    private static final String USERNAME_TOKEN = "$username$";
    private static final String FORM_QUATRINH = "quatrinh";
    private static final String FORM_OPINION = "document_coordinated_opinion";
    private static final List<String> DEFAULT_KEYWORD_COLUMNS = List.of("title", "code", "description", "creator");

    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final PhieuTrinhTdProcessDocumentRepository phieuTrinhTdProcessDocumentRepository;
    private final PhieuTrinhTdFieldMapper fieldMapper;
    private final DocumentLinkService documentLinkService;
    private final CurrentUserProvider currentUserProvider;
    // 17/10/2026: tìm kiếm "timkiem" đọc index tìm kiếm trước (docs/analysis/27), không dùng được thì SQL.
    private final com.td.application.search.AdvancedSearchIndex advancedSearchIndex;

    public static boolean handles(String dbType) {
        return DB_TYPE.equalsIgnoreCase(dbType) || RES_DB_TYPE.equalsIgnoreCase(dbType);
    }

    public PaginationResponse<Map<String, Object>> execute(ViewQueryRequest request) {
        String viewname = request.getViewname() == null ? "" : request.getViewname().trim().toLowerCase(Locale.ROOT);
        Pageable pageable = PageRequest.of(Math.max(1, request.getPageNumber()) - 1,
            Math.min(Math.max(1, request.getPageSize()), 200));
        List<String> keys = request.getKeys() == null ? List.of() : request.getKeys();
        String firstKey = keys.isEmpty() ? null : keys.get(0);

        // Danh sách con (bảng phieutrinhtd_process_documents) - khoá = id phiếu trình.
        if ("vwquatrinh_submission_dossier_id".equals(viewname)) {
            return processes(firstKey, FORM_QUATRINH, request.getItems(), pageable);
        }
        if ("vwcoordinated_opinion_submission_dossier_id".equals(viewname)) {
            return processes(firstKey, FORM_OPINION, request.getItems(), pageable);
        }

        if (GROUP_VIEWS.containsKey(viewname)) {
            return group(viewname, request, pageable);
        }

        var criteria = PhieuTrinhTdListCriteria.builder()
            .keyword(request.getKeyword())
            .keywordColumns(hasText(request.getKeyword())
                && (request.getColumns() == null || request.getColumns().isEmpty())
                ? DEFAULT_KEYWORD_COLUMNS : request.getColumns());
        String me = resolveUser(firstKey);
        if (me == null && !"vwdocument_profiles_id".equals(viewname)) {
            // Bugfix bảo mật 17/10/2026: khoá không phải chính người đăng nhập (hoặc chưa đăng nhập) → rỗng,
            // KHÔNG để quy tắc thành viên chạy với member = null. Ngoại lệ: vwDocument_profiles_id có khoá là
            // id HỒ SƠ (không lọc theo người dùng).
            return toResponse(List.of(), new PageImpl<>(List.of(), pageable, 0));
        }
        if ("timkiem".equals(viewname)) {
            PaginationResponse<Map<String, Object>> fromIndex = searchViaIndex(me, request, pageable);
            if (fromIndex != null) {
                return fromIndex;
            }
        }

        switch (viewname) {
            case "vwuser_all", "phieutrinhtd_toanbo" -> criteria.member(me).memberRule(MemberRule.THAM_GIA).storaged(false);
            case "vwuser_creator" -> creator(criteria, me);
            case "vwuser_creator_0", "phieutrinhtd_dangsoan" -> creator(criteria, me, STATUS_DANG_SOAN_THAO);
            case "vwuser_creator_2", "phieutrinhtd_trinhthongqua" -> creator(criteria, me, STATUS_TRINH_THONG_QUA);
            case "vwuser_creator_3", "phieutrinhtd_khongthongqua" -> creator(criteria, me, STATUS_KHONG_THONG_QUA);
            case "vwuser_creator_4", "phieutrinhtd_dathongqua" -> creator(criteria, me, STATUS_DA_THONG_QUA);
            case "vwuser_creator_5_25", "phieutrinhtd_trinhduyet" ->
                creator(criteria, me, STATUS_TRINH_DUYET_CAP1, STATUS_TRINH_DUYET_CAP2);
            case "vwuser_creator_6_26", "phieutrinhtd_khongduyet" ->
                creator(criteria, me, STATUS_KHONG_DUYET_CAP1, STATUS_KHONG_DUYET_CAP2);
            case "vwuser_creator_7_27", "phieutrinhtd_daduyet" ->
                creator(criteria, me, STATUS_DA_DUYET_CAP1, STATUS_DA_DUYET_CAP2);
            case "vwuser_nguoithongqua_2", "phieutrinhtd_chothongqua" -> criteria.member(me)
                .memberRule(MemberRule.NGUOI_THONG_QUA).statuses(List.of(STATUS_TRINH_THONG_QUA)).storaged(false);
            case "vwuser_nguoiduyet_5_25", "phieutrinhtd_choduyet" ->
                criteria.member(me).memberRule(MemberRule.NGUOI_DUYET_DANG_CHO).storaged(false);
            case "vwuser_coordinator" -> criteria.member(me).memberRule(MemberRule.PHOI_HOP).storaged(false);
            case "vwuser_coordinator_chua", "phieutrinhtd_thamkhao" ->
                criteria.member(me).memberRule(MemberRule.PHOI_HOP_CHUA).storaged(false);
            case "vwuser_coordinator_da" -> criteria.member(me).memberRule(MemberRule.PHOI_HOP_DA).storaged(false);
            // Gửi tham khảo: khoá 2 (tuỳ chọn) "1" = mọi người phối hợp đã cho ý kiến, "0" = còn người chưa.
            case "vwuser_creator_coordinated_status" -> {
                String status = keys.size() > 1 ? keys.get(1) : null;
                creator(criteria, me).coordinatedStatus(
                    "1".equals(status) ? CoordinatedStatus.DA_DU
                        : "0".equals(status) ? CoordinatedStatus.CON_CHUA : CoordinatedStatus.CO_PHOI_HOP);
            }
            case "vwstoraged" -> criteria.member(me).memberRule(MemberRule.THAM_GIA).storaged(true);
            case "vwtrash", "phieutrinhtd_thungrac" -> criteria.member(me).memberRule(MemberRule.CREATOR).trash(true);
            // Tìm kiếm (thay qlvb/search?databasetype=phieutrinhtd): phiếu trình người dùng có tham gia,
            // kể cả đã kết thúc.
            case "timkiem" -> applyAdvancedSearch(criteria.member(me).memberRule(MemberRule.THAM_GIA), request);
            // Tab "Phiếu trình" của hồ sơ công việc - bảng document_links (hoso_phieutrinh), khoá = id hồ sơ.
            case "vwdocument_profiles_id" -> criteria.ids(linkedToHoSo(firstKey));
            default -> throw new IllegalArgumentException("Khong ho tro viewname phieu trinh: " + request.getViewname());
        }

        Page<PhieuTrinhTdDocument> page = phieuTrinhTdDocumentRepository.findByCriteria(criteria.build(), pageable);
        boolean withFiles = wantsField(request.getItems(), "files");
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> fieldMapper.filter(fieldMapper.toDocumentMap(document, withFiles), request.getItems()))
            .toList();
        return toResponse(content, page);
    }

    // Tìm kiếm nâng cao (CollapsePhieuTrinhTD.jsx gửi "search"): create_date = [từ, đến] dd/MM/yyyy,
    // status = mã trạng thái, các field văn bản còn lại = "chứa". form/author/keyword là meta Domino.
    // Bugfix 17/10/2026: "search" (true/false) do trang tìm kiếm chung chèn vào — trước đây thành LIKE '%true%'
    // trên dynamic_fields->>'search' nên tìm kiếm phiếu trình luôn RỖNG.
    private static final java.util.Set<String> SEARCH_META_KEYS = java.util.Set.of("form", "author", "keyword", "create_date", "status", "search");
    private static final java.time.format.DateTimeFormatter SEARCH_DATE = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private PhieuTrinhTdListCriteria.PhieuTrinhTdListCriteriaBuilder applyAdvancedSearch(
            PhieuTrinhTdListCriteria.PhieuTrinhTdListCriteriaBuilder criteria, ViewQueryRequest request) {
        Map<String, Object> search = request.getSearch() == null ? Map.of() : request.getSearch();
        Object keyword = search.get("keyword");
        if (!hasText(request.getKeyword()) && keyword != null && hasText(keyword.toString())) {
            criteria.keyword(keyword.toString()).keywordColumns(
                request.getColumns() == null || request.getColumns().isEmpty() ? DEFAULT_KEYWORD_COLUMNS : request.getColumns());
        }
        if (search.get("create_date") instanceof List<?> range && range.size() == 2) {
            criteria.createdFrom(parseDate(range.get(0))).createdTo(parseDate(range.get(1)));
        }
        Object status = search.get("status");
        if (status != null && hasText(status.toString())) {
            criteria.statuses(List.of(status.toString().trim()));
        }
        Map<String, String> contains = new java.util.LinkedHashMap<>();
        search.forEach((key, value) -> {
            if (!SEARCH_META_KEYS.contains(key) && value != null && !(value instanceof Map) && !(value instanceof List)
                    && hasText(value.toString())) {
                contains.put(key, value.toString());
            }
        });
        return criteria.containsFields(contains);
    }

    private static java.time.LocalDate parseDate(Object value) {
        try {
            return value == null ? null : java.time.LocalDate.parse(value.toString().trim(), SEARCH_DATE);
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    // ===== Tra cứu nhóm (vwBy_* - view_phieutrinh.dxl, GroupTable.jsx) =====
    // Domino là view phân loại nhiều cấp; Java đơn giản hoá CÓ CHỦ ĐÍCH thành 1 cấp (cấp đầu của Domino:
    // năm tạo / người tạo / người thông qua / người duyệt / mã trạng thái - FE tự hiển thị nhãn). Phạm vi
    // = phiếu trình người dùng có tham gia (tương đương quyền đọc Notes), gồm cả đã kết thúc.
    private static final int GROUP_MAX_DOCUMENTS = 5000;
    private static final Map<String, java.util.function.Function<Map<String, Object>, List<String>>> GROUP_VIEWS = Map.of(
        "vwby_create_date", fields -> single(yearOf(fields.get("created_on"))),
        "vwby_creator", fields -> single(fields.get("creator")),
        "vwby_nguoi_thong_qua", fields -> single(fields.get("lanhdaovanphong")),
        "vwby_nguoi_duyet", fields -> {
            List<String> labels = new java.util.ArrayList<>(single(fields.get("lanhdao1")));
            single(fields.get("lanhdao2")).stream().filter(label -> !labels.contains(label)).forEach(labels::add);
            return labels;
        },
        "vwby_status", fields -> single(fields.get("status"))
    );

    private PaginationResponse<Map<String, Object>> group(String viewname, ViewQueryRequest request, Pageable pageable) {
        List<String> keys = request.getKeys() == null ? List.of() : request.getKeys();
        String me = resolveUser(keys.isEmpty() ? null : keys.get(0));
        var criteria = PhieuTrinhTdListCriteria.builder().member(me).memberRule(MemberRule.THAM_GIA).build();
        List<PhieuTrinhTdDocument> documents = phieuTrinhTdDocumentRepository
            .findByCriteria(criteria, PageRequest.of(0, GROUP_MAX_DOCUMENTS)).getContent();

        var labelOf = GROUP_VIEWS.get(viewname);
        Map<String, List<PhieuTrinhTdDocument>> groups = new java.util.TreeMap<>(
            "vwby_create_date".equals(viewname) ? java.util.Comparator.<String>reverseOrder() : String.CASE_INSENSITIVE_ORDER);
        for (PhieuTrinhTdDocument document : documents) {
            Map<String, Object> fields = fieldMapper.toDocumentMap(document, false);
            for (String label : labelOf.apply(fields)) {
                groups.computeIfAbsent(label, ignored -> new java.util.ArrayList<>()).add(document);
            }
        }
        List<String> labels = new java.util.ArrayList<>(groups.keySet());

        if (Boolean.TRUE.equals(request.getIschild())) {
            int index = parseGroupIndex(request.getNavposition());
            List<PhieuTrinhTdDocument> members = index >= 0 && index < labels.size() ? groups.get(labels.get(index)) : List.of();
            boolean withFiles = wantsField(request.getItems(), "files");
            List<Map<String, Object>> all = members.stream()
                .map(document -> fieldMapper.filter(fieldMapper.toDocumentMap(document, withFiles), request.getItems()))
                .toList();
            return pageOf(all, pageable);
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int index = 0; index < labels.size(); index++) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("key", String.valueOf(index + 1));
            row.put("name", labels.get(index));
            row.put("count", groups.get(labels.get(index)).size());
            row.put("children", List.of());
            rows.add(row);
        }
        return pageOf(rows, pageable);
    }

    // navposition = key nhóm ("3" hoặc "3.0" / "3.x" khi FE ghép) - lấy phần đầu.
    private static int parseGroupIndex(String navposition) {
        if (navposition == null || navposition.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(navposition.trim().split("\\.")[0]) - 1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static List<String> single(Object value) {
        return value == null || value.toString().isBlank() ? List.of() : List.of(value.toString().trim());
    }

    private static Object yearOf(Object createdOn) {
        return createdOn instanceof java.time.LocalDateTime time ? String.valueOf(time.getYear()) : null;
    }

    private static PaginationResponse<Map<String, Object>> pageOf(List<Map<String, Object>> all, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        Page<Map<String, Object>> page = new PageImpl<>(all.subList(from, to), pageable, all.size());
        return toResponse(page.getContent(), page);
    }

    private PhieuTrinhTdListCriteria.PhieuTrinhTdListCriteriaBuilder creator(
            PhieuTrinhTdListCriteria.PhieuTrinhTdListCriteriaBuilder criteria, String me, String... statuses) {
        return criteria.member(me).memberRule(MemberRule.CREATOR)
            .statuses(statuses.length == 0 ? null : List.of(statuses)).storaged(false);
    }

    private List<UUID> linkedToHoSo(String hosocvId) {
        UUID id = parseUuid(hosocvId);
        return id == null ? List.of() : documentLinkService.findTargetIds(id, List.of(DocumentLinkType.HOSO_PHIEUTRINH));
    }

    // Số dòng quá trình / ý kiến của 1 phiếu trình nhỏ -> lọc + phân trang trong bộ nhớ, mới nhất trước.
    private PaginationResponse<Map<String, Object>> processes(String parentId, String form, String items, Pageable pageable) {
        UUID id = parseUuid(parentId);
        if (id == null) {
            return toResponse(List.of(), new PageImpl<>(List.of(), pageable, 0));
        }
        boolean withFiles = wantsField(items, "files");
        List<Map<String, Object>> all = phieuTrinhTdProcessDocumentRepository.findByParentId(id).stream()
            .filter(process -> !process.isDeleted())
            .map(process -> fieldMapper.toProcessMap(process, withFiles))
            .filter(fields -> form.equalsIgnoreCase(String.valueOf(fields.getOrDefault("form", FORM_QUATRINH))))
            .map(fields -> fieldMapper.filter(fields, items))
            .toList();
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        Page<Map<String, Object>> page = new PageImpl<>(all.subList(from, to), pageable, all.size());
        return toResponse(page.getContent(), page);
    }

    /**
     * Bugfix bảo mật 17/10/2026: trước đây khoá khác "$username$" được dùng NGUYÊN (client đổi keys là xem được phiếu
     * trình người khác). Nay chỉ chấp nhận chính người đăng nhập; khác → null (trang rỗng).
     */
    private String resolveUser(String key) {
        String current = currentUserProvider.currentUsername().orElse(null);
        if (key == null || key.isBlank() || USERNAME_TOKEN.equals(key.trim())) {
            return current;
        }
        return current != null && current.equalsIgnoreCase(key.trim()) ? current : null;
    }

    // ===== Tìm kiếm qua index tìm kiếm (17/10/2026, docs/analysis/27) =====

    private PaginationResponse<Map<String, Object>> searchViaIndex(String me, ViewQueryRequest request, Pageable pageable) {
        var query = toIndexQuery(me, request);
        var hits = advancedSearchIndex.search(DB_TYPE, DB_TYPE, query, pageable.getPageNumber(), pageable.getPageSize())
            .orElse(null);
        if (hits == null) {
            return null;
        }
        Map<UUID, PhieuTrinhTdDocument> byId = new java.util.HashMap<>();
        if (!hits.ids().isEmpty()) {
            phieuTrinhTdDocumentRepository.findByCriteria(PhieuTrinhTdListCriteria.builder().ids(hits.ids()).build(),
                    PageRequest.of(0, hits.ids().size()))
                .forEach(document -> byId.put(document.getId(), document));
        }
        boolean withFiles = wantsField(request.getItems(), "files");
        List<Map<String, Object>> content = hits.ids().stream()
            .map(byId::get)
            .filter(java.util.Objects::nonNull)
            .map(document -> fieldMapper.filter(fieldMapper.toDocumentMap(document, withFiles), request.getItems()))
            .toList();
        return toResponse(content, new PageImpl<>(content, pageable, hits.total()));
    }

    /**
     * Cùng ngữ nghĩa SQL "timkiem": chưa xoá + người dùng THAM GIA (người tạo / lãnh đạo văn phòng khi đã trình cấp 2 /
     * người đồng ý / từ chối / lãnh đạo cấp 2 khi đã trình cấp 25 / lãnh đạo cấp 1 khi đã trình cấp 5 / người phối hợp)
     * + điều kiện nâng cao (create_date, status, các trường "chứa") + từ khoá.
     */
    com.td.application.search.AdvancedSearchQuery toIndexQuery(String me, ViewQueryRequest request) {
        String member = me.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        var query = new com.td.application.search.AdvancedSearchQuery();
        query.filter(new com.td.application.search.AdvancedSearchQuery.Not(new com.td.application.search.AdvancedSearchQuery.Exists(
            com.td.application.search.AdvancedSearchQuery.Source.COL, "deleted_on")));
        query.filter(com.td.application.search.AdvancedSearchQuery.anyOf(List.of(
            com.td.application.search.AdvancedSearchQuery.dyn("user_creator", member),
            whenSubmitted("trinh2_date", "user_lanhdaovanphong", member),
            com.td.application.search.AdvancedSearchQuery.dyn("user_receivers_agree", member),
            com.td.application.search.AdvancedSearchQuery.dyn("user_receivers_refuse", member),
            whenSubmitted("trinh25_date", "user_lanhdao2", member),
            whenSubmitted("trinh5_date", "user_lanhdao1", member),
            com.td.application.search.AdvancedSearchQuery.dyn("user_coordinator", member))));
        Map<String, Object> search = request.getSearch() == null ? Map.of() : request.getSearch();
        if (search.get("create_date") instanceof List<?> range && range.size() == 2) {
            var from = parseDate(range.get(0));
            var to = parseDate(range.get(1));
            if (from != null || to != null) {
                query.filter(new com.td.application.search.AdvancedSearchQuery.DateRange(
                    com.td.application.search.AdvancedSearchQuery.Source.COL, "created_on", from, to));
            }
        }
        Object status = search.get("status");
        if (status != null && hasText(status.toString())) {
            query.filter(com.td.application.search.AdvancedSearchQuery.col("status", status.toString().trim()));
        }
        search.forEach((key, value) -> {
            if (!SEARCH_META_KEYS.contains(key) && key != null && key.matches("[A-Za-z0-9_]{1,100}") && value != null
                    && !(value instanceof Map) && !(value instanceof List) && hasText(value.toString())) {
                query.filter(new com.td.application.search.AdvancedSearchQuery.Match(key, value.toString().trim()));
            }
        });
        String keyword = hasText(request.getKeyword()) ? request.getKeyword()
            : search.get("keyword") == null ? null : search.get("keyword").toString();
        query.keyword(keyword, request.getColumns() == null || request.getColumns().isEmpty()
            ? DEFAULT_KEYWORD_COLUMNS : request.getColumns());
        return query.sortDesc(com.td.application.search.AdvancedSearchQuery.SortField.CREATED_ON);
    }

    private static com.td.application.search.AdvancedSearchQuery.Condition whenSubmitted(String dateKey, String userField, String member) {
        return new com.td.application.search.AdvancedSearchQuery.AllOf(List.of(
            new com.td.application.search.AdvancedSearchQuery.Exists(com.td.application.search.AdvancedSearchQuery.Source.DYN, dateKey),
            com.td.application.search.AdvancedSearchQuery.dyn(userField, member)));
    }

    private static boolean wantsField(String items, String field) {
        if (items == null || items.isBlank() || "getall".equalsIgnoreCase(items.trim())) {
            return true;
        }
        for (String part : items.split(";")) {
            if (field.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static <T> PaginationResponse<T> toResponse(List<T> content, Page<?> page) {
        return new PaginationResponse<>(
            content, page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages(),
            page.isFirst(), page.isLast());
    }
}
