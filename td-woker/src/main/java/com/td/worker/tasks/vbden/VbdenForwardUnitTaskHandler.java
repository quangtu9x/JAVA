package com.td.worker.tasks.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.TaskHandler;
import com.td.worker.core.task.UnitResult;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Task {@code vbden.forward_unit} — port {@code DocumentInProcess.java
 * #copyDocumentProcessToDocumentIn()} (Domino, tạo mail form {@code document_process_unit} khi
 * "chuyển xử lý" ({@code ChuyenXuLyVbdenUseCase}) giao {@code unit_process} cho 1 đơn vị chạy CSDL/
 * tenant RIÊNG — "đơn vị con có văn thư riêng, vào sổ riêng", xác nhận với người dùng 08/09/2026 là
 * cross-tenant giống hệt Domino, KHÔNG có tương đương Java trước bản này).
 *
 * <p>Chạy TẠI TENANT ĐÍCH (đơn vị con nhận xử lý) — tạo 1 bản sao "văn bản đến" (xem javadoc
 * {@code VbdenForwardUnitMessagePayload}, td-application, về phạm vi đơn giản hoá CÓ CHỦ ĐÍCH:
 * KHÔNG đồng bộ user_process/user_coordinate — đơn vị nhận tự phân công lại từ đầu trên bản sao của
 * họ), rồi publish {@code vbden.forward_unit.reply} về tenant GỐC để dọn khỏi hàng đợi
 * {@code unit_process} bên đó (xem {@link VbdenForwardUnitReplyTaskHandler}).
 *
 * <p><b>Bugfix 08/09/2026 (2) — người dùng test thật: "văn thư/người đại diện nhận đang không thấy
 * văn bản ở bất kỳ đâu"</b>: bản sao PHẢI set {@code identifier_creator = targetIdentifier} — field
 * DUY NHẤT mà MỌI view org-scoped của vbden ({@code chophanxuly}/{@code cuatochuc}/
 * {@code phaixulyCuatochuc}... xem {@code ViewQueryService}) dùng để lọc "văn bản CỦA tổ chức tôi"
 * (qua {@code resolveCurrentIdentifier()}) — thiếu field này thì bản sao "vô hình" ở MỌI view dù ghi
 * DB thành công.</p>
 *
 * <p><b>Bổ sung 08/09/2026 (3) — người dùng: "đã thấy trong 'Văn bản đến Cơ quan' nhưng chưa thấy ở
 * trong 'Văn bản đến trong tỉnh' (như văn bản đi phát hành sang)"</b>: NGOÀI {@code identifier_creator}, bản sao CÒN
 * set {@code contact_unit_identifier = targetIdentifier} + {@code document_internet = "1"} — ĐÚNG 2
 * field {@code ViewQueryService#quamangnoiboFilter} (và 3 view anh em {@code lienthong}/
 * {@code dendientu}/{@code denquamang}) dùng để hiển thị "văn bản đến qua mạng nội bộ" — CÙNG 2 field
 * {@code VbdenIncomingDocumentWriter} (module {@code vbdi}) đã dùng cho luồng {@code contact_unit},
 * vì về bản chất văn thư đơn vị đích xử lý văn bản "đến từ cơ quan khác qua kênh điện tử" theo CÙNG 1
 * quy trình dù đến qua {@code vbdi} (gửi chính thức) hay {@code vbden.forward_unit} (chuyển xử lý nội
 * bộ) — cả 2 nên cùng xuất hiện ở màn hình "Qua mạng nội bộ" mà văn thư đã quen dùng. Từ đó, văn thư
 * "Tiếp nhận"/"vào sổ" rồi tự "chuyển xử lý" tiếp cho 1 người đại diện CỤ THỂ tại tenant đích — bước
 * này dùng LẠI nguyên {@code ChuyenXuLyVbdenUseCase} đã có sẵn (không cần code thêm gì riêng cho bản
 * sao này, nó là 1 văn bản {@code vbden} bình thường sau khi đã tồn tại).</p>
 *
 * <p><b>Bổ sung 08/09/2026 (3, tiếp) — người dùng: "thiếu files nữa, nhưng không phải nhân ra mà đọc
 * chung với đơn vị chuyển xử lý luôn"</b>: {@link #insertFileMetadataRefs} tạo 1 dòng
 * {@code file_metadata} MỚI cho MỖI tệp, trỏ THẲNG {@code objectKey}/{@code bucketName} HIỆN CÓ của
 * văn bản gốc — KHÔNG gọi MinIO {@code CopyObjectArgs} nào (khác hẳn quyết định của vbdi/
 * {@code PrepareVbdiDeliveryFilesUseCase}, xem javadoc {@code GetDocumentFileRefsUseCase} phía
 * td-application) — "đọc chung" 1 object vật lý duy nhất, không nhân bản dung lượng.</p>
 *
 * <p><b>Giai đoạn 1/2 đã port</b> — xem javadoc {@code ChuyenXuLyVbdenUseCase
 * #publishForwardUnitIfCrossTenant} (td-application) về gap giai đoạn 2 (revoke cascade khi "Lấy lại
 * VB"/"Thu hồi" tác động lên bản sao này) — CHƯA port, chờ xác nhận giai đoạn 1 hoạt động đúng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VbdenForwardUnitTaskHandler implements TaskHandler {

    private static final String DOCUMENT_TYPE_VBDEN = "vbden";
    /** = {@code com.td.application.vbden.VbdenStatusService.STATUS_MOI} (giá trị lặp lại, không import). */
    private static final String STATUS_MOI = "0";
    private static final DateTimeFormatter DATE_TIME_PATTERN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final WorkerProperties props;
    private final TenantDataSourceManager tenantDataSources;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RabbitPublisher> rabbitPublisher;

    @Override
    public String taskType() {
        return VbdenTaskConstants.TASK_FORWARD_UNIT;
    }

    @Override
    public UnitResult execute(TaskContext context, JsonNode payload) {
        if (!props.getTasks().getVbden().isEnabled()) {
            return UnitResult.failure("worker.tasks.vbden.enabled=false");
        }
        final VbdenForwardUnitPayload p;
        try {
            p = objectMapper.treeToValue(payload, VbdenForwardUnitPayload.class);
        } catch (Exception e) {
            return UnitResult.failure("payload vbden.forward_unit không hợp lệ: " + e.getMessage());
        }
        if (p == null || p.getOriginDocumentId() == null || p.getOriginDocumentId().isBlank()
                || p.getOriginTenantId() == null || p.getOriginTenantId().isBlank()
                || p.getTargetIdentifier() == null || p.getTargetIdentifier().isBlank()) {
            return UnitResult.failure(
                "payload vbden.forward_unit thiếu field bắt buộc (originDocumentId/originTenantId/targetIdentifier)");
        }

        log.info("vbden.forward_unit: originDocumentId={}, originTenant={}, target={} (tenant hiện tại {})",
            p.getOriginDocumentId(), p.getOriginTenantId(), p.getTargetIdentifier(), context.tenantId());

        var dsOpt = tenantDataSources.getDataSource(context.tenantId());
        if (dsOpt.isEmpty()) {
            return UnitResult.failure(
                "Không có connection pool cho tenant " + context.tenantId()
                    + " (kiểm tra control-plane tenants registry / đường mạng tới DB tenant)");
        }
        DataSource ds = dsOpt.get();
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        // Bo sung 08/09/2026 (3): sinh id MOI cho MOI tep TRUOC (dung chung giua buoc ghi dynamic_
        // fields.files va buoc INSERT file_metadata - 2 noi PHAI khop 1 gia tri duy nhat, xem javadoc
        // lop). Chi tao 1 lan, truyen vao ca 2 ham - KHONG dung field instance (handler la Spring
        // singleton, khong an toan giu state per-request tren field).
        List<PreparedFile> preparedFiles = prepareFiles(p.getFiles());

        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        UUID createdId = tx.execute(status -> {
            UUID id = insertForwardedCopy(jdbc, p, preparedFiles);
            insertFileMetadataRefs(jdbc, id, preparedFiles);
            return id;
        });
        if (createdId == null) {
            return UnitResult.failure("Khong tao duoc ban sao van ban den tai tenant " + context.tenantId());
        }
        log.info("Đã tạo bản sao văn bản đến id={} tại tenant {} (từ tenant {}, văn bản gốc {})",
            createdId, context.tenantId(), p.getOriginTenantId(), p.getOriginDocumentId());
        publishForwardUnitReply(context, p, createdId);
        return UnitResult.success();
    }

    /** 1 tệp đã sẵn sàng để ghi — {@code id} MỚI SINH, dùng chung cho {@code dynamic_fields.files[].file_id}
     * (bên dưới) lẫn cột {@code id} khi INSERT {@code file_metadata} ({@link #insertFileMetadataRefs}). */
    private record PreparedFile(UUID id, String objectKey, String bucketName, String originalFilename,
                                 Long size, String contentType, String fileExtension) {
    }

    private List<PreparedFile> prepareFiles(List<VbdenForwardUnitPayload.VbdenFileRef> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<PreparedFile> prepared = new ArrayList<>();
        for (var file : files) {
            if (file == null || file.getObjectKey() == null || file.getObjectKey().isBlank()) {
                continue;
            }
            prepared.add(new PreparedFile(UUID.randomUUID(), file.getObjectKey(), file.getBucketName(),
                file.getOriginalFilename(), file.getSize(), file.getContentType(), file.getFileExtension()));
        }
        return prepared;
    }

    private UUID insertForwardedCopy(JdbcTemplate jdbc, VbdenForwardUnitPayload p, List<PreparedFile> preparedFiles) {
        UUID id = UUID.randomUUID();
        String title = (p.getSubject() == null || p.getSubject().isBlank())
            ? "Văn bản đến (chuyển xử lý) - " + p.getOriginDocumentId() : p.getSubject();
        Map<String, Object> fields = new LinkedHashMap<>();
        // Bugfix 08/09/2026 (2, bao loi nguoi dung: "van thu/nguoi dai dien nhan dang khong thay
        // van ban o bat ky dau"): "identifier_creator" la field DUY NHAT ma MOI view org-scoped cua
        // vbden (chophanxuly/cuatochuc/phaixulyCuatochuc... xem ViewQueryService) dung de loc "van
        // ban CUA to chuc toi" (qua resolveCurrentIdentifier()) - thieu field nay, ban sao ton tai
        // trong DB nhung KHONG view nao khop duoc, "vo hinh" voi moi nguoi o tenant dich. Gia tri
        // MOI (= targetIdentifier, don vi DICH) - KHONG phai forward tu payload, dai dien "ban sao
        // nay gio thuoc to chuc dich".
        fields.put("identifier_creator", p.getTargetIdentifier());
        // Bo sung 08/09/2026 (3, xem javadoc lop): 2 field them de ban sao CUNG hien thi o man hinh
        // "Qua mang noi bo" (quamangnoiboFilter/lienthongFilter/dendientuFilter, ViewQueryService) -
        // DUNG 2 field VbdenIncomingDocumentWriter (module vbdi) da dung cho contact_unit.
        fields.put("contact_unit_identifier", p.getTargetIdentifier());
        fields.put("document_internet", "1");
        fields.put("origin_document_id", p.getOriginDocumentId());
        fields.put("origin_tenant_id", p.getOriginTenantId());
        putIfNotBlank(fields, "code", p.getCode());
        putIfNotBlank(fields, "promulgation_date", p.getPromulgationDate());
        putIfNotBlank(fields, "forwarded_by", p.getActingUsername());
        putIfNotBlank(fields, "forwarded_opinion", p.getOpinion());
        // Bo sung 08/09/2026 (2, xem javadoc VbdenForwardUnitMessagePayload): forward nguyen ven -
        // deadline_date/unit_in_charge(_identifier) van la han xu ly/co quan ban hanh GOC.
        putIfNotBlank(fields, "deadline_date", p.getDeadlineDate());
        putIfNotBlank(fields, "unit_in_charge", p.getUnitInCharge());
        putIfNotBlank(fields, "unit_in_charge_identifier", p.getUnitInChargeIdentifier());
        fields.put("received_date", LocalDateTime.now().format(DATE_TIME_PATTERN));
        // Bo sung 08/09/2026 (3, xem javadoc lop - files): shape {name,size,type,file_id} KHOP
        // VbdiFilesJsonbSync/GetVbdiDetailUseCase (td-infrastructure) dang dung, de FE hien thi duoc
        // NGAY.
        if (!preparedFiles.isEmpty()) {
            List<Map<String, Object>> filesJson = new ArrayList<>();
            for (PreparedFile f : preparedFiles) {
                Map<String, Object> fileEntry = new LinkedHashMap<>();
                fileEntry.put("name", f.originalFilename());
                fileEntry.put("size", f.size());
                fileEntry.put("type", f.fileExtension());
                fileEntry.put("file_id", f.id());
                filesJson.add(fileEntry);
            }
            fields.put("files", filesJson);
        }
        String dynamicFieldsJson;
        try {
            dynamicFieldsJson = objectMapper.writeValueAsString(fields);
        } catch (Exception ex) {
            dynamicFieldsJson = "{}";
        }
        jdbc.update("""
                INSERT INTO documents
                 (id, title, document_type, status, tags_json, attributes_json, metadata_json,
                  dynamic_fields, version_no, created_on)
                VALUES (?, ?, ?, ?, '[]', '{}', '{}', ?::jsonb, 1, CURRENT_TIMESTAMP)
                """,
            id, title, DOCUMENT_TYPE_VBDEN, STATUS_MOI, dynamicFieldsJson);
        return id;
    }

    /**
     * Bổ sung 08/09/2026 (3, xem javadoc lớp): INSERT 1 dòng {@code file_metadata} MỚI cho MỖI tệp,
     * {@code file_path}/{@code bucket_name} trỏ THẲNG object HIỆN CÓ của văn bản gốc (KHÔNG copy MinIO
     * — "đọc chung", đúng yêu cầu người dùng). {@code stored_filename} dùng {@code f.id()} (MỚI sinh
     * riêng cho mỗi lần forward) để KHÔNG BAO GIỜ đụng UNIQUE constraint dù nhiều tenant/nhiều lượt
     * forward cùng tham chiếu 1 object MinIO vật lý — cùng bugfix đã áp dụng ở
     * {@code VbdenIncomingDocumentWriter#insertFileMetadata} (module vbdi).
     *
     * <p><b>Bổ sung 08/09/2026 (4) — người dùng: "không cho xóa đính kèm gốc"</b>: {@code is_shared =
     * TRUE} (migration {@code V1.0.50}) — dòng này CHỈ tham chiếu, KHÔNG sở hữu object vật lý (văn bản
     * gốc ở tenant KHÁC mới là chủ sở hữu thật) — {@code DeleteFileUseCaseImpl} (td-infrastructure)
     * đọc cờ này để CHẶN xóa hoàn toàn, tránh xóa nhầm object vật lý làm mất tệp của văn bản gốc.</p>
     */
    private void insertFileMetadataRefs(JdbcTemplate jdbc, UUID documentId, List<PreparedFile> preparedFiles) {
        for (PreparedFile f : preparedFiles) {
            String extension = f.fileExtension();
            String storedFilename = f.id() + (extension == null || extension.isBlank() ? "" : "." + extension);
            jdbc.update("""
                    INSERT INTO file_metadata
                     (id, document_id, original_filename, stored_filename, file_path, file_size,
                      content_type, file_extension, file_category, bucket_name, uploaded_at, is_public,
                      is_shared)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DOCUMENT', ?, CURRENT_TIMESTAMP, FALSE, TRUE)
                    """,
                f.id(), documentId, f.originalFilename(), storedFilename, f.objectKey(), f.size(),
                f.contentType(), f.fileExtension(), f.bucketName());
        }
    }

    private void putIfNotBlank(Map<String, Object> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }

    /**
     * Port bước gửi mail {@code document_process_unit} NGƯỢC về tenant gốc (Domino:
     * {@code SendRecievedDocumentInProcessReplyToSender}) — cùng cơ chế envelope/routing đã dùng cho
     * {@code vbdi.reply} ({@code VbdiDeliveryTaskHandler#publishArrivedReply}).
     */
    private void publishForwardUnitReply(TaskContext context, VbdenForwardUnitPayload p, UUID createdDocumentId) {
        if (!props.getRabbit().isEnabled()) {
            return;
        }
        try {
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("originDocumentId", p.getOriginDocumentId());
            reply.put("targetIdentifier", p.getTargetIdentifier());
            reply.put("createdDocumentId", createdDocumentId.toString());
            reply.put("createdTenantId", context.tenantId());
            // Bo sung 08/09/2026 (yeu cau nguoi dung: phan hoi trang thai "Da den" vao qua trinh xu
            // ly) - xem VbdenForwardUnitReplyPayload#targetName.
            reply.put("targetName", p.getTargetName());

            String messageId = UUID.randomUUID().toString();
            Map<String, Object> unit = new LinkedHashMap<>();
            unit.put("unitId", UUID.randomUUID().toString());
            unit.put("tenantId", p.getOriginTenantId());
            unit.put("payload", reply);
            unit.put("target", Map.of());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("messageId", messageId);
            envelope.put("taskType", VbdenTaskConstants.TASK_FORWARD_UNIT_REPLY);
            envelope.put("correlationId", context.correlationId() == null ? "" : context.correlationId());
            envelope.put("source", "td-worker-vbden-forward-unit");
            envelope.put("createdAt", Instant.now().toString());
            envelope.put("headers", Map.of());
            envelope.put("units", java.util.List.of(unit));

            String json = objectMapper.writeValueAsString(envelope);
            String routingKey = "worker." + VbdenTaskConstants.TASK_FORWARD_UNIT_REPLY.replace('.', '_');
            rabbitPublisher.ifAvailable(rp -> rp.publish(props.getRabbit().getExchange(), routingKey, json));
            log.info("Đã publish xác nhận 'forward_unit' (originDocumentId={}) tới tenant {} qua routing {}",
                p.getOriginDocumentId(), p.getOriginTenantId(), routingKey);
        } catch (Exception e) {
            log.warn("Publish xác nhận 'forward_unit' thất bại: {}", e.getMessage());
        }
    }
}
