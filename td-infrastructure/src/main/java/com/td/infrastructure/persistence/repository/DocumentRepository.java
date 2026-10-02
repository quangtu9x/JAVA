package com.td.infrastructure.persistence.repository;

import com.td.application.documents.SearchDocumentsRequest;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.domain.documents.BusinessDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public abstract class DocumentRepository implements com.td.application.documents.DocumentRepository {

    private final DocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    protected DocumentRepository(DocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<BusinessDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<BusinessDocument, UUID>) jpaRepository).findById(id);
    }

    @Override
    public <S extends BusinessDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(BusinessDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public void hardDelete(BusinessDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public Page<BusinessDocument> searchDeleted(SearchDocumentsRequest request, Pageable pageable) {
        return DocumentSearchSupport.searchDeletedWithJpa(jpaRepository, request, pageable);
    }

    @Override
    public Page<BusinessDocument> search(SearchDocumentsRequest request, Pageable pageable) {
        List<DocumentSearchSupport.AttributeFilterRule> filterRules =
            DocumentSearchSupport.parseAttributeFilters(request == null ? null : request.getAttributeFilters());

        if (filterRules.isEmpty()) {
            return DocumentSearchSupport.searchWithJpa(jpaRepository, request, pageable);
        }

        List<BusinessDocument> candidates = findCandidates(request, filterRules);
        return DocumentSearchSupport.filterSortAndPage(candidates, filterRules, pageable);
    }

    protected List<BusinessDocument> findCandidates(
            SearchDocumentsRequest request,
            List<DocumentSearchSupport.AttributeFilterRule> filterRules) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendCommonFilters(sql, parameters, request);
        appendAttributeKeyPredicates(sql, parameters, filterRules);

        Query query = entityManager.createNativeQuery(sql.toString(), BusinessDocument.class);
        parameters.forEach(query::setParameter);

        @SuppressWarnings("unchecked")
        List<BusinessDocument> candidates = query.getResultList();
        return candidates;
    }

    protected abstract void appendAttributeKeyPredicates(
            StringBuilder sql,
            Map<String, Object> parameters,
            List<DocumentSearchSupport.AttributeFilterRule> filterRules);

    @Override
    public Page<BusinessDocument> findPendingForUser(String documentType, String username, Pageable pageable) {
        return findPendingForUser(documentType, username, null, null, pageable);
    }

    // Slice 7 (tim kiem/loc): them keyword/keywordColumns - null/rong o 1 trong 2 = khong loc (hanh
    // vi giong het ban tren, appendKeywordPredicate tu kiem tra rong va khong ghi gi vao sql).
    @Override
    public Page<BusinessDocument> findPendingForUser(
            String documentType, String username, String keyword, List<String> keywordColumns, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendPendingForUserPredicate(sql, parameters, username);
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);
        appendNotRevokedPredicate(sql, parameters, null);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.last_modified_on DESC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), BusinessDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<BusinessDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    protected abstract void appendPendingForUserPredicate(
            StringBuilder sql, Map<String, Object> parameters, String username);

    @Override
    public Page<BusinessDocument> findProcessedForUser(String documentType, String username, Pageable pageable) {
        return findProcessedForUser(documentType, username, null, null, pageable);
    }

    @Override
    public Page<BusinessDocument> findProcessedForUser(
            String documentType, String username, String keyword, List<String> keywordColumns, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendProcessedForUserPredicate(sql, parameters, username);
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);
        appendNotRevokedPredicate(sql, parameters, null);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.last_modified_on DESC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), BusinessDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<BusinessDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    protected abstract void appendProcessedForUserPredicate(
            StringBuilder sql, Map<String, Object> parameters, String username);

    @Override
    public Page<BusinessDocument> findByParentId(String documentType, String parentId, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendParentIdPredicate(sql, parameters, parentId);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.created_on ASC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), BusinessDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<BusinessDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    protected abstract void appendParentIdPredicate(
            StringBuilder sql, Map<String, Object> parameters, String parentId);

    @Override
    public Page<BusinessDocument> findByFieldValue(
            String documentType, String fieldName, String fieldValue, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendFieldValuePredicate(sql, parameters, fieldName, fieldValue);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.created_on ASC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), BusinessDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<BusinessDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    protected abstract void appendFieldValuePredicate(
            StringBuilder sql, Map<String, Object> parameters, String fieldName, String fieldValue);

    // BL-210 (VBDen Core) - N03: khung SQL giong het findByParentId/findByFieldValue, chi khac hook
    // OR-nhieu-field ben duoi.
    @Override
    public Page<BusinessDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value, Pageable pageable) {
        return findByAnyFieldContains(documentType, fieldNames, value, null, null, pageable);
    }

    @Override
    public Page<BusinessDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyFieldContainsPredicate(sql, parameters, fieldNames, value);
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);
        appendNotRevokedPredicate(sql, parameters, null);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.created_on ASC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), BusinessDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<BusinessDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    protected abstract void appendAnyFieldContainsPredicate(
            StringBuilder sql, Map<String, Object> parameters, List<String> fieldNames, String value);

    // 01/09/2026 ("tim kiem nang cao" theodoi/datheodoi - xem javadoc interface): GIU NGUYEN khung SQL
    // + ORDER BY created_on ASC cua ban khong co advancedFilter, chi them cac predicate TUY CHON (deu
    // early-return khi field tuong ung null/rong) - khong doi hanh vi 24 view khac dang goi 2 overload
    // tren.
    @Override
    public Page<BusinessDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value,
            VbdenOrgViewFilter advancedFilter, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyFieldContainsPredicate(sql, parameters, fieldNames, value);

        if (advancedFilter != null) {
            appendStatusPredicate(sql, parameters, advancedFilter.getIncludeFieldValues(), true);
            appendStatusPredicate(sql, parameters, advancedFilter.getExcludeFieldValues(), false);
            appendDynamicFieldValuePredicates(sql, parameters, advancedFilter.getIncludeFieldValues(), true);
            appendDynamicFieldValuePredicates(sql, parameters, advancedFilter.getExcludeFieldValues(), false);
            appendContainsAnyFieldValuePredicates(sql, parameters, advancedFilter.getContainsAnyFieldValues());
            if (advancedFilter.getMinReceivedDate() != null || advancedFilter.getMaxReceivedDate() != null) {
                appendReceivedDateInRangePredicate(
                    sql, parameters, advancedFilter.getMinReceivedDate(), advancedFilter.getMaxReceivedDate());
            }
            appendKeywordPredicate(sql, parameters, advancedFilter.getKeyword(), advancedFilter.getKeywordColumns());
        }
        if (advancedFilter == null || !advancedFilter.isIncludeRevoked()) {
            appendNotRevokedPredicate(sql, parameters, advancedFilter == null ? null : advancedFilter.getIncludeFieldValues());
        }

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        // 02/10/2026 (theodoi/datheodoi): co primarySortField (hoac newestCreatedFirst - van ban gan
        // vao ho so) thi sap theo filter, khong thi giu created_on ASC cu (vanbancantheodoi dang dung
        // overload nay).
        boolean filterSorted = advancedFilter != null
            && ((advancedFilter.getPrimarySortField() != null && !advancedFilter.getPrimarySortField().isBlank())
                || advancedFilter.isNewestCreatedFirst());
        String orderBy = filterSorted ? buildOrderByClause(advancedFilter) : " ORDER BY d.created_on ASC NULLS LAST";
        sql.append(orderBy).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), BusinessDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<BusinessDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    // 01/09/2026: field -> danh sach gia tri OR (containment JSON-array HOAC scalar-equality) - xem
    // javadoc VbdenOrgViewFilter#containsAnyFieldValues. Nhieu field khac nhau ket hop AND.
    protected abstract void appendContainsAnyFieldValuePredicates(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> containsAnyFieldValues);

    // DT-002 (25/08/2026, vwUserCreator_Process_Handler_Submit - xem javadoc ViewQueryService):
    // field dong dang chuoi username noi bang ";" luu SCALAR (vd. user_other_processed_handler =
    // "a;b"). Containment @>/equality cua appendAnyFieldContainsPredicate chi khop TOAN BO chuoi -
    // phai so khop THANH VIEN (orgValue trung 1 phan tu phan cach ";", case-insensitive theo
    // sortnocase='true' cua cot Domino, tuong duong @Explode(field; ";")). 4 nhanh LIKE neo bien
    // ";" de tranh khop sai mot phan (vd. "nam" khong duoc khop trong "an;nam"). Helper CONCRETE o
    // day (chi dung SQL chuan LIKE/||, khong phu thuoc DB) - PostgresDuThaoDocumentRepository copy
    // lai y het theo khuon verbatim-copy da co.
    private void appendAnyListFieldMemberPredicate(
            StringBuilder sql, Map<String, Object> parameters, List<String> fieldNames, String value) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (normalized.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        sql.append(" AND (");
        for (int index = 0; index < fieldNames.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            String fieldName = fieldNames.get(index);
            String column = "LOWER(COALESCE(d.dynamic_fields->>'" + fieldName + "', ''))";
            String exactParam = "listFieldExact" + index;
            String prefixParam = "listFieldPrefix" + index;
            String suffixParam = "listFieldSuffix" + index;
            String middleParam = "listFieldMiddle" + index;
            sql.append("(").append(column).append(" = :").append(exactParam)
                .append(" OR ").append(column).append(" LIKE :").append(prefixParam)
                .append(" OR ").append(column).append(" LIKE :").append(suffixParam)
                .append(" OR ").append(column).append(" LIKE :").append(middleParam)
                .append(")");
            parameters.put(exactParam, normalized);
            parameters.put(prefixParam, normalized + ";%");
            parameters.put(suffixParam, "%;" + normalized);
            parameters.put(middleParam, "%;" + normalized + ";%");
        }
        sql.append(")");
    }

    // N03/N13 (VBDen - hang doi/han xu ly theo to chuc): thay the findByFieldValueAndStatusIn/
    // findByFieldValueWithDeadlineInRange (Slice 3) - khong du tong quat cho to hop field/status/ngay
    // that cua 11 view doc tu docs/views_domino/view_vanbanden.dxl. Tai dung appendAnyFieldContainsPredicate
    // (khoa to chuc) + appendDeadlineInRangePredicate (Slice 3, khoang ngay) da co.
    private static final String STATUS_FIELD_NAME = "status";

    @Override
    public Page<BusinessDocument> findByVbdenOrgView(
            String documentType, VbdenOrgViewFilter filter, Pageable pageable) {
        // Bo sung 14/09/2026 (xem javadoc VbdenOrgViewFilter#deletedOnly, port "thung rac vbden"):
        // deletedOnly mac dinh false GIU NGUYEN dieu kien cu (deleted_on IS NULL) cho MOI callsite
        // hien tai - chi khi true (view thung rac) moi dao nguoc sang IS NOT NULL.
        StringBuilder sql = new StringBuilder(filter.isDeletedOnly()
            ? "SELECT d.* FROM documents d WHERE d.deleted_on IS NOT NULL\n"
            : "SELECT d.* FROM documents d WHERE d.deleted_on IS NULL\n");

        Map<String, Object> parameters = new LinkedHashMap<>();
        // Bugfix 25/08/2026 (cung loi da sua o PostgresDuThaoDocumentRepository): phai goi CO DIEU
        // KIEN - appendAnyFieldContainsPredicate voi orgFieldNames null/rong tu gan "AND 1 = 0",
        // goi vo dieu kien se lam view chi dung orgListFieldNames luon tra trang.
        boolean hasOrgFields = filter.getOrgFieldNames() != null && !filter.getOrgFieldNames().isEmpty();
        boolean hasOrgListFields =
            filter.getOrgListFieldNames() != null && !filter.getOrgListFieldNames().isEmpty();
        if (hasOrgFields) {
            appendAnyFieldContainsPredicate(sql, parameters, filter.getOrgFieldNames(), filter.getOrgValue());
        }
        if (hasOrgListFields) {
            appendAnyListFieldMemberPredicate(sql, parameters, filter.getOrgListFieldNames(), filter.getOrgValue());
        }
        if (!hasOrgFields && !hasOrgListFields) {
            sql.append(" AND 1 = 0");
        }
        appendStatusPredicate(sql, parameters, filter.getIncludeFieldValues(), true);
        appendStatusPredicate(sql, parameters, filter.getExcludeFieldValues(), false);
        appendDynamicFieldValuePredicates(sql, parameters, filter.getIncludeFieldValues(), true);
        appendDynamicFieldValuePredicates(sql, parameters, filter.getExcludeFieldValues(), false);

        if (filter.getMinDeadline() != null || filter.getMaxDeadline() != null) {
            appendDeadlineInRangePredicate(sql, parameters, filter.getMinDeadline(), filter.getMaxDeadline());
        }
        // 31/08/2026 (port "toanbovanban"): xem javadoc VbdenOrgViewFilter#minReceivedDate.
        if (filter.getMinReceivedDate() != null || filter.getMaxReceivedDate() != null) {
            appendReceivedDateInRangePredicate(sql, parameters, filter.getMinReceivedDate(), filter.getMaxReceivedDate());
        }
        if (filter.isRequireDeadlineOrDocumentReply()) {
            appendDeadlineOrDocumentReplyPredicate(sql, parameters);
        }
        if (filter.getRequireEmptyFieldName() != null && !filter.getRequireEmptyFieldName().isBlank()) {
            appendFieldEmptyPredicate(sql, parameters, filter.getRequireEmptyFieldName());
        }
        if (filter.getRequireNonEmptyFieldName() != null && !filter.getRequireNonEmptyFieldName().isBlank()) {
            appendFieldNonEmptyPredicate(sql, parameters, filter.getRequireNonEmptyFieldName());
        }
        if (filter.getMinCreatedOn() != null || filter.getMaxCreatedOn() != null) {
            appendCreatedOnInRangePredicate(sql, parameters, filter.getMinCreatedOn(), filter.getMaxCreatedOn());
        }
        appendKeywordPredicate(sql, parameters, filter.getKeyword(), filter.getKeywordColumns());
        if (!filter.isIncludeRevoked()) {
            appendNotRevokedPredicate(sql, parameters, filter.getIncludeFieldValues());
        }

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(buildOrderByClause(filter)).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), BusinessDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<BusinessDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    // "status" luon la cot tinh documents.status (khong phai dynamic_fields) - xu ly rieng, dialect-neutral.
    private void appendStatusPredicate(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> fieldValues, boolean include) {
        if (fieldValues == null) {
            return;
        }
        List<String> statuses = fieldValues.get(STATUS_FIELD_NAME);
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        List<String> paramRefs = new ArrayList<>();
        String prefix = include ? "incStatus" : "excStatus";
        for (int index = 0; index < statuses.size(); index++) {
            String parameterName = prefix + index;
            paramRefs.add(":" + parameterName);
            parameters.put(parameterName, statuses.get(index) == null ? "" : statuses.get(index).trim().toLowerCase());
        }
        sql.append(include ? " AND LOWER(COALESCE(d.status, '')) IN (" : " AND LOWER(COALESCE(d.status, '')) NOT IN (")
            .append(String.join(",", paramRefs)).append(")");
    }

    // Moi field khac "status" la field dong (dynamic_fields JSONB) - uy quyen cho hook Postgres-specific.
    private void appendDynamicFieldValuePredicates(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> fieldValues, boolean include) {
        if (fieldValues == null) {
            return;
        }
        int index = 0;
        for (Map.Entry<String, List<String>> entry : fieldValues.entrySet()) {
            if (STATUS_FIELD_NAME.equals(entry.getKey())) {
                continue;
            }
            String paramPrefix = (include ? "incDyn" : "excDyn") + index++;
            appendDynamicFieldValueInPredicate(sql, parameters, entry.getKey(), entry.getValue(), include, paramPrefix);
        }
    }

    protected abstract void appendDeadlineInRangePredicate(
            StringBuilder sql, Map<String, Object> parameters, LocalDate minDeadline, LocalDate maxDeadline);

    /**
     * 31/08/2026 (port "toanbovanban"): xem javadoc {@code VbdenOrgViewFilter#minReceivedDate} — cùng
     * công thức xử lý định dạng {@code dd/MM/yyyy} như {@link #appendDeadlineInRangePredicate}, tách
     * riêng vì field Domino khác nhau ({@code received_date} khác {@code deadline_date}).
     */
    protected abstract void appendReceivedDateInRangePredicate(
            StringBuilder sql, Map<String, Object> parameters, LocalDate minReceivedDate, LocalDate maxReceivedDate);

    /**
     * N03/N13: {@code dynamic_fields->>'fieldName'} IN/NOT IN danh sách giá trị — dùng cho các field
     * scalar động (vd. {@code internal}, {@code document_internet}, {@code Reply}, {@code document_type}).
     */
    protected abstract void appendDynamicFieldValueInPredicate(
            StringBuilder sql, Map<String, Object> parameters, String fieldName, List<String> values,
            boolean include, String paramPrefix);

    /**
     * N13 riêng cho {@code phaixuly_cuatochuc}: {@code (deadline_date!="" | documentreply="1")}.
     */
    protected abstract void appendDeadlineOrDocumentReplyPredicate(StringBuilder sql, Map<String, Object> parameters);

    /**
     * N05 riêng cho {@code chophanxuly}: {@code fieldName=""} — field động dạng mảng PHẢI rỗng/vắng
     * mặt (xem javadoc {@link VbdenOrgViewFilter#getRequireEmptyFieldName()}).
     */
    protected abstract void appendFieldEmptyPredicate(
            StringBuilder sql, Map<String, Object> parameters, String fieldName);

    /**
     * Slice 7 (tìm kiếm/lọc): {@code keyword} OR trên {@code keywordColumns} (field động scalar) —
     * {@code null}/rỗng ở {@code keyword} HOẶC {@code keywordColumns} = không thêm điều kiện gì.
     */
    protected abstract void appendKeywordPredicate(
            StringBuilder sql, Map<String, Object> parameters, String keyword, List<String> keywordColumns);

    /**
     * Slice 9 ({@code sovanban}): {@code fieldName != ""} — field động PHẢI khác rỗng/vắng mặt,
     * ngược lại {@link #appendFieldEmptyPredicate}.
     */
    protected abstract void appendFieldNonEmptyPredicate(
            StringBuilder sql, Map<String, Object> parameters, String fieldName);

    /**
     * Slice 9 ({@code homqua}/{@code trongngay}/{@code trongthang}/{@code trongtuan}): khoảng ngày
     * cho CỘT TĨNH {@code documents.created_on} (khác {@link #appendDeadlineInRangePredicate} là
     * field động JSONB) — tương đương {@code @Date(@Created)} trong DXL, tính động tại thời điểm
     * query (KHÔNG hard-code ngày như formula Domino tự sinh lại hàng ngày).
     */
    protected abstract void appendCreatedOnInRangePredicate(
            StringBuilder sql, Map<String, Object> parameters, LocalDate minCreatedOn, LocalDate maxCreatedOn);

    // Slice 9: ORDER BY dong theo VbdenOrgViewFilter.primarySortField/secondarySortField (sovanban)
    // - fallback ve mac dinh cu (last_modified_on DESC) khi khong set, giu nguyen hanh vi truoc do.
    private String buildOrderByClause(VbdenOrgViewFilter filter) {
        if (filter.getPrimarySortField() == null || filter.getPrimarySortField().isBlank()) {
            // 01/10/2026: xem javadoc VbdenOrgViewFilter#newestCreatedFirst.
            return filter.isNewestCreatedFirst()
                ? " ORDER BY d.created_on DESC NULLS LAST, d.last_modified_on DESC NULLS LAST"
                : " ORDER BY d.last_modified_on DESC NULLS LAST";
        }
        String direction = filter.isSortDescending() ? " DESC NULLS LAST" : " ASC NULLS LAST";
        StringBuilder orderBy = new StringBuilder(" ORDER BY ")
            .append(dynamicSortExpression(filter.getPrimarySortField(), filter.getPrimarySortType()))
            .append(direction);
        if (filter.getSecondarySortField() != null && !filter.getSecondarySortField().isBlank()) {
            String secondaryDirection = filter.getSecondarySortDescending() == null
                ? direction
                : (filter.getSecondarySortDescending() ? " DESC NULLS LAST" : " ASC NULLS LAST");
            orderBy.append(", ")
                .append(dynamicSortExpression(filter.getSecondarySortField(), filter.getSecondarySortType()))
                .append(secondaryDirection);
        }
        // 02/10/2026: on dinh phan trang khi trung gia tri sort (vd. cung ngay den).
        orderBy.append(", d.created_on DESC NULLS LAST");
        return orderBy.toString();
    }

    /**
     * Slice 9: biểu thức SQL để {@code ORDER BY} theo 1 field động — {@code sortType="integer"}/
     * {@code "date"} ép kiểu đúng (tránh sort như chuỗi, vd. "10" đứng trước "2"); {@code fieldName}
     * LUÔN là hằng số nội bộ do {@code ViewQueryService}/use case đặt (không phải input người dùng
     * trực tiếp) — cùng mức tin cậy với {@code appendFieldEmptyPredicate} và các hook khác đã nối
     * chuỗi field name tương tự trong file này.
     */
    protected abstract String dynamicSortExpression(String fieldName, String sortType);

    @Override
    public Page<BusinessDocument> findByStatus(String documentType, String status, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM documents d
            WHERE d.deleted_on IS NULL
              AND LOWER(COALESCE(d.status, '')) = :status
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("status", status == null ? "" : status.trim().toLowerCase());
        appendNotRevokedPredicate(sql, parameters, null);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.last_modified_on DESC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), BusinessDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<BusinessDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Bổ sung 30/09/2026 (báo lỗi người dùng: vbdi "Lấy lại văn bản" — văn bản đến đã mất khỏi danh
     * sách văn thư nhưng vẫn hiện ở {@code vbden/xuly/dencoquan}, view {@code cuatochuc}): Domino khi
     * thu hồi/lấy lại đổi {@code Form} sang {@code DocumentIn_Deleted} ({@code RevokeDocumentOut}/
     * {@code RevokeAllDocumentOut}/{@code ReplyDocumentOut}) → văn bản tự rơi khỏi MỌI view
     * {@code Form="DocumentIn"}. Java chỉ ghi {@code reply="1"}/{@code is_revoke="1"}
     * ({@code VbdiRevokeTaskHandler}, {@code VbdenForwardUnitRevokeTaskHandler},
     * {@code CapNhatVanBanThayTheThuHoiUseCase}) nên trước đây CHỈ các view tình cờ có điều kiện
     * {@code Reply!="1"} (vd {@code quamangnoibo}) mới ẩn được — mọi view khác vẫn hiện. Áp dụng TẬP
     * TRUNG cho mọi truy vấn DANH SÁCH của repository (không áp cho {@code findByParentId}/
     * {@code findByFieldValue} — dùng tra cứu nghiệp vụ). Bỏ qua khi {@code includeFieldValues} tự
     * lọc theo {@code is_revoke} (view "Bị thu hồi").
     */
    private void appendNotRevokedPredicate(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> includeFieldValues) {
        if (includeFieldValues != null && includeFieldValues.containsKey(IS_REVOKE_FIELD_NAME)) {
            return;
        }
        appendDynamicFieldValueInPredicate(sql, parameters, IS_REVOKE_FIELD_NAME, List.of("1"), false, "notRevoked");
    }

    private static final String IS_REVOKE_FIELD_NAME = "is_revoke";

    private long countNative(String sql, Map<String, Object> parameters) {
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        parameters.forEach(countQuery::setParameter);
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    protected String toJsonPath(String key) {
        String escaped = key == null ? "" : key.replace("\\", "\\\\").replace("\"", "\\\"");
        return "$.\"" + escaped + "\"";
    }

    private void appendCommonFilters(StringBuilder sql, Map<String, Object> parameters, SearchDocumentsRequest request) {
        if (request == null) {
            return;
        }

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            sql.append("""
                 AND (
                     LOWER(COALESCE(d.title, '')) LIKE :keyword
                     OR LOWER(COALESCE(d.content, '')) LIKE :keyword
                     OR LOWER(COALESCE(d.document_type, '')) LIKE :keyword
                     OR LOWER(COALESCE(d.status, '')) LIKE :keyword
                     OR LOWER(COALESCE(d.tags_json, '')) LIKE :keyword
                     OR LOWER(COALESCE(d.attributes_json, '')) LIKE :keyword
                 )
                """);
            parameters.put("keyword", "%" + request.getKeyword().trim().toLowerCase() + "%");
        }

        if (request.getDocumentType() != null && !request.getDocumentType().isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", request.getDocumentType().trim().toLowerCase());
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.status, '')) = :status");
            parameters.put("status", request.getStatus().trim().toLowerCase());
        }
    }
}
