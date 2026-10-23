package com.td.infrastructure.tonghop;

import com.td.application.tonghop.ProvinceDispatchDocumentItem;
import com.td.application.tonghop.ProvinceDispatchRecipientItem;
import com.td.application.tonghop.ProvinceDispatchSearchRequest;
import com.td.application.tonghop.ProvinceDispatchSummary;
import com.td.application.tonghop.ProvinceDocumentItem;
import com.td.application.tonghop.ProvinceDocumentSearchRequest;
import com.td.application.tonghop.ProvinceDocumentSearchResult;
import com.td.application.tonghop.ProvinceDocumentStates;
import com.td.application.tonghop.ProvinceQueryRepository;
import com.td.application.tonghop.ProvinceStatisticsResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Truy vấn kho tổng hợp toàn tỉnh ({@code province_*}, control-plane). Mọi điều kiện lọc đều qua tham số
 * đặt tên — không ghép chuỗi giá trị người dùng vào SQL.
 */
@Repository
public class PostgresProvinceQueryRepository implements ProvinceQueryRepository {

    private static final int MAX_UNIT_FILTERS = 300;
    private static final int MAX_KEYWORD_TERMS = 8;
    private static final Set<String> KINDS = Set.of(ProvinceDocumentStates.KIND_VBDEN, ProvinceDocumentStates.KIND_VBDI);
    private static final Map<String, String> CHANNEL_COLUMNS = Map.of(
        ProvinceDocumentStates.CHANNEL_NOI_BO, "d.is_internal",
        ProvinceDocumentStates.CHANNEL_LIEN_THONG, "d.is_network",
        ProvinceDocumentStates.CHANNEL_TRUC_QG, "d.is_national",
        ProvinceDocumentStates.CHANNEL_GIAY, "d.is_paper");
    private static final Set<String> RECIPIENT_GROUPS = Set.of("internal_unit", "contact_unit", "external_unit");
    /** Nhóm nơi nhận → cờ kênh trên province_documents (tính từ nơi nhận thật khi đồng bộ). */
    private static final Map<String, String> GROUP_FLAG_COLUMNS = Map.of(
        "internal_unit", "d.is_internal", "contact_unit", "d.is_network", "external_unit", "d.is_national");

