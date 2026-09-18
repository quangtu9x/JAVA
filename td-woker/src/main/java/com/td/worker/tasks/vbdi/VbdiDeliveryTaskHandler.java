package com.td.worker.tasks.vbdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.TaskHandler;
import com.td.worker.core.task.UnitResult;
import com.td.worker.infrastructure.minio.WorkerMinioClient;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.td.worker.transport.rabbit.RabbitPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Task {@code vbdi.delivery} — REFERENCE IMPLEMENTATION cho luồng "đơn vị gửi → đơn vị nhận".
 *
 * <p>Worker Core hoàn toàn generic; nghiệp vụ VBDI nằm GỌN trong module này. Nhiệm vụ phía
 * đơn vị NHẬN:
 * <ol>
 *   <li>Nơi nhận NỘI BỘ (coordinator/internal_unit/contact_unit, có database tenant): ghi nhận
 *       "Đã đến" idempotent vào bảng dedup {@code worker_vbdi_delivery_received} (Worker TỰ QUẢN
 *       bảng này trong database tenant — KHÔNG đụng schema của td-web).</li>
 *   <li><b>contact_unit (cơ quan/đơn vị KHÁC thuộc tỉnh) — 06/09/2026, sửa lại cùng ngày (nhầm
 *       nhóm ban đầu, xem docs/architecture/11 addendum 06/09/2026 (3))</b> VÀ <b>internal_unit
 *       (đơn vị nội bộ CỦA CHÍNH cơ quan gửi — bổ sung 18/09/2026, hoàn thiện yêu cầu người dùng
 *       "phát hành đi khi chọn Đơn vị nội bộ")</b>: NGOÀI dedup, tạo THẬT 1 bản ghi "văn bản đến
 *       chờ vào sổ" (bảng {@code documents}, cùng schema {@code BusinessDocument} td-web đang dùng
 *       — INSERT trực tiếp qua JDBC thuần, KHÔNG gọi lại {@code TiepNhanVbdenUseCase}/phụ thuộc
 *       {@code td-application}, giữ đúng nguyên tắc "Worker độc lập hoàn toàn"). Cả 2 lần ghi
 *       (dedup + văn bản) nằm TRONG CÙNG 1 transaction DB tenant — crash giữa chừng sẽ ROLLBACK CẢ
 *       HAI, lần retry sau coi như CHƯA xử lý (không để lại trạng thái "dedup có, văn bản không").
 *       Xem {@link VbdenIncomingDocumentWriter} cho chi tiết field theo TỪNG nhóm (contact_unit ghi
 *       {@code contact_unit_identifier}; internal_unit ghi {@code internal="1"} +
 *       {@code unit_process}/{@code user_receiver}, khớp view Domino {@code dennoibo}/
 *       {@code dennoibo_choxuly})/đối chiếu hàng đợi FE. {@code internal_unit} bao gồm CẢ trường
 *       hợp người dùng lỡ chọn chính cơ quan mình ở cột "Cơ quan, đơn vị thuộc Tỉnh" thay vì cột
 *       "Đơn vị nội bộ" — {@code SendVbdiDocumentUseCase#reclassifyIfOwnAgency} (td-application) đã
 *       tự chuyển nhóm TRƯỚC khi publish message này, handler ở đây không cần biết sự khác biệt.
 *       {@code coordinator} (cá nhân, định danh nhận là username chứ không phải mã tổ chức) VẪN
 *       CHƯA áp dụng — ngoài phạm vi đợt này. Cờ {@code worker.tasks.vbdi.create-incoming-document}
 *       (mặc định bật) tắt được để rollback vận hành về hành vi cũ mà không cần đổi code.</li>
 *   <li>Nơi nhận NGOÀI tỉnh (external_unit): CHƯA có gateway liên thông thật (đúng quyết định
 *       docs/analysis/07 của project tham khảo) — văn bản chưa thể thực sự rời hệ thống nên KHÔNG
 *       được ghi "Đã đến" và KHÔNG được trả {@code success()} ("DONE giả" sẽ khiến message bị
 *       ACK/bỏ vĩnh viễn dù chưa gửi được gì). Trả {@code UnitResult.waitingDependency(...)}: unit
 *       được checkpoint ở trạng thái {@code WAITING_DEPENDENCY} (không tiêu {@code maxAttempts}),
 *       message vẫn ACK an toàn ở broker, và {@code RetryPoller} tự kiểm tra lại theo
 *       {@code worker.core.dependency-retry-interval} cho tới khi gateway external được triển
 *       khai/cấu hình ở phiên bản Worker kế tiếp.</li>
 *   <li>Tuỳ chọn: kiểm tra tệp tham chiếu trên MinIO + publish phản hồi "Đã đến" về đơn vị
 *       gửi (khi Rabbit bật) — chỉ áp dụng cho nhánh nội bộ đã thực sự "Đã đến".</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VbdiDeliveryTaskHandler implements TaskHandler {

    private final WorkerProperties props;
    private final TenantDataSourceManager tenantDataSources;
    private final WorkerMinioClient minio;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RabbitPublisher> rabbitPublisher;

    @Override
    public String taskType() {
        return VbdiTaskConstants.TASK_DELIVERY;
    }

    @Override
    public UnitResult execute(TaskContext context, JsonNode payload) {
        if (!props.getTasks().getVbdi().isEnabled()) {
            return UnitResult.failure("worker.tasks.vbdi.enabled=false");
        }
        final VbdiDeliveryPayload p;
        try {
            p = objectMapper.treeToValue(payload, VbdiDeliveryPayload.class);
        } catch (Exception e) {
            return UnitResult.failure("payload vbdi.delivery không hợp lệ: " + e.getMessage());
        }
        if (p == null || p.getSendLogId() == null || p.getSendLogId().isBlank()
                || p.getDocumentId() == null || p.getDocumentId().isBlank()
                || p.getRecipientGroup() == null || p.getRecipientIdentifier() == null) {
            return UnitResult.failure(
                "payload vbdi.delivery thiếu field bắt buộc (sendLogId/documentId/recipientGroup/recipientIdentifier)");
        }

        log.info("vbdi.delivery: documentId={}, sendLogId={}, sendType={}, group={}, recipient={} (from tenant {})",
            p.getDocumentId(), p.getSendLogId(), p.getSendType(), p.getRecipientGroup(),
            p.getRecipientIdentifier(), p.getSenderTenantId());

        // --- Nơi nhận NGOÀI TỈNH (external_unit): ghi nhận, không gọi gateway thật ---
        if (p.isExternal()) {
            log.warn("vbdi.delivery tới nơi nhận ngoài tỉnh: group={}, mailin={} — WAITING_DEPENDENCY (chưa có gateway liên thông)",
                p.getRecipientGroup(), p.getRecipientMailin());
            verifyObjectExists(p);
            // Không đánh dấu DONE khi văn bản chưa thể rời hệ thống. RetryPoller sẽ tự thử
            // lại sau khi gateway external được triển khai/cấu hình.
            return UnitResult.waitingDependency(
                "Chưa có gateway liên thông cho recipient group " + p.getRecipientGroup());
        }

        // --- Nơi nhận NỘI BỘ: cần database tenant đích (database-per-tenant) ---
        var dsOpt = tenantDataSources.getDataSource(context.tenantId());
        if (dsOpt.isEmpty()) {
            return UnitResult.failure(
                "Không có connection pool cho tenant " + context.tenantId()
                    + " (kiểm tra control-plane tenants registry / đường mạng tới DB tenant)");
        }
        DataSource ds = dsOpt.get();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        String table = workerTable();

        // Bugfix 06/09/2026 (lỗi thật vận hành: BadSqlGrammarException "relation ... does not
        // exist" ngay trên INSERT dù ensureDeliveryReceivedTable() vừa chạy CREATE TABLE IF NOT
        // EXISTS trước đó — đã xác nhận trực tiếp trên DB tenant thật: bảng KHÔNG hề tồn tại dù
        // handler đã chạy hàng chục lần). Nguyên nhân: worker.tenant.pool-auto-commit MẶC ĐỊNH
        // false cho MỌI pool tenant (WorkerProperties.Tenant) — câu lệnh chạy qua JdbcTemplate mà
        // KHÔNG nằm trong 1 transaction Spring quản lý thì KHÔNG được commit tường minh; HikariCP
        // tự ROLLBACK khi trả connection về pool nếu autoCommit=false và chưa commit (hành vi an
        // toàn mặc định của HikariCP, tránh rò rỉ transaction dở dang cho người mượn connection kế
        // tiếp) — nghĩa là CREATE TABLE/ALTER TABLE tự-chữa-lành trước đây LUÔN bị hoàn tác NGAY
        // SAU khi chạy, bảng dedup không bao giờ thực sự tồn tại, mọi lần sau đều lỗi "relation
        // does not exist" (Postgres phân loại lỗi này CŨNG là BadSqlGrammarException — dễ nhầm với
        // lỗi thiếu UNIQUE constraint đã sửa 05/09/2026, nhưng khác nguyên nhân gốc). Phải bọc DDL
        // trong transaction tường minh (commit thật) — dùng CHUNG 1 TransactionTemplate với khối
        // dedup+văn bản bên dưới (Postgres DDL transactional, an toàn gộp chung).
        var txManager = new DataSourceTransactionManager(ds);
        var tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> ensureDeliveryReceivedTable(jdbc, table));

        // Bo sung 18/09/2026 (hoan thien "phat hanh vbdi -> den noi bo", yeu cau nguoi dung - xem
        // javadoc lop nay muc "internal_unit... CHUA ap dung" da duoc go bo): internal_unit (that va
        // reclassify boi SendVbdiDocumentUseCase#reclassifyIfOwnAgency khi nguoi dung chon nham
        // chinh co quan minh o cot "thuoc Tinh") nay CUNG tao van ban den that, cung 1 co che voi
        // contact_unit - chi khac field ghi (xem VbdenIncomingDocumentWriter#buildDynamicFieldsJson).
        boolean createDocument = props.getTasks().getVbdi().isCreateIncomingDocument()
            && (VbdiTaskConstants.GROUP_CONTACT_UNIT.equals(p.getRecipientGroup())
                || VbdiTaskConstants.GROUP_INTERNAL_UNIT.equals(p.getRecipientGroup()));

        // Claim dedup + (tuỳ điều kiện) tạo văn bản đến trong CÙNG 1 transaction: crash/lỗi giữa
        // chừng ROLLBACK CẢ HAI - lần retry sau (redelivery/RetryPoller) coi như CHƯA xử lý, không
        // để lại trạng thái nửa vời "đã dedup nhưng chưa có văn bản" (đúng yêu cầu "tránh mất hoặc
        // không nhận được văn bản"). DataSourceTransactionManager tạo mới mỗi lần gọi - chi phí
        // không đáng kể so với round-trip DB thật, không cần cache theo tenant.
        AtomicReference<UUID> createdDocumentId = new AtomicReference<>();
        boolean firstTime = Boolean.TRUE.equals(tx.execute(status -> {
            int inserted = markArrived(jdbc, context, p, table);
            if (inserted == 1 && createDocument) {
                // Bo sung 07/09/2026: chuan bi tep dinh kem TRUOC khi build dynamic_fields (can id
                // moi sinh cua tung tep de nhung vao "files[].file_id") - xem javadoc
                // VbdenIncomingDocumentWriter#prepareFiles.
                var preparedFiles = VbdenIncomingDocumentWriter.prepareFiles(p, minio.bucketName());
                // Bo sung 13/09/2026 (port full end-to-end "thay the/thu hoi" + bugfix "gui lai van
                // ban chua gan dung trang thai", xem javadoc VbdenIncomingDocumentWriter
                // #resolveEffectiveBussinessDocType/#resolveRevokeTargetIds): effectiveType tinh
                // TRUOC (thuan tuy tu payload, khong cham DB) roi moi tra cuu CUNG tenant (jdbc o day
                // da la ket noi toi dung tenant nhan) - loi/khong tim thay CHI bo qua phan
                // "documentoutrevoke", KHONG anh huong viec tao van ban binh thuong hay gia tri
                // bussiness_doc_type (method tra cuu da tu bat loi rieng, khong throw).
                var effectiveBussinessDocType = VbdenIncomingDocumentWriter.resolveEffectiveBussinessDocType(p);
                var revokeTargetIds = VbdenIncomingDocumentWriter.resolveRevokeTargetIds(
                    jdbc, p, effectiveBussinessDocType);
                String dynamicFieldsJson;
                try {
                    dynamicFieldsJson = VbdenIncomingDocumentWriter.buildDynamicFieldsJson(
                        objectMapper, p, preparedFiles, effectiveBussinessDocType, revokeTargetIds);
                } catch (Exception e) {
                    // RuntimeException trong callback -> TransactionTemplate tự rollback CẢ 2 write;
                    // propagate ra ngoài để UnitDispatcher xử lý như lỗi thường (RETRY/FAILED theo
                    // maxAttempts), KHÔNG nuốt lỗi.
                    throw new IllegalStateException(
                        "Không build được dynamic_fields cho văn bản đến: " + e.getMessage(), e);
                }
                UUID docId = VbdenIncomingDocumentWriter.insertDocument(
                    jdbc, VbdenIncomingDocumentWriter.resolveTitle(p), dynamicFieldsJson);
                VbdenIncomingDocumentWriter.linkCreatedDocument(jdbc, table, p.getSendLogId(), docId);
                // Bo sung 07/09/2026: ghi file_metadata TRONG CUNG transaction voi insertDocument o
                // tren - loi o BAT KY tep nao ROLLBACK CA van ban lan dedup (xem javadoc
                // VbdenIncomingDocumentWriter#insertFileMetadata).
                VbdenIncomingDocumentWriter.insertFileMetadata(jdbc, docId, preparedFiles);
                createdDocumentId.set(docId);
            }
            return inserted == 1;
        }));

        if (!firstTime) {
            log.info("sendLog {} đã ghi nhận trước đó (idempotent — không xử lý trùng)", p.getSendLogId());
        } else if (createDocument) {
            log.info("Đã tạo văn bản đến (chờ vào sổ) id={} cho sendLog={}, tenant={}",
                createdDocumentId.get(), p.getSendLogId(), context.tenantId());
        }

        verifyObjectExists(p);
        publishArrivedReply(context, p, createdDocumentId.get());
        return UnitResult.success();
    }

    private String workerTable() {
        String t = props.getTasks().getVbdi().getReceivedTable();
        return (t == null || t.isBlank()) ? "worker_vbdi_delivery_received" : t;
    }

    private void ensureDeliveryReceivedTable(JdbcTemplate jdbc, String table) {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS %s (
                send_log_id          VARCHAR(100) PRIMARY KEY,
                document_id          VARCHAR(100),
                sender_tenant_id     VARCHAR(100),
                sender_identifier    VARCHAR(200),
                recipient_identifier VARCHAR(200),
                send_type            VARCHAR(50),
                worker_instance      VARCHAR(200),
                received_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                created_document_id  UUID
            )
            """.formatted(table));
        ensureSendLogIdUniqueConstraint(jdbc, table);
        VbdenIncomingDocumentWriter.ensureCreatedDocumentIdColumn(jdbc, table);
    }

    /**
     * Bugfix 05/09/2026 (người dùng báo lỗi thật: {@code BadSqlGrammarException} trên
     * {@code INSERT ... ON CONFLICT (send_log_id)} tại {@link #markArrived}): bảng này có thể ĐÃ
     * TỒN TẠI TỪ TRƯỚC ở database tenant (Worker tự tạo bảng này lười — "TỰ QUẢN", không qua
     * Flyway — nên không có gì đảm bảo mọi lần tạo trước đó đều đúng cấu trúc, vd 1 bản Worker cũ
     * hơn/thử nghiệm tay có thể đã tạo bảng KHÔNG có {@code PRIMARY KEY}/{@code UNIQUE} trên
     * {@code send_log_id}) — khi đó {@code CREATE TABLE IF NOT EXISTS} chỉ no-op, để lại bảng THIẾU
     * ràng buộc, khiến {@code ON CONFLICT (send_log_id)} luôn lỗi "no unique or exclusion constraint
     * matching the ON CONFLICT specification" (Postgres/Spring gộp chung thành
     * {@code BadSqlGrammarException}). Tự kiểm tra qua {@code information_schema} + tự thêm ràng
     * buộc nếu thiếu — không phụ thuộc giả định "bảng nếu có sẵn thì đã đúng cấu trúc" (đúng bài học
     * đã áp dụng cho {@code outbox_events} phía {@code td-web}, xem
     * {@code docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md} addendum 04/09/2026).
     */
    private void ensureSendLogIdUniqueConstraint(JdbcTemplate jdbc, String table) {
        Boolean hasUniqueConstraint = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name AND tc.table_name = kcu.table_name
                WHERE tc.table_name = ? AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
                  AND kcu.column_name = 'send_log_id'
            )
            """, Boolean.class, table);
        if (Boolean.TRUE.equals(hasUniqueConstraint)) {
            return;
        }
        try {
            jdbc.execute(("ALTER TABLE " + table + " ADD CONSTRAINT " + table + "_send_log_id_uk UNIQUE (send_log_id)"));
            log.warn("Bang {} thieu rang buoc UNIQUE tren send_log_id (co the tao tu ban Worker cu hon) "
                + "- da tu them rang buoc de ON CONFLICT hoat dong dung", table);
        } catch (Exception ex) {
            // Co the loi do 2 instance Worker cung chay ALTER dong thoi (constraint da duoc instance
            // khac them xong truoc) - khong phai loi that, bo qua an toan (idempotent theo tinh
            // than CREATE TABLE IF NOT EXISTS o tren).
            log.debug("Bo qua loi them constraint UNIQUE cho {} (co the da duoc them boi instance khac): {}",
                table, ex.getMessage());
        }
    }

    private int markArrived(JdbcTemplate jdbc, TaskContext context, VbdiDeliveryPayload p, String table) {
        return jdbc.update("""
                INSERT INTO %s
                 (send_log_id, document_id, sender_tenant_id, sender_identifier, recipient_identifier, send_type, worker_instance)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (send_log_id) DO NOTHING
                """.formatted(table),
            p.getSendLogId(), p.getDocumentId(), p.getSenderTenantId(),
            p.getSenderIdentifier(), p.getRecipientIdentifier(), p.getSendType(),
            context.correlationId() != null ? context.correlationId() : "worker");
    }

    /**
     * Bổ sung 07/09/2026: {@code documentSnapshot} nay mang DANH SÁCH tệp ({@code files}, đã copy
     * sẵn phía td-application) thay vì 1 {@code objectKey} đơn (field cũ chưa từng được gán giá trị
     * ở bất kỳ đâu — xem lịch sử) — kiểm tra TỪNG tệp trong danh sách, không chặn nhau (1 tệp lỗi
     * vẫn log tiếp các tệp còn lại).
     */
    private void verifyObjectExists(VbdiDeliveryPayload p) {
        if (!props.getTasks().getVbdi().isVerifyObjectExists() || p.getDocumentSnapshot() == null
                || p.getDocumentSnapshot().getFiles() == null) {
            return;
        }
        for (var file : p.getDocumentSnapshot().getFiles()) {
            if (file == null || file.getObjectKey() == null) {
                continue;
            }
            boolean exists = minio.objectExists(file.getObjectKey());
            log.info("MinIO tham chiếu tệp {} (objectKey={}): {}", p.getSendLogId(),
                file.getObjectKey(), exists ? "tồn tại" : "KHÔNG tồn tại");
        }
    }

    /**
     * Bugfix 07/09/2026 (phát hiện khi hoàn thiện broker phản hồi trạng thái "vào sổ"/"phân xử lý"
     * theo yêu cầu người dùng — đối chiếu lại chính topology RabbitMQ của Worker): bản trước publish
     * {@code senderReplyRoutingKey} ("vbdi.reply." + identifier, KHÔNG khớp BẤT KỲ binding nào —
     * {@link com.td.worker.transport.rabbit.RabbitConfig} chỉ bind {@code worker.<taskType>}/
     * {@code worker.*}, không có khái niệm "route theo identifier") VÀ body chỉ là JSON PHẲNG (thiếu
     * {@code messageId}/{@code taskType}/{@code units[]} bắt buộc theo {@code EnvelopeParser}). Kết
     * quả: message bị EXCHANGE ÂM THẦM HUỶ (topic exchange không match binding nào = mất, publisher-
     * confirm vẫn "ack" vì exchange NHẬN được, chỉ không route đi đâu) — "Đã đến" chưa BAO GIỜ thực
     * sự tới được {@link VbdiReplyTaskHandler} kể từ khi tính năng này được thêm, dù log ở đây báo
     * "đã publish thành công". Sửa: dựng ĐÚNG 1 {@code MessageEnvelope} hoàn chỉnh (khớp
     * {@code com.td.infrastructure.messaging.EnvelopeJsonBuilder} phía publisher THẬT của
     * {@code vbdi.delivery} — đã xác nhận hoạt động đúng qua toàn bộ tính năng contact_unit) và
     * routing key {@code "worker." + taskType.replace('.', '_')} (khớp
     * {@code RabbitMessagePublisher#sanitizedTaskType} — cấu hình mặc định {@code task-type: ""} ở
     * {@code application.yml} nên queue thật đang bind {@code worker.*}, khớp đúng 2 segment
     * "worker.vbdi_reply"). {@code senderReplyRoutingKey} không còn dùng để ROUTE (giữ lại trong
     * payload/dynamic_fields chỉ để tương thích ngược/tham khảo).
     *
     * <p><b>Bổ sung 07/09/2026 (2, báo lỗi người dùng: cột "Nội dung" chưa lưu ID đơn vị nhận như
     * bản Domino)</b>: đối chiếu {@code DocumentInProcess.java#SendRecievedInComingReplyToSender}
     * thật — {@code status_description} luôn có dạng {@code "Đã đến, ID: " + doc_toCopy.getUniversalID()}
     * (id văn bản đến VỪA TẠO phía đơn vị nhận). {@code createdDocumentId} chỉ khác {@code null} khi
     * nhánh {@code contact_unit} vừa tạo văn bản THẬT (xem {@link #execute}) — nhánh
     * internal_unit/coordinator chỉ dedup, không có id để đính kèm, nội dung khi đó chỉ còn nhãn
     * trơn "Đã đến" (đúng thực tế, không suy đoán id không tồn tại).</p>
     */
    private void publishArrivedReply(TaskContext context, VbdiDeliveryPayload p, UUID createdDocumentId) {
        if (!props.getRabbit().isEnabled() || p.getSenderTenantId() == null || p.getSenderTenantId().isBlank()) {
            return;
        }
        try {
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("sendLogId", p.getSendLogId());
            reply.put("documentId", p.getDocumentId());
            reply.put("statusCode", VbdiTaskConstants.STATUS_01_DA_DEN);
            reply.put("statusLabel", VbdiTaskConstants.LABEL_DA_DEN);
            if (createdDocumentId != null) {
                reply.put("statusDetails", "ID: " + createdDocumentId);
            }
            reply.put("recipientIdentifier", p.getRecipientIdentifier());
            reply.put("recipientName", p.getRecipientName());
            reply.put("recipientGroup", p.getRecipientGroup());
            reply.put("senderTenantId", p.getSenderTenantId());
            reply.put("actorDisplayName", "Hệ thống tiếp nhận");

            String messageId = UUID.randomUUID().toString();
            Map<String, Object> unit = new LinkedHashMap<>();
            unit.put("unitId", p.getSendLogId());
            unit.put("tenantId", p.getSenderTenantId());
            unit.put("payload", reply);
            unit.put("target", Map.of());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("messageId", messageId);
            envelope.put("taskType", VbdiTaskConstants.TASK_REPLY);
            envelope.put("correlationId", context.correlationId() == null ? "" : context.correlationId());
            envelope.put("source", "td-worker-vbdi-delivery");
            envelope.put("createdAt", Instant.now().toString());
            envelope.put("headers", Map.of());
            envelope.put("units", java.util.List.of(unit));

            String json = objectMapper.writeValueAsString(envelope);
            String routingKey = "worker." + VbdiTaskConstants.TASK_REPLY.replace('.', '_');
            rabbitPublisher.ifAvailable(rp -> rp.publish(props.getRabbit().getExchange(), routingKey, json));
            log.info("Đã publish phản hồi 'Đã đến' (sendLog={}) tới tenant {} qua routing {}",
                p.getSendLogId(), p.getSenderTenantId(), routingKey);
        } catch (Exception e) {
            // Phản hồi không gửi được KHÔNG làm FAIL unit gốc (reply gửi lại được sau).
            log.warn("Publish phản hồi 'Đã đến' thất bại: {}", e.getMessage());
        }
    }
}
