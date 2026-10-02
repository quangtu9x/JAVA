package com.td.worker.tasks.vbdi;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Task {@code vbdi.revoke} — port {@code RevokeDocumentOut.java}/{@code RevokeAllDocumentOut.java}
 * (Domino, sendType {@code thuhoivanban}/{@code laylaivanban}/{@code thuhoitoanbo}), xử lý ở nơi
 * NHẬN đúng như {@code StatusProcess.ExecuteVBDIBussiness() -> ActionRevokeDocument()} (đọc
 * {@code docs/agent_domino/io/BeforeMailin.ja/StatusProcess.java} thật).
 *
 * <p><b>Bổ sung 08/09/2026 (người dùng báo lỗi thật, đối chiếu log td-worker)</b>: trước bản vá này,
 * {@code AddVbdiSendLogUseCase} publish CẢ 3 sendType "thu hồi"/"lấy lại" qua CÙNG taskType
 * {@code vbdi.delivery} như 1 lượt GỬI MỚI — {@link VbdiDeliveryTaskHandler} xử lý y hệt: dedup theo
 * {@code sendLogId} (LUÔN mới, không trùng sendLogId của lượt gửi gốc — không dedup được gì) rồi TẠO
 * THÊM 1 văn bản đến "chờ vào sổ" MỚI + phản hồi "Đã đến" — hoàn toàn SAI: văn bản đến GỐC vẫn còn
 * nguyên trong danh sách của đơn vị nhận (đúng lỗi người dùng báo), có khi còn nhân đôi vì tạo thêm 1
 * bản ghi mới. Domino gốc gửi mail form {@code feedback_DocumentOut} (KHÔNG PHẢI
 * {@code documentin}/{@code SendDocument.java}) cho hành động thu hồi — {@code ActionRevokeDocument}
 * TÌM văn bản đến ĐÃ CÓ (theo {@code id_DocumentOut} = id văn bản đi gốc) và đánh dấu
 * {@code isRevoke=1}/{@code Reply=1}/{@code Form="DocumentIn_Deleted"} (ẩn khỏi MỌI view lọc
 * {@code Reply!="1"} — {@code quamangnoibo}/{@code lienthong}/{@code dendientu}/{@code denquamang},
 * xem {@code ViewQueryService}), rồi {@code SendReplyToSender} 1 phản hồi {@code status_code="101"}
 * ("Đã lấy lại") NGƯỢC về đơn vị gửi — KHÔNG tạo văn bản mới bao giờ. Handler này port ĐÚNG 2 bước đó.
 *
 * <p><b>Vì sao chỉ cần {@code UPDATE ... WHERE dynamic_fields->>'parentid_send' = documentId}</b>:
 * {@code VbdenIncomingDocumentWriter.buildDynamicFieldsJson} luôn ghi {@code parentid_send} = id văn
 * bản đi GỐC (= {@code id_DocumentOut} Domino, xem javadoc field đó) lên MỌI văn bản đến tự tạo cho
 * nhóm {@code contact_unit} — khớp CHÍNH XÁC điều kiện Domino {@code FTSearch(... id_DocumentOut =
 * documentId)}. {@code documents.dynamic_fields.reply="1"} (viết thường, đúng quy ước
 * {@code DynamicDocumentFieldsService}) là field {@code ViewQueryService#quamangnoiboFilter} (và 3
 * view anh em) đã dùng SẴN để loại trừ — set field này là ĐỦ để văn bản biến mất khỏi danh sách hoạt
 * động, không cần soft-delete {@code documents} (Domino cũng KHÔNG xoá thật — {@code docIn.remove}
 * bị comment — chỉ đổi {@code Form} để loại khỏi view, văn bản vẫn truy vấn được trực tiếp).
 *
 * <p><b>{@code coordinator}/{@code external_unit}</b> (chưa từng tạo văn bản đến thật — chỉ dedup):
 * câu UPDATE tự nhiên khớp 0 dòng (không có {@code parentid_send} nào để so khớp) — no-op an toàn,
 * KHÔNG publish phản hồi (đúng Domino: {@code SendReplyToSender} chỉ gọi khi {@code docs.getCount() > 0}).
 * <b>Cập nhật 21/09/2026 — {@code internal_unit} KHÔNG còn thuộc nhóm "no-op" này</b> (từ 18/09/2026,
 * nhóm này CŨNG tạo văn bản đến thật, xem {@link VbdiDeliveryTaskHandler}) — cần thêm điều kiện lọc
 * theo {@code recipientIdentifier} qua {@link #revokeIncomingDocuments}, KHÔNG thể chỉ dựa vào
 * {@code parentid_send} như {@code contact_unit} (nhiều đơn vị nội bộ CHIA SẺ 1 tenant — xem javadoc
 * phương thức đó).
 *
 * <p><b>Bổ sung 08/09/2026 (3, yêu cầu người dùng "lấy lại tận gốc" — 2 khoảng trống phát hiện thêm
 * sau khi bugfix (2) đã xác nhận đúng cho ca đơn giản nhất)</b>:</p>
 * <ol>
 *   <li><b>Nhiều bản ghi "văn bản đến" cho CÙNG 1 văn bản đi</b> (gửi lại/gửi bổ sung nhiều lần trước
 *   khi bấm "Lấy lại" MỘT LẦN — mỗi lần gửi trước đây tạo 1 bản ghi RIÊNG, xem
 *   {@code VbdiDeliveryTaskHandler#execute}/{@code markArrived}, dedup theo {@code sendLogId} chứ
 *   KHÔNG theo {@code documentId}): câu UPDATE KHÔNG giới hạn {@code LIMIT} nên vốn đã cập nhật MỌI
 *   bản ghi khớp trong 1 lần — giữ nguyên, chỉ cần đảm bảo điều kiện khớp KHÔNG bị thu hẹp sai (xem
 *   điểm 2).</li>
 *   <li><b>Đã "vào sổ" ({@code LuuVanBanDenUseCase}, R4 {@code loaiKhoiDanhSachChoVaoSo})</b>: hành
 *   động "vào sổ" XOÁ {@code contact_unit_identifier} khỏi văn bản gốc (loại khỏi hàng đợi "chờ vào
 *   sổ", đúng thiết kế) — điều kiện cũ {@code AND dynamic_fields->>'contact_unit_identifier' =
 *   recipientIdentifier} vì vậy KHÔNG còn khớp được văn bản ĐÃ vào sổ (0 dòng, revoke coi như "not
 *   found" một cách ÂM THẦM — lỗi thật đã xác nhận qua đối chiếu DB tenant {@code snnmt} trực tiếp với
 *   người dùng). {@code parentid_send} KHÔNG nằm trong danh sách field bị "vào sổ" đụng tới (R1: merge
 *   field khác giữ nguyên) nên vẫn ổn định — bỏ hẳn điều kiện {@code contact_unit_identifier}, chỉ còn
 *   {@code parentid_send} (đã đủ an toàn: Worker đang chạy ĐÚNG tenant DB của đơn vị nhận qua
 *   {@code TenantDataSourceManager}, database-per-tenant — không có văn bản của tổ chức KHÁC lẫn vào
 *   cùng bảng {@code documents}).</li>
 *   <li><b>Đã "vào sổ" CÒN kéo theo 1 bản ghi con {@code vbden_so}</b> ({@code documentType="vbden_so"},
 *   {@code parentid} = id văn bản gốc — xem {@code TiepNhanVbdenUseCase#REGISTER_ENTRY_DOCUMENT_TYPE}/
 *   {@code GetSoVanBanDenUseCase}, tương đương database RIÊNG {@code vbden_so.nsf} của Domino). Domino
 *   thật ({@code ActionRevokeDocument}) CŨNG cập nhật bản ghi này — tra qua view
 *   {@code sovanban_by_idvbden} (khoá {@code parentid}) rồi đánh dấu Y HỆT bản ghi gốc
 *   ({@code isRevoke=1}/{@code Reply=1}/đổi {@code Form}, xem {@code sovanban_bithuhoi} — view "Danh
 *   sách đã thu hồi" lọc {@code isrevoke="1"}). {@link #revokeSoVanBanEntries} port đúng bước này —
 *   ĐÃ BỔ SUNG THÊM (Java không có, cần làm cùng đợt): {@code GetSoVanBanDenUseCase} trước đó KHÔNG hề
 *   loại trừ {@code reply="1"} khỏi danh sách "Sổ văn bản đến" (đối chiếu Domino: view {@code sovanban}
 *   cũng chỉ lọc {@code form="DocumentIn"}, loại trừ THẬT SỰ đến từ việc đổi {@code Form} sang có hậu
 *   tố "Deleted" — Java không có field "Form" mutable tương đương, phải thêm {@code excludeFieldValues
 *   ("reply", "1")} tường minh, xem {@code GetSoVanBanDenUseCase#buildFilter}) — nếu không, đánh dấu
 *   xong vbden_so vẫn hiển thị nguyên trong danh sách, vô nghĩa.</li>
 * </ol>
 *
 * <p><b>Cập nhật 08/09/2026 — gap ở trên ĐÃ ĐƯỢC PORT</b> (không còn là "CHƯA PORT"): trường hợp "văn
 * bản đến cơ quan vào sổ, [sau đó] phân xử lý cho đơn vị (đơn vị có văn thư riêng) vào sổ riêng" hoá
 * ra là cross-tenant thật (xác nhận với người dùng "Có - tenant/DB riêng, như Domino") — đã port thành
 * module RIÊNG {@code com.td.worker.tasks.vbden} (KHÔNG chung gói với {@code vbdi} dù cùng đụng
 * {@code document_type="vbden"} — 2 cơ chế khác bản chất nghiệp vụ, xem javadoc
 * {@code VbdenTaskConstants}): {@code vbden.forward_unit} (port {@code DocumentInProcess.java
 * #copyDocumentProcessToDocumentIn()}) + {@code vbden.forward_unit.reply} (port
 * {@code DocumentInProcessUnit.java#copyToDocumentIn()}) + {@code vbden.forward_unit.revoke} (giai
 * đoạn 2 — cascade "Lấy lại VB"/"Thu hồi" NỘI BỘ của vbden, {@code LayLaiVanBanUseCase}/
 * {@code ThuHoiVanBanUseCase}, sang bản sao ở tenant đích qua {@code forwarded_units}). Đây là 1
 * NHÁNH REVOKE HOÀN TOÀN RIÊNG với {@code vbdi.revoke} (lớp này) — không đụng gì tới nhau, vì
 * {@code vbdi.revoke} chỉ xử lý văn bản đến TẠO TỪ vbdi (giao thức gửi chính thức giữa 2 cơ quan qua
 * {@code parentid_send}), còn {@code vbden.forward_unit.revoke} xử lý bản sao tạo bởi
 * {@code vbden.forward_unit} (chuyển xử lý nội bộ, không qua vbdi).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VbdiRevokeTaskHandler implements TaskHandler {

    private static final String DOCUMENT_TYPE_VBDEN = "vbden";
    /** = {@code com.td.application.vbden.TiepNhanVbdenUseCase.REGISTER_ENTRY_DOCUMENT_TYPE} - lặp lại
     * GIÁ TRỊ, KHÔNG import (2 module độc lập, xem javadoc lớp điểm 3). */
    private static final String REGISTER_ENTRY_DOCUMENT_TYPE = "vbden_so";
    private static final DateTimeFormatter DATE_TIME_PATTERN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final WorkerProperties props;
    private final TenantDataSourceManager tenantDataSources;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RabbitPublisher> rabbitPublisher;

    @Override
    public String taskType() {
        return VbdiTaskConstants.TASK_REVOKE;
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
            return UnitResult.failure("payload vbdi.revoke không hợp lệ: " + e.getMessage());
        }
        if (p == null || p.getSendLogId() == null || p.getSendLogId().isBlank()
                || p.getDocumentId() == null || p.getDocumentId().isBlank()
                || p.getRecipientGroup() == null || p.getRecipientIdentifier() == null) {
            return UnitResult.failure(
                "payload vbdi.revoke thiếu field bắt buộc (sendLogId/documentId/recipientGroup/recipientIdentifier)");
        }

        log.info("vbdi.revoke: documentId={}, sendLogId={}, sendType={}, group={}, recipient={} (from tenant {})",
            p.getDocumentId(), p.getSendLogId(), p.getSendType(), p.getRecipientGroup(),
            p.getRecipientIdentifier(), p.getSenderTenantId());

        var dsOpt = tenantDataSources.getDataSource(context.tenantId());
        if (dsOpt.isEmpty()) {
            return UnitResult.failure(
                "Không có connection pool cho tenant " + context.tenantId()
                    + " (kiểm tra control-plane tenants registry / đường mạng tới DB tenant)");
        }
        DataSource ds = dsOpt.get();
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        // Bugfix 08/09/2026 (2, người dùng báo lỗi thật - đối chiếu trực tiếp DB tenant snnmt: UPDATE
        // báo "Đã lấy lại 1 văn bản" nhưng dynamic_fields.reply/is_revoke VẪN null sau đó): CÙNG
        // nguyên nhân gốc đã sửa ở VbdiDeliveryTaskHandler/VbdiReplyTaskHandler (06-07/09/2026) -
        // worker.tenant.pool-auto-commit MẶC ĐỊNH false cho MỌI pool tenant - jdbc.update() chạy
        // NGOÀI 1 transaction Spring quản lý KHÔNG được commit tường minh, HikariCP tự ROLLBACK khi
        // trả connection về pool (an toàn mặc định, tránh rò rỉ transaction dở dang) - UPDATE báo
        // đúng rowcount NGAY LÚC CHẠY (trong transaction đang mở) nhưng KHÔNG BAO GIỜ thực sự persist.
        // Đã bỏ sót bọc TransactionTemplate khi viết handler này (2 handler kia đã áp dụng đúng từ
        // đầu) - sửa bằng cách bọc UPDATE trong 1 transaction tường minh, y hệt mẫu đã dùng.
        var txManager = new DataSourceTransactionManager(ds);
        var tx = new TransactionTemplate(txManager);
        // Bổ sung 08/09/2026 (3, xem javadoc lớp điểm 3): CẢ 2 bước (văn bản gốc + bản ghi vbden_so
        // liên kết) nằm TRONG CÙNG 1 transaction (1 lần gọi tx.execute duy nhất, KHÔNG phải 2 lần
        // riêng biệt — 2 lần riêng sẽ là 2 transaction ĐỘC LẬP, mất tính atomic) - lỗi ở bước nào cũng
        // ROLLBACK CẢ HAI, retry sau coi như CHƯA xử lý, đúng nguyên tắc "tránh trạng thái nửa vời"
        // đã dùng xuyên suốt module vbdi (vd VbdiDeliveryTaskHandler#execute).
        int[] counts = tx.execute(status -> {
            List<UUID> revokedRootIds = revokeIncomingDocuments(jdbc, p);
            int soCount = revokedRootIds.isEmpty() ? 0 : revokeSoVanBanEntries(jdbc, revokedRootIds);
            return new int[] {revokedRootIds.size(), soCount};
        });
        int rootCount = counts == null ? 0 : counts[0];
        int soCount = counts == null ? 0 : counts[1];
        if (rootCount > 0) {
            log.info("Đã lấy lại {} văn bản đến gốc + {} bản ghi sổ văn bản đến (parentid_send={}) "
                + "tại tenant {} cho recipient={}",
                rootCount, soCount, p.getDocumentId(), context.tenantId(), p.getRecipientIdentifier());
            publishRevokedReply(context, p);
        } else {
            log.info("vbdi.revoke: không tìm thấy văn bản đến nào khớp parentid_send={} tại tenant {} "
                + "(có thể chưa từng tạo văn bản đến thật cho nhóm {} - chỉ dedup)",
                p.getDocumentId(), context.tenantId(), p.getRecipientGroup());
        }
        return UnitResult.success();
    }

    /**
     * Port {@code ActionRevokeDocument} (bước 1/3 — xem javadoc lớp điểm 1/2): đánh dấu MỌI văn bản
     * đến khớp {@code id_DocumentOut} là ĐÃ LẤY LẠI — KHÔNG giới hạn 1 dòng (có thể có NHIỀU bản ghi
     * nếu văn bản đã được gửi lại/gửi bổ sung nhiều lần trước khi "Lấy lại" MỘT LẦN), và KHÔNG đòi hỏi
     * {@code contact_unit_identifier} còn nguyên (đã có thể bị "vào sổ" xoá — xem javadoc lớp điểm 2).
     * Domino không kiểm tra trạng thái hiện tại trước khi ghi đè (nhánh check bị comment trong bản
     * gốc) — port unconditional tương tự, an toàn để chạy lại nhiều lần (idempotent ở mức field value,
     * dù {@code delete_date} sẽ đổi mỗi lần). {@code RETURNING id} để bước 2 biết CHÍNH XÁC những văn
     * bản gốc nào vừa bị lấy lại, tránh phải query lại lần 2.
     *
     * <p><b>Bổ sung 21/09/2026 (bugfix người dùng: "kiểm tra Lấy lại văn bản đã hoàn thiện cho ĐƠN VỊ
     * NỘI BỘ và CÁ NHÂN chưa")</b>: điều kiện CHỈ {@code parentid_send} (không có định danh nơi nhận)
     * vốn AN TOÀN cho {@code contact_unit} vì mỗi tenant chỉ nhận đúng 1 bản ghi/1 văn bản đi (mỗi
     * cơ quan {@code contact_unit} có tenant RIÊNG — xem
     * {@code AddVbdiSendLogUseCase#resolveTargetTenantId}). {@code internal_unit} (bổ sung 18/09/2026,
     * xem javadoc {@code VbdiDeliveryTaskHandler}) KHÁC HẲN: NHIỀU đơn vị nội bộ CÙNG CHIA SẺ 1 tenant
     * (tenant của CHÍNH cơ quan gửi) — nếu văn bản gửi tới CẢ "Phòng A" lẫn "Phòng B" (2 bản ghi văn
     * bản đến RIÊNG trong CÙNG tenant, cùng {@code parentid_send}), "Lấy lại" chỉ chọn Phòng A sẽ khớp
     * điều kiện CŨ (chỉ {@code parentid_send}) và LẤY LẠI NHẦM CẢ Phòng B — lỗi thật, chưa có test nào
     * bắt được (test cũ chỉ dùng {@code contact_unit}). Sửa: với {@code internal_unit}, thêm điều kiện
     * khớp ĐÚNG đơn vị qua {@code unit_process} ({@code VbdenIncomingDocumentWriter#putGroupSpecificFields}
     * ghi mảng 1 phần tử = identifier của CHÍNH đơn vị đó lúc tạo) — kiểm tra CẢ
     * {@code unit_process_registered} (field đích {@code LuuVanBanDenUseCase#loaiKhoiDanhSachChoVaoSo}
     * di chuyển identifier sang khi "vào sổ", xoá khỏi {@code unit_process} — cùng lý do đã bỏ điều
     * kiện {@code contact_unit_identifier} ở điểm 2, KHÔNG được chỉ kiểm tra 1 trong 2 field). Các
     * nhóm khác ({@code contact_unit}/{@code external_unit}/{@code coordinator}) GIỮ NGUYÊN hành vi cũ
     * (không có {@code unit_process}, thêm điều kiện này vào sẽ luôn khớp 0 dòng — không đổi gì).
     */
    private List<UUID> revokeIncomingDocuments(JdbcTemplate jdbc, VbdiDeliveryPayload p) {
        String deleteDate = LocalDateTime.now().format(DATE_TIME_PATTERN);
        boolean scopeToInternalUnit = VbdiTaskConstants.GROUP_INTERNAL_UNIT.equals(p.getRecipientGroup());
        String recipientScopeSql = scopeToInternalUnit
            ? " AND (jsonb_exists(COALESCE(dynamic_fields->'unit_process', '[]'::jsonb), ?)"
                + " OR jsonb_exists(COALESCE(dynamic_fields->'unit_process_registered', '[]'::jsonb), ?))"
            : "";
        List<Object> args = new java.util.ArrayList<>(
            List.of(deleteDate, DOCUMENT_TYPE_VBDEN, p.getDocumentId()));
        if (scopeToInternalUnit) {
            args.add(p.getRecipientIdentifier());
            args.add(p.getRecipientIdentifier());
        }
        return jdbc.query("""
                UPDATE documents
                SET dynamic_fields = COALESCE(dynamic_fields, '{}'::jsonb) || jsonb_build_object(
                        'reply', '1', 'is_revoke', '1', 'delete_date', ?::text)
                WHERE document_type = ?
                  AND dynamic_fields->>'parentid_send' = ?
                """ + recipientScopeSql + """

                RETURNING id
                """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            args.toArray());
    }

    /**
     * Port {@code ActionRevokeDocument} (bước 2/3 — xem javadoc lớp điểm 3): tương đương Domino tra
     * view {@code sovanban_by_idvbden} (khoá {@code parentid} = UNID văn bản gốc, trong database RIÊNG
     * {@code vbden_so.nsf}) rồi đánh dấu Y HỆT bản ghi gốc. Java KHÔNG có database vật lý riêng cho
     * "sổ" (dùng CHUNG bảng {@code documents}, phân biệt qua {@code document_type="vbden_so"} — xem
     * {@code TiepNhanVbdenUseCase#REGISTER_ENTRY_DOCUMENT_TYPE}) nên chỉ cần thêm 1 điều kiện
     * {@code document_type} vào CÙNG kiểu UPDATE. Lặp theo TỪNG root id (thay vì 1 câu {@code IN (...)}
     * với số lượng tham số động) — đơn giản, đủ nhanh vì số bản ghi gốc mỗi lần thường chỉ 1 (hiếm khi
     * quá vài đơn vị), không cần tối ưu số round-trip.
     */
    private int revokeSoVanBanEntries(JdbcTemplate jdbc, List<UUID> rootIds) {
        String deleteDate = LocalDateTime.now().format(DATE_TIME_PATTERN);
        int total = 0;
        for (UUID rootId : rootIds) {
            total += jdbc.update("""
                    UPDATE documents
                    SET dynamic_fields = COALESCE(dynamic_fields, '{}'::jsonb) || jsonb_build_object(
                            'reply', '1', 'is_revoke', '1', 'delete_date', ?::text)
                    WHERE document_type = ?
                      AND dynamic_fields->>'parentid' = ?
                    """,
                deleteDate, REGISTER_ENTRY_DOCUMENT_TYPE, rootId.toString());
        }
        return total;
    }

    /**
     * Port {@code SendReplyToSender} (bước 2/2, gọi TỪ BÊN TRONG {@code ActionRevokeDocument} — xem
     * javadoc lớp): {@code status_code="101"}/{@code status_description="Đã lấy lại"} — CÙNG cơ chế
     * broker {@code vbdi.reply} đã có sẵn (xem {@link VbdiDeliveryTaskHandler#publishArrivedReply},
     * đối chiếu bugfix routing 07/09/2026 (4) đã áp dụng ở đó — routing key
     * {@code "worker." + taskType.replace('.', '_')}). {@code unitId} = {@code sendLogId} của CHÍNH
     * yêu cầu thu hồi này (KHÔNG random) — cùng lý do {@code publishArrivedReply}: unit của
     * {@code vbdi.revoke} này chỉ chạy tới thành công ĐÚNG 1 lần (checkpoint), nên cố định unitId theo
     * sendLogId giúp {@link VbdiReplyTaskHandler} tự dedup đúng nếu message này (hiếm) bị redeliver
     * trước khi checkpoint DONE kịp ghi.
     */
    private void publishRevokedReply(TaskContext context, VbdiDeliveryPayload p) {
        if (!props.getRabbit().isEnabled() || p.getSenderTenantId() == null || p.getSenderTenantId().isBlank()) {
            return;
        }
        try {
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("sendLogId", p.getSendLogId());
            reply.put("documentId", p.getDocumentId());
            reply.put("statusCode", VbdiTaskConstants.STATUS_101_DA_LAY_LAI);
            reply.put("statusLabel", VbdiTaskConstants.LABEL_DA_LAY_LAI);
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
            envelope.put("source", "td-worker-vbdi-revoke");
            envelope.put("createdAt", Instant.now().toString());
            envelope.put("headers", Map.of());
            envelope.put("units", java.util.List.of(unit));

            String json = objectMapper.writeValueAsString(envelope);
            String routingKey = "worker." + VbdiTaskConstants.TASK_REPLY.replace('.', '_');
            rabbitPublisher.ifAvailable(rp -> rp.publish(props.getRabbit().getExchange(), routingKey, json));
            log.info("Đã publish phản hồi 'Đã lấy lại' (sendLog={}) tới tenant {} qua routing {}",
                p.getSendLogId(), p.getSenderTenantId(), routingKey);
        } catch (Exception e) {
            // Phản hồi không gửi được KHÔNG làm FAIL unit gốc (văn bản đến đã bị lấy lại thành công ở
            // trên - reply gửi lại được sau, best-effort giống publishArrivedReply).
            log.warn("Publish phản hồi 'Đã lấy lại' thất bại: {}", e.getMessage());
        }
    }
}
