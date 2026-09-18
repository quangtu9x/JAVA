package com.td.worker.tasks.vbdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.TaskHandler;
import com.td.worker.core.task.UnitResult;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

/**
 * Task {@code vbdi.reply} — REFERENCE IMPLEMENTATION cho luồng "đơn vị nhận → đơn vị gửi"
 * (port {@code ReplyDocumentOut.java}/{@code ResponseStatus}/{@code StatusCode} của Domino,
 * docs/architecture/11 §3). Unit của task này chạy trong tenant = ĐƠN VỊ GỬI (bên nhận phản
 * hồi), ghi vết phản hồi vào bảng TỰ QUẢN {@code worker_vbdi_reply_log}.
 *
 * <p>Bugfix 06/09/2026 (cùng nguyên nhân gốc phát hiện ở {@link VbdiDeliveryTaskHandler}):
 * {@code worker.tenant.pool-auto-commit} mặc định {@code false} cho MỌI pool tenant — mọi câu
 * lệnh JDBC không nằm trong 1 transaction Spring quản lý sẽ bị HikariCP tự ROLLBACK khi trả
 * connection về pool. Bọc CẢ tạo bảng lẫn INSERT trong 1 transaction tường minh để có commit
 * thật (trước đây bảng {@code worker_vbdi_reply_log} không bao giờ thực sự tồn tại).</p>
 *
 * <p><b>Bugfix 07/09/2026 (yêu cầu người dùng: hoàn thiện broker phản hồi trạng thái "vào sổ"/
 * "phân xử lý", đã ghi vết vào "nhật ký gửi nhận của đơn vị")</b>: quyết định "Worker không ghi
 * vào bảng nghiệp vụ của td-web" ở trên chỉ ĐÚNG lúc viết (06/09/2026) — thực tế đã ĐỔI ngay sau
 * đó cùng ngày khi port tệp đính kèm (xem {@code VbdenIncomingDocumentWriter} ghi thẳng
 * {@code documents}/{@code file_metadata}, cùng nguyên tắc "self-heal, JDBC thuần, biết trước
 * shape cột" mà KHÔNG phá vỡ "Worker độc lập hoàn toàn về CODE" — Worker vẫn không phụ thuộc
 * {@code td-domain}/{@code td-application} ở tầng biên dịch, chỉ biết TRƯỚC hình dạng cột SQL).
 * Áp dụng lại NGUYÊN TẮC ĐÓ ở đây: mỗi phản hồi HỢP LỆ (lần đầu, chưa dedup trùng) giờ ghi THÊM 1
 * dòng MỚI vào {@code vbdi_send_log_documents} (append-only, đúng đúng thiết kế "Nhật ký gửi
 * nhận" — 1 dòng/1 sự kiện, xem javadoc {@code VbdiSendLogDocument}) — nếu không, "Nhật ký gửi
 * nhận" mà FE hiển thị sẽ KHÔNG BAO GIỜ thấy bất kỳ trạng thái phản hồi nào (kể cả "Đã đến"), dù
 * broker phía dưới đã chạy đúng.</p>
 *
 * <p><b>Bugfix 07/09/2026 (2) — production log</b>: {@code DuplicateKeyException} NGAY TRÊN câu
 * {@code CREATE TABLE IF NOT EXISTS worker_vbdi_reply_log} (không phải trên INSERT), khiến unit bị
 * RETRY vô hạn. Nguyên nhân: {@code CREATE TABLE IF NOT EXISTS} của Postgres KHÔNG atomic giữa các
 * session/connection khác nhau — 2 (hoặc nhiều) worker-unit thread xử lý {@code vbdi.reply} đồng
 * thời cho CÙNG tenant, lần đầu bảng chưa tồn tại: cả 2 session đều thấy "chưa tồn tại" (mỗi
 * session 1 snapshot/transaction riêng, không thấy được thay đổi CHƯA COMMIT của session kia) nên
 * cả 2 đều thử tạo — session thắng commit trước, session thua nhận
 * {@code duplicate key value violates unique constraint "pg_type_typname_nsp_index"} (Postgres báo
 * lỗi ở tầng catalog nội bộ của kiểu dữ liệu ứng với bảng, Spring dịch thành
 * {@code DuplicateKeyException}) — đây là hành vi ĐÃ BIẾT của Postgres, không phải lỗi logic ứng
 * dụng. Sửa bằng cách tách {@code ensureReplyLogTable} ra 1 TRANSACTION RIÊNG (không chung với
 * transaction INSERT chính) và bắt {@code DuplicateKeyException}: session thua coi như THÀNH CÔNG
 * (bảng đã tồn tại, do session thắng vừa tạo) — xem javadoc {@link #ensureReplyLogTable}.</p>
 *
 * <p><b>Bugfix 07/09/2026 (5) — báo lỗi người dùng: "Trả lại" lần 2/3 KHÔNG ghi vết, văn bản không
 * mất khỏi danh sách dù bấm lại nhiều lần</b>: dedup key CŨ {@code (send_log_id, status_code)} SAI
 * cho các mã CÓ THỂ lặp lại hợp lệ nhiều lần trên CÙNG {@code sendLogId} — "Trả lại"/"Từ chối" là
 * hành động văn thư có thể bấm LẠI (mỗi lần là 1 SỰ KIỆN nghiệp vụ MỚI, không phải redelivery), vì
 * {@code sendLogId} (= id dòng "Gửi văn bản" GỐC, cố định suốt vòng đời văn bản) KHÔNG đổi giữa các
 * lần bấm khác nhau — dedup theo (sendLogId, statusCode) vô tình biến LẦN BẤM THỨ 2 TRỞ ĐI thành
 * "trùng" với lần đầu, dù là 2 sự kiện HOÀN TOÀN khác nhau. Khoá dedup ĐÚNG phải bảo vệ khỏi
 * REDELIVERY THẬT (RabbitMQ gửi lại CHÍNH message đã publish, do lỗi mạng/consumer crash) — không
 * phải khỏi hành động lặp lại của người dùng. {@code TaskContext#unitId} (sinh MỚI ngẫu nhiên mỗi
 * lần publish — {@code PublishVbdiReplyStatusUseCase}: {@code new MessageUnitSpec(UUID.randomUUID()
 * .toString(), ...)}, giữ NGUYÊN khi outbox/RabbitMQ redeliver CHÍNH message đó) mới là khoá đúng —
 * đổi PK {@code worker_vbdi_reply_log} sang {@code unit_id} (globally unique), xem
 * {@link #ensureReplyLogTable}/{@link #migrateOldSchemaIfNeeded}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VbdiReplyTaskHandler implements TaskHandler {

    private final WorkerProperties props;
    private final TenantDataSourceManager tenantDataSources;
    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return VbdiTaskConstants.TASK_REPLY;
    }

    @Override
    public UnitResult execute(TaskContext context, JsonNode payload) {
        if (!props.getTasks().getVbdi().isEnabled()) {
            return UnitResult.failure("worker.tasks.vbdi.enabled=false");
        }
        VbdiReplyPayload p;
        try {
            p = objectMapper.treeToValue(payload, VbdiReplyPayload.class);
        } catch (Exception e) {
            return UnitResult.failure("payload vbdi.reply không hợp lệ: " + e.getMessage());
        }
        if (p == null || p.getSendLogId() == null || p.getSendLogId().isBlank()
                || p.getStatusCode() == null || p.getStatusCode().isBlank()) {
            return UnitResult.failure("payload vbdi.reply thiếu field bắt buộc (sendLogId/statusCode)");
        }

        log.info("vbdi.reply: sendLog={}, documentId={}, statusCode={} ({}) tại tenant {}",
            p.getSendLogId(), p.getDocumentId(), p.getStatusCode(), p.getStatusLabel(), context.tenantId());

        var dsOpt = tenantDataSources.getDataSource(context.tenantId());
        if (dsOpt.isEmpty()) {
            return UnitResult.failure("Không có connection pool cho tenant (đơn vị gửi) "
                + context.tenantId() + " — kiểm tra registry tenants");
        }
        DataSource ds = dsOpt.get();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        String table = replyLogTable();
        // Bo sung 07/09/2026 (xem javadoc lop): dedup ("worker_vbdi_reply_log") + ghi "nhat ky gui
        // nhan" ("vbdi_send_log_documents") nam TRONG CUNG 1 transaction - dedup that bai (da nhan
        // truoc do) thi KHONG ghi them dong nao (tranh trung lap "Nhat ky gui nhan" khi message bi
        // redeliver); dedup lan dau thanh cong nhung ghi nhat ky loi thi ROLLBACK CA HAI, retry sau
        // se lai coi nhu CHUA xu ly (dung nguyen tac "tranh mat trang thai" da ap dung xuyen suot).
        // Bugfix 07/09/2026 (2, xem javadoc lop): tach ensureReplyLogTable ra transaction RIENG -
        // neu bi DuplicateKeyException do dua tao bang voi 1 unit khac dang chay song song, KHONG
        // duoc keo theo ROLLBACK transaction INSERT chinh ben duoi.
        ensureReplyLogTable(ds, table);
        // Bugfix 07/09/2026 (5, xem javadoc lop): unitId sinh MOI ngau nhien moi lan publish (KHAC
        // sendLogId - co dinh suot doi van ban) - dung lam dedup key DUNG (bao ve khoi redelivery
        // THAT, khong chan nham hanh dong lap lai hop le cua nguoi dung).
        String unitId = context.unitId();
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        Integer inserted = tx.execute(status -> {
            int result = jdbc.update("""
                    INSERT INTO %s
                     (unit_id, send_log_id, document_id, status_code, status_label, status_details, recipient_identifier, worker_instance)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (unit_id) DO NOTHING
                    """.formatted(table),
                unitId, p.getSendLogId(), p.getDocumentId(), p.getStatusCode(), p.getStatusLabel(),
                p.getStatusDetails(), p.getRecipientIdentifier(),
                context.correlationId() != null ? context.correlationId() : "worker");
            if (result == 1) {
                insertSendLogEntry(jdbc, p);
            }
            return result;
        });

        if (inserted == null || inserted == 0) {
            log.info("unit {} (sendLog={}, status={}) đã ghi trước đó (idempotent — redelivery)",
                unitId, p.getSendLogId(), p.getStatusCode());
        }
        return UnitResult.success();
    }

    /**
     * Ghi 1 dòng MỚI vào {@code vbdi_send_log_documents} (bảng đã tồn tại sẵn ở MỌI tenant — td-web
     * migrate qua {@code V1.0.33__Create_Vbdi_Send_Log_Documents_Table.sql}, cùng nguyên tắc "Worker
     * chỉ tiêu thụ schema đã sẵn sàng" như {@code documents}/{@code file_metadata} — xem javadoc
     * lớp).
     *
     * <p><b>{@code parent_id}/{@code recipient_group}/{@code recipient_identifier}/
     * {@code recipient_name} lấy TỪ CHÍNH dòng gốc</b> ({@code sendLogId} = {@code id} của dòng
     * {@code vbdi_send_log_documents} đã tạo lúc "Gửi văn bản" ban đầu — {@code AddVbdiSendLogUseCase}
     * đã xác nhận điều này, xem javadoc field {@code VbdiReplyPayload#sendLogId}) — KHÔNG dùng lại
     * giá trị relay qua payload (dù {@code VbdiReplyPayload} có mang sẵn {@code recipientGroup}/
     * {@code recipientIdentifier}/{@code recipientName} làm DỰ PHÒNG khi không tìm thấy dòng gốc,
     * vd dữ liệu cũ/bị xoá) — tránh sai lệch nếu producer phía đơn vị nhận truyền nhầm/thiếu, và
     * tránh phải thêm field mới không cần thiết ở phía tạo văn bản đến. {@code send_type} dùng nhãn
     * RIÊNG {@code "phan_hoi_trang_thai"} (không trùng {@code senddocument}/{@code resenddocument}/
     * ...) để phân biệt "sự kiện phản hồi" với "sự kiện gửi" khi FE/BE sau này cần lọc riêng.
     */
    private void insertSendLogEntry(JdbcTemplate jdbc, VbdiReplyPayload p) {
        UUID sendLogRowId = parseUuidOrNull(p.getSendLogId());
        Map<String, Object> original = sendLogRowId == null ? null : jdbc.query("""
                SELECT parent_id, recipient_group, recipient_identifier, recipient_name
                FROM vbdi_send_log_documents WHERE id = ?
                """, rs -> rs.next() ? Map.of(
                "parent_id", rs.getObject("parent_id", UUID.class),
                "recipient_group", java.util.Objects.requireNonNullElse(rs.getString("recipient_group"), ""),
                "recipient_identifier", java.util.Objects.requireNonNullElse(rs.getString("recipient_identifier"), ""),
                "recipient_name", java.util.Objects.requireNonNullElse(rs.getString("recipient_name"), ""))
                : null,
            sendLogRowId);

        UUID parentId = original != null ? (UUID) original.get("parent_id") : parseUuidOrNull(p.getDocumentId());
        if (parentId == null) {
            log.warn("Bo qua ghi 'Nhat ky gui nhan' cho sendLog={} - khong xac dinh duoc parent_id "
                + "(khong tim thay dong goc lan khong co documentId du phong)", p.getSendLogId());
            return;
        }
        String recipientGroup = original != null ? (String) original.get("recipient_group") : p.getRecipientGroup();
        String recipientIdentifier = original != null ? (String) original.get("recipient_identifier") : p.getRecipientIdentifier();
        String recipientName = original != null ? (String) original.get("recipient_name") : p.getRecipientName();
        String actorDisplayName = (p.getActorDisplayName() == null || p.getActorDisplayName().isBlank())
            ? "Hệ thống tiếp nhận" : p.getActorDisplayName();
        String content = buildContent(p.getStatusCode(), p.getStatusLabel(), p.getStatusDetails(), recipientName);
        jdbc.update("""
                INSERT INTO vbdi_send_log_documents
                 (id, parent_id, send_type, recipient_group, recipient_identifier, recipient_name,
                  status, content, user_creator, created_on)
                VALUES (?, ?, 'phan_hoi_trang_thai', ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
            UUID.randomUUID(), parentId, recipientGroup, recipientIdentifier,
            recipientName, p.getStatusLabel(), content, actorDisplayName);

        if (VbdiTaskConstants.STATUS_TRA_LAI.equalsIgnoreCase(p.getStatusCode())) {
            markOriginalVbdiDocumentReplied(jdbc, parentId);
        }
    }

    /**
     * Bổ sung 07/09/2026 (3, yêu cầu người dùng: "Từ chối/Trả lại" phải phản hồi trạng thái giống
     * 01/03 — xem {@code StatusProcess.java} thật): nhánh {@code status_code="tralai"} (phi chuẩn,
     * KHÔNG thuộc {@code StatusCode.java}) là DUY NHẤT trong 6+1 mã còn ghi NGƯỢC vào CHÍNH văn bản
     * {@code vbdi} gốc phía đơn vị gửi ({@code docvbdi.replaceItemValue("is_document_reply", "1")} —
     * cả 2 nơi gọi {@code addResponseStatusToSender}/{@code CapNhatTrangThaiGuiCQB} đều làm việc này
     * cho riêng mã "tralai", KHÔNG áp dụng cho 01/02/03/04). Worker đang chạy TRONG tenant của đơn vị
     * gửi (unit của task {@code vbdi.reply}) nên làm được trực tiếp qua JDBC thuần, không cần round-
     * trip nào khác — {@code parentId} CHÍNH là id văn bản {@code vbdi} đó (đã xác nhận qua
     * {@code AddVbdiSendLogUseCase}, xem javadoc {@link #insertSendLogEntry}). Dùng {@code ||} nối
     * JSONB (merge/ghi đè đúng 1 key, giữ nguyên mọi field khác) thay vì {@code jsonb_set} — đơn giản
     * hơn cho 1 key duy nhất. Lỗi ở bước này (vd văn bản gốc đã bị xoá) chỉ log cảnh báo, KHÔNG rollback
     * "Nhật ký gửi nhận" đã ghi thành công ở trên (best-effort, giống các bước phụ trợ khác).
     *
     * <p><b>Bugfix 07/09/2026 (4) — báo lỗi người dùng: log WARN "khong tim thay van ban vbdi goc"
     * MỌI LẦN, văn bản không biến mất khỏi danh sách (dù "Nhật ký gửi nhận" đã ghi đúng)</b>: câu
     * UPDATE bản đầu nhắm SAI bảng {@code documents} (bảng DÙNG CHUNG của BL-101/vbden) — module vbdi
     * KHÔNG dùng bảng đó, có bảng vật lý RIÊNG {@code vbdi_documents} (yêu cầu người dùng "tách văn
     * bản đi khỏi bảng dùng chung" — xem javadoc {@code VbdiDocument}, {@code @Table(name =
     * "vbdi_documents")}) — cùng cấu trúc {@code dynamic_fields} JSONB/{@code document_type} nên
     * CHỈ cần sửa tên bảng, không đổi gì khác. Đối chiếu thêm {@code ViewQueryService
     * .VIEW_VBDI_REPLY_REFUSE} xác nhận đúng field {@code dynamic_fields->>'is_document_reply'} là
     * điều kiện đưa văn bản vào danh sách "Văn bản bị từ chối/trả lại" phía đơn vị gửi — UPDATE sai
     * bảng khiến flag không bao giờ được set, văn bản không bao giờ xuất hiện đúng ở view đó.</p>
     */
    private void markOriginalVbdiDocumentReplied(JdbcTemplate jdbc, UUID parentId) {
        try {
            int updated = jdbc.update("""
                    UPDATE vbdi_documents
                    SET dynamic_fields = COALESCE(dynamic_fields, '{}'::jsonb) || '{"is_document_reply": "1"}'::jsonb
                    WHERE id = ? AND document_type = 'vbdi'
                    """, parentId);
            if (updated == 0) {
                log.warn("Khong tim thay van ban vbdi goc id={} de danh dau is_document_reply=1 "
                    + "(nhanh 'tralai') - co the da bi xoa", parentId);
            }
        } catch (Exception ex) {
            log.warn("Loi khi danh dau is_document_reply=1 cho van ban vbdi goc id={}: {}",
                parentId, ex.getMessage());
        }
    }

    /**
     * Bổ sung 07/09/2026 (2, báo lỗi người dùng: cột "Nội dung" chưa hiển thị như bản Domino — kèm
     * ảnh chụp tham khảo) — đối chiếu {@code DocumentInProcess.java}/{@code StatusProcess.java} thật
     * để dựng đúng công thức nội dung cho từng mã trạng thái (KHÔNG bịa thêm mã/định dạng ngoài 6 mã
     * {@code StatusCode.java} đã chốt):
     * <ul>
     *   <li><b>01 (Đã đến)</b>: {@code SendRecievedInComingReplyToSender} luôn có dạng
     *   {@code "Đã đến, ID: " + doc_toCopy.getUniversalID()} — {@code statusDetails} do
     *   {@link VbdiDeliveryTaskHandler#publishArrivedReply} truyền vào đã là {@code "ID: ..."}, chỉ
     *   còn nối vào nhãn.</li>
     *   <li><b>03 (Đã tiếp nhận)</b>: {@code StatusProcess.addResponseStatusToSender}/
     *   {@code CapNhatTrangThaiGuiCQB} luôn dựng {@code "Đơn vị " + coquanngoai + " đã đồng ý yêu cầu"}
     *   — {@code coquanngoai} = tên đơn vị PHẢN HỒI (chính là {@code recipientName} lấy từ dòng gốc
     *   phía trên, KHÔNG cần producer truyền thêm field mới).</li>
     *   <li><b>04 (Phân công xử lý)</b>: Domino nối thêm {@code status_details} (username người được
     *   giao) vào trong ngoặc sau nhãn — {@code ChuyenXuLyVbdenUseCase} truyền {@code statusDetails}
     *   = danh sách người mới được giao (";"-nối).</li>
     *   <li><b>05 (Đang xử lý)</b>: Domino KHÔNG có opinion riêng — giữ nguyên nhãn trơn.</li>
     *   <li><b>06 (Hoàn thành)</b>: {@code CapNhatVanBanDen.java} case {@code ketthucxuly} không có
     *   {@code status_details} cố định — nếu {@code KetThucXuLyUseCase} có truyền ý kiến thì nối
     *   thêm sau dấu hai chấm, không thì giữ nhãn trơn.</li>
     *   <li><b>"tralai" (Trả lại, PHI CHUẨN — bổ sung 07/09/2026 (3), theo yêu cầu người dùng sửa lại
     *   quyết định trước đó)</b>: {@code StatusProcess.java} thật ({@code addResponseStatusToSender}/
     *   {@code CapNhatTrangThaiGuiCQB}) LUÔN dùng nội dung CỐ ĐỊNH
     *   {@code "Đơn vị " + coquanngoai + " đã trả lại văn bản"} — BỎ QUA {@code opinion} người dùng
     *   nhập hoàn toàn (khác 01/03/04/06 đều có dùng opinion/status_details) — {@code recipientName}
     *   cùng nguồn với nhánh 03.</li>
     * </ul>
     * Mọi mã khác (kể cả 02, publish qua {@code TuChoiTiepNhanVbdenUseCase} nhưng KHÔNG có công thức
     * nội dung cố định riêng trong {@code StatusProcess.java}) fallback về {@code statusDetails} nếu
     * có, không thì nhãn trơn — an toàn, không throw khi gặp mã lạ.
     */
    private String buildContent(String statusCode, String statusLabel, String statusDetails, String recipientName) {
        String label = statusLabel == null ? "" : statusLabel;
        String details = statusDetails == null ? "" : statusDetails.trim();
        if (statusCode == null) {
            return details.isEmpty() ? label : details;
        }
        return switch (statusCode) {
            case "01" -> details.isEmpty() ? label : label + ", " + details;
            case "03" -> "Đơn vị " + (recipientName == null ? "" : recipientName) + " đã đồng ý yêu cầu";
            case "04" -> details.isEmpty() ? label : label + " (" + details + ")";
            case "06" -> details.isEmpty() ? label : label + ": " + details;
            case "tralai" -> "Đơn vị " + (recipientName == null ? "" : recipientName) + " đã trả lại văn bản";
            default -> details.isEmpty() ? label : details;
        };
    }

    private UUID parseUuidOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String replyLogTable() {
        return "worker_vbdi_reply_log";
    }

    /**
     * Bugfix 07/09/2026 (2, xem javadoc lớp): chạy trong TRANSACTION RIÊNG của chính nó (không dùng
     * chung {@code jdbc}/transaction với INSERT chính) — nếu thua trong race {@code CREATE TABLE IF
     * NOT EXISTS} với 1 unit khác đang xử lý song song (cùng tenant, bảng chưa từng tồn tại), bắt
     * {@code DuplicateKeyException} và coi như thành công (bảng đã tồn tại, do session thắng vừa
     * tạo) — KHÔNG để lỗi này lan ra làm rollback/fail toàn bộ unit.
     */
    private void ensureReplyLogTable(DataSource ds, String table) {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.execute(status -> {
            try {
                JdbcTemplate localJdbc = new JdbcTemplate(ds);
                migrateOldSchemaIfNeeded(localJdbc, table);
                localJdbc.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        unit_id              VARCHAR(100) NOT NULL PRIMARY KEY,
                        send_log_id          VARCHAR(100) NOT NULL,
                        document_id          VARCHAR(100),
                        status_code          VARCHAR(10)  NOT NULL,
                        status_label         VARCHAR(200),
                        status_details       TEXT,
                        recipient_identifier VARCHAR(200),
                        worker_instance      VARCHAR(200),
                        replied_at           TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                    )
                    """.formatted(table));
            } catch (DuplicateKeyException ex) {
                log.debug("worker_vbdi_reply_log: thua race CREATE TABLE voi 1 unit khac dang chay "
                    + "dong thoi - bang da duoc tao, bo qua an toan: {}", ex.getMessage());
            }
            return null;
        });
    }

    /**
     * Bugfix 07/09/2026 (5, xem javadoc lớp): bảng {@code worker_vbdi_reply_log} đã tồn tại ở
     * production với SCHEMA CŨ (PK {@code (send_log_id, status_code)}, KHÔNG có cột {@code unit_id})
     * — {@code CREATE TABLE IF NOT EXISTS} KHÔNG tự đổi PK của bảng đã tồn tại. Bảng này CHỈ là bảng
     * TỰ QUẢN/dedup nội bộ của Worker (KHÔNG phải dữ liệu nghiệp vụ — dữ liệu thật nằm ở
     * {@code vbdi_send_log_documents}, xem javadoc lớp), nên an toàn để DROP + tạo lại đúng schema
     * mới khi phát hiện thiếu cột {@code unit_id} — mất tối đa vài dòng dedup đang "nóng" tại đúng
     * thời điểm redeploy (rủi ro cực thấp, không ảnh hưởng dữ liệu nghiệp vụ).
     */
    private void migrateOldSchemaIfNeeded(JdbcTemplate jdbc, String table) {
        Boolean tableExists = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ?)",
            Boolean.class, table);
        if (!Boolean.TRUE.equals(tableExists)) {
            return;
        }
        Boolean hasUnitIdColumn = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_name = ? AND column_name = 'unit_id'
            )
            """, Boolean.class, table);
        if (!Boolean.TRUE.equals(hasUnitIdColumn)) {
            log.warn("{}: schema cu (thieu cot unit_id, dedup theo send_log_id+status_code - da biet "
                + "gay bo sot cac lan phan hoi lap lai hop le vd 'Tra lai' nhieu lan) - drop va tao "
                + "lai dung schema moi (chi la bang tu quan/dedup, khong mat du lieu nghiep vu)", table);
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
    }
}