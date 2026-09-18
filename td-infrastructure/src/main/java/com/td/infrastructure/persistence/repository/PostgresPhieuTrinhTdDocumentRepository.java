package com.td.infrastructure.persistence.repository;

import com.td.application.phieutrinhtd.PhieuTrinhTdDocumentRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
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
 * Adapter cho bảng vật lý ĐỘC LẬP {@code phieutrinhtd_documents} — copy CÓ CHỦ Ý predicate SQL của
 * {@code PostgresVbcddhDocumentRepository} (chỉ đổi tên bảng/entity đích), đúng tiền lệ đã dùng cho
 * {@code vbcddh}/{@code hosoluutru}. Xem
 * {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md} mục 6.
 *
 * <p><b>Cập nhật 09/09/2026 (đối chiếu DXL thật `docs/views_domino/view_phieutrinh.dxl` — path DXL
 * xác nhận đây chính là `phieutrinhtd.nsf`, 26 view)</b> — sửa 2 sai lệch thật phát hiện được so với
 * bản port ban đầu (suy từ FE, chưa có DXL):
 * <ul>
 *   <li>Tên field khoá thật là {@code user_creator}/{@code user_lanhdao1}/{@code user_lanhdao2}/
 *       {@code user_lanhdaovanphong} (có tiền tố {@code "user_"}) — bản trước dùng nhầm
 *       {@code usercreator}/{@code lanhdao1}/{@code lanhdao2}/{@code lanhdaovanphong} (thiếu tiền tố/
 *       thiếu gạch dưới), khiến MỌI view cá nhân (dangsoan/trinhduyet/choduyet/daduyet/khongduyet/
 *       trinhthongqua/chothongqua/dathongqua/khongthongqua) không khớp được bản ghi nào trên dữ liệu
 *       thật.</li>
 *   <li>MỌI view cá nhân thật đều có thêm điều kiện {@code storaged != "1"} (vd `vwUser_Creator_0`
 *       dòng 397 DXL) — bản trước KHÔNG lọc field này, sẽ hiển thị nhầm cả hồ sơ đã "kết thúc"
 *       (storaged) trong các hàng đợi cá nhân.</li>
 * </ul>
 * <p>Riêng {@link #findThamKhaoChoYKien}: đổi HẲN cơ chế nền — bản trước dùng {@code nguoithamkhaohide}/
 * {@code nguoidathamkhaohide} (chỉ có bằng chứng từ modal FE PHỤ/ít dùng, KHÔNG có view Domino nào hậu
 * thuẫn trong 26 view thật). DXL xác nhận cơ chế "cho ý kiến"/"phối hợp" THẬT là {@code user_coordinator}
 * (danh sách phân tách bởi DẤU PHẨY ",", KHÁC quy ước ";" của các field khác — DXL tự
 * {@code @ReplaceSubstring(user_coordinator; ","; ";")} trước khi so khớp, xem `vwUser_Coordinator_chua`
 * dòng 893-929)/{@code user_coordinated} (chuỗi so khớp {@code @Contains} thô, không tách phần tử) — đã
 * viết lại theo đúng 2 view `vwUser_Coordinator_chua`/`vwUser_Coordinated_Status`.</p>
 */
@Repository
public class PostgresPhieuTrinhTdDocumentRepository implements PhieuTrinhTdDocumentRepository {

    private final PhieuTrinhTdDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresPhieuTrinhTdDocumentRepository(PhieuTrinhTdDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<PhieuTrinhTdDocument> findById(UUID id) {
        // Ep kieu ro JpaRepository<PhieuTrinhTdDocument, UUID> - dung tien le
        // PostgresVbcddhDocumentRepository#findById (BaseRepository ke thua ca CrudRepository lan
        // IRepository nen goi thang qua bien jpaRepository bi ambiguous).
        return ((org.springframework.data.jpa.repository.JpaRepository<PhieuTrinhTdDocument, UUID>) jpaRepository)
            .findById(id);
    }

    @Override
    public <S extends PhieuTrinhTdDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(PhieuTrinhTdDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public Page<PhieuTrinhTdDocument> findByAnyFieldContainsMember(
            List<String> fieldNames, String member, Pageable pageable) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM phieutrinhtd_documents d
            WHERE d.deleted_on IS NULL
              AND (
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyFieldContainsMemberOr(sql, parameters, fieldNames, member);
        sql.append(")");
        appendNotStoragedFilter(sql);

        return executeQuery(sql, parameters, pageable);
    }

    @Override
    public Page<PhieuTrinhTdDocument> findByAnyFieldContainsMemberAndStatusIn(
            List<String> fieldNames, String member, List<String> statuses, Pageable pageable) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM phieutrinhtd_documents d
            WHERE d.deleted_on IS NULL
              AND (
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyFieldContainsMemberOr(sql, parameters, fieldNames, member);
        sql.append(")");
        appendStatusInFilter(sql, parameters, statuses);
        appendNotStoragedFilter(sql);

        return executeQuery(sql, parameters, pageable);
    }

    @Override
    public Page<PhieuTrinhTdDocument> findApproverWaiting(String member, Pageable pageable) {
        // DXL vwUser_NguoiDuyet_5_25 (dong 540-575): key = user_lanhdao1 khi status="5", user_lanhdao2
        // khi status="25" - CHU Y field co tien to "user_" (khac lanhdao1/lanhdao2 la field TEN HIEN
        // THI, khong dung de so khop username) + dieu kien storaged != "1".
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM phieutrinhtd_documents d
            WHERE d.deleted_on IS NULL
              AND (
                (d.status = '5' AND (';' || UPPER(TRIM(COALESCE(d.dynamic_fields->>'user_lanhdao1', ''))) || ';')
                    LIKE '%;' || UPPER(TRIM(:member)) || ';%')
                OR
                (d.status = '25' AND (';' || UPPER(TRIM(COALESCE(d.dynamic_fields->>'user_lanhdao2', ''))) || ';')
                    LIKE '%;' || UPPER(TRIM(:member)) || ';%')
              )
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("member", member == null ? "" : member);
        appendNotStoragedFilter(sql);

        return executeQuery(sql, parameters, pageable);
    }

    // Doi ten/cai lai logic 09/09/2026 (doi chieu DXL that): "cho y kien"/"phoi hop" THAT la co che
    // user_coordinator (danh sach ngan cach BOI DAU PHAY ",", KHAC quy uoc ";" cua cac field khac -
    // DXL tu doi "," -> ";" truoc khi so khop, xem vwUser_Coordinator_chua) / user_coordinated (so
    // khop @Contains THO, khong tach phan tu - gia lap bang LIKE substring khong bien ";"). Bo han co
    // che cu nguoithamkhaohide/nguoidathamkhaohide (KHONG co view Domino nao hau thuan trong 26 view
    // that cua phieutrinhtd.nsf).
    @Override
    public Page<PhieuTrinhTdDocument> findThamKhaoChoYKien(String member, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM phieutrinhtd_documents d
            WHERE d.deleted_on IS NULL
              AND (';' || UPPER(TRIM(REPLACE(COALESCE(d.dynamic_fields->>'user_coordinator', ''), ',', ';'))) || ';')
                  LIKE '%;' || UPPER(TRIM(:member)) || ';%'
              AND UPPER(COALESCE(d.dynamic_fields->>'user_coordinated', ''))
                  NOT LIKE '%' || UPPER(TRIM(:member)) || '%'
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("member", member == null ? "" : member);
        appendNotStoragedFilter(sql);

        return executeQuery(sql, parameters, pageable);
    }

    @Override
    public Page<PhieuTrinhTdDocument> findTrash(Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM phieutrinhtd_documents d
            WHERE d.deleted_on IS NOT NULL
            """);
        return executeQuery(sql, new LinkedHashMap<>(), pageable);
    }

    // Tuong duong @Explode(field;";") roi so khop KHONG phan biet hoa/thuong - dung tien le
    // PostgresVbcddhDocumentRepository#appendAnyFieldContainsMemberOr.
    // Package-private (khong phai private) de unit test SQL builder goi truc tiep, dung khuon
    // PostgresHoSoLuuTruDocumentRepositoryTest.
    void appendAnyFieldContainsMemberOr(
            StringBuilder sql, Map<String, Object> parameters, List<String> fieldNames, String member) {
        for (int index = 0; index < fieldNames.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            sql.append("(';' || UPPER(TRIM(COALESCE(d.dynamic_fields->>'").append(fieldNames.get(index))
                .append("', ''))) || ';') LIKE '%;' || UPPER(TRIM(:member)) || ';%'");
        }
        parameters.put("member", member == null ? "" : member);
    }

    // 09/09/2026 (doi chieu DXL that): MOI view "ca nhan" cua phieutrinhtd deu co dieu kien
    // "storaged != \"1\"" (vd vwUser_Creator_0 dong 397, vwUser_NguoiDuyet_5_25 dong 557,
    // vwUser_Coordinator_chua dong 911 cua view_phieutrinh.dxl) - ho so da "ket thuc" (storaged="1")
    // KHONG con xuat hien trong hang doi ca nhan. Ap dung cho MOI method truy van hang doi ca nhan
    // (KHONG ap dung cho findTrash - thung rac la tap rieng theo deleted_on, khong lien quan storaged).
    void appendNotStoragedFilter(StringBuilder sql) {
        sql.append(" AND COALESCE(d.dynamic_fields->>'storaged', '') != '1'");
    }

    void appendStatusInFilter(StringBuilder sql, Map<String, Object> parameters, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        sql.append(" AND d.status IN (");
        for (int index = 0; index < statuses.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            String paramName = "status" + index;
            sql.append(":").append(paramName);
            parameters.put(paramName, statuses.get(index));
        }
        sql.append(")");
    }

    private Page<PhieuTrinhTdDocument> executeQuery(StringBuilder sql, Map<String, Object> parameters, Pageable pageable) {
        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.created_on ASC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), PhieuTrinhTdDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<PhieuTrinhTdDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private long countNative(String sql, Map<String, Object> parameters) {
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        parameters.forEach(countQuery::setParameter);
        return ((Number) countQuery.getSingleResult()).longValue();
    }
}
