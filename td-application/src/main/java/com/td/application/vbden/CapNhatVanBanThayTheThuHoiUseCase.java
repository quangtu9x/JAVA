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
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.documents.SupersedeDocumentFilesUseCase;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.vbden.VbdenUpdateHistoryDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
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
 *
 * <p><b>Bugfix 24/09/2026 (báo lỗi người dùng: "đã vào sổ 1 VB đến trong tỉnh, đơn vị phát hành gửi
 * lại có bổ sung tệp đính kèm, bấm Cập nhật VB nhưng tệp bổ sung không vào VB đã vào sổ")</b> —
 * đối chiếu dữ liệu thật tenant {@code h05_01_sokhcn} (C={@code 5c041ec1...}, 21/09/2026): nhánh
 * {@code nghiepvu=="2"} ở trên KHÔNG BAO GIỜ cập nhật được gì trong thực tế, vì 3 lỗi:</p>
 * <ol>
 *   <li>Guard {@code docRevoke.bussiness_doc_type == "0"} không khớp văn bản gửi LẦN ĐẦU: Domino
 *   ({@code SendDocument.java:1787}) LUÔN ghi field này kể cả "0", còn worker Java
 *   ({@code VbdenIncomingDocumentWriter}) CỐ Ý không ghi khi "0" → field vắng mặt → "" ≠ "0".</li>
 *   <li>Văn bản ĐÃ VÀO SỔ có thể chính là 1 bản "VB cập nhật" ({@code bussiness_doc_type="2"} — người
 *   dùng vào sổ thẳng bản gửi lại khi bản gốc chưa vào sổ, đúng dữ liệu thật trên) → guard cũ cũng
 *   loại. Domino lọc ứng viên bằng VIEW {@code vbden_by_id_documentout_davaoso} (chỉ văn bản ĐÃ VÀO
 *   SỔ, khoá có {@code identifier} đơn vị đang thao tác); Java trước đây KHÔNG có bước lọc này (worker
 *   trả mọi văn bản đến cùng {@code parentid_send}, gồm cả các bản gửi lại đang chờ). Sửa: thay guard
 *   bằng đúng điều kiện của view — {@code docRevoke} phải có bản ghi sổ ({@code vbden_so},
 *   {@code parentid}=docRevoke, chưa xoá) của ĐƠN VỊ đang thao tác ({@code identifier_creator}), bất kể
 *   {@code bussiness_doc_type} — xem {@link #isRegisteredByCurrentUnit}.</li>
 *   <li>Không cập nhật được đích nào mà vẫn trả thành công VÀ đánh {@code C.reply="1"} (loại khỏi mọi
 *   view "chờ") → văn bản gửi lại BIẾN MẤT khỏi danh sách, không còn cách xử lý tiếp. Sửa: nhánh "2"
 *   không cập nhật được đích nào → trả lỗi rõ ràng, KHÔNG ghi gì (C còn nguyên để vào sổ như mới).</li>
 * </ol>
 * <p><b>Tệp + lịch sử (yêu cầu người dùng 24/09/2026 (2): "tạm tệp cũ chưa xoá, chỉ hiển thị tệp mới
 * cập nhật; lưu vết ngày giờ cập nhật, có liên kết tới văn bản đã cập nhật, thể hiện tệp cũ nhưng không
 * cho tải, chỉ hiển thị tên")</b> — cả 2 nhánh gộp nội dung (gửi lại "2" và "1"/"4" đang xử lý dở) đi qua
 * {@link #mergeIntoTarget}: tệp mới copy sang, tệp trùng hẳn giữ nguyên, tệp cũ không còn trong bộ tệp
 * mới bị đánh dấu ĐÃ THAY THẾ (ẩn khỏi mọi truy vấn, KHÔNG xoá — tương đương "xoá sạch rồi copy lại" của
 * Domino về mặt hiển thị, nhưng giữ được dữ liệu gốc), và ghi 1 dòng
 * {@code vbden_update_history_documents} (thời điểm, người cập nhật, snapshot văn bản nguồn + tệp của
 * nó để mở xem chỉ đọc, danh sách tệp mới/giữ nguyên/đã thay thế, field đã đổi).</p>
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
    // 28/09/2026: thong nhat ten co thu hoi ve "is_revoke" - CUNG ten worker (VbdiRevokeTaskHandler/
    // VbdenForwardUnitRevokeTaskHandler) ghi va ViewQueryService/FE doc. Truoc day "isRevoke" (ghi thang
    // vao map, khong qua lowercase) nen khong noi nao doc duoc - xem migration V1.0.67.
    private static final String IS_REVOKE_FIELD = "is_revoke";
    private static final String SUBJECT_FIELD = "subject";
    private static final String INTERNAL_FIELD = "internal";
    private static final String UNIT_PROCESS_FIELD = "unit_process";
    private static final String UNIT_PROCESS_REGISTERED_FIELD = "unit_process_registered";
    private static final String VBDEN_DACAPNHAT_OBJ_FIELD = "vbden_dacapnhat_obj";
    private static final String REGISTER_ENTRY_DOCUMENT_TYPE = TiepNhanVbdenUseCase.REGISTER_ENTRY_DOCUMENT_TYPE;
    private static final String IDENTIFIER_CREATOR_FIELD = "identifier_creator";
    private static final int MAX_REGISTER_ENTRIES_PER_PARENT = 100;
    static final String NO_REGISTERED_TARGET_MESSAGE = "Không tìm thấy văn bản đến đã vào sổ tương ứng để "
        + "cập nhật. Vui lòng vào sổ văn bản này như văn bản mới.";
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

    /** Field của văn bản nguồn chụp lại vào lịch sử cập nhật ({@code update_obj.source}). */
    private static final List<String> SOURCE_SNAPSHOT_FIELDS = List.of(
        "code", "promulgation_date", SUBJECT_FIELD, "unit_in_charge", "document_type", "signer",
        "received_date", BUSSINESS_DOC_TYPE_FIELD);

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
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;
    private final SupersedeDocumentFilesUseCase supersedeDocumentFilesUseCase;
    private final VbdenUpdateHistoryDocumentRepository vbdenUpdateHistoryDocumentRepository;
    private final AppUserRepository appUserRepository;

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
        String currentIdentifier = isCapNhat
            ? resolveIdentifierByLevelUseCase.resolveForCurrentUser().orElse(null) : null;
        int updatedCount = 0;
        for (String revokeId : revokeIds) {
            if (isCapNhat) {
                if (processCapNhatTarget(revokeId, document, fields, actingUsername, currentIdentifier)) {
                    updatedCount++;
                }
            } else {
                processRevokeTarget(revokeId, document, fields, actingUsername);
            }
        }
        if (isCapNhat && updatedCount == 0) {
            // Bugfix 24/09/2026 (xem javadoc lop, loi 3): KHONG ghi reply=1 - C phai con trong danh
            // sach cho de nguoi dung vao so nhu van ban moi.
            log.warn("Cap nhat van ban den {} (nghiepvu=2): khong co van ban da vao so nao cua don vi {} "
                + "trong documentoutrevoke={} - tra loi, khong thay doi gi", document.getId(), currentIdentifier, revokeIds);
            return Result.failure(NO_REGISTERED_TARGET_MESSAGE);
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
        mergeIntoTarget(docRevoke, revokeFields, documentC, documentFieldsOfC, METADATA_FIELDS_TO_COPY,
            VbdenUpdateHistoryDocument.ACTION_THAY_THE_THU_HOI, actingUsername);

        String acceptedSubject = (actingUsername == null || actingUsername.isBlank() ? "Người dùng" : actingUsername)
            + " đã chấp nhận thay thế thu hồi văn bản (" + LocalDateTime.now().format(ACCEPTED_AT_FORMATTER) + ")";
        documentFieldsOfC.put(SUBJECT_FIELD, acceptedSubject);
        documentC.update(null, null, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY, null, null, null, null);
    }

    /**
     * Xử lý 1 {@code docRevoke} (văn bản đến CŨ) khi {@code nghiepvu=="2"} ("cập nhật lại nội dung"
     * — mirror {@code ReplyDocumentOut.java:136-227}, xem javadoc lớp). CHỈ xử lý khi
     * {@code docRevoke} ĐÃ VÀO SỔ bởi đơn vị đang thao tác (bugfix 24/09/2026 — thay guard cũ
     * {@code bussiness_doc_type == "0"}, xem javadoc lớp); không thoả thì bỏ qua HOÀN TOÀN (không copy
     * gì, không huỷ {@code C}). Trả {@code true} khi đã cập nhật thật {@code docRevoke}.
     */
    private boolean processCapNhatTarget(String revokeId, BusinessDocument documentC,
            Map<String, Object> documentFieldsOfC, String actingUsername, String currentIdentifier) {
        UUID revokeUuid;
        try {
            revokeUuid = UUID.fromString(revokeId);
        } catch (IllegalArgumentException ex) {
            log.warn("documentoutrevoke cua van ban den {} chua id khong hop le \"{}\" - bo qua",
                documentC.getId(), revokeId);
            return false;
        }
        var docRevokeOptional = documentRepository.findById(revokeUuid);
        if (docRevokeOptional.isEmpty() || docRevokeOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(docRevokeOptional.get().getDocumentType())) {
            log.warn("Khong tim thay van ban den can cap nhat (documentoutrevoke id={}) cho van ban "
                + "den {} - co the da bi xoa truoc do", revokeId, documentC.getId());
            return false;
        }
        var docRevoke = docRevokeOptional.get();
        if (docRevoke.getId().equals(documentC.getId())
                || !isRegisteredByCurrentUnit(docRevoke.getId(), currentIdentifier)) {
            // = Domino view vbden_by_id_documentout_davaoso: chi van ban DA VAO SO cua don vi dang thao
            // tac (con lai: ban gui lai dang cho / ban goc chua vao so / cua don vi khac).
            log.info("Bo qua cap nhat noi dung van ban den {}: van ban den cu {} chua vao so boi don vi {}",
                documentC.getId(), docRevoke.getId(), currentIdentifier);
            return false;
        }
        Map<String, Object> revokeFields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(docRevoke.getDynamicFieldsJson()));
        mergeIntoTarget(docRevoke, revokeFields, documentC, documentFieldsOfC, METADATA_FIELDS_TO_COPY_CAP_NHAT,
            VbdenUpdateHistoryDocument.ACTION_CAP_NHAT, actingUsername);

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
        return true;
    }

    /**
     * Bugfix 24/09/2026 — tương đương view Domino {@code vbden_by_id_documentout_davaoso}: văn bản đến
     * có ít nhất 1 bản ghi sổ ({@code vbden_so}, chưa xoá) của đơn vị đang thao tác. Không xác định được
     * đơn vị ({@code currentIdentifier} rỗng) thì chấp nhận bản ghi sổ của bất kỳ đơn vị nào.
     */
    private boolean isRegisteredByCurrentUnit(UUID docRevokeId, String currentIdentifier) {
        var page = documentRepository.findByParentId(REGISTER_ENTRY_DOCUMENT_TYPE, docRevokeId.toString(),
            PageRequest.of(0, MAX_REGISTER_ENTRIES_PER_PARENT));
        if (page == null) {
            return false;
        }
        boolean anyUnit = currentIdentifier == null || currentIdentifier.isBlank();
        return page.getContent().stream().anyMatch(entry -> anyUnit || currentIdentifier.equalsIgnoreCase(asString(
            dynamicDocumentFieldsService.parseDynamicFields(entry.getDynamicFieldsJson()).get(IDENTIFIER_CREATOR_FIELD))));
    }

    /**
     * Gộp nội dung + tệp từ {@code C} (bản gửi lại/thay thế) vào {@code docRevoke} (văn bản đích) và
     * ghi 1 dòng lịch sử cập nhật — dùng chung cho nhánh "2" (gửi lại) và nhánh "1"/"4" (docRevoke đang
     * xử lý dở).
     *
     * <p><b>Tệp</b> (yêu cầu người dùng 24/09/2026: "tạm tệp cũ chưa xoá, chỉ hiển thị tệp mới cập
     * nhật, tệp cũ chỉ hiển thị tên, không cho tải") — so theo (tên gốc, kích thước):</p>
     * <ul>
     *   <li>tệp của C chưa có trên docRevoke → copy sang (tệp mới / tệp cùng tên đã đổi nội dung);</li>
     *   <li>tệp trùng hẳn → giữ nguyên, không copy lại;</li>
     *   <li>tệp của docRevoke KHÔNG còn trong bộ tệp của C → đánh dấu ĐÃ THAY THẾ
     *   ({@link SupersedeDocumentFilesUseCase} — ẩn khỏi mọi truy vấn, KHÔNG xoá). Tên các tệp này chỉ
     *   còn trong {@code update_obj.superseded_files} của lịch sử.</li>
     * </ul>
     * <p>An toàn: C KHÔNG có tệp nào (vd bước chuẩn bị tệp phía gửi lỗi) → KHÔNG thay thế tệp nào của
     * docRevoke — tránh "ẩn sạch" tệp chỉ vì bản gửi lại thiếu dữ liệu.</p>
     */
    private void mergeIntoTarget(BusinessDocument docRevoke, Map<String, Object> revokeFields,
            BusinessDocument documentC, Map<String, Object> documentFieldsOfC, Set<String> fieldsToCopy,
            String action, String actingUsername) {
        Map<String, Object> changedFields = new LinkedHashMap<>();
        for (String fieldName : fieldsToCopy) {
            if (!documentFieldsOfC.containsKey(fieldName)) {
                continue;
            }
            Object oldValue = revokeFields.get(fieldName);
            Object newValue = documentFieldsOfC.get(fieldName);
            if (!asString(oldValue).equals(asString(newValue))) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("oldvalue", oldValue);
                change.put("newvalue", newValue);
                changedFields.put(fieldName, change);
            }
            revokeFields.put(fieldName, newValue);
        }
        docRevoke.replaceDynamicFields(toJson(revokeFields));
        documentRepository.save(docRevoke);
        documentCacheService.evict(docRevoke.getId());

        List<FileDto> sourceFiles = listFiles(documentC.getId());
        List<FileDto> targetFiles = listFiles(docRevoke.getId());
        Set<String> sourceKeys = new HashSet<>();
        sourceFiles.forEach(f -> sourceKeys.add(fileKey(f)));
        Set<String> targetKeys = new HashSet<>();
        targetFiles.forEach(f -> targetKeys.add(fileKey(f)));

        List<Map<String, Object>> newFiles = new ArrayList<>();
        Set<String> newFilenames = new HashSet<>();
        for (FileDto f : sourceFiles) {
            if (f.getOriginalFileName() != null && !targetKeys.contains(fileKey(f))) {
                newFiles.add(fileRef(f, true));
                newFilenames.add(f.getOriginalFileName());
            }
        }
        List<Map<String, Object>> keptFiles = new ArrayList<>();
        List<Map<String, Object>> supersededFiles = new ArrayList<>();
        List<UUID> supersededIds = new ArrayList<>();
        for (FileDto f : targetFiles) {
            if (sourceKeys.contains(fileKey(f))) {
                keptFiles.add(fileRef(f, false));
            } else if (!sourceFiles.isEmpty() && f.getFileId() != null) {
                supersededFiles.add(fileRef(f, true));
                supersededIds.add(f.getFileId());
            }
        }

        if (!newFilenames.isEmpty()) {
            var copyResult = copyDocumentFilesUseCase.execute(CopyDocumentFilesRequest.builder()
                .sourceDocumentId(documentC.getId())
                .targetDocumentId(docRevoke.getId())
                .module(COPY_MODULE)
                .filenames(newFilenames)
                .build());
            if (!copyResult.isSuccess()) {
                log.warn("Copy tep dinh kem tu van ban den {} sang {} ({}) that bai: {} - KHONG thay the tep cu",
                    documentC.getId(), docRevoke.getId(), action, copyResult.getError());
                // Copy loi: giu nguyen tep cu hien hanh, lich su van ghi nhan dung tinh trang.
                keptFiles.addAll(supersededFiles);
                supersededFiles.clear();
                supersededIds.clear();
                newFiles.clear();
            }
        }

        Map<String, Object> updateObj = new LinkedHashMap<>();
        updateObj.put("source", buildSourceSnapshot(documentC, documentFieldsOfC));
        List<Map<String, Object>> sourceFileRefs = new ArrayList<>();
        sourceFiles.forEach(f -> sourceFileRefs.add(fileRef(f, true)));
        updateObj.put("source_files", sourceFileRefs);
        updateObj.put("new_files", stripFileIds(newFiles));
        updateObj.put("kept_files", keptFiles);
        updateObj.put("superseded_files", supersededFiles);
        updateObj.put("changed_fields", changedFields);

        var history = new VbdenUpdateHistoryDocument(docRevoke.getId(), documentC.getId(), action,
            toJson(updateObj), resolveFullName(actingUsername), actingUsername);
        if (!supersededIds.isEmpty()) {
            supersedeDocumentFilesUseCase.execute(docRevoke.getId(), supersededIds, history.getId());
        }
        vbdenUpdateHistoryDocumentRepository.save(history);
    }

    /** Thông tin chính của văn bản nguồn TẠI THỜI ĐIỂM cập nhật (C tự huỷ sau đó ở nhánh "2"). */
    private Map<String, Object> buildSourceSnapshot(BusinessDocument documentC, Map<String, Object> fieldsOfC) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", documentC.getId());
        for (String key : SOURCE_SNAPSHOT_FIELDS) {
            Object value = fieldsOfC.get(key);
            if (value != null && !asString(value).isBlank()) {
                source.put(key, value);
            }
        }
        source.putIfAbsent(SUBJECT_FIELD, documentC.getTitle());
        return source;
    }

    // file_id CHI giu cho tep con tai duoc (tep cua C - source_files) va de truy vet noi bo (superseded -
    // FE KHONG dung de tai, file da bi an o BE). new_files chi can ten/kich thuoc (ban sao tren docRevoke
    // da hien trong danh sach tep chinh).
    private Map<String, Object> fileRef(FileDto f, boolean withId) {
        Map<String, Object> ref = new LinkedHashMap<>();
        if (withId) {
            ref.put("file_id", f.getFileId());
        }
        ref.put("name", f.getOriginalFileName());
        ref.put("size", f.getFileSize());
        return ref;
    }

    private List<Map<String, Object>> stripFileIds(List<Map<String, Object>> refs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> ref : refs) {
            Map<String, Object> copy = new LinkedHashMap<>(ref);
            copy.remove("file_id");
            result.add(copy);
        }
        return result;
    }

    private String resolveFullName(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return appUserRepository.findByUsernameAndDeletedOnIsNull(username)
            .map(com.td.domain.sharedcore.AppUser::getFullName)
            .orElse(null);
    }

    private List<FileDto> listFiles(UUID documentId) {
        var response = listDocumentFilesUseCase.execute(documentId);
        return response == null || response.getItems() == null ? List.of() : response.getItems();
    }

    private static String fileKey(FileDto f) {
        return f.getOriginalFileName() + "|" + f.getFileSize();
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
