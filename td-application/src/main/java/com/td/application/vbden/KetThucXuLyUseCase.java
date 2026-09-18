package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * N17 (04/09/2026 — "Kết thúc xử lý", nút dùng chung bởi hầu hết màn xử lý vbden — xem
 * {@code ChoYKienModal.jsx} với {@code action="ketthucxuly"}, mở từ {@code VBDenButtonActionXuLy.jsx}
 * và các file {@code ItemsActiveList*.jsx}/{@code ItemsTVUB.jsx}/{@code ItemsListGroup.jsx} khác):
 * tương đương {@code CapNhatVanBanDen.java} thật, hàm {@code ketthucxuly()} (dòng 568-620).
 *
 * <p><b>Thuật toán ({@code ketthucxuly()} thật)</b>:</p>
 * <ol>
 *   <li>Loại {@code actingUsername} thẳng khỏi {@code user_process}, {@code user_receiver},
 *   {@code user_processing}, {@code user_coordinate}, {@code user_to_read},
 *   {@code user_distributor}, {@code user_returned} — KHÔNG điều kiện theo đơn vị (khác hẳn
 *   {@link CapNhatKetQuaXuLyUseCase}, chỉ loại CHÍNH người đang thao tác, không loại người khác).</li>
 *   <li>Thêm {@code actingUsername} vào {@code user_processed} (không trùng lặp).</li>
 *   <li>Tính lại {@code status} theo công thức chung ({@link VbdenStatusService}, giống
 *   {@link CapNhatKetQuaXuLyUseCase#recomputeStatus}/{@link XuLyLaiUseCase#recomputeStatus}).</li>
 *   <li>Tạo bản ghi {@code vbden_res} mới ({@code action="ketthucxuly"}, {@code opinion},
 *   {@code creator}/{@code position} hiển thị, {@code identifier} resolve server-side — cùng quy ước
 *   {@link CapNhatKetQuaXuLyUseCase}/{@link XuLyLaiUseCase}).</li>
 * </ol>
 *
 * <p><b>Bổ sung 14/09/2026 (yêu cầu người dùng "làm luôn chức năng Gắn văn bản trả lời ở modal này
 * luôn") — port {@code processResObj()}/{@code res_obj}</b> ("gắn văn bản trả lời", tab "Sổ văn bản
 * đi"/"Văn bản đi liên quan" trong {@code ChoYKienModal.jsx}, xem {@link VbdenReplyDocumentRef}):
 * <ul>
 *   <li>Lưu NGUYÊN mảng {@code resObj} (nếu có) vào field {@code res_obj} trên bản ghi
 *   {@code vbden_res} vừa tạo (JSON, đúng khuôn Domino gốc — CHỈ để hiển thị lại ở tab "Quá trình xử
 *   lý", {@code Ketquaxuly.jsx#build_res_obj} đọc {@code item.code}).</li>
 *   <li>{@code promulgationDate} của phần tử CUỐI (nếu parse được {@code dd/MM/yyyy}) dùng làm mốc
 *   {@code ngaybanhanh} thay {@code @Today} khi tính {@code remainingDays} — ĐÚNG hành vi
 *   {@code processResObj()} thật (biến {@code ngaybanhanh} bị GHI ĐÈ mỗi vòng lặp, chỉ giá trị CUỐI
 *   còn tồn tại khi gọi {@code capNhatTrangThaiVBden()} sau đó).</li>
 *   <li><b>KHÔNG port</b> ghi ngược {@code documentins_obj} lên chính văn bản đi được chọn — xác nhận
 *   qua grep TOÀN BỘ {@code BE_THANHHOA} + FE: field này CHỈ được ĐỌC LẠI bởi chính hàm ghi ra nó
 *   ({@code processResObj()}, để nối thêm phần tử vào JSON cũ), KHÔNG có bất kỳ nơi nào khác (Domino
 *   hay FE) từng đọc field này — write-only, không có tác dụng quan sát được, không phải thiếu sót.</li>
 * </ul>
 *
 * <p><b>CHƯA port (ghi nhận có chủ đích, không suy đoán thêm — xem
 * {@code docs/analysis/02-checklist-port-vbden-con-lai.md})</b>:</p>
 * <ul>
 *   <li>Bước lọc {@code user_process}/{@code user_processing}/{@code user_processed} theo
 *   {@code diagram_obj}/{@code internal} TRƯỚC khi tính status ({@code TDDocumentUlties.
 *   capNhatTrangThaiVBden()} dòng 2123-2142 thật) — Java tính status trực tiếp trên field hiện có,
 *   không lọc lại theo sơ đồ luân chuyển (cùng cách tiếp cận D.11 "đọc live" đã dùng cho các use case
 *   khác trong module này).</li>
 *   <li>{@code processed_end_date} (set/xoá khi status chuyển 3/4) và thông báo
 *   {@code ResponseStatus.createAndSendResponseStatus()} tới đơn vị chuyển xử lý — chưa có bằng
 *   chứng FE Java đọc lại 2 field/luồng này.</li>
 *   <li>{@code TDInit.InitGiam}/{@code TDInit.setViewError} (giảm counter badge thông báo qua Redis)
 *   — thuần cơ chế thông báo/cache, không có khái niệm tương đương và KHÔNG cần port (cùng kết luận
 *   R11 đã ghi ở javadoc {@code LuuVanBanDenUseCase}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class KetThucXuLyUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_KET_THUC_XU_LY = "ketthucxuly";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_RECEIVER_FIELD = "user_receiver";
    private static final String USER_PROCESSING_FIELD = "user_processing";
    private static final String USER_COORDINATE_FIELD = "user_coordinate";
    private static final String USER_TO_READ_FIELD = "user_to_read";
    private static final String USER_DISTRIBUTOR_FIELD = "user_distributor";
    private static final String USER_RETURNED_FIELD = "user_returned";
    private static final String USER_PROCESSED_FIELD = "user_processed";
    private static final String DEADLINE_DATE_FIELD = "deadline_date";
    private static final DateTimeFormatter DEADLINE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_PROCESS_FIELD, USER_RECEIVER_FIELD, USER_PROCESSING_FIELD, USER_COORDINATE_FIELD,
        USER_TO_READ_FIELD, USER_DISTRIBUTOR_FIELD, USER_RETURNED_FIELD, USER_PROCESSED_FIELD);

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final VbdenStatusService vbdenStatusService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final com.td.application.vbdi.PublishVbdiReplyStatusUseCase publishVbdiReplyStatusUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(KetThucXuLyRequest request) {
        var parentOptional = documentRepository.findById(request.getParentid());
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var parent = parentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(parent.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (isBlank(actingUsername)) {
            return Result.failure("Khong xac dinh duoc nguoi dung hien tai");
        }

        LocalDate ngayBanHanh = resolveNgayBanHanh(request.getResObj());
        updateParentDocument(parent, actingUsername, ngayBanHanh);

        var result = createProcessingRecord(request, actingUsername);
        // Bo sung 07/09/2026 (2, yeu cau nguoi dung "lam not" STATUS_06 - doi chieu
        // CapNhatVanBanDen.java that, case "ketthucxuly": status_pack.status_code = STATUS_06 ban
        // VO DIEU KIEN moi lan action nay thanh cong, khong gac theo "van ban da het nguoi xu ly
        // chua" - status.staff_name = connection.user_token.name, dung actingUsername).
        if (result.isSuccess()) {
            publishReplyStatusAfterHoanThanh(parent, actingUsername, request.getOpinion());
        }
        return result;
    }

    private void publishReplyStatusAfterHoanThanh(BusinessDocument parent, String actingUsername, String opinion) {
        var replyRequest = new com.td.application.vbdi.PublishVbdiReplyStatusRequest();
        replyRequest.setDynamicFields(dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));
        replyRequest.setStatusCode("06");
        replyRequest.setStatusLabel("Hoàn thành");
        if (opinion != null && !opinion.isBlank()) {
            replyRequest.setStatusDetails(opinion);
        }
        replyRequest.setActorDisplayName(actingUsername);
        publishVbdiReplyStatusUseCase.execute(replyRequest);
    }

    // Domino processResObj(): moi phan tu resObj co promulgation_date hop le se GHI DE ngaybanhanh -
    // chi gia tri phan tu CUOI con ton tai (dung Domino that, khong phai gop/uu tien phan tu dau).
    private LocalDate resolveNgayBanHanh(List<VbdenReplyDocumentRef> resObj) {
        if (resObj == null || resObj.isEmpty()) {
            return null;
        }
        LocalDate result = null;
        for (VbdenReplyDocumentRef ref : resObj) {
            LocalDate parsed = parseDate(ref == null ? null : ref.getPromulgationDate());
            if (parsed != null) {
                result = parsed;
            }
        }
        return result;
    }

    // Domino ketthucxuly() dong 579-605 (bo qua InitGiam/setViewError - xem javadoc lop). ngayBanHanh
    // (bo sung 14/09/2026): moc so sanh thay @Today khi tinh remainingDays neu resObj co gan van ban
    // tra loi hop le - xem javadoc lop.
    private void updateParentDocument(BusinessDocument parent, String actingUsername, LocalDate ngayBanHanh) {
        var before = buildSnapshot(parent);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        List<String> userProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        List<String> userReceiver = toStringList(fields.get(USER_RECEIVER_FIELD));
        List<String> userProcessing = toStringList(fields.get(USER_PROCESSING_FIELD));
        List<String> userCoordinate = toStringList(fields.get(USER_COORDINATE_FIELD));
        List<String> userToRead = toStringList(fields.get(USER_TO_READ_FIELD));
        List<String> userDistributor = toStringList(fields.get(USER_DISTRIBUTOR_FIELD));
        List<String> userReturned = toStringList(fields.get(USER_RETURNED_FIELD));
        List<String> userProcessed = toStringList(fields.get(USER_PROCESSED_FIELD));

        userProcess.remove(actingUsername);
        userReceiver.remove(actingUsername);
        userProcessing.remove(actingUsername);
        userCoordinate.remove(actingUsername);
        userToRead.remove(actingUsername);
        userDistributor.remove(actingUsername);
        userReturned.remove(actingUsername);
        if (!userProcessed.contains(actingUsername)) {
            userProcessed.add(actingUsername);
        }

        fields.put(USER_PROCESS_FIELD, userProcess);
        fields.put(USER_RECEIVER_FIELD, userReceiver);
        fields.put(USER_PROCESSING_FIELD, userProcessing);
        fields.put(USER_COORDINATE_FIELD, userCoordinate);
        fields.put(USER_TO_READ_FIELD, userToRead);
        fields.put(USER_DISTRIBUTOR_FIELD, userDistributor);
        fields.put(USER_RETURNED_FIELD, userReturned);
        fields.put(USER_PROCESSED_FIELD, userProcessed);

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));
        recomputeStatus(parent, fields, userProcess, userProcessing, userProcessed, ngayBanHanh);

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Bugfix 04/09/2026 (bao loi nguoi dung: status khong chuyen sang "da xu ly" sau ketthucxuly) -
    // doc lai truc tiep TDDocumentUlties.tinhSoNgayConLai() that (dong 2208-2215): BAT KY LOI NAO khi
    // tinh so ngay con lai (thieu/khong parse duoc deadline_date) -> tra ve 0 (KHONG bo qua/giu nguyen
    // status cu nhu truoc day) - capNhatTrangThaiVBden() (dong 2111-2186) LUON ghi de "status" khong
    // dieu kien. Voi remainingDays=0 (khong am) + hasPendingAssignee=false (vua xoa xong) +
    // hasProcessed=true (vua them vao) -> status="4" (dung han, da xu ly xong) dung nhu Domino, du
    // van ban khong co/co deadline_date sai dinh dang.
    private void recomputeStatus(
            BusinessDocument parent, Map<String, Object> fields,
            List<String> userProcess, List<String> userProcessing, List<String> userProcessed,
            LocalDate ngayBanHanh) {
        LocalDate deadlineDate = parseDate(asString(fields.get(DEADLINE_DATE_FIELD)));
        boolean hasPendingAssignee = !userProcess.isEmpty() || !userProcessing.isEmpty();
        boolean hasProcessed = !userProcessed.isEmpty();
        int remainingDays = deadlineDate != null
            ? vbdenStatusService.computeRemainingDays(deadlineDate, ngayBanHanh)
            : 0;
        String newStatus = vbdenStatusService.determineStatus(remainingDays, hasPendingAssignee, hasProcessed);
        parent.update(null, null, newStatus, null, null, null, null);
    }

    // Domino TaoQTXL() (qua CreateOrUpdateDocument(), chay truoc switch-case): parentid/action/
    // opinion/creator/position - dung ten field Java thuc su dung, cung quy uoc CapNhatKetQuaXuLyUseCase.
    private Result<UUID> createProcessingRecord(KetThucXuLyRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_KET_THUC_XU_LY);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", ACTION_KET_THUC_XU_LY);
        childFields.put("nguoi_thuc_hien", actingUsername);
        String displayCreator = request.getCreatorName() != null && !request.getCreatorName().isBlank()
            ? request.getCreatorName() : actingUsername;
        childFields.put("creator", displayCreator);
        if (request.getPosition() != null && !request.getPosition().isBlank()) {
            childFields.put("position", request.getPosition());
        }
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            childFields.put("opinion", request.getOpinion());
        }
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));
        addResObjIfPresent(childFields, request.getResObj());
        childRequest.setFields(childFields);

        return addVbdenResDocumentUseCase.execute(childRequest);
    }

    // Bo sung 14/09/2026 - xem javadoc lop (khong ghi documentins_obj len vbdi, chi luu lai audit tren
    // chinh ban ghi vbden_res, dung Ketquaxuly.jsx#build_res_obj doc lai qua "code").
    private void addResObjIfPresent(Map<String, String> childFields, List<VbdenReplyDocumentRef> resObj) {
        if (resObj == null || resObj.isEmpty()) {
            return;
        }
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        for (VbdenReplyDocumentRef ref : resObj) {
            if (ref == null) {
                continue;
            }
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("id", ref.getId() != null ? ref.getId().toString() : "");
            node.put("code", ref.getCode() != null ? ref.getCode() : "");
            if (ref.getPromulgationDate() != null && !ref.getPromulgationDate().isBlank()) {
                node.put("promulgationDate", ref.getPromulgationDate());
            }
            if (ref.getUnitPublish() != null && !ref.getUnitPublish().isBlank()) {
                node.put("unitPublish", ref.getUnitPublish());
            }
            array.add(node);
        }
        if (!array.isEmpty()) {
            childFields.put("res_obj", array.toString());
        }
    }

    private Map<String, Object> buildSnapshot(BusinessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DEADLINE_DATE_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
