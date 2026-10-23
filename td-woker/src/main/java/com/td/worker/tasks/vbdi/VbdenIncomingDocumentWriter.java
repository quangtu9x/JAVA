package com.td.worker.tasks.vbdi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.infrastructure.fileregistry.SharedFileRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tạo THẬT 1 bản ghi "văn bản đến chờ vào sổ" khi {@code vbdi.delivery} tới nơi nhận
 * {@code contact_unit} (cơ quan/đơn vị KHÁC thuộc tỉnh) — thay vì chỉ ghi dedup như trước (xem
 * javadoc {@link VbdiDeliveryTaskHandler}).
 *
 * <p><b>Bugfix 06/09/2026 (sửa nhầm nhóm cùng ngày)</b>: bản đầu tiên gắn logic này vào
 * {@code internal_unit} — SAI. Xác nhận lại qua dữ liệu thật
 * ({@code ThanhHoaDEV/src/utils/global.jsx}: {@code contact_unit_rtext = "Danh sách cơ quan, đơn
 * vị thuộc " + CAP}, CAP="Tỉnh") + người dùng xác nhận trực tiếp: {@code contact_unit} MỚI là "cơ
 * quan/đơn vị THUỘC TỈNH" (khác đơn vị mình) — đúng phạm vi yêu cầu gốc. {@code internal_unit}
 * ("Danh sách đơn vị nội bộ", KHÔNG có "thuộc Tỉnh") là đơn vị nội bộ CỦA CHÍNH cơ quan gửi — khác
 * hẳn phạm vi lúc đó, giữ nguyên chỉ dedup (xem docs/architecture/11 addendum 06/09/2026 (3)).
 * <b>Cập nhật 18/09/2026 (hoàn thiện, yêu cầu người dùng)</b>: {@code internal_unit} giờ CŨNG tạo
 * văn bản đến thật, dùng CHUNG method này — xem {@link #putGroupSpecificFields} cho field riêng
 * từng nhánh (khác {@code contact_unit} ở field ghi, giống nhau ở toàn bộ phần copy mô tả văn bản
 * bên dưới).</p>
 *
 * <p><b>Vì sao JDBC thuần, không gọi lại {@code TiepNhanVbdenUseCase}</b>: {@code td-worker}
 * KHÔNG phụ thuộc {@code td-domain}/{@code td-application} (module độc lập hoàn toàn — xem
 * {@code docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md} §11/§13, và người dùng
 * đã chốt lại "trạng thái và văn bản nên tách worker độc lập"). Bảng {@code documents} đã tồn
 * tại sẵn ở MỌI tenant (td-web là NGƯỜI DUY NHẤT migrate — Worker chỉ tiêu thụ schema đã sẵn
 * sàng, không tự tạo bảng này) nên chỉ cần INSERT đúng hình dạng cột {@code BusinessDocument}
 * (td-domain) đã dùng, KHÔNG cần entity/JPA.</p>
 *
 * <p><b>Đối chiếu field để văn bản hiện đúng ở hàng đợi "chờ vào sổ" FE thật đang đọc</b> — hàng
 * đợi ĐÚNG cho {@code contact_unit} là {@code quamangnoibo} (KHÔNG phải {@code dennoibo}, vốn dành
 * cho {@code internal_unit} — xem
 * {@code docs/analysis/07-...md} §2 điểm 4: "identifier đơn vị nội bộ (dennoibo)... identifier cơ
 * quan liên hệ (quamangnoibo)"; `ViewQueryService#quamangnoiboFilter`, dxl 1468-1501:
 * {@code Form="DocumentIn" & contact_unit_identifier!="" & Reply!="1" & document_internet="1"}):</p>
 * <ul>
 *   <li>{@code documents.document_type = "vbden"} (bảng dùng CHUNG cho vbden/vbden_so).</li>
 *   <li>{@code documents.status = "0"} ({@code VbdenStatusService.STATUS_MOI} — Mới, chưa phân
 *       công; hằng số lặp lại GIÁ TRỊ, KHÔNG import class đó vì không có dependency;
 *       {@code quamangnoiboFilter} không có điều kiện status riêng nên giá trị này an toàn, chỉ để
 *       nhất quán với các nơi khác dùng field này).</li>
 *   <li>{@code dynamic_fields.contact_unit_identifier = recipientIdentifier} — CHÍNH giá trị
 *       {@code Organization.identifier} của tenant đích (mỗi tenant = 1 tổ chức, database-per-
 *       tenant) → khớp {@code resolveCurrentIdentifier()} của bất kỳ user văn thư nào thuộc tenant
 *       đó khi họ mở "Văn bản đến qua mạng nội bộ".</li>
 *   <li>{@code dynamic_fields.document_internet = "1"} — đúng cờ Domino gốc phân biệt "đến qua
 *       mạng nội bộ" (liên thông trong tỉnh, đã có tenant/Organization thật, "1") với "liên thông
 *       quốc gia" ("2", {@code lienthongFilter} — KHÔNG áp dụng ở đây, đó là {@code external_unit},
 *       vẫn CHƯA có gateway, xem nhánh {@code isExternal()} handler). KHÔNG set {@code reply} (để
 *       trống là đủ thoả điều kiện loại trừ {@code Reply!="1"}).</li>
 * </ul>
 *
 * <p>KHÔNG tự động "vào sổ" (không gán {@code document_register}/{@code document_number}) — đúng
 * quyết định đã chốt ở tài liệu 11 §7.2/§12: đây vẫn là thao tác NGƯỜI DÙNG chủ động
 * ({@code LuuVanBanDenUseCase}, phía td-web) sau khi văn bản đã "tới nơi" và sẵn sàng.</p>
 *
 * <p><b>Bugfix 07/09/2026 (người dùng gửi mẫu Domino thật {@code docs/views_domino/
 * data_vbdi_SendDocument_output.json}, báo "dữ liệu vẫn chưa khớp hoàn toàn với DOMINO cũ")</b>:
 * trước đó chỉ ghi {@code code}/{@code promulgation_date}/{@code unit_in_charge(_identifier)} —
 * thiếu {@code draftor}/{@code signer}/{@code signer_position}/{@code creator}/
 * {@code delivery_method}/{@code document_type}/{@code sector}/{@code confidentiality}/
 * {@code page_amount}/{@code document_replace_retrieve} mà Domino thật COPY NGUYÊN từ văn bản đi
 * sang bản ghi văn bản đến khi SendDocument, và {@code priority} bị HARD-CODE "Thường" bất kể văn
 * bản đi thật sự chọn độ khẩn gì. Đối chiếu {@code VBDI_FORM_FIELDS}
 * ({@code ThanhHoaDEV/src/utils/global.jsx}, nguồn xác thực nhất cho tên field văn bản đi) xác
 * nhận: các field trên GIỮ NGUYÊN tên với {@code SubViewThongTinCoBan.jsx} (màn "Chi tiết văn bản
 * đi") — chỉ thiếu bước forward từ {@code AddVbdiSendLogUseCase} (td-application, đã sửa cùng
 * ngày) sang {@code documentSnapshot} rồi ghi ở đây. Riêng {@code document_replace_retrieve}: form
 * "Chi tiết sổ văn bản đến - Đầy đủ" (vbden, {@code ChiTietSoVanBanDenDayDu.jsx}) lại dùng 3 tên
 * khác nhau cho khái niệm gần giống ({@code bussiness_doc_type}, {@code paper}, cả 2 đều comment
 * "Loại văn bản"; {@code documentreply}, comment "Loại giấy") — KHÔNG rõ cái nào đúng là bản dịch,
 * nên đã hỏi và người dùng chọn GHI VERBATIM tên {@code document_replace_retrieve} (không dịch
 * sang tên vbden) thay vì đoán — xem docs/architecture/11 addendum 07/09/2026. Tên file đính kèm
 * gốc ({@code tepbanhanh}/{@code tepkemtheo}) CHƯA đưa vào ở lần bổ sung đó — người dùng xác nhận
 * để việc riêng sau.</p>
 *
 * <p><b>Bổ sung tiếp 07/09/2026 (cùng ngày, sau khi hỏi lại người dùng về tệp đính kèm)</b>: mỗi
 * tệp trong {@code snapshot.getFiles()} (đã copy sẵn 1 lần cho cả lượt gửi phía td-application —
 * {@code PrepareVbdiDeliveryFilesUseCase}, KHÔNG phải đường dẫn gốc của văn bản đi) được:</p>
 * <ol>
 *   <li>Ghi 1 dòng {@code file_metadata} MỚI, RIÊNG cho tenant nơi nhận này ({@code document_id}
 *       = văn bản đến vừa tạo, {@code file_path}/{@code bucket_name} = CÙNG object đã copy — KHÔNG
 *       copy vật lý thêm lần nào nữa, đúng yêu cầu "không muốn nhân tệp đính kèm") — TRONG CÙNG
 *       transaction với {@code insertDocument} (xem {@link VbdiDeliveryTaskHandler#execute}): lỗi
 *       ghi tệp giữa chừng ROLLBACK CẢ dedup lẫn văn bản, không để lại "văn bản có mà thiếu tệp"
 *       (mở rộng đúng nguyên tắc "tránh mất hoặc không nhận được văn bản" sang cả tệp đính kèm).</li>
 *   <li>Thêm 1 phần tử vào {@code dynamic_fields.files} — CÙNG hình dạng {@code {name,size,type,
 *       file_id}} mà {@code VbdiFilesJsonbSync}/{@code GetVbdiDetailUseCase} (td-infrastructure)
 *       đã dùng cho vbdi, để {@code filesVBden.jsx}/{@code filesVBdi.jsx} (FE, đọc {@code
 *       resdata.files}) hiển thị được NGAY, và API tải tệp hiện có
 *       ({@code DocumentsController#downloadFile}/{@code FileController}) hoạt động KHÔNG cần sửa
 *       gì (chỉ cần đúng {@code (documentId, fileId)} khớp 1 dòng {@code file_metadata} ở tenant
 *       đang đọc — đã có sau bước 1).</li>
 * </ol>
 * <p>Best-effort ở CẤP TỪNG TỆP khi build (không phải khi INSERT — INSERT đã nằm trong transaction
 * bắt buộc ở trên): {@code snapshot.getFiles()} rỗng/null (văn bản đi không có tệp, hoặc bước copy
 * phía gửi lỗi toàn bộ) → {@code dynamic_fields} không có {@code files}, KHÔNG lỗi.</p>
 */
@Slf4j
final class VbdenIncomingDocumentWriter {

    static final String DOCUMENT_TYPE_VBDEN = "vbden";
    /** = {@code com.td.application.vbden.VbdenStatusService.STATUS_MOI} (giá trị lặp lại, không import). */
    static final String STATUS_MOI = "0";
    /**
     * Định dạng ngày THẬT các field {@code _date} đang dùng — xác nhận qua dữ liệu mẫu thật
     * {@code docs/views_domino/data_vbden_trongtinh_mau.txt} ({@code "received_date": "06/09/2026
     * 17:01:39"}) VÀ {@code ThanhHoaDEV/src/utils/global.jsx#formatDate} (chỉ
     * {@code text.replace(/ 00:00:00$/, "")} — KHÔNG parse qua dayjs với format cụ thể nào khác,
     * nghĩa là giá trị lưu PHẢI ĐÃ ĐÚNG "dd/MM/yyyy HH:mm:ss" sẵn, FE chỉ hiển thị thẳng chuỗi).
     * Bugfix 06/09/2026 (2): bản đầu dùng {@code LocalDate.now().toString()} (ISO
     * "yyyy-MM-dd") — SAI định dạng, FE sẽ hiển thị nguyên văn chuỗi ISO thay vì ngày Việt Nam.
     */
    private static final DateTimeFormatter DATE_TIME_PATTERN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private VbdenIncomingDocumentWriter() {
    }

    /**
     * Bugfix/self-heal cùng tinh thần {@code ensureSendLogIdUniqueConstraint}: cột này có thể CHƯA
     * có trên bảng dedup đã tạo bởi 1 bản Worker cũ hơn — tự thêm nếu thiếu, bỏ qua an toàn nếu 2
     * instance cùng ALTER đồng thời.
     */
    static void ensureCreatedDocumentIdColumn(JdbcTemplate jdbc, String table) {
        Boolean hasColumn = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_name = ? AND column_name = 'created_document_id'
            )
            """, Boolean.class, table);
        if (Boolean.TRUE.equals(hasColumn)) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN created_document_id UUID");
        } catch (Exception ex) {
            log.debug("Bo qua loi them cot created_document_id cho {} (co the da duoc them boi instance khac): {}",
                table, ex.getMessage());
        }
    }

    /**
     * 1 tệp đính kèm đã sẵn sàng để INSERT vào {@code file_metadata} PHÍA NƠI NHẬN — {@code id}
     * MỚI SINH tại đây (KHÔNG phải id nào từ phía gửi, tenant nào cũng tự có id riêng cho dòng
     * {@code file_metadata} của tenant đó), dùng CHUNG cho cả {@code dynamic_fields.files[].file_id}
     * (bên dưới) lẫn cột {@code id} khi INSERT ({@link #insertFileMetadata}) — 2 nơi PHẢI khớp 1
     * giá trị duy nhất để API tải tệp hiện có ({@code GetFileUseCase}, tra theo
     * {@code (documentId, fileId)}) tìm đúng dòng.
     */
    record PreparedFile(UUID id, String objectKey, String bucketName, String originalFilename,
                         Long size, String contentType, String fileExtension, String attachmentGroup) {
    }

    static final String GROUP_TEP_BAN_HANH = "tepbanhanh";
    static final String GROUP_TEP_KEM_THEO = "tepkemtheo";

    /**
     * Bổ sung 07/09/2026 — chuẩn bị danh sách tệp từ {@code snapshot.getFiles()} (đã copy sẵn phía
     * td-application, xem javadoc lớp): sinh {@code id} MỚI cho mỗi tệp (dùng ở cả 2 nơi, xem
     * {@link PreparedFile}), bỏ qua AN TOÀN tệp thiếu {@code objectKey} (dữ liệu không đầy đủ — thà
     * thiếu 1 tệp còn hơn cả văn bản lỗi vì 1 tệp hỏng). {@code fallbackBucketName}: dùng khi
     * {@code bucketName} của 1 tệp bị thiếu trong envelope (không nên xảy ra bình thường —
     * {@code PrepareVbdiDeliveryFilesUseCaseImpl} luôn set, nhưng {@code file_metadata.bucket_name}
     * là cột NOT NULL nên PHẢI có giá trị để tránh lỗi INSERT giữa chừng làm rollback cả văn bản).
     */
    static List<PreparedFile> prepareFiles(VbdiDeliveryPayload p, String fallbackBucketName) {
        var snapshot = p.getDocumentSnapshot();
        if (snapshot == null || snapshot.getFiles() == null || snapshot.getFiles().isEmpty()) {
            return List.of();
        }
        List<PreparedFile> prepared = new ArrayList<>();
        for (var file : snapshot.getFiles()) {
            if (file == null || file.getObjectKey() == null || file.getObjectKey().isBlank()) {
                log.warn("Bo qua 1 file dinh kem thieu objectKey khi tao van ban den cho sendLog={}",
                    p.getSendLogId());
                continue;
            }
            String bucket = (file.getBucketName() == null || file.getBucketName().isBlank())
                ? fallbackBucketName : file.getBucketName();
            String originalFilename = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                ? "tep-dinh-kem" : file.getOriginalFilename();
            long size = file.getSize() == null ? 0L : file.getSize();
            prepared.add(new PreparedFile(UUID.randomUUID(), file.getObjectKey(), bucket,
                originalFilename, size, file.getContentType(), file.getFileExtension(), file.getAttachmentGroup()));
        }
        return prepared;
    }

    /**
     * Bổ sung 13/09/2026 (2, báo lỗi người dùng: "gửi lại văn bản đi chưa gán đúng trạng thái cho
     * đơn vị nhận, lần đầu gửi là văn bản mới, gửi lại phải là văn bản cập nhật") — đối chiếu
     * {@code SendDocument.java} thật (hàm dựng {@code copyToDoc}, dòng ~1773-1787): giá trị
     * {@code bussiness_doc_type} ghi lên văn bản đến MỚI KHÔNG PHẢI luôn luôn = verbatim
     * {@code document_replace_retrieve} của chính văn bản đi — bị GHI ĐÈ theo {@code sendType} của
     * CHÍNH LƯỢT GỬI NÀY, bất kể văn bản có đánh dấu thay thế/thu hồi hay không:
     * <ul>
     *   <li>{@code guibosung} (gửi bổ sung nơi nhận MỚI) → LUÔN "0" ("coi như văn bản MỚI" — Domino:
     *   {@code if(bosung.equalsIgnoreCase("guibosung")) document_replace_retrieve = "0";}).</li>
     *   <li>{@code resenddocument}/{@code guilaitoanbo} (gửi lại — Domino gốc gộp chung 1 actionname
     *   {@code "guilaivanban"}, xem {@code YkienModal.jsx#guiLaiVanBanDi}) → LUÔN "2" ("VB cập nhật"
     *   — Domino: {@code else if(actionname.equalsIgnoreCase("guilaivanban")) document_replace_retrieve
     *   = "2";}).</li>
     *   <li>Còn lại ({@code senddocument} lần đầu, {@code UnitRevoke}) → verbatim
     *   {@code document_replace_retrieve} của văn bản đi ("0"/"1"/"4").</li>
     * </ul>
     * Trước bản vá này, method chỉ đọc verbatim — KHIẾN mọi lần "gửi lại" ĐỀU bị coi là "văn bản
     * mới" ở phía nhận (FE {@code VaoSoNhieu.jsx}/{@code ColumnsVBdenQuaMang.jsx}:
     * {@code bussiness_doc_type=="2" ? "VB cập nhật" : "Văn bản mới"}), đúng triệu chứng người dùng
     * báo. KHÔNG bao giờ trả "3" (giá trị nghi vấn code chết trong Domino gốc — xem addendum
     * {@code docs/analysis/07} 29/08/2026 (4) mục 2, chưa có bằng chứng được gán thật).
     */
    static String resolveEffectiveBussinessDocType(VbdiDeliveryPayload p) {
        String sendType = p.getSendType();
        if (VbdiTaskConstants.SEND_TYPE_GUI_BO_SUNG.equalsIgnoreCase(sendType)) {
            return "0";
        }
        if (VbdiTaskConstants.SEND_TYPE_RESEND_DOCUMENT.equalsIgnoreCase(sendType)
                || VbdiTaskConstants.SEND_TYPE_GUI_LAI_TOAN_BO.equalsIgnoreCase(sendType)) {
            return VbdiTaskConstants.BUSSINESS_DOC_TYPE_CAP_NHAT;
        }
        var snapshot = p.getDocumentSnapshot();
        String raw = snapshot == null ? null : snapshot.getDocumentReplaceRetrieve();
        return (raw == null || raw.isBlank()) ? "0" : raw;
    }

    /**
     * Bổ sung 13/09/2026 (yêu cầu người dùng "port full end-to-end nhánh thay thế/thu hồi" — xem
     * {@code ReplyDocumentOut.java} Domino thật, nhánh {@code type=="vaoso"}): tìm văn bản đến CŨ,
     * CÙNG TENANT NHẬN NÀY, để gán {@code documentoutrevoke} cho văn bản đến MỚI — KHÔNG cần tra cứu
     * cross-tenant: {@code jdbc} ở đây đã là kết nối tới ĐÚNG tenant đang nhận (xem
     * {@code VbdiDeliveryTaskHandler#execute}).
     *
     * <p>2 trường hợp khác NHAU về "văn bản CŨ cần tìm là ai" (cùng field
     * {@code dynamic_fields.parentid_send}, mọi văn bản đến tạo qua {@link #buildDynamicFieldsJson}
     * đều tự mang), khớp đúng 2 nhánh {@code ReplyDocumentOut.java} thật:</p>
     * <ul>
     *   <li>{@code effectiveType} "1"/"4" (thay thế/thu hồi — 2 văn bản đi KHÁC NHAU): tìm theo
     *   {@code parentid_send = replaces_document_id} (UUID văn bản đi A, ghi bởi
     *   {@code GanVbdiDocumentUseCase}) — Domino: view {@code vbden_by_id_documentout} theo
     *   {@code idRevoke} (snapshot A trong {@code documentOutRevoke}).</li>
     *   <li>{@code effectiveType} "2" (gửi lại — CÙNG 1 văn bản đi, tự tham chiếu lần giao TRƯỚC của
     *   CHÍNH NÓ): tìm theo {@code parentid_send = p.getDocumentId()} (id văn bản đi ĐANG gửi, tự
     *   tham chiếu) — Domino: view {@code vbden_by_id_documentout_davaoso} theo khoá tổ hợp
     *   code/identifier/promulgation_date/id_documentout; vì {@code id_documentout} luôn = UNID văn
     *   bản đi GỐC (KHÔNG đổi giữa các lần gửi/gửi lại CÙNG 1 văn bản — đối chiếu dòng ~1740
     *   {@code copyToDoc.replaceItemValue("id_DocumentOut", docDocument.getUniversalID());}), khoá
     *   tổ hợp mơ hồ của Domino ĐÃ được thay bằng khoá CHÍNH XÁC {@code parentid_send} sẵn có trong
     *   kiến trúc Java — không cần suy diễn thêm.</li>
     * </ul>
     * {@code effectiveType} "0" (không thay thế/thu hồi/cập nhật) → trả rỗng, không tra cứu gì (đúng
     * Domino khi {@code idRevoke} rỗng và {@code nghiepvu != "2"}). Không tìm thấy (vd nơi nhận B
     * khác nơi nhận A, hoặc lần gửi đầu tiên chưa từng tới tenant này) → trả rỗng, KHÔNG lỗi — văn
     * bản mới vẫn tạo bình thường, chỉ không đánh dấu liên kết gì thêm.
     *
     * <p>Trả về TOÀN BỘ id khớp (không chỉ 1) — hiếm khi xảy ra (vd "vào sổ nhiều" tạo &gt;1 bản ghi
     * cùng {@code parentid_send} tại CÙNG tenant), nhưng port đúng tinh thần Domino
     * ({@code view.getAllDocumentsByKey} trả cả {@code DocumentCollection}, xử lý VÒNG LẶP mọi kết
     * quả — xem {@code CapNhatVanBanThayTheThuHoiUseCase}, td-application, nơi xử lý danh sách này).
     * Lỗi truy vấn (vd bảng chưa sẵn sàng) chỉ log cảnh báo rồi coi như không tìm thấy — KHÔNG được
     * làm rollback cả việc tạo văn bản đến bình thường chỉ vì bước "làm giàu" tuỳ chọn này lỗi.</p>
     */
    static List<String> resolveRevokeTargetIds(JdbcTemplate jdbc, VbdiDeliveryPayload p, String effectiveType) {
        String targetDocumentId;
        if (VbdiTaskConstants.BUSSINESS_DOC_TYPE_CAP_NHAT.equals(effectiveType)) {
            targetDocumentId = p.getDocumentId();
        } else if ("1".equals(effectiveType) || "4".equals(effectiveType)) {
            var snapshot = p.getDocumentSnapshot();
            targetDocumentId = snapshot == null ? null : snapshot.getReplacesDocumentId();
        } else {
            return List.of();
        }
        if (targetDocumentId == null || targetDocumentId.isBlank()) {
            return List.of();
        }
        try {
            return jdbc.queryForList("""
                    SELECT id::text FROM documents
                    WHERE document_type = 'vbden' AND deleted_on IS NULL
                      AND dynamic_fields ->> 'parentid_send' = ?
                    """, String.class, targetDocumentId);
        } catch (Exception e) {
            log.warn("Khong tim duoc van ban den cu ung voi parentid_send={} (bussiness_doc_type={}) "
                    + "- bo qua, tao van ban den moi binh thuong (khong gan documentoutrevoke): {}",
                targetDocumentId, effectiveType, e.getMessage());
            return List.of();
        }
    }

    /**
     * Field động (JSONB {@code documents.dynamic_fields}) — key ĐÃ lowercase sẵn (đúng quy ước
     * {@code DynamicDocumentFieldsService.buildDynamicFieldsJson}: entry.getKey().toLowerCase()),
     * giá trị dạng TEXT scalar string thuần (không cần {@code date_fields}/{@code number_fields}
     * kèm theo vì {@code FieldTypeInferrer} tự suy DATETIME cho hậu tố {@code _date} và giữ
     * nguyên chuỗi passthrough cho cả DATETIME lẫn TEXT — xem {@code DynamicFieldValueConverter}).
     * {@code effectiveBussinessDocType}/{@code revokeTargetIds} (bổ sung 13/09/2026): xem javadoc
     * {@link #resolveEffectiveBussinessDocType}/{@link #resolveRevokeTargetIds}.
     */
    static String buildDynamicFieldsJson(ObjectMapper objectMapper, VbdiDeliveryPayload p,
            List<PreparedFile> preparedFiles, String effectiveBussinessDocType, List<String> revokeTargetIds)
            throws JsonProcessingException {
        Map<String, Object> fields = new LinkedHashMap<>();
        putGroupSpecificFields(fields, p);
        // Bugfix 29/09/2026 (bao loi "vbden/xuly/chophancong - lanh dao thay van ban nhung mo chi tiet
        // khong co chuc nang nao"): Domino gan Form="DocumentIn" cho moi van ban den; FE
        // (VBDenButtonActionXuLy.jsx) CHI render nhom nut xu ly khi resdata.form === "DocumentIn".
        // Truoc day Worker khong ghi field nay -> moi van ban den nhan qua mang deu "cam" nut.
        // Du lieu da tao truoc do: backfill boi V1.0.68 (td-web).
        fields.put("form", "DocumentIn");
        // 14/10/2026: văn bản từ Trục liên thông QUỐC GIA = "2" (ViewQueryService.lienthongFilter, kho tổng hợp toàn
        // tỉnh); "1" = liên thông trong tỉnh. Kèm mã văn bản eDXML gốc để phản hồi trạng thái về đơn vị gửi.
        fields.put("document_internet", p.isNationalIncoming() ? "2" : "1");
        if (p.isNationalIncoming()) {
            putIfNotBlank(fields, "national_document_id", p.getNationalDocumentId());
        }
        fields.put("received_date", LocalDateTime.now().format(DATE_TIME_PATTERN));
        putIfNotBlank(fields, "unit_in_charge", p.getSenderOrganName());
        putIfNotBlank(fields, "unit_in_charge_identifier", p.getSenderIdentifier());
        // 4 field phuc vu outbox "vbdi.reply" chieu NGUOC (don vi nhan -> don vi gui) khi van thu
        // "vao so"/"phan xu ly" van ban den nay - xem PublishVbdiReplyStatusUseCase (td-application).
        // Bo sung 07/09/2026 (yeu cau nguoi dung, doi chieu Domino that SendRecievedInComingReplyToSender/
        // ChuyenXuLy.java): "parentid_send" = id van ban di GOC (khong phai id van ban den vua tao) -
        // Worker phia GUI (VbdiReplyTaskHandler) can gia tri nay de dien dung parent_id khi ghi 1 dong
        // MOI vao vbdi_send_log_documents (bang NOT NULL, khong co gia tri nay se loi INSERT).
        fields.put("parentid_send", p.getDocumentId());
        fields.put("send_log_id", p.getSendLogId());
        putIfNotBlank(fields, "sender_identifier", p.getSenderIdentifier());
        putIfNotBlank(fields, "sender_reply_routing_key", p.getSenderReplyRoutingKey());
        var snapshot = p.getDocumentSnapshot();
        String priority = snapshot == null ? null : snapshot.getPriority();
        // Mặc định khớp giá trị FE tự chọn sẵn khi văn thư "Tiếp nhận" thủ công (SubFormThongTinCoBan.jsx)
        // CHỈ dùng khi văn bản đi KHÔNG có priority thật (getDoKhan đọc field này an toàn dù rỗng,
        // nhưng "Thường" đúng ngữ nghĩa mặc định hơn để trống) - bugfix 07/09/2026, trước đó hard-code
        // "Thường" bất kể văn bản đi chọn độ khẩn gì (xem javadoc lớp).
        fields.put("priority", (priority == null || priority.isBlank()) ? "Thường" : priority);
        if (snapshot != null) {
            putIfNotBlank(fields, "code", snapshot.getCode());
            putIfNotBlank(fields, "promulgation_date", snapshot.getPromulgationDate());
            // Bo sung 07/09/2026 (doi chieu docs/views_domino/data_vbdi_SendDocument_output.json) -
            // xem javadoc lop + AddVbdiSendLogUseCase.publishDeliveryMessage (td-application, nguon
            // du lieu). Ten field GIU NGUYEN theo VBDI_FORM_FIELDS (ThanhHoaDEV/src/utils/global.jsx),
            // KHONG dich sang ten vbden - da chot voi nguoi dung, xem docs/architecture/11 addendum
            // 07/09/2026.
            putIfNotBlank(fields, "draftor", snapshot.getDraftor());
            putIfNotBlank(fields, "signer", snapshot.getSigner());
            putIfNotBlank(fields, "signer_position", snapshot.getSignerPosition());
            putIfNotBlank(fields, "creator", snapshot.getCreator());
            putIfNotBlank(fields, "delivery_method", snapshot.getDeliveryMethod());
            putIfNotBlank(fields, "document_type", snapshot.getDocumentType());
            putIfNotBlank(fields, "sector", snapshot.getSector());
            putIfNotBlank(fields, "confidentiality", snapshot.getConfidentiality());
            putIfNotBlank(fields, "page_amount", snapshot.getPageAmount());
            putIfNotBlank(fields, "document_replace_retrieve", snapshot.getDocumentReplaceRetrieve());
        }
        // Bugfix 13/09/2026 (2, bao loi nguoi dung: "gui lai van ban... lan dau gui la van ban moi,
        // gui lai phai la van ban cap nhat") - xem javadoc resolveEffectiveBussinessDocType: PHAI set
        // "bussiness_doc_type" VO DIEU KIEN theo effectiveBussinessDocType (KHONG con gate theo "tim
        // duoc documentoutrevoke hay khong" nhu truoc - do la 2 viec KHAC nhau: gia tri nay phan anh
        // Y DINH cua nguoi GUI, con documentoutrevoke chi la 1 "lam giau" tuy chon neu tim duoc van
        // ban CU tai CHINH tenant nay). "0" = van ban MOI, KHONG ghi field (FE mac dinh hieu thieu
        // field nay la "Van ban moi" - xem VaoSoNhieu.jsx/ColumnsVBdenQuaMang.jsx).
        if (!"0".equals(effectiveBussinessDocType)) {
            fields.put("bussiness_doc_type", effectiveBussinessDocType);
        }
        // "documentoutrevoke" = mang [{"id":...}] (key lowercase, KHOP cach FE dang doc
        // resdata?.documentoutrevoke - xem DocumentInfo.jsx) - CHI set khi THUC SU tim duoc van ban
        // den CU tai tenant nay (xem javadoc resolveRevokeTargetIds).
        if (revokeTargetIds != null && !revokeTargetIds.isEmpty()) {
            List<Map<String, Object>> revokeJson = new ArrayList<>();
            for (String revokeId : revokeTargetIds) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", revokeId);
                revokeJson.add(entry);
            }
            fields.put("documentoutrevoke", revokeJson);
        }
        // Bo sung 07/09/2026 (tiep, sau khi hoi lai nguoi dung ve dinh kem) - shape {name,size,type,
        // file_id} KHOP {@code VbdiFilesJsonbSync}/{@code GetVbdiDetailUseCase} (td-infrastructure)
        // dang dung cho vbdi, de FE (filesVBden.jsx/filesVBdi.jsx doc resdata.files) hien thi duoc
        // NGAY - xem javadoc lop.
        if (preparedFiles != null && !preparedFiles.isEmpty()) {
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
            putAttachmentGroupFields(fields, preparedFiles);
        }
        return objectMapper.writeValueAsString(fields);
    }

    /**
     * Bổ sung 30/09/2026 (yêu cầu người dùng: văn bản đến nhận được phải chia "Tệp ban hành"/"Tệp kèm
     * theo" như chi tiết văn bản đi, tệp "Phát hành kèm tệp văn bản đến" vào "Tệp kèm theo"): ghi tên
     * tệp vào {@code tepbanhanh}/{@code tepkemtheo} theo {@link PreparedFile#attachmentGroup()} (phía gửi
     * đã gán) — chuỗi "a.pdf;b.docx;" cùng quy ước FE. {@code filesVBden.jsx} chia nhóm theo
     * {@code tepbanhanh} (tệp không có tên trong đó = "Tệp kèm theo"). Message cũ không có nhóm → không
     * ghi field nào (hiển thị không phân nhóm như trước).
     */
    private static void putAttachmentGroupFields(Map<String, Object> fields, List<PreparedFile> preparedFiles) {
        StringBuilder banHanh = new StringBuilder();
        StringBuilder kemTheo = new StringBuilder();
        for (PreparedFile f : preparedFiles) {
            if (GROUP_TEP_BAN_HANH.equals(f.attachmentGroup())) {
                banHanh.append(f.originalFilename()).append(';');
            } else if (GROUP_TEP_KEM_THEO.equals(f.attachmentGroup())) {
                kemTheo.append(f.originalFilename()).append(';');
            }
        }
        if (!banHanh.isEmpty()) {
            fields.put(GROUP_TEP_BAN_HANH, banHanh.toString());
        }
        if (!kemTheo.isEmpty()) {
            fields.put(GROUP_TEP_KEM_THEO, kemTheo.toString());
        }
    }

    private static void putIfNotBlank(Map<String, Object> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }

    /**
     * Bổ sung 18/09/2026 (hoàn thiện "phát hành vbdi → đến nội bộ", yêu cầu người dùng): trước bản
     * vá này, method này LUÔN ghi {@code contact_unit_identifier} bất kể nhóm nào — đúng cho
     * {@code contact_unit} nhưng SAI cho {@code internal_unit} (nay CŨNG tạo văn bản đến thật, xem
     * javadoc {@link VbdiDeliveryTaskHandler}). Rẽ nhánh theo {@code recipientGroup} — khớp CHÍNH
     * XÁC field Domino gốc set cho từng nhánh {@code SendDocument.java}:
     * <ul>
     *   <li>{@code contact_unit} (cơ quan/đơn vị KHÁC thuộc tỉnh — {@code copy_Document_Vbden}, dòng
     *   ~1740+): {@code contact_unit_identifier = recipientIdentifier} — hành vi CŨ, giữ nguyên.</li>
     *   <li>{@code internal_unit} (đơn vị nội bộ CỦA CHÍNH cơ quan gửi, bao gồm cả trường hợp
     *   {@code SendVbdiDocumentUseCase#reclassifyIfOwnAgency} tự chuyển nhóm khi người dùng lỡ chọn
     *   chính cơ quan mình ở cột "Cơ quan, đơn vị thuộc Tỉnh" — {@code copy_Document_Vbden_NoiBo}/
     *   {@code _CAY}, dòng 1253-1254/1592-1593): {@code internal = "1"} (view Domino gốc
     *   {@code dennoibo}, dxl {@code Form="DocumentIn" & ... & internal="1"}, khớp
     *   {@code ViewQueryService.dennoiboFilter()} phía td-application), {@code unit_process} = mảng
     *   1 phần tử {@code [recipientIdentifier]} (khớp {@code dennoibo} categorize theo
     *   {@code unit_process}, dxl {@code @Explode(unit_process;";")} — Java dùng JSON array thay
     *   chuỗi phân tách ";", đúng cách {@code appendAnyFieldContainsPredicate}
     *   ({@code PostgresDocumentRepository.java}) match containment {@code dynamic_fields @>
     *   {"unit_process":["X"]}}), {@code user_receiver} = mảng 1 phần tử
     *   {@code [recipientReceiverUsername]} NẾU có (khớp view {@code dennoibo_choxuly}, bỏ qua field
     *   nếu rỗng — không có người đại diện cấu hình sẵn KHÔNG phải lỗi), {@code identifier_creator =
     *   senderIdentifier} (khớp Domino dòng 1254/1593 — KHÔNG set {@code contact_unit_identifier}
     *   cho nhánh này).</li>
     * </ul>
     * Domino gốc set {@code document_internet="1"} cho CẢ HAI nhánh (dòng 1080/1416) — giữ nguyên ở
     * {@link #buildDynamicFieldsJson} (set VÔ ĐIỀU KIỆN sau lời gọi method này), không lặp lại ở
     * đây.
     */
    private static void putGroupSpecificFields(Map<String, Object> fields, VbdiDeliveryPayload p) {
        if (VbdiTaskConstants.GROUP_INTERNAL_UNIT.equals(p.getRecipientGroup())) {
            fields.put("internal", "1");
            fields.put("unit_process", List.of(p.getRecipientIdentifier()));
            if (p.getRecipientReceiverUsername() != null && !p.getRecipientReceiverUsername().isBlank()) {
                fields.put("user_receiver", List.of(p.getRecipientReceiverUsername()));
            }
            putIfNotBlank(fields, "identifier_creator", p.getSenderIdentifier());
        } else {
            fields.put("contact_unit_identifier", p.getRecipientIdentifier());
        }
    }

    static String resolveTitle(VbdiDeliveryPayload p) {
        var snapshot = p.getDocumentSnapshot();
        if (snapshot != null && snapshot.getSubject() != null && !snapshot.getSubject().isBlank()) {
            return snapshot.getSubject();
        }
        return "Văn bản đến (chưa có trích yếu) - " + p.getDocumentId();
    }

    /**
     * INSERT trực tiếp vào {@code documents} (schema {@code BusinessDocument}, td-domain — xem
     * javadoc lớp). {@code id} tự sinh phía Java (không nhờ {@code DEFAULT uuid_generate_v4()} của
     * cột) để không phụ thuộc hàm DB cụ thể - portable H2 (test)/Postgres (thật) như phần còn lại
     * của Worker. {@code created_by}/{@code last_modified_by} để {@code NULL} (không có user thật -
     * hành động của hệ thống, 2 cột này vốn nullable, xem migration {@code V1.0.3}/{@code V1.0.27}
     * phía td-web).
     */
    static UUID insertDocument(JdbcTemplate jdbc, String title, String dynamicFieldsJson) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO documents
                 (id, title, document_type, status, tags_json, attributes_json, metadata_json,
                  dynamic_fields, version_no, created_on)
                VALUES (?, ?, ?, ?, '[]', '{}', '{}', ?, 1, CURRENT_TIMESTAMP)
                """,
            id, title, DOCUMENT_TYPE_VBDEN, STATUS_MOI, dynamicFieldsJson);
        return id;
    }

    static void linkCreatedDocument(JdbcTemplate jdbc, String table, String sendLogId, UUID documentId) {
        jdbc.update(("UPDATE " + table + " SET created_document_id = ? WHERE send_log_id = ?"),
            documentId, sendLogId);
    }

    /**
     * INSERT trực tiếp vào {@code file_metadata} (bảng đã tồn tại sẵn ở MỌI tenant — td-web migrate
     * qua {@code V1.0.2__Create_File_Metadata_Tables.sql}, cùng nguyên tắc "Worker chỉ tiêu thụ
     * schema đã sẵn sàng" như bảng {@code documents} — xem javadoc lớp). {@code file_path}/
     * {@code bucket_name} trỏ CÙNG object đã copy sẵn phía gửi (KHÔNG copy vật lý thêm lần nào ở
     * đây) — đúng yêu cầu "ánh xạ để đọc trực tiếp... không muốn nhân tệp đính kèm". Dòng này CHẠY
     * TRONG CÙNG transaction với {@link #insertDocument} (gọi bởi {@link VbdiDeliveryTaskHandler}) —
     * lỗi ở BẤT KỲ tệp nào ({@code NOT NULL}/kiểu dữ liệu...) ROLLBACK CẢ văn bản lẫn dedup, không để
     * lại "văn bản có mà thiếu tệp không rõ lý do" (đúng yêu cầu "tránh mọi trường hợp có khả năng
     * xảy ra lỗi đính kèm" — thà RETRY cả unit còn hơn để lại trạng thái nửa vời khó phát hiện).
     *
     * <p><b>Bugfix 07/09/2026 (lỗi thật vận hành, log thực tế: {@code DuplicateKeyException} trên
     * {@code file_metadata_stored_filename_key}, sendType=resenddocument)</b>: bản đầu lấy
     * {@code stored_filename} từ segment CUỐI của {@code objectKey} — SAI, vì {@code objectKey} là
     * giá trị CỐ ĐỊNH, DÙNG CHUNG cho MỌI nơi nhận của CÙNG 1 lượt gửi (đúng thiết kế "copy 1 lần",
     * xem {@code PrepareVbdiDeliveryFilesUseCaseImpl}) — nếu 2 nơi nhận KHÁC NHAU (2
     * {@code sendLogId} khác nhau) cùng trỏ về CÙNG 1 tenant đích (vd gửi lại/gửi bổ sung tới cùng 1
     * đơn vị đã từng nhận trước đó), CẢ 2 lần INSERT đều dùng CHUNG {@code objectKey} → CHUNG
     * {@code stored_filename} → lần INSERT thứ 2 (dù là sendLogId hoàn toàn khác, KHÔNG bị chặn bởi
     * dedup theo send_log_id) đụng UNIQUE constraint TRONG CÙNG database tenant — và vì giá trị này
     * KHÔNG đổi giữa các lần retry (vẫn cùng 1 message), unit RETRY vô hạn tới khi hết
     * {@code maxAttempts} rồi FAILED, không tự phục hồi được. Sửa: lấy {@code stored_filename} từ
     * {@code f.id()} — id MỚI SINH RIÊNG cho MỖI lần gọi {@link #prepareFiles} (tức MỖI
     * nơi-nhận/MỖI lần xử lý unit), KHÔNG BAO GIỜ trùng giữa 2 tenant/2 sendLogId khác nhau dù cùng
     * tham chiếu 1 object MinIO vật lý — cột này vốn chỉ cần DUY NHẤT trong phạm vi 1 database
     * tenant, không mang ý nghĩa nghiệp vụ nào khác ngoài thoả ràng buộc UNIQUE.</p>
     *
     * <p><b>Bổ sung 22/10/2026 — người dùng: "các đơn vị dùng chung file... đơn vị có xóa văn bản đến thì không
     * xóa đính kèm để đơn vị khác vẫn dùng được, chỉ xóa khi hết đơn vị"</b>: {@code is_shared = TRUE} (như
     * bản sao "Chuyển xử lý") — td-web chặn xóa tệp, object chỉ bị xóa bởi {@code SharedFileGcJob} khi không còn
     * đơn vị nào tham chiếu. Handler ghi tham chiếu vào sổ đăng ký SAU KHI commit
     * ({@link #toSharedFileRefs}).</p>
     */
    static void insertFileMetadata(JdbcTemplate jdbc, UUID documentId, List<PreparedFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (PreparedFile f : files) {
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

    static List<SharedFileRegistry.SharedFileRef> toSharedFileRefs(UUID documentId, List<PreparedFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
            .map(f -> new SharedFileRegistry.SharedFileRef(f.id(), documentId, f.bucketName(), f.objectKey()))
            .toList();
    }
}
