package com.td.infrastructure.persistence.repository;

import com.td.application.vbdi.VbdiDocumentOutFilter;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.vbdi.VbdiDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * BL-220 (VBDi Core), yêu cầu người dùng (tách văn bản đi khỏi bảng dùng chung): adapter cho bảng vật
 * lý ĐỘC LẬP {@code vbdi_documents} (xem javadoc {@link VbdiDocument}) — KHÔNG dùng lại
 * {@code DocumentRepository} (query cứng "documents"). Copy CÓ CHỦ Ý predicate SQL của
 * {@code DocumentRepository#findByStatus} (chỉ đổi tên bảng/entity đích) — KHÔNG viết lại thuật toán.
 *
 * <p>Chỉ implement ĐÚNG method khai báo ở {@link VbdiDocumentRepository} (xem javadoc ở đó về việc
 * không copy nguyên văn toàn bộ surface {@code DocumentRepository}).</p>
 */
@Repository
public class PostgresVbdiDocumentRepository implements VbdiDocumentRepository {

    // Yeu cau nguoi dung 28/09/2026 ("So van ban di co quan/don vi/phong ban... sap xep uu tien ngay
    // ban hanh va so lon nhat len tren, fix tong the cac danh sach van ban di"): thay ORDER BY
    // last_modified_on cho MOI danh sach (tru thung rac - giu theo deleted_on).
    // - promulgation_date luu chuoi "dd/MM/yyyy" (da doi chieu du lieu that) - guard regex truoc TO_DATE.
    // - document_number co 2 dang that: so JSON ("12.0") va chuoi ("12/2026-VBD") - lay phan so DAU
    //   chuoi de so sanh theo gia tri (tranh "9" > "10" khi so sanh chuoi).
    // - Van ban chua vao so (chua co ngay/so) xuong cuoi (NULLS LAST), hoa vao nhau theo last_modified_on.
    private static final String ISSUED_ORDER_BY = " ORDER BY"
        + " (CASE WHEN NULLIF(d.dynamic_fields->>'promulgation_date', '') ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}'"
        + " THEN TO_DATE(LEFT(d.dynamic_fields->>'promulgation_date', 10), 'DD/MM/YYYY') ELSE NULL END) DESC NULLS LAST,"
        + " (CASE WHEN TRIM(COALESCE(d.dynamic_fields->>'document_number', '')) ~ '^[0-9]{1,18}'"
        + " THEN CAST(SUBSTRING(TRIM(d.dynamic_fields->>'document_number') FROM '^[0-9]{1,18}') AS bigint)"
        + " ELSE NULL END) DESC NULLS LAST,"
        + " d.last_modified_on DESC NULLS LAST";

    // 01/10/2026 (yêu cầu người dùng "vbdi/xuly/chocapkyso sắp xếp theo ngày tạo vì danh sách này chưa
    // vào sổ"): văn bản CHƯA vào sổ (DRAFT) chưa có ngày ban hành/số → ISSUED_ORDER_BY vô nghĩa, xếp
    // theo ngày tạo mới nhất trước. Áp cho chocapkyso/chobanhanh (findWaitingForRegistrationByIdentifier)
    // và chocapkysomc (findByAnyFieldEqualsIdentifier status DRAFT).
    private static final String CREATED_ORDER_BY =
        " ORDER BY d.created_on DESC NULLS LAST, d.last_modified_on DESC NULLS LAST";

    static String orderByForStatus(String status) {
        return status != null && com.td.application.vbdi.VbdiStatus.DRAFT.equalsIgnoreCase(status.trim())
            ? CREATED_ORDER_BY
            : ISSUED_ORDER_BY;
    }

    private final VbdiDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresVbdiDocumentRepository(VbdiDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<VbdiDocument> findById(UUID id) {
        // Ep kieu ro JpaRepository<VbdiDocument, UUID> - BaseRepository ke thua CA CrudRepository
        // (Spring Data) LẪN IRepository (port rieng) nen goi thang qua bien jpaRepository (kieu
        // VbdiDocumentJpaRepository) bi ambiguous - dung tien le PostgresDocumentRepository#findById.
        return ((org.springframework.data.jpa.repository.JpaRepository<VbdiDocument, UUID>) jpaRepository).findById(id);
    }

    @Override
    public <S extends VbdiDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(VbdiDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public Page<VbdiDocument> findByStatus(String documentType, String status, VbdiDocumentOutFilter filter, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NULL
              AND LOWER(COALESCE(d.status, '')) = :status
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("status", status == null ? "" : status.trim().toLowerCase());

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        appendFilterPredicates(sql, parameters, filter);

        long total = countNative(sql.toString(), parameters);

        sql.append(orderByForStatus(status)).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<VbdiDocument> findWaitingForRegistrationByIdentifier(
            String documentType, String status, String identifier, VbdiDocumentOutFilter filter, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NULL
              AND LOWER(COALESCE(d.status, '')) = :status
              AND COALESCE(d.dynamic_fields->>'mc_madv', '') = ''
              AND (COALESCE(d.dynamic_fields->>'unit_publish_identifier', '') = :identifier
                   OR COALESCE(d.dynamic_fields->>'unit_publish_identifier_other', '') = :identifier)
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("status", status == null ? "" : status.trim().toLowerCase());
        parameters.put("identifier", identifier == null ? "" : identifier.trim());

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        appendFilterPredicates(sql, parameters, filter);

        long total = countNative(sql.toString(), parameters);

        sql.append(orderByForStatus(status)).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<VbdiDocument> findIssuedByPublishOrCreatorIdentifier(
            String documentType, String status, String identifier, VbdiDocumentOutFilter filter, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NULL
              AND LOWER(COALESCE(d.status, '')) = :status
              AND (
                COALESCE(d.dynamic_fields->>'unit_publish_identifier', '') = :identifier
                OR (COALESCE(d.dynamic_fields->>'unit_publish_identifier_other', '') != ''
                    AND d.dynamic_fields->>'unit_publish_identifier_other' = :identifier)
                OR (COALESCE(d.dynamic_fields->>'unit_publish_identifier_other', '') = ''
                    AND COALESCE(d.dynamic_fields->>'unit_creator_identifier', '') = :identifier)
              )
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("status", status == null ? "" : status.trim().toLowerCase());
        parameters.put("identifier", identifier == null ? "" : identifier.trim());

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
        appendFilterPredicates(sql, parameters, filter);

        long total = countNative(sql.toString(), parameters);

        sql.append(ISSUED_ORDER_BY).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<VbdiDocument> findIssuedByCreatorOrUnitIdentifier(
            String documentType, String status, String identifier, VbdiDocumentOutFilter filter, Pageable pageable) {
        // dxl view_vanbandi.dxl (homqua/vwCreator_DocumentOut_ToDay/trongtuan/trongthang/4 tab SNV):
        // cot $4 = user_creator + ";" + unit_creator_identifier + ";" + unit_publish_identifier,
        // @Explode - OR PHANG 3 field (KHONG co nhanh @If nhu findIssuedByPublishOrCreatorIdentifier).
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NULL
              AND LOWER(COALESCE(d.status, '')) = :status
              AND (
                COALESCE(d.dynamic_fields->>'user_creator', '') = :identifier
                OR COALESCE(d.dynamic_fields->>'unit_creator_identifier', '') = :identifier
                OR COALESCE(d.dynamic_fields->>'unit_publish_identifier', '') = :identifier
              )
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("status", status == null ? "" : status.trim().toLowerCase());
        parameters.put("identifier", identifier == null ? "" : identifier.trim());

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
        appendFilterPredicates(sql, parameters, filter);

        long total = countNative(sql.toString(), parameters);

        sql.append(ISSUED_ORDER_BY).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 29/08/2026: áp {@link VbdiDocumentOutFilter} (khoảng ngày {@code promulgation_date}, keyword,
     * công thức SNV cố định, {@code document_register_id}) — dùng chung bởi cả 2 method identifier
     * ở trên, tránh lặp code (chỉ khác NHAU ở predicate khoá identifier gốc).
     *
     * <p>09/09/2026 (mở rộng cho "tìm kiếm nâng cao" — cutover dashboard {@code qlvb/tichhop/TimKiem?
     * databasetype=vbdi}): thêm {@code includeFieldValues} (EXACT match, IN-list)/
     * {@code containsAnyFieldValues} (containment JSON-array-HOẶC-scalar-equality, OR nhiều giá trị)
     * — cùng kỹ thuật SQL {@code PostgresDocumentRepository} đã dùng cho vbden, viết lại cho bảng
     * {@code vbdi_documents} (khác tên bảng nên KHÔNG tái dùng trực tiếp được method của lớp đó).</p>
     */
    private void appendFilterPredicates(StringBuilder sql, Map<String, Object> parameters, VbdiDocumentOutFilter filter) {
        if (filter == null) {
            return;
        }
        appendIncludeFieldValuesPredicates(sql, parameters, filter.getIncludeFieldValues());
        appendContainsAnyFieldValuePredicates(sql, parameters, filter.getContainsAnyFieldValues());
        TextContainsSql.append(sql, parameters, filter.getTextContainsFieldValues(), true); // 19/10/2026
        // Bugfix 21/08/2026 style (PostgresDocumentRepository#dynamicSortExpression): promulgation_date
        // luon luu "DD/MM/YYYY" (chuoi) - guard regex truoc khi TO_DATE, tranh throw voi du lieu
        // cu/sai dang (tra NULL, khong khop khoang ngay thay vi loi runtime).
        String promulgationDateExpr = "(CASE WHEN NULLIF(d.dynamic_fields->>'promulgation_date', '') "
            + "~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$' THEN TO_DATE(d.dynamic_fields->>'promulgation_date', "
            + "'DD/MM/YYYY') ELSE NULL END)";
        if (filter.getPromulgationDateFrom() != null) {
            sql.append(" AND ").append(promulgationDateExpr).append(" >= :promulgationDateFrom");
            parameters.put("promulgationDateFrom", java.sql.Date.valueOf(filter.getPromulgationDateFrom()));
        }
        if (filter.getPromulgationDateTo() != null) {
            sql.append(" AND ").append(promulgationDateExpr).append(" <= :promulgationDateTo");
            parameters.put("promulgationDateTo", java.sql.Date.valueOf(filter.getPromulgationDateTo()));
        }
        if (filter.getDocumentRegisterId() != null && !filter.getDocumentRegisterId().isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.dynamic_fields->>'document_register_id', '')) = :documentRegisterId");
            parameters.put("documentRegisterId", filter.getDocumentRegisterId().trim().toLowerCase());
        }
        // Bugfix/port 29/08/2026 (vwDocumentOut_BaoCaoChuaXem) - loai van ban ma :member (da duoc
        // findByAnyFieldContainsMember bind san tu truoc khi goi method nay) DA co trong field nay
        // (vd log_seen_document - "da xem") - tai dung CHINH parameter ":member" da dang ky, khong
        // can them tham so rieng.
        if (filter.getExcludeMemberFieldName() != null && !filter.getExcludeMemberFieldName().isBlank()) {
            sql.append(" AND NOT ((';' || LOWER(COALESCE(d.dynamic_fields->>'")
                .append(filter.getExcludeMemberFieldName())
                .append("', '')) || ';') LIKE '%;' || LOWER(:member) || ';%')");
        }
        // extraPredicateSql: hang so noi bo co dinh (4 cong thuc SNV, xem ViewQueryService) - KHONG
        // BAO GIO tu client, nen noi thang vao SQL an toan (xem javadoc VbdiDocumentOutFilter).
        if (filter.getExtraPredicateSql() != null && !filter.getExtraPredicateSql().isBlank()) {
            sql.append(" AND (").append(filter.getExtraPredicateSql()).append(")");
        }
        String keyword = filter.getKeyword();
        List<String> keywordColumns = filter.getKeywordColumns();
        if (keyword != null && !keyword.isBlank() && keywordColumns != null && !keywordColumns.isEmpty()) {
            String likeValue = SearchTextSql.containsLike(keyword); // 20/10/2026: không phân biệt dấu
            sql.append(" AND (");
            for (int index = 0; index < keywordColumns.size(); index++) {
                if (index > 0) {
                    sql.append(" OR ");
                }
                String parameterName = "keyword" + index;
                sql.append("td_search_norm(").append(keywordColumnExpression(keywordColumns.get(index))).append(")")
                    .append(" LIKE :").append(parameterName);
                parameters.put(parameterName, likeValue);
            }
            sql.append(")");
        }
    }

    // 09/09/2026 ("tim kiem nang cao"): field -> danh sach gia tri, field PHAI khop CHINH XAC 1 trong
    // so do (IN-list qua ->>'field') - nhieu field khac nhau trong map ket hop AND. Cung ky thuat
    // PostgresDocumentRepository#appendDynamicFieldValueInPredicate (vbden), viet lai cho vbdi_documents.
    private void appendIncludeFieldValuesPredicates(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> includeFieldValues) {
        if (includeFieldValues == null || includeFieldValues.isEmpty()) {
            return;
        }
        int fieldIndex = 0;
        for (Map.Entry<String, List<String>> entry : includeFieldValues.entrySet()) {
            String fieldName = entry.getKey();
            List<String> values = entry.getValue();
            if (fieldName == null || fieldName.isBlank() || values == null || values.isEmpty()) {
                fieldIndex++;
                continue;
            }
            List<String> paramRefs = new java.util.ArrayList<>();
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                String parameterName = "include" + fieldIndex + "_" + valueIndex;
                paramRefs.add(":" + parameterName);
                parameters.put(parameterName, values.get(valueIndex) == null ? "" : values.get(valueIndex));
            }
            sql.append(" AND COALESCE(d.dynamic_fields->>'").append(fieldName).append("', '') IN (")
                .append(String.join(",", paramRefs)).append(")");
            fieldIndex++;
        }
    }

    // 09/09/2026 ("tim kiem nang cao"): field -> danh sach gia tri, field PHAI khop IT NHAT 1 gia tri
    // (OR) - moi gia tri thu ca 2 dang: mang JSON chua phan tu ("@>" + jsonb_build_array) HOAC scalar
    // bang chuoi (an toan ca 2 kieu luu du lieu, cung ngu nghia appendContainsAnyFieldValuePredicates
    // ben vbden - viet lai bang jsonb_build_array truc tiep trong SQL thay vi Jackson ObjectMapper vi
    // lop nay chua san co dependency do).
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
                    .append(scalarParam);
                // 27/09/2026: + khop thanh vien chuoi "a;b" - xem javadoc DelimitedMemberSql.
                DelimitedMemberSql.appendOrMember(
                    sql, parameters, fieldName, value, "anyValMember" + fieldIndex + "_" + valueIndex);
                sql.append(")");
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

    /**
     * Bugfix 29/08/2026 (người dùng báo "tìm kiếm không báo lỗi nhưng không ra kết quả" — văn bản
     * có trích yếu "van ban12" tìm không thấy): {@code SoanThaoVbdiUseCase} ghi trích yếu vào CỘT
     * TĨNH {@code title} (KHÔNG ghi trùng vào {@code dynamic_fields->>'subject'}, xem javadoc
     * {@code toFilteredDocumentFields} — {@code putIfAbsent("subject", document.getTitle())} đã xử
     * lý fallback này khi ĐỌC, nhưng tìm kiếm chỉ tìm trên {@code dynamic_fields} nên bỏ sót). Cột
     * {@code subject} trong {@code ColumsTableVBdiSeach} (FE) phải fallback về {@code d.title} khi
     * {@code dynamic_fields} không có — các cột khác giữ nguyên chỉ tìm trên {@code dynamic_fields}.
     */
    private String keywordColumnExpression(String columnName) {
        // 01/10/2026: ten cot den tu client va bi noi thang vao SQL - chan dinh danh khong an toan.
        if (!KeywordSql.isSafeFieldName(columnName)) {
            return "''";
        }
        if ("subject".equals(columnName)) {
            return "COALESCE(NULLIF(d.dynamic_fields->>'subject', ''), d.title, '')";
        }
        return "COALESCE(d.dynamic_fields->>'" + columnName + "', '')";
    }

    @Override
    public Page<VbdiDocument> findByKiemTrungKey(
            String documentType, String status, String compositeKey, Pageable pageable) {
        // Ghep key SQL-side dung khuon Domino (dxl view_vanbandi.dxl dong ~2395-2402): document_register_id
        // + "|" + document_number [+ "|" + number_other neu khac rong] + "|" + nam(promulgation_date).
        // promulgation_date luon luu dinh dang "DD/MM/YYYY" (VBDI_DATE_FIELDS, vbdiApi.js) nen RIGHT(...,4)
        // lay dung 4 ky tu nam cuoi chuoi - don gian/on dinh hon tu parse date trong SQL.
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NULL
              AND LOWER(COALESCE(d.status, '')) = :status
              AND COALESCE(d.dynamic_fields->>'dulieucu', '') != '1'
              AND COALESCE(d.dynamic_fields->>'document_number', '') != ''
              AND COALESCE(d.dynamic_fields->>'document_register_id', '') != ''
              AND LOWER(TRIM(BOTH FROM (
                    COALESCE(d.dynamic_fields->>'document_register_id', '') || '|' ||
                    COALESCE(d.dynamic_fields->>'document_number', '') ||
                    CASE WHEN COALESCE(d.dynamic_fields->>'number_other', '') != ''
                         THEN '|' || (d.dynamic_fields->>'number_other')
                         ELSE '' END ||
                    '|' || RIGHT(COALESCE(d.dynamic_fields->>'promulgation_date', ''), 4)
                  ))) = :compositeKey
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("status", status == null ? "" : status.trim().toLowerCase());
        parameters.put("compositeKey", compositeKey == null ? "" : compositeKey.trim().toLowerCase());

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(ISSUED_ORDER_BY).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<VbdiDocument> findByAnyFieldEqualsIdentifier(
            String documentType, String status, List<String> fieldNames, String identifier,
            VbdiDocumentOutFilter filter, Pageable pageable) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NULL
              AND LOWER(COALESCE(d.status, '')) = :status
              AND (
            """);
        for (int index = 0; index < fieldNames.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            sql.append("COALESCE(d.dynamic_fields->>'").append(fieldNames.get(index)).append("', '') = :identifier");
        }
        sql.append(")");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("status", status == null ? "" : status.trim().toLowerCase());
        parameters.put("identifier", identifier == null ? "" : identifier.trim());

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
        appendFilterPredicates(sql, parameters, filter);

        long total = countNative(sql.toString(), parameters);

        sql.append(orderByForStatus(status)).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<VbdiDocument> findByAnyFieldContainsMember(
            String documentType, String status, List<String> fieldNames, String member,
            VbdiDocumentOutFilter filter, Pageable pageable) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        // 06/10/2026: status null = KHONG loc trang thai (vwIssuance_Document_Profiles_ID - tab "Van ban
        // phat hanh" ho so cong viec, formula Domino khong co dieu kien status). Moi caller cu deu truyen
        // VbdiStatus.ISSUED nen khong doi hanh vi.
        boolean filterStatus = status != null;
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NULL
            """);
        if (filterStatus) {
            sql.append("  AND LOWER(COALESCE(d.status, '')) = :status\n");
        }
        sql.append("  AND (\n");
        // Tuong duong @Explode(field;";") roi so khop - boc them ";" 2 dau field/gia tri tim de tranh
        // khop nham chuoi con (vd "H05.1" khong duoc khop nham trong "H05.10") - xem javadoc method.
        for (int index = 0; index < fieldNames.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            sql.append("(';' || COALESCE(d.dynamic_fields->>'").append(fieldNames.get(index))
                .append("', '') || ';') LIKE '%;' || :member || ';%'");
        }
        sql.append(")");

        Map<String, Object> parameters = new LinkedHashMap<>();
        if (filterStatus) {
            parameters.put("status", status.trim().toLowerCase());
        }
        parameters.put("member", member == null ? "" : member.trim());

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
        appendFilterPredicates(sql, parameters, filter);

        long total = countNative(sql.toString(), parameters);

        sql.append(ISSUED_ORDER_BY).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<VbdiDocument> findByPublishOrCreatorIdentifier(
            String documentType, String identifier, VbdiDocumentOutFilter filter, Pageable pageable) {
        // Cung cong thuc @If-fallback voi findIssuedByPublishOrCreatorIdentifier - KHONG co dieu
        // kien status (giao het cho filter.extraPredicateSql, xem javadoc interface).
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NULL
              AND (
                COALESCE(d.dynamic_fields->>'unit_publish_identifier', '') = :identifier
                OR (COALESCE(d.dynamic_fields->>'unit_publish_identifier_other', '') != ''
                    AND d.dynamic_fields->>'unit_publish_identifier_other' = :identifier)
                OR (COALESCE(d.dynamic_fields->>'unit_publish_identifier_other', '') = ''
                    AND COALESCE(d.dynamic_fields->>'unit_creator_identifier', '') = :identifier)
              )
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("identifier", identifier == null ? "" : identifier.trim());

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
        appendFilterPredicates(sql, parameters, filter);

        long total = countNative(sql.toString(), parameters);

        sql.append(ISSUED_ORDER_BY).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<VbdiDocument> findByFieldContainsMemberAnySeparator(
            String documentType, String status, String fieldName, String member,
            VbdiDocumentOutFilter filter, Pageable pageable) {
        // vwPrivateImportant (MarkImportant): "," VA ";" la 2 dau phan cach tuong duong - chuan hoa
        // "," -> ";" truoc khi boc dau phan cach so khop (xem javadoc interface). KHONG co dieu kien
        // status trong SELECT formula goc (@Trim(MarkImportant)!="" ap dung cho MOI trang thai) -
        // status o day la TUY CHON (null/rong = khong loc), khac moi method khac trong lop nay.
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NULL
              AND (';' || REPLACE(COALESCE(d.dynamic_fields->>'%s', ''), ',', ';') || ';')
                  LIKE '%%;' || :member || ';%%'
            """.formatted(fieldName));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("member", member == null ? "" : member.trim());

        if (status != null && !status.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.status, '')) = :status");
            parameters.put("status", status.trim().toLowerCase());
        }

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
        appendFilterPredicates(sql, parameters, filter);

        long total = countNative(sql.toString(), parameters);

        sql.append(ISSUED_ORDER_BY).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<VbdiDocument> findTrashedByUsernameOrDeleter(
            String documentType, String username, UUID deletedByUserId,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        // 11/09/2026 (port "vbdi/thungrac/thungrac", SUA LAI - xem javadoc interface): DUY NHAT
        // method loc deleted_on IS NOT NULL (moi method khac trong lop nay loc NGUOC LAI). Khoa theo
        // view Domino THAT vwCreator_DocumentOut_Delete - categorized user_creator/
        // unit_creator_identifier/delete_people, FE gui username hien tai lam key.
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NOT NULL
              AND (
                COALESCE(d.dynamic_fields->>'user_creator', '') = :username
                OR COALESCE(d.dynamic_fields->>'unit_creator_identifier', '') = :username
                OR d.deleted_by = :deletedByUserId
              )
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("username", username == null ? "" : username.trim());
        parameters.put("deletedByUserId", deletedByUserId);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
        if (keyword != null && !keyword.isBlank() && keywordColumns != null && !keywordColumns.isEmpty()) {
            String likeValue = SearchTextSql.containsLike(keyword); // 20/10/2026: không phân biệt dấu
            sql.append(" AND (");
            for (int index = 0; index < keywordColumns.size(); index++) {
                if (index > 0) {
                    sql.append(" OR ");
                }
                String parameterName = "keyword" + index;
                sql.append("td_search_norm(").append(keywordColumnExpression(keywordColumns.get(index))).append(")")
                    .append(" LIKE :").append(parameterName);
                parameters.put(parameterName, likeValue);
            }
            sql.append(")");
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.deleted_on DESC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private long countNative(String sql, Map<String, Object> parameters) {
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        parameters.forEach(countQuery::setParameter);
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    // 06/10/2026 (bang document_links - docs/architecture/document-links.md): van ban theo TAP id lay tu
    // DocumentLinkService (khong con khop field id noi ";" trong dynamic_fields).
    @Override
    public Page<VbdiDocument> findByIds(
            String documentType, java.util.Collection<UUID> ids, com.td.application.vbdi.VbdiDocumentOutFilter filter, Pageable pageable) {
        if (ids == null || ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbdi_documents d
            WHERE d.deleted_on IS NULL
              AND d.id IN (:ids)
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("ids", ids);
        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
        appendFilterPredicates(sql, parameters, filter);

        long total = countNative(sql.toString(), parameters);

        sql.append(ISSUED_ORDER_BY).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdiDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdiDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }
}