    private final NamedParameterJdbcTemplate jdbc;
    /** Transaction chỉ đọc — để {@code SET LOCAL work_mem} áp dụng cho cả nhóm truy vấn thống kê nặng. */
    private final TransactionTemplate readTx;
    private final Cache<String, Object> cache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofMinutes(2))
        .build();

    public PostgresProvinceQueryRepository(@Qualifier("controlPlaneDataSource") DataSource controlPlaneDataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(controlPlaneDataSource);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(controlPlaneDataSource));
        tx.setReadOnly(true);
        this.readTx = tx;
    }

    // ------------------------------------------------------------------ Tìm kiếm văn bản

    @Override
    public ProvinceDocumentSearchResult searchDocuments(ProvinceDocumentSearchRequest r, LocalDate from, LocalDate to,
                                                        int offset, int limit) {
        return cached("search", r + "|" + from + "|" + to + "|" + offset + "|" + limit,
            () -> loadDocuments(r, from, to, offset, limit));
    }

    private ProvinceDocumentSearchResult loadDocuments(ProvinceDocumentSearchRequest r, LocalDate from, LocalDate to,
                                                       int offset, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();
        where.add("d.deleted = FALSE");
        addScopeTenant(where, p, r.getScopeTenantId());
        if (!Boolean.TRUE.equals(r.getIncludeRevoked())) {
            where.add("d.is_revoked = FALSE");
        }
        List<String> kinds = upper(r.getKinds()).stream().filter(KINDS::contains).toList();
        if (!kinds.isEmpty()) {
            where.add("d.doc_kind IN (:kinds)");
            p.addValue("kinds", kinds);
        }
        addUnitFilter(where, p, "d.owner_identifier", "unit", r.getUnitIdentifiers(),
            !Boolean.FALSE.equals(r.getIncludeChildUnits()));
        addDateRange(where, p, "d.doc_date", from, to);
        List<String> channelConditions = upper(r.getChannels()).stream()
            .map(CHANNEL_COLUMNS::get).filter(c -> c != null).distinct().toList();
        if (!channelConditions.isEmpty()) {
            where.add("(" + String.join(" OR ", channelConditions) + ")");
        }
        List<String> states = upper(r.getProcessStates());
        if (!states.isEmpty()) {
            where.add("d.process_state IN (:states)");
            p.addValue("states", states);
        }
        if (r.getSigned() != null) {
            where.add("d.is_signed = :signed");
            p.addValue("signed", r.getSigned());
        }
        if (r.getOverdue() != null) {
            where.add("d.is_overdue = :overdue");
            p.addValue("overdue", r.getOverdue());
        }
        addKeyword(where, p, r.getKeyword());
        String whereSql = " WHERE " + String.join(" AND ", where);

        Map<String, Map<String, Long>> facets = jdbc.query("""
                SELECT COUNT(*) AS total,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDEN') AS k_vbden,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDI') AS k_vbdi,
                  COUNT(*) FILTER (WHERE d.process_state = 'CHO_VAO_SO') AS s_cho_vao_so,
                  COUNT(*) FILTER (WHERE d.process_state = 'CHUA_XU_LY') AS s_chua_xu_ly,
                  COUNT(*) FILTER (WHERE d.process_state = 'DANG_XU_LY') AS s_dang_xu_ly,
                  COUNT(*) FILTER (WHERE d.process_state = 'DA_XU_LY') AS s_da_xu_ly,
                  COUNT(*) FILTER (WHERE d.process_state = 'TU_CHOI') AS s_tu_choi,
                  COUNT(*) FILTER (WHERE d.process_state = 'DA_GUI') AS s_da_gui,
                  COUNT(*) FILTER (WHERE d.process_state = 'CHUA_GUI') AS s_chua_gui,
                  COUNT(*) FILTER (WHERE d.is_internal) AS c_noi_bo,
                  COUNT(*) FILTER (WHERE d.is_network) AS c_lien_thong,
                  COUNT(*) FILTER (WHERE d.is_national) AS c_truc_qg,
                  COUNT(*) FILTER (WHERE d.is_paper) AS c_giay,
                  COUNT(*) FILTER (WHERE d.is_signed) AS f_signed,
                  COUNT(*) FILTER (WHERE d.is_overdue) AS f_overdue
                FROM province_documents d""" + whereSql, p, rs -> {
            Map<String, Map<String, Long>> out = new LinkedHashMap<>();
            if (!rs.next()) {
                return out;
            }
            Map<String, Long> kind = new LinkedHashMap<>();
            kind.put("VBDEN", rs.getLong("k_vbden"));
            kind.put("VBDI", rs.getLong("k_vbdi"));
            Map<String, Long> state = new LinkedHashMap<>();
            for (String s : List.of("CHO_VAO_SO", "CHUA_XU_LY", "DANG_XU_LY", "DA_XU_LY", "TU_CHOI", "DA_GUI", "CHUA_GUI")) {
                state.put(s, rs.getLong("s_" + s.toLowerCase()));
            }
            Map<String, Long> channel = new LinkedHashMap<>();
            for (String c : List.of("NOI_BO", "LIEN_THONG", "TRUC_QG", "GIAY")) {
                channel.put(c, rs.getLong("c_" + c.toLowerCase()));
            }
            Map<String, Long> flags = new LinkedHashMap<>();
            flags.put("total", rs.getLong("total"));
            flags.put("signed", rs.getLong("f_signed"));
            flags.put("overdue", rs.getLong("f_overdue"));
            out.put("kind", kind);
            out.put("processState", state);
            out.put("channel", channel);
            out.put("flags", flags);
            return out;
        });
        long total = facets == null || facets.isEmpty() ? 0 : facets.get("flags").get("total");

        p.addValue("limit", limit).addValue("offset", offset);
        List<ProvinceDocumentItem> items = total == 0 ? List.of() : jdbc.query(
            "SELECT d.* FROM province_documents d" + whereSql
                + " ORDER BY d.doc_date DESC NULLS LAST, d.created_on DESC NULLS LAST LIMIT :limit OFFSET :offset",
            p, (rs, i) -> mapDocument(rs));
        return ProvinceDocumentSearchResult.builder()
            .items(items)
            .totalItems(total)
            .facets(facets)
            .build();
    }

    @Override
    public Optional<ProvinceDocumentItem> findDocument(String tenantId, UUID documentId) {
        List<ProvinceDocumentItem> rows = jdbc.query(
            "SELECT d.* FROM province_documents d WHERE d.tenant_id = :t AND d.document_id = :id",
            new MapSqlParameterSource("t", tenantId).addValue("id", documentId), (rs, i) -> mapDocument(rs));
        return rows.stream().findFirst();
    }

    // ------------------------------------------------------------------ Tổng hợp chung

    @Override
    public ProvinceStatisticsResult statistics(LocalDate from, LocalDate to) {
        return cached("statistics", from + "|" + to, () -> loadStatistics(from, to));
    }

    private ProvinceStatisticsResult loadStatistics(LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        List<String> where = new ArrayList<>(List.of("d.deleted = FALSE", "d.is_revoked = FALSE",
            "d.owner_identifier IS NOT NULL"));
        addDateRange(where, p, "d.doc_date", from, to);
        String whereSql = " WHERE " + String.join(" AND ", where);
        List<ProvinceStatisticsResult.UnitStatistic> units = jdbc.query("""
                SELECT d.owner_identifier, MAX(d.owner_name) AS owner_name,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDEN') AS vbden,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDEN' AND d.process_state IN ('CHO_VAO_SO','CHUA_XU_LY','DANG_XU_LY')) AS vbden_chuaxl,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDEN' AND d.process_state = 'DA_XU_LY') AS vbden_daxl,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDEN' AND d.is_overdue) AS vbden_quahan,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDEN' AND d.is_paper) AS vbgiay,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDEN' AND d.is_internal) AS vbden_noibo,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDEN' AND (d.is_network OR d.is_national)) AS vbden_truc,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDI') AS vbdi,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDI' AND d.is_signed) AS vbdi_kyso,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDI' AND d.is_internal) AS vbdi_noibo,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDI' AND (d.is_network OR d.is_national)) AS vbdi_truc,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDI' AND d.is_paper) AS vbdi_vbgiay,
                  MAX(d.created_on) AS last_activity
                FROM province_documents d""" + whereSql + " GROUP BY d.owner_identifier ORDER BY d.owner_identifier",
            p, (rs, i) -> ProvinceStatisticsResult.UnitStatistic.builder()
                .identifier(rs.getString("owner_identifier"))
                .name(rs.getString("owner_name"))
                .vbden(rs.getLong("vbden"))
                .vbden_chuaxl(rs.getLong("vbden_chuaxl"))
                .vbden_daxl(rs.getLong("vbden_daxl"))
                .vbden_quahan(rs.getLong("vbden_quahan"))
                .vbgiay(rs.getLong("vbgiay"))
                .vbden_noibo(rs.getLong("vbden_noibo"))
                .vbden_truc(rs.getLong("vbden_truc"))
                .vbdi(rs.getLong("vbdi"))
                .vbdi_kyso(rs.getLong("vbdi_kyso"))
                .vbdi_noibo(rs.getLong("vbdi_noibo"))
                .vbdi_truc(rs.getLong("vbdi_truc"))
                .vbdi_vbgiay(rs.getLong("vbdi_vbgiay"))
                .lastActivity(rs.getObject("last_activity", OffsetDateTime.class))
                .build());

        ProvinceStatisticsResult.UnitStatistic totals = ProvinceStatisticsResult.UnitStatistic.builder().build();
        for (ProvinceStatisticsResult.UnitStatistic u : units) {
            totals.setVbden(totals.getVbden() + u.getVbden());
            totals.setVbden_chuaxl(totals.getVbden_chuaxl() + u.getVbden_chuaxl());
            totals.setVbden_daxl(totals.getVbden_daxl() + u.getVbden_daxl());
            totals.setVbden_quahan(totals.getVbden_quahan() + u.getVbden_quahan());
            totals.setVbgiay(totals.getVbgiay() + u.getVbgiay());
            totals.setVbden_noibo(totals.getVbden_noibo() + u.getVbden_noibo());
            totals.setVbden_truc(totals.getVbden_truc() + u.getVbden_truc());
            totals.setVbdi(totals.getVbdi() + u.getVbdi());
            totals.setVbdi_kyso(totals.getVbdi_kyso() + u.getVbdi_kyso());
            totals.setVbdi_noibo(totals.getVbdi_noibo() + u.getVbdi_noibo());
            totals.setVbdi_truc(totals.getVbdi_truc() + u.getVbdi_truc());
            totals.setVbdi_vbgiay(totals.getVbdi_vbgiay() + u.getVbdi_vbgiay());
            if (u.getLastActivity() != null && (totals.getLastActivity() == null
                || u.getLastActivity().isAfter(totals.getLastActivity()))) {
                totals.setLastActivity(u.getLastActivity());
            }
        }

        // Xu hướng theo tháng: trong khoảng đã chọn, hoặc 12 tháng gần nhất khi không chọn "Từ ngày".
        MapSqlParameterSource mp = new MapSqlParameterSource();
        List<String> mWhere = new ArrayList<>(List.of("d.deleted = FALSE", "d.is_revoked = FALSE", "d.doc_date IS NOT NULL"));
        if (from == null) {
            mWhere.add("d.doc_date >= (date_trunc('month', CURRENT_DATE) - INTERVAL '11 months')::date");
        }
        addDateRange(mWhere, mp, "d.doc_date", from, to);
        List<ProvinceStatisticsResult.MonthlyPoint> monthly = jdbc.query("""
                SELECT to_char(date_trunc('month', d.doc_date), 'YYYY-MM') AS m,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDEN') AS vbden,
                  COUNT(*) FILTER (WHERE d.doc_kind = 'VBDI') AS vbdi
                FROM province_documents d WHERE """ + " " + String.join(" AND ", mWhere) + " GROUP BY 1 ORDER BY 1",
            mp, (rs, i) -> ProvinceStatisticsResult.MonthlyPoint.builder()
                .month(rs.getString("m")).vbden(rs.getLong("vbden")).vbdi(rs.getLong("vbdi")).build());

        OffsetDateTime syncedUntil = jdbc.query("SELECT MIN(last_success_on) AS s FROM province_sync_state",
            new MapSqlParameterSource(), rs -> rs.next() ? rs.getObject("s", OffsetDateTime.class) : null);
        return ProvinceStatisticsResult.builder()
            .units(units)
            .totals(totals)
            .monthly(monthly)
            .syncedUntil(syncedUntil)
            .build();
    }

    // ------------------------------------------------------------------ Giám sát gửi văn bản đi
    //
    // Tối ưu cho dữ liệu lớn: KHÔNG cuộn toàn bộ bảng nơi nhận rồi mới lọc (cách cũ, chậm tuyến tính theo tổng dữ
    // liệu). Lọc văn bản TRƯỚC (chỉ mục doc_kind/doc_date/owner + EXISTS theo khoá chính (tenant_id, vbdi_id) của
    // bảng nơi nhận), phân trang, rồi mới cuộn số liệu nơi nhận cho đúng các dòng trên trang (LATERAL).

    private static final String ROLLUP_COLUMNS = """
          COUNT(x.*) AS recipients,
          COUNT(x.*) FILTER (WHERE x.state = 'SENT') AS waiting,
          COUNT(x.*) FILTER (WHERE x.state = 'ARRIVED') AS arrived,
          COUNT(x.*) FILTER (WHERE x.state IN ('ACCEPTED', 'ASSIGNED', 'PROCESSING')) AS processing,
          COUNT(x.*) FILTER (WHERE x.state = 'DONE') AS done,
          COUNT(x.*) FILTER (WHERE x.state = 'REFUSED') AS refused,
          COUNT(x.*) FILTER (WHERE x.state = 'REVOKED') AS revoked,
          COUNT(x.*) FILTER (WHERE x.state = 'SENT'
                             AND x.last_sent_on < NOW() - make_interval(mins => :lateArr)) AS late_arrival,
          COUNT(x.*) FILTER (WHERE x.state IN ('SENT', 'ARRIVED')
                             AND x.last_sent_on < NOW() - make_interval(hours => :lateAcc)) AS late_accept,
          COALESCE(BOOL_OR(x.recipient_group = 'internal_unit'), FALSE) AS has_internal,
          COALESCE(BOOL_OR(x.recipient_group = 'contact_unit'), FALSE) AS has_contact,
          COALESCE(BOOL_OR(x.recipient_group = 'external_unit'), FALSE) AS has_external,
          MIN(x.first_sent_on) AS first_sent_on,
          MAX(x.last_event_on) AS last_event_on
        """;

    @Override
    public ProvinceDispatchPage searchDispatchDocuments(ProvinceDispatchSearchRequest r, LocalDate from, LocalDate to,
                                                        int lateArrivalMinutes, int lateAcceptHours,
                                                        int offset, int limit) {
        return cached("dispatchPage", r + "|" + from + "|" + to + "|" + lateArrivalMinutes + "|" + lateAcceptHours
            + "|" + offset + "|" + limit, () -> loadDispatchPage(r, from, to, lateArrivalMinutes, lateAcceptHours, offset, limit));
    }

    private ProvinceDispatchPage loadDispatchPage(ProvinceDispatchSearchRequest r, LocalDate from, LocalDate to,
                                                  int lateArrivalMinutes, int lateAcceptHours, int offset, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String fromWhere = dispatchDocumentsFromWhere(r, from, to, lateArrivalMinutes, lateAcceptHours, p);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + fromWhere, p, Long.class);
        if (total == null || total == 0) {
            return new ProvinceDispatchPage(List.of(), 0);
        }
        p.addValue("limit", limit).addValue("offset", offset);
        String groupFilter = groups(r).isEmpty() ? "" : " AND x.recipient_group IN (:groups)";
        List<ProvinceDispatchDocumentItem> items = jdbc.query(
            "WITH pg AS (SELECT d.tenant_id, d.document_id, d.code, d.title, d.document_type_name, d.owner_identifier,"
                + " d.owner_name, d.signer, d.doc_date, d.is_signed, d.created_on " + fromWhere
                + " ORDER BY d.doc_date DESC NULLS LAST, d.created_on DESC NULLS LAST LIMIT :limit OFFSET :offset)"
                + " SELECT pg.*, a.* FROM pg CROSS JOIN LATERAL (SELECT " + ROLLUP_COLUMNS
                + " FROM province_vbdi_dispatches x WHERE x.tenant_id = pg.tenant_id AND x.vbdi_id = pg.document_id"
                + groupFilter + ") a"
                + " ORDER BY pg.doc_date DESC NULLS LAST, pg.created_on DESC NULLS LAST",
            p, (rs, i) -> ProvinceDispatchDocumentItem.builder()
                .tenantId(rs.getString("tenant_id"))
                .documentId(rs.getObject("document_id", UUID.class))
                .code(rs.getString("code"))
                .title(rs.getString("title"))
                .documentTypeName(rs.getString("document_type_name"))
                .senderIdentifier(rs.getString("owner_identifier"))
                .senderName(rs.getString("owner_name"))
                .signer(rs.getString("signer"))
                .promulgationDate(localDate(rs, "doc_date"))
                .signed(rs.getBoolean("is_signed"))
                .recipients(rs.getLong("recipients"))
                .waiting(rs.getLong("waiting"))
                .arrived(rs.getLong("arrived"))
                .processing(rs.getLong("processing"))
                .done(rs.getLong("done"))
                .refused(rs.getLong("refused"))
                .revoked(rs.getLong("revoked"))
                .lateArrival(rs.getLong("late_arrival"))
                .lateAccept(rs.getLong("late_accept"))
                .hasInternal(rs.getBoolean("has_internal"))
                .hasContact(rs.getBoolean("has_contact"))
                .hasExternal(rs.getBoolean("has_external"))
                .firstSentOn(rs.getObject("first_sent_on", OffsetDateTime.class))
                .lastEventOn(rs.getObject("last_event_on", OffsetDateTime.class))
                .build());
        return new ProvinceDispatchPage(items, total);
    }

    @Override
    public ProvinceDispatchSummary dispatchSummary(ProvinceDispatchSearchRequest r, LocalDate from, LocalDate to,
                                                   int lateArrivalMinutes, int lateAcceptHours) {
        return cached("dispatchSummary", r + "|" + from + "|" + to + "|" + lateArrivalMinutes + "|" + lateAcceptHours,
            () -> groups(r).isEmpty()
                ? loadDispatchSummaryFromRollup(r, from, to, lateArrivalMinutes, lateAcceptHours)
                : loadDispatchSummary(r, from, to, lateArrivalMinutes, lateAcceptHours));
    }

    /**
     * Đường NHANH (không lọc nhóm nơi nhận — mặc định): KPI + xếp hạng đơn vị phát hành đọc cột cuộn sẵn {@code rx_*}
     * của bảng văn bản (không nối bảng nơi nhận); số "chậm" và xếp hạng đơn vị nhận chỉ đọc nơi nhận ĐANG CHỜ
     * (chỉ mục riêng ix_province_vbdi_dispatches_pending — tập nhỏ vì phần lớn nơi nhận đã tiếp nhận).
     */
    private ProvinceDispatchSummary loadDispatchSummaryFromRollup(ProvinceDispatchSearchRequest r, LocalDate from,
                                                                  LocalDate to, int lateArrivalMinutes,
                                                                  int lateAcceptHours) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String fromWhere = dispatchDocumentsFromWhere(r, from, to, lateArrivalMinutes, lateAcceptHours, p);
        ProvinceDispatchSummary summary = ProvinceDispatchSummary.builder().build();
        Map<String, ProvinceDispatchSummary.UnitRank> senders = new LinkedHashMap<>();
        Map<String, ProvinceDispatchSummary.UnitRank> receivers = new LinkedHashMap<>();
        double[] acceptSum = {0};
        long[] acceptCount = {0};

        readTx.executeWithoutResult(tx -> {
            jdbc.getJdbcTemplate().execute("SET LOCAL work_mem = '64MB'");

            // (1) Theo đơn vị phát hành, chỉ bảng văn bản; dòng tổng cộng ở Java.
            jdbc.query("SELECT d.owner_identifier AS identifier, MAX(d.owner_name) AS name, COUNT(*) AS documents,"
                    + " COUNT(*) FILTER (WHERE d.rx_waiting + d.rx_arrived > 0) AS documents_pending,"
                    + " SUM(d.rx_total) AS recipients, SUM(d.rx_waiting) AS waiting, SUM(d.rx_arrived) AS arrived,"
                    + " SUM(d.rx_processing) AS processing, SUM(d.rx_done) AS done, SUM(d.rx_refused) AS refused,"
                    + " SUM(d.rx_revoked) AS revoked, SUM(d.rx_accept_hours_sum) AS accept_sum,"
                    + " SUM(d.rx_accept_count) AS accept_n "
                    + fromWhere + " GROUP BY d.owner_identifier",
                p, rs -> {
                    summary.setDocuments(summary.getDocuments() + rs.getLong("documents"));
                    summary.setDocumentsPending(summary.getDocumentsPending() + rs.getLong("documents_pending"));
                    summary.setRecipients(summary.getRecipients() + rs.getLong("recipients"));
                    summary.setWaiting(summary.getWaiting() + rs.getLong("waiting"));
                    summary.setArrived(summary.getArrived() + rs.getLong("arrived"));
                    summary.setProcessing(summary.getProcessing() + rs.getLong("processing"));
                    summary.setDone(summary.getDone() + rs.getLong("done"));
                    summary.setRefused(summary.getRefused() + rs.getLong("refused"));
                    summary.setRevoked(summary.getRevoked() + rs.getLong("revoked"));
                    double sum = rs.getDouble("accept_sum");
                    long n = rs.getLong("accept_n");
                    acceptSum[0] += sum;
                    acceptCount[0] += n;
                    long pending = rs.getLong("waiting") + rs.getLong("arrived");
                    if (pending > 0) {
                        senders.put(rs.getString("identifier"), ProvinceDispatchSummary.UnitRank.builder()
                            .identifier(rs.getString("identifier"))
                            .name(rs.getString("name"))
                            .total(rs.getLong("documents"))
                            .pending(pending)
                            .avgAcceptHours(n == 0 ? null : sum / n)
                            .build());
                    }
                });

            // (2) Chỉ nơi nhận ĐANG CHỜ của các văn bản đã lọc: số chậm + xếp hạng đơn vị nhận.
            jdbc.query("WITH docs AS (SELECT d.document_id, d.owner_identifier " + fromWhere + ")"
                    + " SELECT docs.owner_identifier AS sender, COALESCE(x.recipient_identifier, x.recipient_name) AS receiver,"
                    + " MAX(x.recipient_name) AS receiver_name, COUNT(*) AS pending,"
                    + " COUNT(*) FILTER (WHERE x.state = 'SENT'"
                    + "   AND x.last_sent_on < NOW() - make_interval(mins => :lateArr)) AS late_arrival,"
                    + " COUNT(*) FILTER (WHERE x.last_sent_on < NOW() - make_interval(hours => :lateAcc)) AS late_accept"
                    + " FROM province_vbdi_dispatches x JOIN docs ON docs.document_id = x.vbdi_id"
                    + " WHERE x.state IN ('SENT', 'ARRIVED') GROUP BY 1, 2",
                p, rs -> {
                    long lateArrival = rs.getLong("late_arrival");
                    long lateAccept = rs.getLong("late_accept");
                    summary.setLateArrival(summary.getLateArrival() + lateArrival);
                    summary.setLateAccept(summary.getLateAccept() + lateAccept);
                    ProvinceDispatchSummary.UnitRank sender = senders.get(rs.getString("sender"));
                    if (sender != null) {
                        sender.setLate(sender.getLate() + lateAccept);
                    }
                    String key = rs.getString("receiver");
                    ProvinceDispatchSummary.UnitRank receiver = receivers.computeIfAbsent(key,
                        k -> ProvinceDispatchSummary.UnitRank.builder().identifier(k).build());
                    if (receiver.getName() == null) {
                        receiver.setName(rs.getString("receiver_name"));
                    }
                    receiver.setPending(receiver.getPending() + rs.getLong("pending"));
                    receiver.setLate(receiver.getLate() + lateAccept);
                });
        });

        summary.setAvgAcceptHours(acceptCount[0] == 0 ? null : acceptSum[0] / acceptCount[0]);

        List<ProvinceDispatchSummary.UnitRank> topSenders = new ArrayList<>(senders.values());
        topSenders.sort(java.util.Comparator.comparingLong(ProvinceDispatchSummary.UnitRank::getPending)
            .thenComparingLong(ProvinceDispatchSummary.UnitRank::getLate).reversed());
        List<ProvinceDispatchSummary.UnitRank> topReceivers = new ArrayList<>(receivers.values());
        topReceivers.sort(java.util.Comparator.comparingLong(ProvinceDispatchSummary.UnitRank::getLate)
            .thenComparingLong(ProvinceDispatchSummary.UnitRank::getPending).reversed());
        topReceivers = new ArrayList<>(topReceivers.subList(0, Math.min(10, topReceivers.size())));
        fillReceiverTotals(topReceivers, fromWhere, p);
        summary.setTopSenders(new ArrayList<>(topSenders.subList(0, Math.min(10, topSenders.size()))));
        summary.setTopSlowReceivers(topReceivers);
        return summary;
    }

    /** Tổng lượt nhận + thời gian tiếp nhận TB cho đúng ≤10 đơn vị nhận trong bảng xếp hạng (tra theo chỉ mục nơi nhận). */
    private void fillReceiverTotals(List<ProvinceDispatchSummary.UnitRank> receivers, String fromWhere,
                                    MapSqlParameterSource base) {
        if (receivers.isEmpty()) {
            return;
        }
        MapSqlParameterSource p = new MapSqlParameterSource(base.getValues());
        p.addValue("rk", receivers.stream().map(ProvinceDispatchSummary.UnitRank::getIdentifier).toList());
        Map<String, ProvinceDispatchSummary.UnitRank> byKey = receivers.stream()
            .collect(Collectors.toMap(ProvinceDispatchSummary.UnitRank::getIdentifier, x -> x, (a, b) -> a));
        jdbc.query("WITH docs AS (SELECT d.document_id " + fromWhere + ")"
                + " SELECT COALESCE(x.recipient_identifier, x.recipient_name) AS k, COUNT(*) AS total,"
                + " AVG(EXTRACT(EPOCH FROM (x.accepted_on - x.last_sent_on))::double precision / 3600.0)"
                + "   FILTER (WHERE x.accepted_on >= x.last_sent_on) AS avg_h"
                + " FROM province_vbdi_dispatches x JOIN docs ON docs.document_id = x.vbdi_id"
                + " WHERE (x.recipient_identifier IN (:rk) OR (x.recipient_identifier IS NULL AND x.recipient_name IN (:rk)))"
                + " GROUP BY 1",
            p, rs -> {
                ProvinceDispatchSummary.UnitRank rank = byKey.get(rs.getString("k"));
                if (rank != null) {
                    rank.setTotal(rs.getLong("total"));
                    rank.setAvgAcceptHours(rs.getObject("avg_h", Double.class));
                }
            });
    }

    private ProvinceDispatchSummary loadDispatchSummary(ProvinceDispatchSearchRequest r, LocalDate from, LocalDate to,
                                                        int lateArrivalMinutes, int lateAcceptHours) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String fromWhere = dispatchDocumentsFromWhere(r, from, to, lateArrivalMinutes, lateAcceptHours, p);
        String groupFilter = groups(r).isEmpty() ? "" : " AND x.recipient_group IN (:groups)";
        String docs = "WITH docs AS (SELECT d.document_id, d.owner_identifier, d.owner_name " + fromWhere + ")";
        // Nối chỉ theo vbdi_id (UUID duy nhất toàn cục): nối 2 cột (tenant_id, vbdi_id) làm Postgres ước lượng sai
        // hàng chục lần → chọn sắp xếp tràn đĩa.
        String joined = " FROM province_vbdi_dispatches x JOIN docs ON docs.document_id = x.vbdi_id WHERE TRUE" + groupFilter;
        String pending = "x.state IN ('SENT', 'ARRIVED')";
        String lateAccept = pending + " AND x.last_sent_on < NOW() - make_interval(hours => :lateAcc)";
        String accepted = "x.accepted_on >= x.last_sent_on";

        ProvinceDispatchSummary summary = ProvinceDispatchSummary.builder().build();
        List<ProvinceDispatchSummary.UnitRank> senders = new ArrayList<>();
        List<ProvinceDispatchSummary.UnitRank> receivers = new ArrayList<>();
        Map<String, Long> documentsBySender = new java.util.HashMap<>();
        double[] acceptSum = {0};
        long[] acceptCount = {0};

        // Đo thật trên 1 triệu văn bản / 1,3 triệu nơi nhận: GROUPING SETS và COUNT(DISTINCT) KHÔNG chạy song song
        // (5-23 s) — tách thành các GROUP BY thường (song song) rồi cộng tổng ở Java.
        readTx.executeWithoutResult(tx -> {
            // work_mem mặc định server = 4MB: băm/sắp xếp vài trăm nghìn dòng sẽ tràn đĩa. Chỉ nâng trong transaction này.
            jdbc.getJdbcTemplate().execute("SET LOCAL work_mem = '64MB'");

            // (1) Theo đơn vị NHẬN — đồng thời là nguồn cộng các thẻ KPI (mỗi nơi nhận thuộc đúng 1 nhóm).
            jdbc.query(docs + " SELECT COALESCE(x.recipient_identifier, x.recipient_name) AS identifier,"
                    + " MAX(x.recipient_name) AS name, COUNT(*) AS recipients,"
                    + " COUNT(*) FILTER (WHERE x.state = 'SENT') AS waiting,"
                    + " COUNT(*) FILTER (WHERE x.state = 'ARRIVED') AS arrived,"
                    + " COUNT(*) FILTER (WHERE x.state IN ('ACCEPTED', 'ASSIGNED', 'PROCESSING')) AS processing,"
                    + " COUNT(*) FILTER (WHERE x.state = 'DONE') AS done,"
                    + " COUNT(*) FILTER (WHERE x.state = 'REFUSED') AS refused,"
                    + " COUNT(*) FILTER (WHERE x.state = 'REVOKED') AS revoked,"
                    + " COUNT(*) FILTER (WHERE x.state = 'SENT' AND x.last_sent_on < NOW() - make_interval(mins => :lateArr)) AS late_arrival,"
                    + " COUNT(*) FILTER (WHERE " + lateAccept + ") AS late_accept,"
                    + " SUM(EXTRACT(EPOCH FROM (x.accepted_on - x.last_sent_on))::double precision / 3600.0)"
                    + "   FILTER (WHERE " + accepted + ") AS accept_sum,"
                    + " COUNT(*) FILTER (WHERE " + accepted + ") AS accept_n"
                    + joined + " GROUP BY 1",
                p, rs -> {
                    summary.setRecipients(summary.getRecipients() + rs.getLong("recipients"));
                    summary.setWaiting(summary.getWaiting() + rs.getLong("waiting"));
                    summary.setArrived(summary.getArrived() + rs.getLong("arrived"));
                    summary.setProcessing(summary.getProcessing() + rs.getLong("processing"));
                    summary.setDone(summary.getDone() + rs.getLong("done"));
                    summary.setRefused(summary.getRefused() + rs.getLong("refused"));
                    summary.setRevoked(summary.getRevoked() + rs.getLong("revoked"));
                    summary.setLateArrival(summary.getLateArrival() + rs.getLong("late_arrival"));
                    summary.setLateAccept(summary.getLateAccept() + rs.getLong("late_accept"));
                    double sum = rs.getDouble("accept_sum");
                    long n = rs.getLong("accept_n");
                    acceptSum[0] += sum;
                    acceptCount[0] += n;
                    long pend = rs.getLong("waiting") + rs.getLong("arrived");
                    if (pend > 0) {
                        receivers.add(ProvinceDispatchSummary.UnitRank.builder()
                            .identifier(rs.getString("identifier"))
                            .name(rs.getString("name"))
                            .total(rs.getLong("recipients"))
                            .pending(pend)
                            .late(rs.getLong("late_accept"))
                            .avgAcceptHours(n == 0 ? null : sum / n)
                            .build());
                    }
                });

            // (2) Theo đơn vị PHÁT HÀNH.
            jdbc.query(docs + " SELECT docs.owner_identifier AS identifier, MAX(docs.owner_name) AS name,"
                    + " COUNT(*) FILTER (WHERE " + pending + ") AS pending,"
                    + " COUNT(*) FILTER (WHERE " + lateAccept + ") AS late,"
                    + " AVG(EXTRACT(EPOCH FROM (x.accepted_on - x.last_sent_on))::double precision / 3600.0)"
                    + "   FILTER (WHERE " + accepted + ") AS avg_h"
                    + joined + " GROUP BY 1 HAVING COUNT(*) FILTER (WHERE " + pending + ") > 0",
                p, rs -> {
                    senders.add(ProvinceDispatchSummary.UnitRank.builder()
                        .identifier(rs.getString("identifier"))
                        .name(rs.getString("name"))
                        .pending(rs.getLong("pending"))
                        .late(rs.getLong("late"))
                        .avgAcceptHours(rs.getObject("avg_h", Double.class))
                        .build());
                });

            // (3) Số văn bản (tổng + theo đơn vị phát hành) — chỉ quét bảng văn bản.
            jdbc.query(docs + " SELECT owner_identifier, COUNT(*) AS n FROM docs GROUP BY ROLLUP (owner_identifier)", p, rs -> {
                String owner = rs.getString("owner_identifier");
                documentsBySender.put(owner == null ? "\u0000total" : owner, rs.getLong("n"));
            });

            // (4) Văn bản còn nơi nhận chưa tiếp nhận: lọc dòng chờ TRƯỚC rồi mới khử trùng theo UUID.
            Long pendingDocs = jdbc.queryForObject(docs + " SELECT COUNT(*) FROM (SELECT DISTINCT x.vbdi_id" + joined
                + " AND " + pending + ") z", p, Long.class);
            summary.setDocumentsPending(pendingDocs == null ? 0 : pendingDocs);
        });

        summary.setAvgAcceptHours(acceptCount[0] == 0 ? null : acceptSum[0] / acceptCount[0]);
        summary.setDocuments(documentsBySender.getOrDefault("\u0000total", 0L));
        // Bảng "đơn vị phát hành": cột Tổng là số VĂN BẢN.
        senders.forEach(s -> s.setTotal(documentsBySender.getOrDefault(s.getIdentifier(), 0L)));
        senders.sort(java.util.Comparator.comparingLong(ProvinceDispatchSummary.UnitRank::getPending)
            .thenComparingLong(ProvinceDispatchSummary.UnitRank::getLate).reversed());
        receivers.sort(java.util.Comparator.comparingLong(ProvinceDispatchSummary.UnitRank::getLate)
            .thenComparingLong(ProvinceDispatchSummary.UnitRank::getPending).reversed());
        summary.setTopSenders(new ArrayList<>(senders.subList(0, Math.min(10, senders.size()))));
        summary.setTopSlowReceivers(new ArrayList<>(receivers.subList(0, Math.min(10, receivers.size()))));
        return summary;
    }


    @Override
    public List<ProvinceDispatchRecipientItem> dispatchRecipients(String tenantId, UUID vbdiId) {
        MapSqlParameterSource p = new MapSqlParameterSource("t", tenantId).addValue("id", vbdiId);
        List<ProvinceDispatchRecipientItem> recipients = jdbc.query("""
                SELECT * FROM province_vbdi_dispatches WHERE tenant_id = :t AND vbdi_id = :id
                ORDER BY CASE recipient_group WHEN 'internal_unit' THEN 0 WHEN 'contact_unit' THEN 1 ELSE 2 END,
                         recipient_name
                """, p, (rs, i) -> ProvinceDispatchRecipientItem.builder()
            .recipientKey(rs.getString("recipient_key"))
            .recipientGroup(rs.getString("recipient_group"))
            .recipientIdentifier(rs.getString("recipient_identifier"))
            .recipientName(rs.getString("recipient_name"))
            .state(rs.getString("state"))
            .stateLabel(rs.getString("state_label"))
            .sendCount(rs.getInt("send_count"))
            .firstSentOn(rs.getObject("first_sent_on", OffsetDateTime.class))
            .lastSentOn(rs.getObject("last_sent_on", OffsetDateTime.class))
            .arrivedOn(rs.getObject("arrived_on", OffsetDateTime.class))
            .acceptedOn(rs.getObject("accepted_on", OffsetDateTime.class))
            .completedOn(rs.getObject("completed_on", OffsetDateTime.class))
            .lastEventOn(rs.getObject("last_event_on", OffsetDateTime.class))
            .events(new ArrayList<>())
            .build());
        Map<String, ProvinceDispatchRecipientItem> byKey = recipients.stream()
            .collect(Collectors.toMap(ProvinceDispatchRecipientItem::getRecipientKey, x -> x, (a, b) -> a, LinkedHashMap::new));
        jdbc.query("""
                SELECT recipient_key, send_type, status, content, user_creator, created_on
                FROM province_vbdi_dispatch_events WHERE tenant_id = :t AND vbdi_id = :id ORDER BY created_on
                """, p, rs -> {
            ProvinceDispatchRecipientItem owner = byKey.get(rs.getString("recipient_key"));
            if (owner != null) {
                owner.getEvents().add(ProvinceDispatchRecipientItem.Event.builder()
                    .sendType(rs.getString("send_type"))
                    .status(rs.getString("status"))
                    .content(rs.getString("content"))
                    .userCreator(rs.getString("user_creator"))
                    .createdOn(rs.getObject("created_on", OffsetDateTime.class))
                    .build());
            }
        });
        return recipients;
    }

    /**
     * {@code FROM ... WHERE ...} chọn văn bản đi theo bộ lọc. Tình trạng ({@code situation}) và loại nơi nhận lọc
     * bằng EXISTS trên bảng nơi nhận theo khoá chính — chi phí tỉ lệ số văn bản ứng viên, không tỉ lệ cả bảng.
     */
    private String dispatchDocumentsFromWhere(ProvinceDispatchSearchRequest r, LocalDate from, LocalDate to,
                                              int lateArrivalMinutes, int lateAcceptHours, MapSqlParameterSource p) {
        p.addValue("lateArr", lateArrivalMinutes).addValue("lateAcc", lateAcceptHours);
        List<String> where = new ArrayList<>(List.of("d.deleted = FALSE", "d.doc_kind = 'VBDI'"));
        addScopeTenant(where, p, r.getScopeTenantId());
        addUnitFilter(where, p, "d.owner_identifier", "sender", r.getSenderIdentifiers(), true);
        addDateRange(where, p, "d.doc_date", from, to);
        addKeyword(where, p, r.getKeyword());

        List<String> groups = groups(r);
        String groupCond = "";
        if (!groups.isEmpty()) {
            p.addValue("groups", groups);
            groupCond = " AND x.recipient_group IN (:groups)";
        }
        // "Có ít nhất 1 nơi nhận (thuộc nhóm đã chọn)" = cột đã tính sẵn lúc đồng bộ (refreshVbdiChannels) —
        // lọc cột thay vì EXISTS cho TỪNG văn bản (400k lần tra chỉ mục ≈ 9 s khi không giới hạn thời gian).
        if (groups.isEmpty()) {
            where.add("d.rx_total > 0");
        } else {
            where.add("(" + String.join(" OR ", groups.stream().map(GROUP_FLAG_COLUMNS::get).sorted().toList()) + ")");
        }
        String exists = "EXISTS (SELECT 1 FROM province_vbdi_dispatches x WHERE x.tenant_id = d.tenant_id"
            + " AND x.vbdi_id = d.document_id" + groupCond;

        List<String> recipientCond = new ArrayList<>();
        addUnitFilter(recipientCond, p, "x.recipient_identifier", "recv", r.getRecipientIdentifiers(), true);
        if (!recipientCond.isEmpty()) {
            where.add(exists + " AND " + recipientCond.get(0) + ")");
        }

        String situation = r.getSituation() == null ? "" : r.getSituation().trim().toUpperCase();
        if (groups.isEmpty()) {
            // Không lọc nhóm nơi nhận: tình trạng đọc thẳng cột cuộn sẵn (rx_*), không chạm bảng nơi nhận.
            switch (situation) {
                case "CHUA_HOAN_TAT" -> where.add("d.rx_waiting + d.rx_arrived > 0");
                case "HOAN_TAT" -> where.add("d.rx_waiting + d.rx_arrived = 0");
                case "CO_TU_CHOI" -> where.add("d.rx_refused > 0");
                case "CHAM_DEN" -> where.add("d.rx_oldest_waiting_on < NOW() - make_interval(mins => :lateArr)");
                case "CHAM_TIEP_NHAN" -> where.add("d.rx_oldest_pending_on < NOW() - make_interval(hours => :lateAcc)");
                default -> {
                }
            }
            return "FROM province_documents d WHERE " + String.join(" AND ", where);
        }
        switch (situation) {
            case "CHUA_HOAN_TAT" -> where.add(exists + " AND x.state IN ('SENT', 'ARRIVED'))");
            case "HOAN_TAT" -> where.add("NOT " + exists + " AND x.state IN ('SENT', 'ARRIVED'))");
            case "CO_TU_CHOI" -> where.add(exists + " AND x.state = 'REFUSED')");
            case "CHAM_DEN" -> where.add(exists + " AND x.state = 'SENT'"
                + " AND x.last_sent_on < NOW() - make_interval(mins => :lateArr))");
            case "CHAM_TIEP_NHAN" -> where.add(exists + " AND x.state IN ('SENT', 'ARRIVED')"
                + " AND x.last_sent_on < NOW() - make_interval(hours => :lateAcc))");
            default -> {
            }
        }
        return "FROM province_documents d WHERE " + String.join(" AND ", where);
    }

    private static List<String> groups(ProvinceDispatchSearchRequest r) {
        if (r.getRecipientGroups() == null) {
            return List.of();
        }
        return r.getRecipientGroups().stream().filter(g -> g != null && RECIPIENT_GROUPS.contains(g.trim()))
            .map(String::trim).distinct().toList();
    }

    // ------------------------------------------------------------------ Bộ nhớ đệm kết quả
    //
    // Kho chỉ đổi khi đồng bộ (5 phút/lần) nhưng màn Tổng hợp chung mở cho MỌI người dùng — cache theo
    // "phiên bản dữ liệu" (MAX(province_sync_state.last_data_change_on), đọc 1 dòng nhỏ mỗi request): đồng bộ có
    // thay đổi thì tự vô hiệu; TTL 2 phút để số "chậm đến/chậm tiếp nhận" (phụ thuộc NOW()) không cũ quá lâu.
    // Đúng cả khi nhiều instance (phiên bản đọc từ DB, không từ bộ nhớ instance).

    @SuppressWarnings("unchecked")
    private <T> T cached(String kind, String key, java.util.function.Supplier<T> loader) {
        String version = jdbc.query("SELECT COALESCE(MAX(last_data_change_on)::text, '') AS v FROM province_sync_state",
            new MapSqlParameterSource(), rs -> rs.next() ? rs.getString("v") : "");
        return (T) cache.get(kind + "|" + version + "|" + key, k -> inReadTx(loader));
    }

    /**
     * Chạy nhóm truy vấn trong 1 transaction chỉ đọc với tham số phiên phù hợp truy vấn tổng hợp (chỉ có hiệu lực
     * trong transaction này, không đổi cấu hình server): tắt JIT (đo thật: ~160 ms biên dịch/truy vấn, lớn hơn phần
     * tiết kiệm với truy vấn cỡ này) và nâng work_mem (mặc định server 4MB làm băm/sắp xếp tràn đĩa).
     */
    private <T> T inReadTx(java.util.function.Supplier<T> loader) {
        return readTx.execute(tx -> {
            jdbc.getJdbcTemplate().execute("SET LOCAL jit = off");
            jdbc.getJdbcTemplate().execute("SET LOCAL work_mem = '64MB'");
            return loader.get();
        });
    }

    // ------------------------------------------------------------------ Tiện ích lọc

    /**
     * Phạm vi đơn vị (ProvinceScopePolicy, 22/10/2026): admin đơn vị chỉ thấy văn bản của tenant mình. Null = toàn tỉnh.
     * Cột {@code tenant_id} đứng đầu khoá chính province_documents nên lọc dùng được chỉ mục.
     */
    static void addScopeTenant(List<String> where, MapSqlParameterSource p, String scopeTenantId) {
        if (scopeTenantId == null || scopeTenantId.isBlank()) {
            return;
        }
        where.add("d.tenant_id = :scopeTenant");
        p.addValue("scopeTenant", scopeTenantId);
    }

    /** Đơn vị khớp chính xác hoặc (nếu gồm đơn vị con) khớp tiền tố {@code <mã>.} của mã định danh phân cấp. */
    private static void addUnitFilter(List<String> where, MapSqlParameterSource p, String column, String name,
                                      List<String> identifiers, boolean includeChildren) {
        if (identifiers == null) {
            return;
        }
        List<String> ids = identifiers.stream().filter(s -> s != null && !s.isBlank()).map(String::trim)
            .distinct().limit(MAX_UNIT_FILTERS).toList();
        if (ids.isEmpty()) {
            return;
        }
        p.addValue(name + "Ids", ids);
        if (!includeChildren) {
            where.add(column + " IN (:" + name + "Ids)");
            return;
        }
        // Mỗi tiền tố 1 điều kiện LIKE riêng (không dùng LIKE ANY(ARRAY[...]) — Postgres không dùng chỉ mục
        // varchar_pattern_ops cho dạng đó); các nhánh OR được gộp bằng BitmapOr trên chỉ mục.
        StringBuilder cond = new StringBuilder("(").append(column).append(" IN (:").append(name).append("Ids)");
        for (int i = 0; i < ids.size(); i++) {
            String param = name + "Prefix" + i;
            p.addValue(param, escapeLike(ids.get(i)) + ".%");
            cond.append(" OR ").append(column).append(" LIKE :").append(param);
        }
        where.add(cond.append(')').toString());
    }

    private static void addDateRange(List<String> where, MapSqlParameterSource p, String column, LocalDate from, LocalDate to) {
        if (from != null) {
            where.add(column + " >= :fromDate");
            p.addValue("fromDate", Date.valueOf(from));
        }
        if (to != null) {
            where.add(column + " <= :toDate");
            p.addValue("toDate", Date.valueOf(to));
        }
    }

    /** Mỗi từ (đã bỏ dấu) phải xuất hiện trong {@code search_text} — số ký hiệu, trích yếu, cơ quan, người ký. */
    private static void addKeyword(List<String> where, MapSqlParameterSource p, String keyword) {
        String normalized = ProvinceText.normalize(keyword);
        if (normalized.isEmpty()) {
            return;
        }
        String[] terms = normalized.split(" ");
        for (int i = 0; i < terms.length && i < MAX_KEYWORD_TERMS; i++) {
            where.add("d.search_text LIKE :kw" + i);
            p.addValue("kw" + i, "%" + escapeLike(terms[i]) + "%");
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static List<String> upper(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(v -> v != null && !v.isBlank()).map(v -> v.trim().toUpperCase()).distinct().toList();
    }


    private static ProvinceDocumentItem mapDocument(ResultSet rs) throws SQLException {
        return ProvinceDocumentItem.builder()
            .tenantId(rs.getString("tenant_id"))
            .documentId(rs.getObject("document_id", UUID.class))
            .kind(rs.getString("doc_kind"))
            .ownerIdentifier(rs.getString("owner_identifier"))
            .ownerName(rs.getString("owner_name"))
            .code(rs.getString("code"))
            .title(rs.getString("title"))
            .documentTypeName(rs.getString("document_type_name"))
            .agencyIdentifier(rs.getString("agency_identifier"))
            .agencyName(rs.getString("agency_name"))
            .signer(rs.getString("signer"))
            .priority(rs.getString("priority"))
            .docDate(localDate(rs, "doc_date"))
            .promulgationDate(localDate(rs, "promulgation_date"))
            .deadlineDate(localDate(rs, "deadline_date"))
            .processState(rs.getString("process_state"))
            .overdue(rs.getBoolean("is_overdue"))
            .internal(rs.getBoolean("is_internal"))
            .network(rs.getBoolean("is_network"))
            .national(rs.getBoolean("is_national"))
            .paper(rs.getBoolean("is_paper"))
            .signed(rs.getBoolean("is_signed"))
            .revoked(rs.getBoolean("is_revoked"))
            .createdOn(rs.getObject("created_on", OffsetDateTime.class))
            .build();
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }
}
