package com.td.infrastructure.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.duthao.DuThaoDocumentRepository;
import com.td.domain.duthao.DuThaoDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * BL-230 (DuThao Core), yêu cầu người dùng 24/08/2026: adapter cho bảng vật lý ĐỘC LẬP
 * {@code duthao_documents} (xem javadoc {@link DuThaoDocument}) — KHÔNG dùng lại
 * {@code PostgresDocumentRepository} (query cứng "documents"). Copy CÓ CHỦ Ý phần predicate SQL
 * (containment/status IN/keyword — {@code PostgresDocumentRepository}, đã qua nhiều bugfix thực tế
 * trong phiên vbden/vbdi) chỉ đổi tên bảng/entity — KHÔNG viết lại thuật toán, để không lặp lại các
 * lỗi đã sửa (vd. bugfix 21/08/2026: containment PHẢI OR thêm scalar-equality vì field có thể lưu
 * dạng scalar text, không chỉ JSON array — xem {@code PostgresDocumentRepository#appendAnyFieldContainsPredicate}).
 *
 * <p>Chỉ implement ĐÚNG method khai báo ở {@link DuThaoDocumentRepository} (xem javadoc ở đó về
 * việc không copy nguyên văn toàn bộ surface {@code DocumentRepository}).</p>
 */
@Repository
public class PostgresDuThaoDocumentRepository implements DuThaoDocumentRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STATUS_FIELD_NAME = "status";
    // 06/10/2026 (yêu cầu người dùng "các danh sách ở dự thảo sắp theo ngày tạo mới nhất lên trên"):
    // MỌI danh sách dự thảo dùng chung 1 thứ tự — trước đây lẫn created_on ASC (hàng đợi cá nhân) và
    // last_modified_on DESC (dự thảo cũ vừa xử lý nhảy lên đầu). FE gửi orderBy nhưng Java bỏ qua, nên
    // chốt ở đây — cùng quy ước PostgresHoSoCVDocumentRepository#NEWEST_CREATED_FIRST.
    static final String NEWEST_CREATED_FIRST = "d.created_on DESC NULLS LAST, d.last_modified_on DESC NULLS LAST";

    private final DuThaoDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresDuThaoDocumentRepository(DuThaoDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<DuThaoDocument> findById(UUID id) {
        // Ep kieu ro JpaRepository<DuThaoDocument, UUID> - BaseRepository ke thua CA CrudRepository
        // (Spring Data) LAN IRepository (port rieng, cung khai bao findById(UUID)) nen goi thang qua
        // bien jpaRepository (kieu DuThaoDocumentJpaRepository) bi ambiguous - dung tien le
        // PostgresDocumentRepository#findById.
        return ((org.springframework.data.jpa.repository.JpaRepository<DuThaoDocument, UUID>) jpaRepository).findById(id);
    }

    @Override
    public <S extends DuThaoDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(DuThaoDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public Page<DuThaoDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value, Pageable pageable) {
        return findByAnyFieldContains(documentType, fieldNames, value, null, null, pageable);
    }

    @Override
    public Page<DuThaoDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM duthao_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyFieldContainsPredicate(sql, parameters, fieldNames, value);
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY ").append(NEWEST_CREATED_FIRST).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), DuThaoDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<DuThaoDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<DuThaoDocument> findByVbdenOrgView(
            String documentType, VbdenOrgViewFilter filter, Pageable pageable) {
        // 22/10/2026 (thùng rác dự thảo vwUser_Draft_Deleted): deletedOnly đảo điều kiện xoá mềm — xem
        // javadoc VbdenOrgViewFilter#deletedOnly (cùng khuôn DocumentRepository).
        StringBuilder sql = new StringBuilder(filter.isDeletedOnly()
            ? """
            SELECT d.*
            FROM duthao_documents d
            WHERE d.deleted_on IS NOT NULL
            """
            : """
            SELECT d.*
            FROM duthao_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendVbdenOrgKeyPredicates(sql, parameters, filter);
        appendStatusPredicate(sql, parameters, filter.getIncludeFieldValues(), true);
        appendStatusPredicate(sql, parameters, filter.getExcludeFieldValues(), false);
        appendDynamicFieldValuePredicates(sql, parameters, filter.getIncludeFieldValues(), true);
        appendDynamicFieldValuePredicates(sql, parameters, filter.getExcludeFieldValues(), false);
        // 09/09/2026 ("tim kiem nang cao" duthao - cutover qlvb/tichhop/TimKiem?databasetype=duthao):
        // field -> danh sach gia tri, khop IT NHAT 1 (OR) - dung cho field text tu do cua
        // CollapseVBduthao.jsx - xem javadoc ViewQueryService#buildDuThaoAdvancedSearchFilter.
        appendContainsAnyFieldValuePredicates(sql, parameters, filter.getContainsAnyFieldValues());
        TextContainsSql.append(sql, parameters, filter.getTextContainsFieldValues(), true); // 19/10/2026
        // DT-002 loat 2 (25/08/2026): cac view moi (vwUser_Return_Draft/vwCreator_Signer_Submit/...)
        // dung dieu kien scalar tren field dong - KHONG dung duoc hook kieu JSON-array cua base
        // DocumentRepository (jsonb_array_length bao loi tren scalar). Ban scalar rieng ben duoi.
        if (filter.getRequireEmptyFieldName() != null && !filter.getRequireEmptyFieldName().isBlank()) {
            sql.append(" AND COALESCE(d.dynamic_fields->>'")
                .append(filter.getRequireEmptyFieldName()).append("', '') = ''");
        }
        if (filter.getRequireNonEmptyFieldName() != null && !filter.getRequireNonEmptyFieldName().isBlank()) {
            sql.append(" AND COALESCE(d.dynamic_fields->>'")
                .append(filter.getRequireNonEmptyFieldName()).append("', '') <> ''");
        }
        appendKeywordPredicate(sql, parameters, filter.getKeyword(), filter.getKeywordColumns());
        // 09/09/2026: "Ngay du thao" (CollapseVBduthao.jsx#create_date) khop dung ngu nghia cot tinh
        // created_on (AuditableEntity) - cung quyet dinh da ap dung cho hosocv (findByVbdenOrgView).
        if (filter.getMinCreatedOn() != null) {
            sql.append(" AND CAST(d.created_on AS date) >= :minCreatedOn");
            parameters.put("minCreatedOn", java.sql.Date.valueOf(filter.getMinCreatedOn()));
        }
        if (filter.getMaxCreatedOn() != null) {
            sql.append(" AND CAST(d.created_on AS date) <= :maxCreatedOn");
            parameters.put("maxCreatedOn", java.sql.Date.valueOf(filter.getMaxCreatedOn()));
        }

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY ").append(NEWEST_CREATED_FIRST).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), DuThaoDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<DuThaoDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    // DT-002 (26/08/2026) - vwUser_Coordinated: OR-group khong bieu dien duoc bang VbdenOrgViewFilter
    // (1 nhom AND). Selection that: (fieldWithoutStatus != "") OR (fieldWithStatus != "" AND status
    // IN doneStatuses) - viet SQL rieng, KHONG tai dung findByVbdenOrgView.
    @Override
    public Page<DuThaoDocument> findByFieldOrFieldWithStatus(
            String documentType, String fieldWithoutStatus, String fieldWithStatus,
            List<String> doneStatuses, String value, String keyword, List<String> keywordColumns,
            VbdenOrgViewFilter search, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM duthao_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendFieldOrFieldWithStatusPredicate(sql, parameters, fieldWithoutStatus, fieldWithStatus, doneStatuses, value);
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);
        appendAdvancedSearchPredicates(sql, parameters, search);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY ").append(NEWEST_CREATED_FIRST).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), DuThaoDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<DuThaoDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    // 01/10/2026 - view gop "Van thu tra lai" (xem javadoc DuThaoDocumentRepository).
    @Override
    public Page<DuThaoDocument> findReturnedByClerkForUser(
            String documentType, Map<String, String> statusByReturnFor, String username,
            String keyword, List<String> keywordColumns, VbdenOrgViewFilter search, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM duthao_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendReturnedByClerkPredicate(sql, parameters, statusByReturnFor, username);
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);
        appendAdvancedSearchPredicates(sql, parameters, search);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY ").append(NEWEST_CREATED_FIRST).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), DuThaoDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<DuThaoDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    // 22/10/2026 - vwChoDuyetKyBanHanh/MC (xem javadoc DuThaoDocumentRepository).
    @Override
    public Page<DuThaoDocument> findByFieldStatusBranches(
            String documentType, Map<String, List<String>> statusesByField, String value,
            String requireEmptyField, String requireNonEmptyField,
            String keyword, List<String> keywordColumns, VbdenOrgViewFilter search, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM duthao_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendFieldStatusBranchesPredicate(sql, parameters, statusesByField, value);
        if (requireEmptyField != null && !requireEmptyField.isBlank()) {
            sql.append(" AND COALESCE(d.dynamic_fields->>'").append(requireEmptyField).append("', '') = ''");
        }
        if (requireNonEmptyField != null && !requireNonEmptyField.isBlank()) {
            sql.append(" AND COALESCE(d.dynamic_fields->>'").append(requireNonEmptyField).append("', '') <> ''");
        }
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);
        appendAdvancedSearchPredicates(sql, parameters, search);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY ").append(NEWEST_CREATED_FIRST).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), DuThaoDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<DuThaoDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    // Package-private seam cho unit test. Moi nhanh: value khop field (containment/scalar/thanh vien "a;b",
    // cung ngu nghia orgFieldNames) VA status thuoc danh sach rieng cua nhanh. Khong co nhanh/value -> 1 = 0.
    void appendFieldStatusBranchesPredicate(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> statusesByField, String value) {
        if (value == null || value.isBlank() || statusesByField == null || statusesByField.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        List<String> branches = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<String>> entry : statusesByField.entrySet()) {
            List<String> statuses = entry.getValue() == null ? List.of() : entry.getValue();
            if (statuses.isEmpty()) {
                index++;
                continue;
            }
            String fieldName = entry.getKey();
            String containsParam = "branchContains" + index;
            String scalarParam = "branchScalar" + index;
            StringBuilder branch = new StringBuilder("((d.dynamic_fields @> CAST(:").append(containsParam)
                .append(" AS jsonb) OR COALESCE(d.dynamic_fields->>'").append(fieldName).append("', '') = :")
                .append(scalarParam);
            appendDelimitedMemberMatch(branch, parameters, fieldName, value, "branchMember" + index);
            branch.append(")");
            parameters.put(containsParam, buildContainsFilter(fieldName, value));
            parameters.put(scalarParam, value);
            List<String> paramRefs = new ArrayList<>();
            for (int statusIndex = 0; statusIndex < statuses.size(); statusIndex++) {
                String parameterName = "branchStatus" + index + "_" + statusIndex;
                paramRefs.add(":" + parameterName);
                String status = statuses.get(statusIndex);
                parameters.put(parameterName, status == null ? "" : status.trim().toLowerCase());
            }
            branch.append(" AND LOWER(COALESCE(d.status, '')) IN (").append(String.join(",", paramRefs)).append("))");
            branches.add(branch.toString());
            index++;
        }
        if (branches.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        sql.append(" AND (").append(String.join(" OR ", branches)).append(")");
    }

    // Package-private seam cho unit test. Moi cap (return_document_out_for -> status) la 1 nhanh OR,
    // tranh hien du thao da roi khoi trang thai tra lai (vd tra lanh dao rf=1 roi lanh dao tra tiep
    // chuyen vien -> status=2 nhung rf van =1).
    void appendReturnedByClerkPredicate(
            StringBuilder sql, Map<String, Object> parameters, Map<String, String> statusByReturnFor,
            String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase();
        if (normalized.isEmpty() || statusByReturnFor == null || statusByReturnFor.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        sql.append(" AND LOWER(TRIM(COALESCE(d.dynamic_fields->>'return_document_out_for_user', ''))) = :returnedForUser");
        parameters.put("returnedForUser", normalized);
        List<String> branches = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, String> entry : statusByReturnFor.entrySet()) {
            String forParam = "returnedFor" + index;
            String statusParam = "returnedStatus" + index;
            branches.add("(COALESCE(d.dynamic_fields->>'return_document_out_for', '') = :" + forParam
                + " AND COALESCE(d.status, '') = :" + statusParam + ")");
            parameters.put(forParam, entry.getKey());
            parameters.put(statusParam, entry.getValue());
            index++;
        }
        sql.append(" AND (").append(String.join(" OR ", branches)).append(")");
    }

    // Tach seam package-private de unit-test SQL builder khong can EntityManager (cung khuon
    // appendVbdenOrgKeyPredicates). "fieldWithoutStatus" khop khong dieu kien status (nhanh
    // user_coordinated); "fieldWithStatus" CHI tinh khi status nam trong doneStatuses (nhanh
    // user_coordinator, dxl 183: & (status="8"|status="7"|status="4")). doneStatuses rong -> nhanh
    // 2 khong bao gio dung (thay vi loi SQL "IN ()" rong).
    static final String DELEGATED_COORDINATOR_FIELD = "user_coordinated_movement";

    void appendFieldOrFieldWithStatusPredicate(
            StringBuilder sql, Map<String, Object> parameters, String fieldWithoutStatus,
            String fieldWithStatus, List<String> doneStatuses, String value) {
        sql.append(" AND (")
            .append("(d.dynamic_fields @> CAST(:orGroupContains0 AS jsonb)")
            .append(" OR COALESCE(d.dynamic_fields->>'").append(fieldWithoutStatus).append("', '') = :orGroupScalar0");
        appendDelimitedMemberMatch(sql, parameters, fieldWithoutStatus, value, "orGroupMember0");
        sql.append(")");
        // 19/10/2026 (ủy quyền ở Người nhận phối hợp): người ủy quyền (đã xác minh, ghi bởi
        // DuThaoCoordinatorDelegations) vẫn thấy dự thảo ở "Đã phối hợp" - nhánh OR thứ 3, không điều kiện status.
        sql.append(" OR (d.dynamic_fields @> CAST(:orGroupContainsDelegated AS jsonb)")
            .append(" OR COALESCE(d.dynamic_fields->>'").append(DELEGATED_COORDINATOR_FIELD).append("', '') = :orGroupScalarDelegated");
        appendDelimitedMemberMatch(sql, parameters, DELEGATED_COORDINATOR_FIELD, value, "orGroupMemberDelegated");
        sql.append(")");
        parameters.put("orGroupContainsDelegated", buildContainsFilter(DELEGATED_COORDINATOR_FIELD, value));
        parameters.put("orGroupScalarDelegated", value == null ? "" : value);
        sql.append(" OR ((d.dynamic_fields @> CAST(:orGroupContains1 AS jsonb)")
            .append(" OR COALESCE(d.dynamic_fields->>'").append(fieldWithStatus).append("', '') = :orGroupScalar1");
        appendDelimitedMemberMatch(sql, parameters, fieldWithStatus, value, "orGroupMember1");
        sql.append(")");
        parameters.put("orGroupContains0", buildContainsFilter(fieldWithoutStatus, value));
        parameters.put("orGroupScalar0", value == null ? "" : value);
        parameters.put("orGroupContains1", buildContainsFilter(fieldWithStatus, value));
        parameters.put("orGroupScalar1", value == null ? "" : value);

        List<String> statuses = doneStatuses == null ? List.of() : doneStatuses;
        if (statuses.isEmpty()) {
            sql.append(" AND 1 = 0");
        } else {
            List<String> paramRefs = new ArrayList<>();
            for (int index = 0; index < statuses.size(); index++) {
                String parameterName = "orGroupDoneStatus" + index;
                paramRefs.add(":" + parameterName);
                parameters.put(parameterName, statuses.get(index) == null ? "" : statuses.get(index).trim().toLowerCase());
            }
            sql.append(" AND LOWER(COALESCE(d.status, '')) IN (").append(String.join(",", paramRefs)).append(")");
        }
        sql.append("))");
    }

    // DT-002 - BUGFIX 25/08/2026 (vwUserCreator_Process_Handler_Submit tra RONG du DB co du lieu):
    // truoc day appendAnyFieldContainsPredicate bi goi VO DIEU KIEN voi filter.getOrgFieldNames()
    // - helper do voi fieldNames null/rong tu gan "AND 1 = 0" (hop dong "khong khoa to chuc = khong
    // ve gi") nen view chi dung orgListFieldNames (orgFieldNames = null, truong hop cua view UserCreator)
    // luon tra trang khong du lieu. Tach composition ra package-private seam de unit-test truc tiep
    // SQL builder ma khong can EntityManager (cung khuon PostgresDocumentRepositoryTest).
    // Contract: it nhat 1 trong 2 loai khoa phai co, nguoc lai giu "AND 1 = 0".
    void appendVbdenOrgKeyPredicates(
            StringBuilder sql, Map<String, Object> parameters, VbdenOrgViewFilter filter) {
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
    }

    // Copy dung y het PostgresDocumentRepository#appendAnyFieldContainsPredicate (bugfix 21/08/2026)
    // - OR containment (JSON array) + scalar-equality cho MOI field, khong can biet truoc field nao
    // la array/scalar. Package-private cho unit test (xem appendVbdenOrgKeyPredicates).
    void appendAnyFieldContainsPredicate(
            StringBuilder sql, Map<String, Object> parameters, List<String> fieldNames, String value) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        sql.append(" AND (");
        for (int index = 0; index < fieldNames.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            String containsParam = "containsField" + index;
            String scalarParam = "containsFieldScalar" + index;
            String fieldName = fieldNames.get(index);
            sql.append("(d.dynamic_fields @> CAST(:").append(containsParam).append(" AS jsonb)")
                .append(" OR COALESCE(d.dynamic_fields->>'").append(fieldName).append("', '') = :")
                .append(scalarParam);
            appendDelimitedMemberMatch(sql, parameters, fieldName, value, "containsFieldMember" + index);
            sql.append(")");
            parameters.put(containsParam, buildContainsFilter(fieldName, value));
            parameters.put(scalarParam, value == null ? "" : value);
        }
        sql.append(")");
    }

    // Bugfix 27/09/2026 (DT-29136): khớp thành viên chuỗi "a;b" — xem javadoc DelimitedMemberSql.
    private void appendDelimitedMemberMatch(
            StringBuilder sql, Map<String, Object> parameters, String fieldName, String value, String paramName) {
        DelimitedMemberSql.appendOrMember(sql, parameters, fieldName, value, paramName);
    }

    private String buildContainsFilter(String fieldName, String value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(fieldName, List.of(value)));
        } catch (Exception ex) {
            return "{}";
        }
    }

    // Copy dung y het DocumentRepository#appendAnyListFieldMemberPredicate (DT-002, 25/08/2026 -
    // vwUserCreator_Process_Handler_Submit): so khop THANH VIEN cua field dong dang chuoi ";"
    // -joined luu scalar (vd. user_other_processed_handler = "a;b") - containment/equality ben tren
    // chi khop toan bo chuoi nen khong dung duoc cho view nay. Package-private cho unit test.
    void appendAnyListFieldMemberPredicate(
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

    // "status" luon la cot tinh duthao_documents.status (khong phai dynamic_fields).
    // 19/10/2026 (bộ lọc dự thảo bị bỏ qua ở Phối hợp/Văn thư trả lại): 2 view SQL riêng ở trên không đi
    // qua findByVbdenOrgView - áp cùng điều kiện lọc nâng cao (includeFieldValues + khoảng created_on,
    // xem ViewQueryService#buildDuThaoAdvancedSearchFilter). null = không lọc thêm.
    private void appendAdvancedSearchPredicates(
            StringBuilder sql, Map<String, Object> parameters, VbdenOrgViewFilter search) {
        if (search == null) {
            return;
        }
        appendStatusPredicate(sql, parameters, search.getIncludeFieldValues(), true);
        appendDynamicFieldValuePredicates(sql, parameters, search.getIncludeFieldValues(), true);
        if (search.getMinCreatedOn() != null) {
            sql.append(" AND CAST(d.created_on AS date) >= :minCreatedOn");
            parameters.put("minCreatedOn", java.sql.Date.valueOf(search.getMinCreatedOn()));
        }
        if (search.getMaxCreatedOn() != null) {
            sql.append(" AND CAST(d.created_on AS date) <= :maxCreatedOn");
            parameters.put("maxCreatedOn", java.sql.Date.valueOf(search.getMaxCreatedOn()));
        }
    }

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

    // Moi field khac "status" la field dong (dynamic_fields JSONB).
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
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            String paramPrefix = (include ? "incDyn" : "excDyn") + index++;
            List<String> paramRefs = new ArrayList<>();
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                String parameterName = paramPrefix + "_" + valueIndex;
                paramRefs.add(":" + parameterName);
                parameters.put(parameterName, values.get(valueIndex) == null ? "" : values.get(valueIndex));
            }
            sql.append(" AND COALESCE(d.dynamic_fields->>'").append(KeywordSql.requireSafeFieldName(entry.getKey())).append("', '')")
                .append(include ? " IN (" : " NOT IN (")
                .append(String.join(",", paramRefs)).append(")");
        }
    }

    // 09/09/2026 ("tim kiem nang cao"): field -> danh sach gia tri, field PHAI khop IT NHAT 1 gia tri
    // (OR) - moi gia tri thu ca 2 dang: mang JSON chua phan tu ("@>" + jsonb_build_array) HOAC scalar
    // bang chuoi - cung ky thuat da dung cho vbdi/hosocv, viet lai cho duthao_documents.
    private void appendContainsAnyFieldValuePredicates(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> containsAnyFieldValues) {
        if (containsAnyFieldValues == null || containsAnyFieldValues.isEmpty()) {
            return;
        }
        int fieldIndex = 0;
        for (Map.Entry<String, List<String>> entry : containsAnyFieldValues.entrySet()) {
            String fieldName = entry.getKey();
            List<String> values = entry.getValue();
            if (fieldName == null || fieldName.isBlank() || values == null || values.isEmpty()) {
                fieldIndex++;
                continue;
            }
            sql.append(" AND (");
            boolean first = true;
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                String value = values.get(valueIndex);
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (!first) {
                    sql.append(" OR ");
                }
                first = false;
                String containsParam = "anyVal" + fieldIndex + "_" + valueIndex;
                String scalarParam = "anyValScalar" + fieldIndex + "_" + valueIndex;
                sql.append("(d.dynamic_fields->'").append(fieldName).append("' @> jsonb_build_array(CAST(:")
                    .append(containsParam).append(" AS text))")
                    .append(" OR COALESCE(d.dynamic_fields->>'").append(fieldName).append("', '') = :")
                    .append(scalarParam).append(")");
                parameters.put(containsParam, value);
                parameters.put(scalarParam, value);
            }
            if (first) {
                sql.append("1 = 0");
            }
            sql.append(")");
            fieldIndex++;
        }
    }

    private void appendKeywordPredicate(
            StringBuilder sql, Map<String, Object> parameters, String keyword, List<String> keywordColumns) {
        // 01/10/2026: xem KeywordSql (title + chong SQL injection ten cot).
        KeywordSql.append(sql, parameters, keyword, keywordColumns, "d");
    }

    private long countNative(String sql, Map<String, Object> parameters) {
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        parameters.forEach(countQuery::setParameter);
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    // 06/10/2026 (bang document_links - docs/architecture/document-links.md): van ban theo TAP id lay tu
    // DocumentLinkService (khong con khop field id noi ";" trong dynamic_fields).
    @Override
    public Page<DuThaoDocument> findByIds(
            String documentType, java.util.Collection<UUID> ids, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (ids == null || ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM duthao_documents d
            WHERE d.deleted_on IS NULL
              AND d.id IN (:ids)
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("ids", ids);
        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY ").append(NEWEST_CREATED_FIRST).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), DuThaoDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<DuThaoDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }
}
