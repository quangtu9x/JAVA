package com.td.application.duthao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * BL-230 (DuThao Core) — DT-001 (tài liệu phân tích 04 mục 12, "GAP P0"): xử lý chuyển trạng thái
 * dự thảo (trình duyệt/ký nháy/ký duyệt/trả lại/thu hồi/chuyển người khác/kết thúc/đánh giá), đối
 * chiếu trực tiếp {@code BE_THANHHOA/.../duthao/DraftProcesses.java#DraftProcessing()}.
 *
 * <p><b>Phạm vi đã port</b> (đúng field mutation của từng nhánh {@code duthao_status} trong
 * {@code DraftProcessing()}):</p>
 * <ul>
 *   <li>Trước mọi nhánh: nếu {@code user_process_handler} hiện tại == người đang thao tác, tự thêm
 *   vào {@code user_processed} (dxl/DraftProcesses.java dòng 338-345, {@code DraftProcess_Handler()}).</li>
 *   <li>Luôn ghi {@code status_pre} = trạng thái HIỆN TẠI (trước khi ghi đè) — dòng 80.</li>
 *   <li>{@link DuThaoStatus#PENDING_SIGNATURE} (chuyenkyduyet): nếu {@code signer_obj} có và
 *   {@code user_signer} đang rỗng, tự parse JSON lấy {@code user_signer} từ đó — dòng 84-99.</li>
 *   <li>{@link DuThaoStatus#FORWARDED_FOR_APPROVAL} (chuyenduyet): set {@code process_handler}/
 *   {@code user_process_handler}/{@code user_processed_handler} — dòng 101-106 (KHÔNG port 2 dòng
 *   {@code createDocumentReaderAuthor} — thuần ACL Domino, không có khái niệm tương đương Postgres).</li>
 *   <li>{@link DuThaoStatus#OTHER_HANDLER_SUBMIT} (chuyennguoikhac/chuyenphoihop): set
 *   {@code other_process_handler}/{@code user_other_process_handler}, nối thêm username hiện tại
 *   vào {@code user_other_processed_handler} (danh sách ";", KHÔNG dedupe — đúng Domino) — dòng 107-121.</li>
 *   <li>{@link DuThaoStatus#ENDED} (ketthuc): set {@code draft_end="1"}, {@code draft_end_date}
 *   = hôm nay (định dạng {@code dd/MM/yyyy}, khớp {@code create_date} mẫu — xem
 *   {@code docs/views_domino/data_duthao_mau.txt}) — dòng 123-128.</li>
 *   <li>{@link DuThaoStatus#DRAFTING}/{@link DuThaoStatus#RETURNED} (kynhay/tralai/chonnguoitralai/
 *   thuhoi-về-0-hoặc-2): set {@code receiver_username}; nếu trạng thái TRƯỚC đó là
 *   {@code FORWARDED_FOR_APPROVAL} thì xoá {@code process_handler}/{@code user_process_handler}/
 *   {@code status_pre}; nếu là {@code OTHER_HANDLER_SUBMIT} thì xoá {@code other_process_handler}/
 *   {@code user_other_process_handler}/{@code status_pre} — dòng 136-150.</li>
 *   <li>Với action {@code chuyenkyduyet}/{@code chuyennguoikhac} (so sánh CHÍNH XÁC chuỗi
 *   action — KHÔNG áp dụng cho {@code chuyenphoihop} dù cùng đích trạng thái): luôn xoá
 *   {@code receiver_username} còn sót từ vòng xử lý trước — dòng 210-213.</li>
 *   <li>{@code nguoichuyen_obj} (nếu request có gửi): ghi đè nguyên văn vào dự thảo — dòng 192-198.</li>
 *   <li>{@code danhgiaduthao} (status Domino {@code "99"}): CHỈ ghi {@code draft_rate}, KHÔNG đổi
 *   {@code status}/{@code status_pre}/{@code last_process_id} — đúng nhánh early-return dòng 56-61.</li>
 * </ul>
 *
 * <p><b>Cập nhật 24/08/2026 (DT-007, theo yêu cầu người dùng "thực hiện port luôn draft_process")
 * </b>: giờ CÓ tạo document con {@code draft_process} (qua {@link AddDuThaoDynamicDocumentUseCase})
 * ngay trước khi lưu document cha, và ghi id của nó vào {@code last_process_id} trên document cha —
 * đúng điểm 1-3 mục 4.2 tài liệu phân tích 04 ({@code DraftProcessing()}: "Tạo 1 document process
 * làm vết xử lý" → "Ghi lại status_pre, status, last_process_id trên document cha").</p>
 *
 * <p><b>Cập nhật thứ 2, cùng ngày (yêu cầu người dùng làm rõ lại: "tạo bảng riêng, để độc lập dữ
 * liệu" — KHÔNG phải partition)</b>: {@code duthao} giờ lưu ở bảng vật lý ĐỘC LẬP HOÀN TOÀN
 * {@code duthao_documents} (migration {@code V1.0.28__Create_DuThao_Documents_Table.sql}
 * — {@code V1.0.27} đã đảo ngược cách làm partition trước đó), qua entity/repository RIÊNG
 * ({@link DuThaoDocument}/{@link DuThaoDocumentRepository}/{@code PostgresDuThaoDocumentRepository})
 * — KHÔNG còn dùng {@code BusinessDocument}/{@code DocumentRepository}/{@code AddDynamicDocumentUseCase}
 * (bảng {@code documents} dùng chung của mọi module khác) nữa.</p>
 *
 * <p><b>Cập nhật 25/08/2026 (yêu cầu người dùng: "tách draft_process sang bảng riêng, bảng vật lý
 * như duthao_documents, không lưu chung với duthao_documents")</b>: vết xử lý
 * {@code draft_process} tách sang bảng vật lý RIÊNG {@code draft_process_documents} (migration
 * {@code V1.0.30__Create_Draft_Process_Documents_Table.sql}), ghi qua
 * {@link AddDraftProcessDocumentUseCase} — {@code duthao_documents} từ đây CHỈ chứa document chính
 * {@code document_type='duthao'}.</p>
 *
 * <p><b>CHƯA port (có bằng chứng rõ, không suy diễn)</b>:</p>
 * <ul>
 *   <li><b>Nhánh {@code danhgiaduthao}</b> (xem {@link #applyRating}) CHỦ Ý KHÔNG tạo document con
 *   {@code draft_process} — đúng bằng chứng dòng 56-61 {@code DraftProcessing()}: nhánh "99" early-
 *   return TRƯỚC đoạn tạo process document, không đụng {@code status_pre}/{@code last_process_id}.</li>
 *   <li><b>FE đính kèm file/ý kiến riêng khi Lưu</b> ({@code ChiTietPage.jsx#onFinish}, gọi
 *   {@code qlvb/add?databasetype=duthao&parentid=...} với {@code Form=draft_process},
 *   {@code action_type="gankemtep"}) — luồng RIÊNG, KHÔNG đi qua {@code XuLyDuThaoUseCase}/action
 *   nào ở đây, vẫn gọi Domino nguyên trạng. Chỉ nhánh tạo qua workflow action (chuyển/ký/trả lại/...)
 *   được port trong lượt này.</li>
 *   <li><b>Thông báo/làm mới view cache</b> ({@code TDInit.setViewError}/{@code senThongBao} trong
 *   {@code DraftSignerProcessing}/{@code DraftRetrievalProcessing}/{@code DraftReturnProcessing}/
 *   {@code DraftProcess_Other_Handler_Init}) — xác nhận lại: TOÀN BỘ 4 hàm hậu-xử-lý theo
 *   {@code action} trong Domino chỉ làm 2 việc này, KHÔNG có mutation field nào khác — nên bỏ qua
 *   trọn vẹn không mất field nghiệp vụ nào (đúng tiền lệ "event/notification bổ sung sau" đã áp
 *   dụng cho vbden/vbdi/hosocv).</li>
 *   <li><b>ACL Domino</b> ({@code createDocumentReaderAuthor}, xoá item {@code reader}) — không có
 *   khái niệm tương đương ở Postgres/JSONB (quyền đọc do tầng ứng dụng Java tự kiểm soát, không
 *   theo field trên document).</li>
 *   <li><b>Tích hợp hệ thống ngoài</b> ({@code he_thong_id}/{@code mc_mahs} — "một cửa") — không có
 *   bằng chứng Java đã tích hợp hệ thống này, không suy diễn thêm.</li>
 * </ul>
 *
 * <p><b>Trạng thái đích tự suy ra từ {@code action}</b> (KHÔNG nhận {@code status}/{@code oldstatus}
 * từ request — xem javadoc {@link XuLyDuThaoRequest}): bảng ánh xạ đúng
 * {@code DuThaoActionModal.jsx#gettitle()}. Riêng {@code thuhoi} đọc {@code status_pre} hiện có
 * trên chính document (server-truth, KHÔNG tin {@code resdata.status_pre} phía client).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class XuLyDuThaoUseCase {

    public static final String DOCUMENT_TYPE = "duthao";
    // DT-007 - tu 25/08/2026 vat ly luu o bang doc lap RIENG draft_process_documents (migration
    // V1.0.30, qua AddDraftProcessDocumentUseCase - hang so DOCUMENT_TYPE o do); truoc do luu chung
    // duthao_documents (V1.0.28). Hang nay giu lai lam tai lieu doi chieu voi Domino.
    static final String DRAFT_PROCESS_DOCUMENT_TYPE = "draft_process";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản dự thảo";
    private static final String FALLBACK_USERNAME = "system";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Ten action - dxl DuThaoActionModal.jsx#gettitle().
    private static final String ACTION_CHUYEN_KY_DUYET = "chuyenkyduyet";
    private static final String ACTION_CHUYEN_KY_NHAY = "chuyenkynhay";
    private static final String ACTION_KY_DUYET = "kyduyet";
    private static final String ACTION_KY_NHAY = "kynhay";
    private static final String ACTION_TRA_LAI = "tralai";
    private static final String ACTION_CHON_NGUOI_TRA_LAI = "chonnguoitralai";
    private static final String ACTION_THU_HOI = "thuhoi";
    private static final String ACTION_CHUYEN_DUYET = "chuyenduyet";
    private static final String ACTION_CHUYEN_NGUOI_KHAC = "chuyennguoikhac";
    private static final String ACTION_CHUYEN_PHOI_HOP = "chuyenphoihop";
    private static final String ACTION_KET_THUC = "ketthuc";
    private static final String ACTION_DANH_GIA = "danhgiaduthao";

    // Action can xoa "receiver_username" con sot - DraftProcesses.java dong 210-213: dieu kien SO
    // SANH CHINH XAC chuoi action ("chuyenkyduyet"/"chuyenvanthu"/"chuyennguoikhac"), KHONG bao gom
    // "chuyenphoihop" (chuoi khac, du cung dich status OTHER_HANDLER_SUBMIT) - "chuyenvanthu" khong
    // lien quan o day vi action do khong di qua use case nay (xem javadoc lop).
    private static final Set<String> ACTIONS_CLEARING_RECEIVER_USERNAME =
        Set.of(ACTION_CHUYEN_KY_DUYET, ACTION_CHUYEN_NGUOI_KHAC);

    // Ten field dong - giu NGUYEN VAN ten Domino de tuong thich nguoc voi du lieu/queue (ViewQueryService).
    private static final String STATUS_PRE_FIELD = "status_pre";
    private static final String USER_PROCESS_HANDLER_FIELD = "user_process_handler";
    private static final String USER_PROCESSED_FIELD = "user_processed";
    private static final String USER_PROCESSED_HANDLER_FIELD = "user_processed_handler";
    private static final String PROCESS_HANDLER_FIELD = "process_handler";
    private static final String OTHER_PROCESS_HANDLER_FIELD = "other_process_handler";
    private static final String USER_OTHER_PROCESS_HANDLER_FIELD = "user_other_process_handler";
    private static final String USER_OTHER_PROCESSED_HANDLER_FIELD = "user_other_processed_handler";
    private static final String DRAFT_END_FIELD = "draft_end";
    private static final String DRAFT_END_DATE_FIELD = "draft_end_date";
    private static final String RECEIVER_USERNAME_FIELD = "receiver_username";
    private static final String SIGNER_OBJ_FIELD = "signer_obj";
    private static final String USER_SIGNER_FIELD = "user_signer";
    private static final String NGUOICHUYEN_OBJ_FIELD = "nguoichuyen_obj";
    private static final String DRAFT_RATE_FIELD = "draft_rate";
    private static final String LAST_PROCESS_ID_FIELD = "last_process_id";
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong". docParentDocument that trong Domino
    // (DraftProcesses.java) duoc luu qua .save() truc tiep, KHONG qua CreateOrUpdateDocument() - port
    // nay CHU DICH ap dung rong hon 1:1 Domino (quyet dinh nguoi dung "port day du co che... anh
    // huong moi module") de dam bao author luon phan anh dung nguoi/don vi lien quan truc tiep.
    private static final Set<String> AUTHOR_TOUCHED_FIELDS = Set.of(
        USER_PROCESS_HANDLER_FIELD, USER_PROCESSED_FIELD, USER_PROCESSED_HANDLER_FIELD,
        USER_OTHER_PROCESS_HANDLER_FIELD, USER_OTHER_PROCESSED_HANDLER_FIELD, USER_SIGNER_FIELD);

    // Field cua document con draft_process - dxl DraftProcesses.java diem 2 muc 4.2 tai lieu phan
    // tich 04 ("Doc action, status, oldstatus, receiver, receiver_username tu process document").
    private static final String PROCESS_PARENT_ID_FIELD = "parentid";
    // Bugfix 26/08/2026 (port vwGroup_process_by_draft_id): ten field THAT SU Domino ghi len process
    // document la "action_type" (khong phai "action") - doi chieu vn.com.tandan.xsp.services.vbdi.
    // XuLyDuThao.java dong 241/355 (docquatrinhduthao.replaceItemValue("action_type", ...)) VA
    // DraftProcesses.java dong 71 (action = getDocumentItemValue(doc_process, "action_type")) VA FE
    // DuThaoActionModal.jsx (formData.set("action_type", actionname)). FE doc lai qua duthaofile.jsx
    // (dataIndex "action_type", trangthai.VanBanDuThaoQuaTrinh) - gia tri (chuyenduyet/kyduyet/...)
    // KHOP dung vocabulary cua ham do, chi TEN KHOA truoc day sai ("action") khien cot "Trang thai"
    // trong tab qua trinh xu ly luon rong voi vet tao qua Java. Doi ten khoa, KHONG doi gia tri.
    private static final String PROCESS_ACTION_FIELD = "action_type";
    private static final String PROCESS_STATUS_FIELD = "status";
    private static final String PROCESS_OLD_STATUS_FIELD = "oldstatus";
    private static final String PROCESS_RECEIVER_FIELD = "receiver";
    private static final String PROCESS_OPINION_FIELD = "opinion";
    private static final String PROCESS_CREATOR_FIELD = "creator";

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    // DT-007 tách bảng (25/08/2026): vết xử lý ghi qua AddDraftProcessDocumentUseCase -> bang vat ly
    // rieng draft_process_documents (migration V1.0.30), KHONG con qua AddDuThaoDynamicDocumentUseCase
    // (bang duthao_documents chi con document chinh 'duthao').
    private final AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(XuLyDuThaoRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id văn bản dự thảo");
        }
        var documentOptional = duThaoDocumentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String action = normalizeAction(request.getAction());
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        if (ACTION_DANH_GIA.equals(action)) {
            return applyRating(document, fields, request);
        }

        String targetStatus = resolveTargetStatus(action, fields);
        if (targetStatus == null) {
            return Result.failure("Không hỗ trợ action: " + request.getAction());
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        var before = buildSnapshot(document);
        String previousStatus = document.getStatus();

        applySelfProcessedMarker(fields, actingUsername);
        fields.put(STATUS_PRE_FIELD, previousStatus);
        createDraftProcessRecord(document.getId(), action, targetStatus, previousStatus, request, actingUsername)
            .ifPresent(processId -> fields.put(LAST_PROCESS_ID_FIELD, processId));
        applyTargetStatusMutations(targetStatus, previousStatus, fields, request, actingUsername);
        if (ACTIONS_CLEARING_RECEIVER_USERNAME.contains(action)) {
            fields.remove(RECEIVER_USERNAME_FIELD);
        }
        if (request.getNguoiChuyenObj() != null && !request.getNguoiChuyenObj().isBlank()) {
            fields.put(NGUOICHUYEN_OBJ_FIELD, request.getNguoiChuyenObj());
        }

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        document.replaceDynamicFields(toJson(fields));
        document.update(null, null, targetStatus, null, null, null, null);
        var saved = duThaoDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        return Result.success(saved.getId());
    }

    // dxl DraftProcesses.java dong 56-61: nhanh "99" CHI ghi draft_rate, KHONG dong cham status/
    // status_pre/last_process_id - tra ve SOM, khong di qua bat ky mutation nao khac ben duoi.
    private Result<UUID> applyRating(DuThaoDocument document, Map<String, Object> fields, XuLyDuThaoRequest request) {
        var before = buildSnapshot(document);
        fields.put(DRAFT_RATE_FIELD, request.getRating());
        document.replaceDynamicFields(toJson(fields));
        var saved = duThaoDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        return Result.success(saved.getId());
    }

    private String resolveTargetStatus(String action, Map<String, Object> fieldsBeforeMutation) {
        return switch (action) {
            case ACTION_CHUYEN_KY_DUYET -> DuThaoStatus.PENDING_SIGNATURE;
            case ACTION_CHUYEN_KY_NHAY -> DuThaoStatus.PRE_SIGN_SUBMIT;
            case ACTION_KY_DUYET -> DuThaoStatus.SIGNED_WAITING_SUBMIT;
            case ACTION_KY_NHAY -> DuThaoStatus.DRAFTING;
            case ACTION_TRA_LAI, ACTION_CHON_NGUOI_TRA_LAI -> DuThaoStatus.RETURNED;
            case ACTION_CHUYEN_DUYET -> DuThaoStatus.FORWARDED_FOR_APPROVAL;
            case ACTION_CHUYEN_NGUOI_KHAC, ACTION_CHUYEN_PHOI_HOP -> DuThaoStatus.OTHER_HANDLER_SUBMIT;
            case ACTION_KET_THUC -> DuThaoStatus.ENDED;
            case ACTION_THU_HOI -> resolveThuHoiTargetStatus(fieldsBeforeMutation);
            default -> null;
        };
    }

    // Domino: settrangthai(resdata?.status_pre || "0") - o day doc thang status_pre da luu tren
    // chinh document (server-truth) thay vi tin gia tri resdata phia client (xem javadoc lop).
    private String resolveThuHoiTargetStatus(Map<String, Object> fieldsBeforeMutation) {
        Object statusPre = fieldsBeforeMutation.get(STATUS_PRE_FIELD);
        String value = statusPre == null ? "" : String.valueOf(statusPre).trim();
        return value.isBlank() ? DuThaoStatus.DRAFTING : value;
    }

    // DraftProcesses.java dong 338-345 (DraftProcess_Handler()) - chay TRUOC moi nhanh status,
    // khong phu thuoc action/target status.
    private void applySelfProcessedMarker(Map<String, Object> fields, String actingUsername) {
        Object handler = fields.get(USER_PROCESS_HANDLER_FIELD);
        if (handler == null || !actingUsername.equals(String.valueOf(handler).trim())) {
            return;
        }
        Object processedRaw = fields.get(USER_PROCESSED_FIELD);
        String processed = (processedRaw == null || String.valueOf(processedRaw).isBlank())
            ? actingUsername
            : processedRaw + ";" + actingUsername;
        fields.put(USER_PROCESSED_FIELD, processed);
    }

    private void applyTargetStatusMutations(
            String targetStatus, String previousStatus, Map<String, Object> fields,
            XuLyDuThaoRequest request, String actingUsername) {
        switch (targetStatus) {
            case DuThaoStatus.PENDING_SIGNATURE -> applySignerFromSignerObjIfMissing(fields);
            case DuThaoStatus.FORWARDED_FOR_APPROVAL -> {
                fields.put(PROCESS_HANDLER_FIELD, request.getReceiver());
                fields.put(USER_PROCESS_HANDLER_FIELD, request.getReceiverUsername());
                fields.put(USER_PROCESSED_HANDLER_FIELD, actingUsername);
            }
            case DuThaoStatus.OTHER_HANDLER_SUBMIT -> {
                fields.put(OTHER_PROCESS_HANDLER_FIELD, request.getReceiver());
                fields.put(USER_OTHER_PROCESS_HANDLER_FIELD, request.getReceiverUsername());
                Object existing = fields.get(USER_OTHER_PROCESSED_HANDLER_FIELD);
                String appended = (existing == null || String.valueOf(existing).isBlank())
                    ? actingUsername
                    : existing + ";" + actingUsername;
                fields.put(USER_OTHER_PROCESSED_HANDLER_FIELD, appended);
            }
            case DuThaoStatus.ENDED -> {
                fields.put(DRAFT_END_FIELD, "1");
                fields.put(DRAFT_END_DATE_FIELD, DATE_FORMATTER.format(LocalDate.now()));
            }
            case DuThaoStatus.DRAFTING, DuThaoStatus.RETURNED -> {
                fields.put(RECEIVER_USERNAME_FIELD, request.getReceiverUsername());
                if (DuThaoStatus.FORWARDED_FOR_APPROVAL.equals(previousStatus)) {
                    fields.remove(PROCESS_HANDLER_FIELD);
                    fields.remove(USER_PROCESS_HANDLER_FIELD);
                    fields.remove(STATUS_PRE_FIELD);
                } else if (DuThaoStatus.OTHER_HANDLER_SUBMIT.equals(previousStatus)) {
                    fields.remove(OTHER_PROCESS_HANDLER_FIELD);
                    fields.remove(USER_OTHER_PROCESS_HANDLER_FIELD);
                    fields.remove(STATUS_PRE_FIELD);
                }
            }
            default -> {
                // SIGNED_WAITING_SUBMIT(3) va cac target khac: khong co mutation them ngoai
                // status/status_pre da xu ly chung o execute().
            }
        }
    }

    // DraftProcesses.java dong 84-99: chi ap dung khi user_signer dang RONG va signer_obj co gia
    // tri - try/catch nuot loi parse JSON (giu nguyen user_signer rong neu signer_obj khong hop le).
    private void applySignerFromSignerObjIfMissing(Map<String, Object> fields) {
        Object signerObjRaw = fields.get(SIGNER_OBJ_FIELD);
        Object userSigner = fields.get(USER_SIGNER_FIELD);
        boolean userSignerBlank = userSigner == null || String.valueOf(userSigner).isBlank();
        if (signerObjRaw == null || String.valueOf(signerObjRaw).isBlank() || !userSignerBlank) {
            return;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(String.valueOf(signerObjRaw));
            String user = node.path("user").asText("");
            if (!user.isBlank()) {
                fields.put(USER_SIGNER_FIELD, user);
            }
        } catch (Exception ignored) {
            // xem javadoc method
        }
    }

    // DT-007: "Tao 1 document process lam vet xu ly" (diem 1 muc 4.2 tai lieu phan tich 04) - tu
    // 25/08/2026 ghi qua AddDraftProcessDocumentUseCase vao bang vat ly RIENG draft_process_documents
    // (migration V1.0.30; truoc do luu chung duthao_documents qua AddDuThaoDynamicDocumentUseCase).
    // Loi tao process document KHONG duoc lam hong ca giao dich chinh (Optional.empty() - execute()
    // van tiep tuc luu document cha, chi thieu last_process_id lan nay) - dung Domino thuc: process
    // document la VET GHI LAI, khong phai dieu kien tien quyet de chuyen trang thai thanh cong.
    private Optional<String> createDraftProcessRecord(
            UUID draftId, String action, String targetStatus, String previousStatus,
            XuLyDuThaoRequest request, String actingUsername) {
        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setStatus(targetStatus);
            processRequest.setTitle("Xử lý dự thảo: " + action);

            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put(PROCESS_PARENT_ID_FIELD, draftId.toString());
            processFields.put(PROCESS_ACTION_FIELD, action);
            processFields.put(PROCESS_STATUS_FIELD, targetStatus);
            putIfNotBlank(processFields, PROCESS_OLD_STATUS_FIELD, previousStatus);
            putIfNotBlank(processFields, RECEIVER_USERNAME_FIELD, request.getReceiverUsername());
            putIfNotBlank(processFields, PROCESS_RECEIVER_FIELD, request.getReceiver());
            putIfNotBlank(processFields, PROCESS_OPINION_FIELD, request.getOpinion());
            processFields.put(PROCESS_CREATOR_FIELD, actingUsername);
            processRequest.setFields(processFields);

            var result = addDraftProcessDocumentUseCase.execute(processRequest);
            return result.isSuccess() ? Optional.of(result.getData().toString()) : Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void putIfNotBlank(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }

    private String normalizeAction(String action) {
        return action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> buildSnapshot(DuThaoDocument document) {
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
