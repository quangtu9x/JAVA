package com.td.application.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.CopyDocumentFilesRequest;
import com.td.application.documents.CopyDocumentFilesUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Yêu cầu người dùng 13/09/2026 ("đọc và port luôn nhánh 2 đi cho xong việc này"): port nhánh
 * "thay thế/thu hồi" của {@code ReplyDocumentOut.java} thật ({@code type=="vaoso"}, phần XỬ LÝ khi
 * {@code documentOutRevoke}/{@code bussiness_doc_type} có giá trị — KHÁC nhánh {@code type!="vaoso"}
 * đã port trước đó ở {@link TuChoiTiepNhanVbdenUseCase}). Đây là NỬA SAU của tính năng "gửi văn bản
 * thay thế" (nửa đầu — liên kết B→A qua {@code replaces_document_id}
 * ({@link com.td.application.vbdi.GanVbdiDocumentUseCase}) và tạo văn bản đến MỚI mang đúng
 * {@code bussiness_doc_type}/{@code documentoutrevoke} lúc giao hàng — đã port ở
 * {@code com.td.worker.tasks.vbdi.VbdenIncomingDocumentWriter#resolveRevokeTargetIds}, td-woker).
 *
 * <p><b>Đối chiếu Domino thật (ReplyDocumentOut.java:101-321, không suy đoán)</b> — văn bản đến
 * {@code C} (request {@code id}) là bản ghi "thay thế/thu hồi" vừa nhận, mang {@code bussiness_doc_type}
 * ("1"=Thu hồi/"4"=Thay thế — verbatim từ {@code document_replace_retrieve} bên vbdi, xem javadoc
 * writer trên) + {@code documentoutrevoke} (mảng {@code [{id:...}]} trỏ văn bản đến CŨ, "docRevoke",
 * CÙNG tenant — do worker tìm được lúc giao hàng). Với MỖI {@code docRevoke} tìm được:</p>
 * <ul>
 *   <li><b>{@code docRevoke.status == "0"}</b> (Mới, CHƯA ai xử lý gì) — huỷ HẲN {@code docRevoke}
 *   (soft-delete, thay {@code Form=DocumentIn_Deleted} Domino — {@code isRevoke=1},
 *   {@code nghiepvu="thuhoi|thaythe"}, {@code reply=1}). {@code C} KHÔNG đổi gì thêm ngoài phần đuôi
 *   chung (xem dưới).</li>
 *   <li><b>{@code docRevoke.status} thuộc {"1","2","3","4"}</b> (đã xử lý/đang xử lý dở) — GIỮ NGUYÊN
 *   {@code docRevoke} (không huỷ, không đụng trạng thái/người xử lý đang dở), chỉ COPY NỘI DUNG +
 *   tệp đính kèm từ {@code C} ĐÈ lên {@code docRevoke} ({@code document_type/subject/confidentiality/
 *   priority/delivery_method/position/signer/page_amount/sector/creator/promulgation_date} — ĐÚNG
 *   {@code itemsToCopy} dòng ~284-296 Domino thật, KHÔNG kèm {@code tepkemtheo}/{@code tepbanhanh}
 *   như nhánh {@code nghiepvu=="2"}, xem gap ghi bên dưới), rồi đánh dấu {@code C.status="4"}
 *   ({@link VbdenStatusService#STATUS_DUNG_HAN_DA_XU_LY}, ĐÚNG giá trị Domino gán cứng) +
 *   {@code C.subject}="{actor} đã chấp nhận thay thế thu hồi văn bản ({thời điểm})".</li>
 * </ul>
 * <p>Sau vòng lặp (VÔ ĐIỀU KIỆN, đúng Domino cuối {@code setReplyDocumentOut()}): publish STATUS_03
 * "Đã tiếp nhận" qua {@link com.td.application.vbdi.PublishVbdiReplyStatusUseCase} (tự bỏ qua nếu
 * {@code C} không có {@code send_log_id}/{@code sender_identifier} — không phải văn bản qua giao
 * liên thông) + đánh dấu {@code C.reply="1"} (field {@code ViewQueryService} dùng loại khỏi 4 view
 * "chờ xử lý", cùng field {@link TuChoiTiepNhanVbdenUseCase} đã port).</p>
 *
 * <p><b>Bổ sung 13/09/2026 (2, báo lỗi người dùng: "gửi lại văn bản đi chưa gán đúng trạng thái cho
 * đơn vị nhận, lần đầu gửi là văn bản mới, gửi lại phải là văn bản cập nhật") — ĐÃ PORT nhánh
 * {@code nghiepvu=="2"}</b> ("cập nhật lại nội dung" — {@code ReplyDocumentOut.java:136-227}, trước
 * đây ghi nhận là gap chưa port vì tưởng "2" không bao giờ được gán): xác nhận lại
 * {@code SendDocument.java:1776-1787} — {@code bussiness_doc_type="2"} chính là giá trị Domino gán
 * cho MỌI lần "gửi lại văn bản" (Domino {@code actionname="guilaivanban"}, Java
 * {@code sendType} {@code resenddocument}/{@code guilaitoanbo} — xem
 * {@code com.td.worker.tasks.vbdi.VbdenIncomingDocumentWriter#resolveEffectiveBussinessDocType}),
 * KHÔNG PHẢI trường hợp hiếm/không dùng tới như đánh giá trước đó. {@code docRevoke} tìm qua
 * {@code documentoutrevoke} (worker đã gán, tự tham chiếu {@code parentid_send} = CHÍNH văn bản đi
 * đang gửi — thay cho khoá tổ hợp mơ hồ code/identifier/promulgation_date/id_documentout của
 * Domino, xem javadoc worker) — CHỈ xử lý khi {@code docRevoke.bussiness_doc_type == "0"} (văn bản
 * cũ còn "thuần", chưa từng là mục tiêu thay thế/thu hồi/cập nhật khác — đúng guard Domino, KHÔNG
 * suy đoán thêm cho trường hợp khác). Copy 13 field (thêm {@code tepkemtheo}/{@code tepbanhanh} so
 * với nhánh "1"/"4") + tệp đính kèm từ {@code C} ĐÈ lên {@code docRevoke} (docRevoke SỐNG, giữ
 * nguyên trạng thái/người xử lý đang dở), rồi HUỶ {@code C} (khác nhánh "1"/"4" — C ở đây là bản
 * SAO TRÙNG LẶP tự sinh ra khi gửi lại, không mang ý nghĩa riêng để giữ lại) — CÓ điều kiện: nếu
 * {@code internal=="1"} (văn bản nội bộ nhiều đơn vị nhận CÙNG 1 dòng) chỉ huỷ khi đơn vị đang xử
 * lý là đơn vị CUỐI CÙNG còn lại trong {@code unit_process}, ngược lại LUÔN huỷ (đúng
 * {@code ReplyDocumentOut.java:204-225}). Trường Domino tự tham chiếu {@code id_vbden_capnhat} =
 * UNID chính {@code docDocument}/{@code C} — port VERBATIM (không "sửa" thành tham chiếu
 * {@code docRevoke}, dù có vẻ là 1 quirk của Domino — không đủ căn cứ để khẳng định đó là bug thay
 * vì chủ ý, giữ nguyên hành vi nguồn).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CapNhatVanBanThayTheThuHoiUseCase {

    private static final String DOCUMENT_TYPE = "vbden";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";
    private static final String COPY_MODULE = "vbden";

    private static final String BUSSINESS_DOC_TYPE_FIELD = "bussiness_doc_type";
    private static final String DOCUMENT_OUT_REVOKE_FIELD = "documentoutrevoke";
    private static final String NGHIEPVU_FIELD = "nghiepvu";
    private static final String REPLY_FIELD = "reply";
    private static final String IS_REVOKE_FIELD = "isRevoke";
    private static final String SUBJECT_FIELD = "subject";
    private static final String INTERNAL_FIELD = "internal";
    private static final String UNIT_PROCESS_FIELD = "unit_process";
    private static final String UNIT_PROCESS_REGISTERED_FIELD = "unit_process_registered";
    private static final String VBDEN_DACAPNHAT_OBJ_FIELD = "vbden_dacapnhat_obj";
    private static final String STATUS_UPDATE_NOI_DUNG = "2";
    private static final String NGHIEPVU_THU_HOI_THAY_THE = "thuhoi|thaythe";
    private static final String NGHIEPVU_CAP_NHAT = "capnhat";
    private static final String STATUS_MOI = VbdenStatusService.STATUS_MOI;

    /** ĐÚNG {@code itemsToCopy} dòng ~284-296 {@code ReplyDocumentOut.java} thật (nhánh docRevoke đã xử lý dở). */
    private static final Set<String> METADATA_FIELDS_TO_COPY = Set.of(
        "document_type", SUBJECT_FIELD, "confidentiality", "priority", "delivery_method",
        "position", "signer", "page_amount", "sector", "creator", "promulgation_date");

    /**
     * ĐÚNG {@code itemsToCopy} dòng ~180-194 {@code ReplyDocumentOut.java} thật (nhánh
     * {@code nghiepvu=="2"}, "gửi lại văn bản") — GIỐNG {@link #METADATA_FIELDS_TO_COPY} + thêm
     * {@code tepkemtheo}/{@code tepbanhanh} (tên tệp đính kèm/ban hành, khác nhánh "1"/"4").
     */
    private static final Set<String> METADATA_FIELDS_TO_COPY_CAP_NHAT = Set.of(
        "document_type", SUBJECT_FIELD, "confidentiality", "priority", "delivery_method",
        "position", "signer", "page_amount", "sector", "creator", "promulgation_date",
        "tepkemtheo", "tepbanhanh");

    private static final DateTimeFormatter ACCEPTED_AT_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final com.td.application.sharedcore.ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final CopyDocumentFilesUseCase copyDocumentFilesUseCase;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final com.td.application.vbdi.PublishVbdiReplyStatusUseCase publishVbdiReplyStatusUseCase;

    public Result<UUID> execute(CapNhatVanBanThayTheThuHoiRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id văn bản đến");
        }
        var documentOptional = documentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse("");
        var before = buildSnapshot(document);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        String nghiepvu = asString(fields.get(BUSSINESS_DOC_TYPE_FIELD));
        List<String> revokeIds = parseDocumentOutRevokeIds(fields.get(DOCUMENT_OUT_REVOKE_FIELD));

        boolean isCapNhat = STATUS_UPDATE_NOI_DUNG.equals(nghiepvu);
        for (String revokeId : revokeIds) {
            if (isCapNhat) {
                processCapNhatTarget(revokeId, document, fields, actingUsername);
            } else {
                processRevokeTarget(revokeId, document, fields, actingUsername);
            }
        }

        fields.put(REPLY_FIELD, "1");
        document.replaceDynamicFields(toJson(fields));
        var saved = documentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        publishReplyStatusAfterChapNhan(saved, actingUsername);

        return Result.success(saved.getId());
    }

    /**
     * Xử lý 1 {@code docRevoke} (văn bản đến CŨ) — mirror khối {@code while(docRevoke != null)}
     * dòng ~129-312 {@code ReplyDocumentOut.java}, nhánh {@code nghiepvu} thuộc {"1","4"}.
     * {@code documentFieldsOfC}: dynamic_fields ĐANG SỬA của {@code C} (đọc để copy sang docRevoke,
     * KHÔNG đổi ở đây — {@code C} chỉ đổi status/subject NGAY TRÊN entity {@code documentC}).
     */
    private void processRevokeTarget(String revokeId, BusinessDocument documentC,
            Map<String, Object> documentFieldsOfC, String actingUsername) {
        UUID revokeUuid;
        try {
            revokeUuid = UUID.fromString(revokeId);
        } catch (IllegalArgumentException ex) {
            log.warn("documentoutrevoke cua van ban den {} chua id khong hop le \"{}\" - bo qua",
                documentC.getId(), revokeId);
            return;
        }
        var docRevokeOptional = documentRepository.findById(revokeUuid);
        if (docRevokeOptional.isEmpty() || docRevokeOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(docRevokeOptional.get().getDocumentType())) {
            log.warn("Khong tim thay van ban den can thay the/thu hoi (documentoutrevoke id={}) "
                + "cho van ban den {} - co the da bi xoa truoc do", revokeId, documentC.getId());
            return;
        }
        var docRevoke = docRevokeOptional.get();
        Map<String, Object> revokeFields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(docRevoke.getDynamicFieldsJson()));
        String statusRevoke = docRevoke.getStatus() == null ? "" : docRevoke.getStatus();

        if (STATUS_MOI.equals(statusRevoke)) {
            // Domino: Form=DocumentIn_Deleted + isRevoke=1 + nghiepvu="thuhoi|thaythe" + reply=1.
            revokeFields.put(IS_REVOKE_FIELD, "1");
            revokeFields.put(NGHIEPVU_FIELD, NGHIEPVU_THU_HOI_THAY_THE);
            revokeFields.put(REPLY_FIELD, "1");
            docRevoke.replaceDynamicFields(toJson(revokeFields));
            // deletedBy=null: khong co UUID AppUser san co o day (chi co username qua
            // CurrentUserProvider) - cung mo hinh cac use case khac chap nhan null cho tham so nay
            // khi chua can truy nguoc AppUserRepository rieng chi de phuc vu 1 cot audit.
            docRevoke.markAsDeleted(null);
            documentRepository.save(docRevoke);
            documentCacheService.evict(docRevoke.getId());
            return;
        }

        // status thuoc {1,2,3,4} - da xu ly/dang xu ly do, KHONG huy - chi copy noi dung + tep tu C.
        for (String fieldName : METADATA_FIELDS_TO_COPY) {
            if (documentFieldsOfC.containsKey(fieldName)) {
                revokeFields.put(fieldName, documentFieldsOfC.get(fieldName));
            }
        }
        docRevoke.replaceDynamicFields(toJson(revokeFields));
        documentRepository.save(docRevoke);
        documentCacheService.evict(docRevoke.getId());

        var copyResult = copyDocumentFilesUseCase.execute(CopyDocumentFilesRequest.builder()
            .sourceDocumentId(documentC.getId())
            .targetDocumentId(docRevoke.getId())
            .module(COPY_MODULE)
            .build());
        if (!copyResult.isSuccess()) {
            log.warn("Copy tep dinh kem tu van ban den {} sang {} (thay the/thu hoi) that bai: {}",
                documentC.getId(), docRevoke.getId(), copyResult.getError());
        }

        String acceptedSubject = (actingUsername == null || actingUsername.isBlank() ? "Người dùng" : actingUsername)
            + " đã chấp nhận thay thế thu hồi văn bản (" + LocalDateTime.now().format(ACCEPTED_AT_FORMATTER) + ")";
        documentFieldsOfC.put(SUBJECT_FIELD, acceptedSubject);
        documentC.update(null, null, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY, null, null, null, null);
    }

    /**
     * Xử lý 1 {@code docRevoke} (văn bản đến CŨ) khi {@code nghiepvu=="2"} ("cập nhật lại nội dung"
     * — mirror {@code ReplyDocumentOut.java:136-227}, xem javadoc lớp). CHỈ xử lý khi
     * {@code docRevoke.bussiness_doc_type == "0"} (còn "thuần") — khác giá trị đó thì bỏ qua HOÀN
     * TOÀN (không copy gì, không huỷ {@code C}), đúng Domino (không có nhánh {@code else} nào khác
     * trong khối {@code if(nghiepvu.equals("2"))}).
     */
    private void processCapNhatTarget(String revokeId, BusinessDocument documentC,
            Map<String, Object> documentFieldsOfC, String actingUsername) {
        UUID revokeUuid;
        try {
            revokeUuid = UUID.fromString(revokeId);
        } catch (IllegalArgumentException ex) {
            log.warn("documentoutrevoke cua van ban den {} chua id khong hop le \"{}\" - bo qua",
                documentC.getId(), revokeId);
            return;
        }
        var docRevokeOptional = documentRepository.findById(revokeUuid);
        if (docRevokeOptional.isEmpty() || docRevokeOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(docRevokeOptional.get().getDocumentType())) {
            log.warn("Khong tim thay van ban den can cap nhat (documentoutrevoke id={}) cho van ban "
                + "den {} - co the da bi xoa truoc do", revokeId, documentC.getId());
            return;
        }
        var docRevoke = docRevokeOptional.get();
        Map<String, Object> revokeFields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(docRevoke.getDynamicFieldsJson()));
        String revokeBussinessType = asString(revokeFields.get(BUSSINESS_DOC_TYPE_FIELD));
        if (!STATUS_MOI.equals(revokeBussinessType)) {
            // Domino: KHONG co nhanh xu ly nao khac ngoai bussiness_doc_type=="0" trong khoi
            // nghiepvu=="2" - van ban cu da tung la muc tieu thay the/thu hoi/cap nhat khac thi bo
            // qua hoan toan, khong suy doan them.
            log.info("Bo qua cap nhat noi dung van ban den {}: van ban den cu {} co "
                + "bussiness_doc_type=\"{}\" (chi ho tro khi = \"0\")",
                documentC.getId(), docRevoke.getId(), revokeBussinessType);
            return;
        }

        for (String fieldName : METADATA_FIELDS_TO_COPY_CAP_NHAT) {
            if (documentFieldsOfC.containsKey(fieldName)) {
                revokeFields.put(fieldName, documentFieldsOfC.get(fieldName));
            }
        }
        docRevoke.replaceDynamicFields(toJson(revokeFields));
        documentRepository.save(docRevoke);
        documentCacheService.evict(docRevoke.getId());

        var copyResult = copyDocumentFilesUseCase.execute(CopyDocumentFilesRequest.builder()
            .sourceDocumentId(documentC.getId())
            .targetDocumentId(docRevoke.getId())
            .module(COPY_MODULE)
            .build());
        if (!copyResult.isSuccess()) {
            log.warn("Copy tep dinh kem tu van ban den {} sang {} (cap nhat noi dung) that bai: {}",
                documentC.getId(), docRevoke.getId(), copyResult.getError());
        }

        // Ghi vet "da duoc dung lam nguon cap nhat" NGAY TREN C (tu tham chieu id cua chinh no - port
        // VERBATIM theo Domino, xem javadoc lop ve quirk nay).
        appendVbdenDaCapNhatVet(documentFieldsOfC, documentC.getId());

        // Domino: internal=="1" (nhieu don vi CUNG 1 dong) chi huy C khi la don vi CUOI CUNG con lai
        // trong unit_process (sau khi loai identifier dang thao tac); khac thi LUON huy.
        boolean isInternal = "1".equals(asString(documentFieldsOfC.get(INTERNAL_FIELD)));
        boolean shouldDeleteC = true;
        if (isInternal) {
            String identifier = resolveIdentifierByLevelUseCase.resolveForCurrentUser().orElse(null);
            if (identifier != null && !identifier.isBlank()) {
                List<String> unitProcess = toStringList(documentFieldsOfC.get(UNIT_PROCESS_FIELD));
                if (unitProcess.remove(identifier)) {
                    documentFieldsOfC.put(UNIT_PROCESS_FIELD, unitProcess);
                }
            }
            List<String> remainingUnitProcess = toStringList(documentFieldsOfC.get(UNIT_PROCESS_FIELD));
            List<String> unitProcessRegistered = toStringList(documentFieldsOfC.get(UNIT_PROCESS_REGISTERED_FIELD));
            shouldDeleteC = remainingUnitProcess.isEmpty() && unitProcessRegistered.isEmpty();
        }
        if (shouldDeleteC) {
            documentFieldsOfC.put(NGHIEPVU_FIELD, NGHIEPVU_CAP_NHAT);
            // deletedBy=null - xem ghi chu tuong tu o processRevokeTarget.
            documentC.markAsDeleted(null);
        }
    }

    /**
     * = {@code res.put("vbden_dacapnhat","1"); res.put("vbden_dacapnhat_date",...);
     * res.put("id_vbden_capnhat", docDocument.getUniversalID()); addValueToRichText(docDocument,
     * "vbden_dacapnhat_obj", res.toString());} — Domino nối THÊM 1 phần tử vào rich-text nhiều-giá-
     * trị; port thành mảng JSON (append, không ghi đè) trên field cùng tên.
     */
    private void appendVbdenDaCapNhatVet(Map<String, Object> documentFieldsOfC, UUID documentCId) {
        List<Object> existing = new ArrayList<>();
        Object raw = documentFieldsOfC.get(VBDEN_DACAPNHAT_OBJ_FIELD);
        if (raw instanceof List<?> list) {
            existing.addAll(list);
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("vbden_dacapnhat", "1");
        entry.put("vbden_dacapnhat_date", LocalDateTime.now().format(ACCEPTED_AT_FORMATTER));
        entry.put("id_vbden_capnhat", documentCId.toString());
        existing.add(entry);
        documentFieldsOfC.put(VBDEN_DACAPNHAT_OBJ_FIELD, existing);
    }

    private List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        } else if (value != null) {
            for (String part : value.toString().split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private void publishReplyStatusAfterChapNhan(BusinessDocument document, String actingUsername) {
        var replyRequest = new com.td.application.vbdi.PublishVbdiReplyStatusRequest();
        replyRequest.setDynamicFields(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        replyRequest.setStatusCode("03");
        replyRequest.setStatusLabel("Đã tiếp nhận");
        if (actingUsername != null && !actingUsername.isBlank()) {
            replyRequest.setActorDisplayName(actingUsername);
        }
        publishVbdiReplyStatusUseCase.execute(replyRequest);
    }

    /**
     * {@code documentoutrevoke} khoan dung CẢ 2 dạng lưu trữ: mảng JSONB THẬT ({@code List<Map>>},
     * do {@link com.td.worker.tasks.vbdi.VbdenIncomingDocumentWriter} ghi cho văn bản MỚI) hoặc
     * chuỗi JSON kiểu Domino cũ (dữ liệu migrate — {@code String} chứa {@code [{id:...}]}, có thể
     * lẫn xuống dòng như bản gốc {@code ReplyDocumentOut.java} tự strip).
     */
    private List<String> parseDocumentOutRevokeIds(Object raw) {
        List<String> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("id") != null) {
                    ids.add(map.get("id").toString());
                }
            }
        } else if (raw instanceof String str && !str.isBlank()) {
            try {
                JsonNode arr = OBJECT_MAPPER.readTree(
                    str.replaceAll("\r\n", "").replaceAll("[\r\n]", ""));
                if (arr.isArray()) {
                    for (JsonNode el : arr) {
                        if (el.hasNonNull("id")) {
                            ids.add(el.get("id").asText());
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Khong doc duoc documentoutrevoke dang chuoi: {}", ex.getMessage());
            }
        }
        return ids;
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private Map<String, Object> buildSnapshot(BusinessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
