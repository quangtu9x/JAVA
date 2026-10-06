package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documentlinks.DocumentLinkService;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.CopyDocumentFilesRequest;
import com.td.application.documents.CopyDocumentFilesUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.AddVbdiDynamicDocumentUseCase;
import com.td.application.vbdi.VbdiStatus;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.documentlinks.DocumentLinkType;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BL-230 (DuThao Core) — DoD (3): chuyển dự thảo thành văn bản đi. Đúng cơ chế thật
 * ({@code DraftToDocumentOut.CopyDraftToDocumentOut()}): tạo 1 document {@code vbdi} mới
 * (Domino: {@code Form="DocumentOut"}, {@code status="0"} — ở đây {@link VbdiStatus#DRAFT}),
 * copy field từ dự thảo, set {@code draft_id} trỏ ngược; rồi đánh dấu dự thảo gốc
 * {@code document_out_id}=id văn bản đi mới + trạng thái {@link DuThaoStatus#PUBLISHED} (Domino:
 * {@code status="4"}).
 *
 * <p><b>Bảo vệ chống tạo trùng</b>: đúng guard thật (dòng ~50-55 {@code CopyDraftToDocumentOut()})
 * — nếu dự thảo đã có {@code document_out_id}, trả về NGAY id đã có (idempotent), không tạo văn
 * bản đi thứ 2.
 *
 * <p><b>Đính chính 27/08/2026 (yêu cầu người dùng "kiểm tra dữ liệu đang copy nguyên của dự thảo
 * sang, port lại cho đúng")</b>: bản trước đó copy TOÀN BỘ field động của dự thảo trừ 1 blocklist
 * nhỏ — SAI, không khớp Domino thật. Đối chiếu lại nguyên văn {@code DraftToDocumentOut.java} dòng
 * 93-148: Domino chỉ copy đúng {@link #DUTHAO_WHITELIST_FIELDS} (49 field cố định) qua vòng lặp
 * {@code itemsToCopy}, CỘng thêm 1 số field gán tay RIÊNG BIỆT ngay sau đó (dòng 160-359, xem
 * {@link #applyExplicitFieldMappings}) — KHÔNG copy field nào khác dự thảo có (vd
 * {@code user_coordinator}/{@code opinion}/mọi field nội bộ khác) dù chúng không nằm trong
 * blocklist cũ. Đã sửa lại đúng theo whitelist thật thay vì blocklist suy diễn.</p>
 *
 * <p><b>Field KHÔNG port (có bằng chứng rõ, không phải bỏ sót)</b>:</p>
 * <ul>
 *   <li>(ĐÍNH CHÍNH 06/10/2026: mục này từng ghi "không port {@code *_rtext}/{@code *_obj_log_rtext}"
 *   — SAI, FE vbdi đọc chính các field đó cho tab Phát hành; nay đã port, xem
 *   {@code applyRecipientMappings}.)</li>
 *   <li>{@code unit_publish_identifier_other} — CHỈ port nhánh copy trực tiếp field đã có sẵn trên
 *   dự thảo (dòng 341-344); KHÔNG port nhánh fallback tra cứu chéo database {@code danh_muc_chung}
 *   theo {@code unit_publish_id} (dòng 345-355) — cần 1 catalog/DB riêng chưa có evidence tương ứng
 *   trong data model Java hiện tại.</li>
 *   <li>Reader/Author ACL ({@code createDocumentReaderAuthor}, {@code DefaultAuthors}) — khái niệm
 *   bảo mật cấp document của Notes, không áp dụng cho RBAC/JWT của Java (quyết định đã chốt từ đợt
 *   hardening MinIO 27/08/2026, xem {@code docs/analysis/04-...} mục 9).</li>
 *   <li>{@code TDInit.setViewError(...)}/{@code senThongBao(...)} (làm mới cache view + thông báo
 *   socket cho văn thư) — đã khảo sát lại: Postgres/JPA đọc trực tiếp không có khái niệm view-index
 *   cần refresh ({@link DocumentCacheService#evict}/{@link DocumentCacheService#evictAllListCaches}
 *   đã là tương đương đủ dùng); hạ tầng socket/realtime CHƯA tồn tại trong toàn bộ {@code JAVA/}
 *   (không riêng gì use case này — cùng gap đã ghi nhận ở {@code LuuVanBanDenUseCase}/vbdi/duthao
 *   khác) nên không có gì để gọi, giữ nguyên là gap đã biết.</li>
 *   <li>Reindex Elasticsearch ({@code elastic.CreateOrUpdateDocument(...)}) — chưa use case
 *   {@code duthao}/{@code vbdi} nào khác gọi bước này, không tự thêm riêng ở đây để tránh lệch hành
 *   vi so với phần còn lại của module.</li>
 * </ul>
 *
 * <p><b>Đã port thêm (đợt đính chính này, có bằng chứng rõ + hạ tầng Java sẵn có)</b>:</p>
 * <ul>
 *   <li><b>Copy tệp đính kèm</b> — CHỈ copy tệp có tên nằm trong {@code tepbanhanh}/
 *   {@code tepkemtheo} qua {@link CopyDocumentFilesUseCase} (xem đính chính lần 2 ở javadoc
 *   {@link CopyDocumentFilesRequest}: Domino {@code duthao_file} là 1 document RIÊNG chỉ chứa đúng
 *   bộ tệp chính thức nên "copy toàn bộ duthao_file" ở Domino đã tương đương "chỉ copy tệp chính
 *   thức" — ở Java {@code file_metadata.document_id} dùng CHUNG cho cả tệp chính thức và tệp gắn
 *   theo từng vết xử lý qua {@code process_id} nên phải LỌC theo tên mới tái tạo đúng Ý ĐỊNH gốc,
 *   không phải copy nguyên theo document_id). Lỗi copy KHÔNG chặn nghiệp vụ chính (log rồi bỏ qua).</li>
 *   <li><b>{@code draft_end}/{@code draft_end_date}/xoá {@code reader}</b> (Domino dòng 463-473) —
 *   đánh dấu dự thảo đã kết thúc + xoá field ACL cũ nếu còn sót dữ liệu cũ.</li>
 *   <li><b>Đồng bộ {@code idvbdi} cho nhiệm vụ CĐĐH liên kết</b> (Domino dòng 477-503, view
 *   {@code vwNhiem_Vu_By_Idvanban}) — {@link #syncVbcddhIdVbdi} dùng
 *   {@code DocumentRepository#findByFieldValue("vbcddh", "idvanban", draftId, ...)} (đúng pattern
 *   đã dùng ở {@code VbcddhStatusRollupService}), set {@code idvbdi} trên từng nhiệm vụ con.</li>
 *   <li><b>Ghi vết draft_process</b> (Domino: {@code CreateOrUpdateDocument()} tạo document
 *   {@code Form="draft_process"} TRƯỚC khi copy sang vbdi) — ghi qua
 *   {@link AddDraftProcessDocumentUseCase} cùng field convention với
 *   {@code XuLyDuThaoUseCase#createDraftProcessRecord} ({@code action_type=chuyenvanthu},
 *   {@code receiver="Văn thư"} ép cứng — đúng {@code DuThaoActionModal.jsx} dòng ~311).</li>
 *   <li><b>{@code form="DocumentOut"}</b> (Domino dòng 160: {@code copyToDoc.replaceItemValue("Form",
 *   "DocumentOut")}, gán CỨNG không điều kiện — khác {@code documentType} là cột entity luôn cố
 *   định {@code "vbdi"}; {@code form} là field ĐỘNG riêng của văn bản đi, dùng ở nơi khác trong
 *   Domino thật, vd {@code RevokeDocumentOut} đổi thành {@code "..._Deleted"} khi thu hồi/xoá) — bị
 *   bỏ sót ở lần port đầu, đã bổ sung.</li>
 * </ul>
 *
 * <p>Không sinh {@code document_number} ở bước này — đúng bằng chứng thật (không có logic numbering
 * trong {@code CopyDraftToDocumentOut()}); số đi vẫn được sinh ở bước ban hành (BL-220,
 * {@code BanHanhVbdiUseCase}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChuyenThanhVanBanDiUseCase {

    public static final String DOCUMENT_TYPE = "duthao";
    public static final String TARGET_DOCUMENT_TYPE = "vbdi";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy dự thảo";
    private static final String DOCUMENT_OUT_ID_FIELD = "document_out_id";
    private static final String DRAFT_ID_FIELD = "draft_id";
    private static final String DRAFT_END_FIELD = "draft_end";
    private static final String DRAFT_END_DATE_FIELD = "draft_end_date";
    private static final String READER_FIELD = "reader";
    // Dung de loc tep dinh kem sang van ban di - xem javadoc lop + CopyDocumentFilesRequest.
    private static final String TEP_BAN_HANH_FIELD = "tepbanhanh";
    private static final String TEP_KEM_THEO_FIELD = "tepkemtheo";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Dung nguyen van 49 field trong itemsToCopy[] cua DraftToDocumentOut.java dong 93-148 -
    // KHONG suy dien them/bot. Thu tu khong quan trong (chi dung de kiem tra membership).
    private static final Set<String> DUTHAO_WHITELIST_FIELDS = Set.of(
        "unit_creator", "unit_creator_id", "document_sign_method", "document_sector", "document_type",
        "priority", "confidentiality", "unit_publish", "signer", "signer_id", "user_signer",
        "signer_position", "subject", "recipient_other", "unit_in_charge", "unit_in_charge_identifier",
        "unit_identifier", "assignment_of_directive_document", "tinhtrangphancong", TEP_KEM_THEO_FIELD,
        TEP_BAN_HANH_FIELD, "tepphieutrinh", "he_thong_id", "he_thong", "lien_thong", "mc_mahs",
        "mc_thamsoan", "mc_madv", "mc_id", "mc_thutuc", "creator_cccd", "signer_cccd", "ldkyso",
        "phathanhkemvbden", "list_tvub_obj", "list_tvnb_obj", "is_tvub_opinion", "is_tvnb_opinion",
        "is_btv_opinion", "is_bch_opinion", "is_ttdu_opinion", "deadline_date", "archives_info",
        "category", "assignment_of_ubnd_document",
        "ketthucnhiemvuubtinh", "ketthucnhiemvuubtinh_id");

    // Field gan tay RIENG BIET sau vong lap whitelist (DraftToDocumentOut.java dong 160-359) - moi
    // dong la 1 cap (field nguon tren du thao -> field dich tren vbdi), doc dieu kien "hasItem" that.
    private static final String NAME_FIELD = "name";
    private static final String USER_CREATOR_FIELD = "user_creator";
    private static final String CREATOR_FIELD = "creator";
    private static final String CREATOR_ID_FIELD = "creator_id";
    private static final String UNIT_PUBLISH_ID_FIELD = "unit_publish_id";
    private static final String UNIT_PUBLISH_IDENTIFIER_FIELD = "unit_publish_identifier";
    private static final String UNIT_PUBLISH_IDENTIFIER_OTHER_FIELD = "unit_publish_identifier_other";
    private static final String DRAFT_IDENTIFIER_ID_FIELD = "draft_identifier_id";
    private static final String USER_DRAFT_FIELD = "user_draft";
    private static final String DRAFTOR_FIELD = "draftor";
    private static final String DRAFTOR_ID_FIELD = "draftor_id";
    private static final String RECIPIENTS_COPY_FIELD = "recipients_copy";
    // Domino dong 160: copyToDoc.replaceItemValue("Form", "DocumentOut") - gan CUNG (khong dieu
    // kien hasItem nhu cac field khac), khac voi documentType (cot entity, luon la "vbdi") - "form"
    // la field DONG rieng cua VAN BAN DI, dung o noi khac (vd RevokeDocumentOut doi thanh
    // "..._Deleted" khi thu hoi/xoa).
    private static final String FORM_FIELD = "form";
    private static final String FORM_DOCUMENT_OUT_VALUE = "DocumentOut";

    // Cung field convention voi XuLyDuThaoUseCase#createDraftProcessRecord - xem javadoc lop.
    private static final String PROCESS_PARENT_ID_FIELD = "parentid";
    private static final String PROCESS_ACTION_FIELD = "action_type";
    private static final String PROCESS_STATUS_FIELD = "status";
    private static final String PROCESS_OLD_STATUS_FIELD = "oldstatus";
    private static final String PROCESS_RECEIVER_FIELD = "receiver";
    private static final String PROCESS_OPINION_FIELD = "opinion";
    private static final String PROCESS_CREATOR_FIELD = "creator";
    private static final String ACTION_CHUYEN_VAN_THU = "chuyenvanthu";
    private static final String RECEIVER_VAN_THU = "Văn thư";
    private static final String FALLBACK_USERNAME = "system";

    // vwNhiem_Vu_By_Idvanban (Domino) - cung "documents" dung chung (BusinessDocument), documentType
    // "vbcddh", khoa boi field dong "idvanban" = id van ban goc (o day la id du thao CHA).
    private static final String VBCDDH_DOCUMENT_TYPE = "vbcddh";
    private static final String VBCDDH_IDVANBAN_FIELD = "idvanban";
    private static final String VBCDDH_IDVBDI_FIELD = "idvbdi";
    private static final int MAX_VBCDDH_SIBLINGS = 2000;

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DocumentLinkService documentLinkService;
    private final com.td.application.documentlinks.RelatedDocumentSnapshots relatedDocumentSnapshots;
    private final AddVbdiDynamicDocumentUseCase addVbdiDynamicDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final CopyDocumentFilesUseCase copyDocumentFilesUseCase;
    private final AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentRepository documentRepository;
    // 25/09/2026: creator = tên đầy đủ, user_creator = tài khoản (xem javadoc DraftProcessActorFields).
    private final DraftProcessActorFields draftProcessActorFields;

    public Result<UUID> execute(ChuyenThanhVanBanDiRequest request) {
        var draftOptional = duThaoDocumentRepository.findById(request.getId());
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var draft = draftOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(draft.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> draftFields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        String existingDocumentOutId = asString(draftFields.get(DOCUMENT_OUT_ID_FIELD));
        if (existingDocumentOutId != null && !existingDocumentOutId.isBlank()) {
            try {
                return Result.success(UUID.fromString(existingDocumentOutId));
            } catch (IllegalArgumentException ignored) {
                // du lieu cu khong phai UUID hop le - tiep tuc tao moi thay vi loi
            }
        }
        String previousStatus = draft.getStatus();

        var targetResult = createVbdiDocument(draft, draftFields);
        if (!targetResult.isSuccess()) {
            return targetResult;
        }
        UUID vbdiId = targetResult.getData();

        markDraftAsPublished(draft, draftFields, vbdiId);
        copyAttachedFiles(draft.getId(), vbdiId, draftFields);
        syncVbcddhIdVbdi(draft.getId(), vbdiId);
        linkIssuedDocument(draft.getId(), vbdiId);
        writeProcessTrail(draft.getId(), previousStatus, request.getOpinion());

        return targetResult;
    }

    // 06/10/2026 (bang document_links - docs/architecture/document-links.md): du thao -> van ban di
    // (duthao_vbdi) + moi ho so dang chua du thao -> van ban di nay (hoso_vbdi_phathanh, thay field cu
    // document_profiles_from_draft_id) - de tab "Van ban phat hanh" cua ho so hien van ban di.
    // draft_id/document_out_id van ghi vi la field nghiep vu (thu hoi, ket thuc du thao... doc).
    private void linkIssuedDocument(UUID draftId, UUID vbdiId) {
        documentLinkService.link(DocumentLinkType.DUTHAO_VBDI, draftId, vbdiId);
        // Giai doan 2: "Tra loi cho van ban den"/"Van ban kem theo" (truoc chep qua whitelist
        // documentin_obj/vanban_lienquan_obj) - chep lien ket + ban chup sang van ban di.
        relatedDocumentSnapshots.copyFromDraftToVbdi(draftId, vbdiId);
        for (UUID hosocvId : documentLinkService.findSourceIds(draftId, List.of(DocumentLinkType.HOSO_DUTHAO))) {
            documentLinkService.link(DocumentLinkType.HOSO_VBDI_PHATHANH, hosocvId, vbdiId);
        }
    }

    // Xem dinh chinh lan 2 o javadoc lop + CopyDocumentFilesRequest: LOC theo ten tep trong
    // tepbanhanh/tepkemtheo - KHONG copy toan bo theo document_id (khac Domino "copyAllItems" ve
    // mat co che, nhung DUNG Y DINH that vi duthao_file phia Domino von chi chua dung bo tep chinh
    // thuc). Loi KHONG chan nghiep vu chinh.
    private void copyAttachedFiles(UUID draftId, UUID vbdiId, Map<String, Object> draftFields) {
        try {
            Set<String> filenames = new LinkedHashSet<>();
            filenames.addAll(splitFilenames(draftFields.get(TEP_BAN_HANH_FIELD)));
            filenames.addAll(splitFilenames(draftFields.get(TEP_KEM_THEO_FIELD)));
            if (filenames.isEmpty()) {
                // Khong co ten tep hop le nao trong 2 field phan loai - khong copy gi (KHAC "khong
                // truyen filter" cua CopyDocumentFilesUseCase, von co nghia "copy TOAN BO").
                return;
            }

            copyDocumentFilesUseCase.execute(CopyDocumentFilesRequest.builder()
                .sourceDocumentId(draftId)
                .targetDocumentId(vbdiId)
                .module(TARGET_DOCUMENT_TYPE)
                .filenames(filenames)
                .build());
        } catch (Exception ex) {
            log.error("Copy tep dinh kem tu du thao {} sang van ban di {} that bai", draftId, vbdiId, ex);
        }
    }

    // "a.pdf;b.docx;" (quy uoc FE, xem ChiTietPage.jsx#handleRemoveFromClassification) -> danh sach
    // ten tep da trim, bo phan tu rong.
    private List<String> splitFilenames(Object fieldValue) {
        if (fieldValue == null) {
            return List.of();
        }
        String raw = fieldValue.toString();
        if (raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : raw.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    // Domino dong 477-503 (view vwNhiem_Vu_By_Idvanban) - cap nhat idvbdi tren tung nhiem vu CDDH
    // con tro ve du thao nay. Loi KHONG chan nghiep vu chinh (dong bo phu, khong phai du lieu loi).
    private void syncVbcddhIdVbdi(UUID draftId, UUID vbdiId) {
        try {
            Pageable pageable = PageRequest.of(0, MAX_VBCDDH_SIBLINGS);
            List<BusinessDocument> nhiemVuList = documentRepository
                .findByFieldValue(VBCDDH_DOCUMENT_TYPE, VBCDDH_IDVANBAN_FIELD, draftId.toString(), pageable)
                .getContent();
            for (BusinessDocument nhiemVu : nhiemVuList) {
                Map<String, Object> fields = new LinkedHashMap<>(
                    dynamicDocumentFieldsService.parseDynamicFields(nhiemVu.getDynamicFieldsJson()));
                fields.put(VBCDDH_IDVBDI_FIELD, vbdiId.toString());
                nhiemVu.replaceDynamicFields(toJson(fields));
                documentRepository.save(nhiemVu);
            }
        } catch (Exception ex) {
            log.error("Dong bo idvbdi cho nhiem vu CDDH cua du thao {} that bai", draftId, ex);
        }
    }

    // Xem javadoc lop, muc "Ghi vet draft_process" - loi KHONG chan nghiep vu chinh (dung nguyen
    // tac cua XuLyDuThaoUseCase#createDraftProcessRecord: vet la GHI LAI, khong phai dieu kien tien
    // quyet).
    private void writeProcessTrail(UUID draftId, String previousStatus, String opinion) {
        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle("Xử lý dự thảo: " + ACTION_CHUYEN_VAN_THU);
            processRequest.setStatus(DuThaoStatus.PUBLISHED);

            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put(PROCESS_PARENT_ID_FIELD, draftId.toString());
            processFields.put(PROCESS_ACTION_FIELD, ACTION_CHUYEN_VAN_THU);
            processFields.put(PROCESS_STATUS_FIELD, DuThaoStatus.PUBLISHED);
            if (previousStatus != null && !previousStatus.isBlank()) {
                processFields.put(PROCESS_OLD_STATUS_FIELD, previousStatus);
            }
            processFields.put(PROCESS_RECEIVER_FIELD, RECEIVER_VAN_THU);
            if (opinion != null && !opinion.isBlank()) {
                processFields.put(PROCESS_OPINION_FIELD, opinion);
            }
            draftProcessActorFields.put(processFields, currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME));
            processRequest.setFields(processFields);

            addDraftProcessDocumentUseCase.execute(processRequest);
        } catch (Exception ex) {
            log.error("Ghi vet draft_process 'chuyenvanthu' cho du thao {} that bai", draftId, ex);
        }
    }

    private Result<UUID> createVbdiDocument(DuThaoDocument draft, Map<String, Object> draftFields) {
        var targetRequest = new AddDynamicDocumentRequest();
        targetRequest.setTitle(draft.getTitle());
        targetRequest.setDocumentType(TARGET_DOCUMENT_TYPE);
        targetRequest.setStatus(VbdiStatus.DRAFT);

        Map<String, String> targetFields = new LinkedHashMap<>();
        // 1) Whitelist 49 field co dinh - dung Domino thuc (dong 93-148), KHONG copy toan bo field
        // dong nhu ban truoc (xem dinh chinh o javadoc lop).
        for (String field : DUTHAO_WHITELIST_FIELDS) {
            putIfPresent(targetFields, draftFields, field, field);
        }
        // 2) Field gan tay rieng biet sau vong lap whitelist (dong 160-359).
        targetFields.put(FORM_FIELD, FORM_DOCUMENT_OUT_VALUE);
        targetFields.put(DRAFT_ID_FIELD, draft.getId().toString());
        putIfPresent(targetFields, draftFields, NAME_FIELD, DRAFT_IDENTIFIER_ID_FIELD);
        putIfPresent(targetFields, draftFields, USER_CREATOR_FIELD, USER_DRAFT_FIELD);
        putIfPresent(targetFields, draftFields, CREATOR_FIELD, DRAFTOR_FIELD);
        putIfPresent(targetFields, draftFields, CREATOR_ID_FIELD, DRAFTOR_ID_FIELD);
        putIfPresent(targetFields, draftFields, CREATOR_FIELD, RECIPIENTS_COPY_FIELD);
        putIfPresent(targetFields, draftFields, UNIT_PUBLISH_ID_FIELD, UNIT_PUBLISH_ID_FIELD);
        putIfPresent(targetFields, draftFields, UNIT_PUBLISH_IDENTIFIER_FIELD, UNIT_PUBLISH_IDENTIFIER_FIELD);
        putIfPresent(targetFields, draftFields, UNIT_PUBLISH_IDENTIFIER_OTHER_FIELD, UNIT_PUBLISH_IDENTIFIER_OTHER_FIELD);
        // 3) Tab "Noi nhan van ban" cua du thao -> tab "Phat hanh" cua van ban di (Domino dong 168-296).
        applyRecipientMappings(targetFields, draftFields);
        // "author" (field vua ACL vua khoa view "vanbandi_by_author" — xem javadoc
        // com.td.application.documents.AuthorAclFieldsService): KHÔNG tự tính ở đây nữa (16/09/2026,
        // bản vá hẹp lúc đầu) — addVbdiDynamicDocumentUseCase.execute() bên dưới ĐÃ tự áp dụng
        // AuthorAclFieldsService (union user_draft/user_signer/identifier/SET_AUTHOR_FIELDS), tính từ
        // đúng targetFields này — tự làm lại ở đây sẽ trùng lặp logic.
        targetRequest.setFields(targetFields);

        return addVbdiDynamicDocumentUseCase.execute(targetRequest);
    }

    // 06/10/2026 (bao loi "Chuyen van thu chua copy tab Noi nhan van ban sang van ban di"): Domino
    // DraftToDocumentOut dong 168-296 doi tung nhom noi nhan "<nguon>_obj" cua du thao thanh 2 field
    // tren van ban di: "<dich>_rtext" (ten noi chuoi - tab Phat hanh hien thi) + "<dich>_obj_log_rtext"
    // (JSON danh sach da chon - FE vbdi nap lai vao modal Phat hanh/Nguoi nhan, ControllModal.jsx
    // updatevalue*). Ban port dau bo sot (javadoc lop tung ghi nham "FE khong doc richtext").
    // Ten noi bang "; " - dung quy uoc FE updatevalue* + SendVbdiDocumentUseCase#mergeRecipientNamesIntoField.
    private static final Map<String, String> RECIPIENT_FIELD_MAPPINGS = Map.of(
        "internal_unit_obj", "internal_unit",
        "external_unit_obj", "external_unit",
        "contact_unit_obj", "contact_unit",
        "individual_obj", "coordinator");

    private void applyRecipientMappings(Map<String, String> target, Map<String, Object> source) {
        RECIPIENT_FIELD_MAPPINGS.forEach((sourceKey, targetPrefix) -> {
            List<?> items = asJsonList(source.get(sourceKey));
            if (items.isEmpty()) {
                return;
            }
            List<String> names = new ArrayList<>();
            for (Object item : items) {
                if (item instanceof Map<?, ?> map && map.get(NAME_FIELD) != null) {
                    String name = map.get(NAME_FIELD).toString().trim();
                    if (!name.isEmpty()) {
                        names.add(name);
                    }
                }
            }
            target.put(targetPrefix + "_rtext", String.join("; ", names));
            target.put(targetPrefix + "_obj_log_rtext", toJsonValue(items));
        });
    }

    // Field "_obj" da duoc parse san thanh List (FieldTypeInferrer) hoac van la chuoi JSON (du lieu
    // cu) - chap nhan ca 2 dang, dang khac/loi -> rong.
    private List<?> asJsonList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                Object parsed = OBJECT_MAPPER.readValue(str, Object.class);
                return parsed instanceof List<?> list ? list : List.of();
            } catch (Exception ex) {
                return List.of();
            }
        }
        return List.of();
    }

    // Domino: "if(docParentDocument.hasItem(x)) copyToDoc.copyItem(...)" - CHI copy khi field nguon
    // TON TAI va khac rong (tuong duong hasItem tra ve gia tri that su).
    //
    // Bugfix 04/09/2026 (bao loi nguoi dung: "Chuyen van thu" lam mat documentin_obj cua van ban di
    // vua tao): field hau to "_obj" (documentin_obj/vanban_lienquan_obj/list_tvub_obj/list_tvnb_obj -
    // ca 4 deu nam trong DUTHAO_WHITELIST_FIELDS) da duoc FieldTypeInferrer suy FieldType.JSON va
    // PARSE SAN thanh List/Map that (khong con la String) tu buoc
    // dynamicDocumentFieldsService.parseDynamicFields(draft...) o execute() - goi thang value.toString()
    // tren 1 List/Map cho ra toString() mac dinh cua Java (vd "[{id=1, code=DEN-01}]", dung "=" thay
    // ":", key khong co ngoac kep) - KHONG PHAI JSON HOP LE. Chuoi rac nay duoc ghi thang vao
    // dynamic_fields van ban di moi tao, roi lai bi suy FieldType.JSON THEM 1 LAN NUA khi luu (van hau
    // to "_obj") - parse that bai, fallback giu nguyen chuoi rac (DynamicFieldValueConverter.parseJson
    // catch-all). FE doc lai (safeJSONParse/JSON.parse) that bai tren chuoi rac do - bao "mat thong
    // tin" dung nhu nguoi dung gap, dung ra la BI HONG chu khong phai bi BO SOT khi copy.
    // Sua: value KHONG phai String (da duoc parse san thanh JSON that) -> serialize LAI dung JSON qua
    // OBJECT_MAPPER (toJsonValue), KHONG goi .toString() tho.
    private void putIfPresent(Map<String, String> target, Map<String, Object> source, String sourceKey, String targetKey) {
        Object value = source.get(sourceKey);
        if (value == null) {
            return;
        }
        String stringValue = (value instanceof String str) ? str : toJsonValue(value);
        if (!stringValue.isBlank()) {
            target.put(targetKey, stringValue);
        }
    }

    private String toJsonValue(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "";
        }
    }

    private void markDraftAsPublished(DuThaoDocument draft, Map<String, Object> draftFields, UUID vbdiId) {
        var before = buildSnapshot(draft);
        Map<String, Object> updatedFields = new LinkedHashMap<>(draftFields);
        updatedFields.put(DOCUMENT_OUT_ID_FIELD, vbdiId.toString());
        // Domino dong 463-465: danh dau da ket thuc du thao (chuyen sang van ban di).
        updatedFields.put(DRAFT_END_FIELD, "1");
        updatedFields.put(DRAFT_END_DATE_FIELD, LocalDateTime.now().toString());
        // Domino dong 467-473: xoa "reader" (ACL Domino-only, khong con y nghia o Java nhung xoa
        // neu du lieu cu con sot de dung logic that).
        updatedFields.remove(READER_FIELD);

        draft.replaceDynamicFields(toJson(updatedFields));
        draft.update(null, null, DuThaoStatus.PUBLISHED, null, null, null, null);
        var saved = duThaoDocumentRepository.save(draft);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private Map<String, Object> buildSnapshot(DuThaoDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
