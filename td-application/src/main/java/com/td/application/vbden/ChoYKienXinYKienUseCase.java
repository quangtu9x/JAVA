package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.vbden.XinYKienDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * N08 (31/08/2026 — "cho ý kiến" TVUB/TVNB/BCH/BTV/TTĐU, xem docs/analysis/02-checklist-port-vbden-
 * con-lai.md + javadoc {@link XinYKienDocument}): hành động GHI Ý KIẾN trên "văn bản đến giả"
 * ({@code document_type="vbden_xinykien"}, bảng vật lý ĐỘC LẬP {@code vbden_xinykien_documents}) —
 * chuyển username hiện tại khỏi {@code user_process} (rời khỏi {@code chochoykien}/
 * {@code chochoykien*}) sang {@code user_processed} (vào {@code dachoykien}/{@code dachoykien*}),
 * đồng thời thêm vào {@code user_delivered} (vào {@code dachuyenchoykien}/{@code dachuyenchoykien*})
 * — ĐÚNG 3 field containment mà 14 view {@code ViewQueryService} đã port dùng.
 *
 * <p><b>Cập nhật 31/08/2026 (đối chiếu dữ liệu mẫu thật + {@code TraLoiVanBanXinYKien.java} —
 * hành động "cho ý kiến" THẬT của Domino, trước đây chưa đọc tới)</b>: ngoài việc chuyển
 * user_process/user_processed/user_delivered, Domino CÒN làm thêm 2 việc hiển thị (đối chiếu
 * {@code data_xinykien_mau.txt}):</p>
 * <ol>
 *   <li>Ghi 1 field ĐỘNG TÊN CHÍNH LÀ USERNAME người cho ý kiến (vd field {@code "hopln.snv"}) chứa
 *   giá trị ý kiến/trạng thái — mẫu thật: {@code "hopln.snv": "Đồng ý"}. Field này không có tên cố
 *   định trước (tên field = username biến thiên) nên KHÔNG thể khai báo hằng số — ghi trực tiếp qua
 *   {@code fields.put(actingUsername, ...)}.</li>
 *   <li>Field {@code tralois} tích luỹ log dạng người-đọc-được, nối bằng {@code "|"} khi có nhiều
 *   người: {@code "{tên}: {ý kiến}"} — mẫu thật: {@code "tralois": "Lê Ngọc Hợp: Đồng ý"}.</li>
 * </ol>
 *
 * <p><b>Sửa 01/09/2026 (đọc kỹ lại {@code TraLoiVanBanXinYKien.java} dòng
 * {@code docVBden.replaceItemValue(username, docQTXL.getItemValueString("status"))} trước khi cutover
 * FE)</b>: bản port 31/08 từng GỘP NHẦM 2 field Domino tách biệt vào chung 1 field
 * {@code opinion}. Domino thật có 2 field RIÊNG trên document con (docQTXL):
 * {@code status} (nút "Đồng ý"/"Không đồng ý" người dùng bấm — đây mới là giá trị ghi vào field
 * tên=username + {@code tralois} trên văn bản cha) và {@code opinion} (ô nhập tự do, CHỈ lưu trên
 * document con, KHÔNG ghi lên cha). Xác nhận thêm qua FE thật ({@code ColumnsDenXinYkien.jsx}
 * dòng ~269-281 tô màu field tên=username dựa trên regex "đồng ý"/"không đồng ý" — đúng là giá trị
 * {@code status}, không phải ý kiến tự do) — nay tách lại đúng 2 field ở {@link ChoYKienXinYKienRequest}.</p>
 * <p>CHƯA port (ghi rõ, không suy đoán): tính lại {@code status} tổng thể của văn bản qua
 * {@code tinhSoNgayConLai}/{@code xacDinhTrangThai} (Domino: {@code docVBden.replaceItemValue(
 * "status", trangThai)}) — VbdenStatusService hiện có tính theo ngữ nghĩa "quá hạn/còn hạn" của vbden
 * GỐC (deadline_date/user_process), chưa xác nhận có khớp đúng ngữ nghĩa status của xinykien hay
 * không, cần đối chiếu thêm trước khi áp dụng; và gửi mail thông báo cho người gửi ban đầu (Domino:
 * {@code XuLyTVUBThanhHoa} — tạo + gửi + xoá document {@code Form="frmTraLoiVBLayYkien"} qua cơ chế
 * mail-routing Domino, không có khái niệm tương đương trực tiếp ở Java — hệ thống có cơ chế thông
 * báo riêng (WebSocket, xem {@code TDInit.sendSocketNotSave}), không suy đoán tích hợp lại đây).</p>
 *
 * <p><b>Đơn giản hoá CÓ CHỦ Ý so với {@link ChuyenXuLyVbdenUseCase}</b> (BL-211, xử lý văn bản đến
 * THẬT): KHÔNG replicate deadline recompute / sơ đồ xử lý ({@code process_diagrams}) / snapshot
 * "_obj" đầy đủ tên-chức vụ / các nhánh phân công-phối hợp-xem để biết-theo dõi-phụ trách. Xây RIÊNG
 * (không mở rộng {@link ChuyenXuLyVbdenUseCase}) để KHÔNG tăng rủi ro hồi quy cho use case đã ship.</p>
 *
 * <p><b>CHƯA có trong slice này</b>: use case TẠO MỚI "văn bản đến giả" — xem {@link GuiXinYKienUseCase}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChoYKienXinYKienUseCase {

    public static final String DOCUMENT_TYPE = "vbden_xinykien";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_xinykien_res";
    public static final String ACTION_CHO_Y_KIEN = "choykien";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban xin y kien";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_PROCESSED_FIELD = "user_processed";
    private static final String USER_DELIVERED_FIELD = "user_delivered";
    private static final String TRALOI_FIELD = "tralois";
    private static final String DEFAULT_STATUS = "Đồng ý";
    // Domino: action ghi tren docQTXL = "dongy"/"khongdongy" tuy status (TraLoiVanBanXinYKien.java:
    // formData.set("action", status === "Đồng ý" ? "dongy" : "khongdongy")) - dung dung gia tri nay
    // (khong phai ACTION_CHO_Y_KIEN) de actionMap FE (dongy/khongdongy da co san) hien thi dung.
    private static final String ACTION_DONG_Y = "dongy";
    private static final String ACTION_KHONG_DONG_Y = "khongdongy";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_PROCESS_FIELD, USER_PROCESSED_FIELD, USER_DELIVERED_FIELD);

    private final XinYKienDocumentRepository xinYKienDocumentRepository;
    private final AddXinYKienDynamicDocumentUseCase addXinYKienDynamicDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(ChoYKienXinYKienRequest request) {
        var parentOptional = xinYKienDocumentRepository.findById(request.getParentid());
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
        String actingName = appUserRepository.findByUsernameAndDeletedOnIsNull(actingUsername)
            .map(user -> user.getFullName())
            .filter(name -> name != null && !name.isBlank())
            .orElse(actingUsername);
        String status = (request.getStatus() == null || request.getStatus().isBlank())
            ? DEFAULT_STATUS : request.getStatus().trim();
        String opinion = request.getOpinion() == null ? "" : request.getOpinion().trim();

        updateParentDocument(parent, actingUsername, actingName, status);

        return createProcessingRecord(request, actingUsername, status, opinion);
    }

    // Domino: khong co ChuyenXuLy.java rieng cho xinykien - hanh vi suy tu chinh field khoa cua 3
    // view chochoykien(user_process)/dachoykien(user_processed)/dachuyenchoykien(user_delivered) da
    // port o ViewQueryService, CONG 2 field hien thi (field ten=username, tralois) doi chieu
    // TraLoiVanBanXinYKien.java + du lieu mau that (xem javadoc lop).
    private void updateParentDocument(
            XinYKienDocument parent, String actingUsername, String actingName, String status) {
        var before = buildSnapshot(parent);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        List<String> userProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        userProcess.remove(actingUsername);
        fields.put(USER_PROCESS_FIELD, userProcess);

        addOne(fields, USER_PROCESSED_FIELD, actingUsername);
        addOne(fields, USER_DELIVERED_FIELD, actingUsername);

        // Field dong TEN = username (Domino: docVBden.replaceItemValue(username, status)).
        fields.put(actingUsername, status);

        String existingTralois = asString(fields.get(TRALOI_FIELD));
        String entry = actingName + ": " + status;
        fields.put(TRALOI_FIELD, isBlank(existingTralois) ? entry : existingTralois + "|" + entry);

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));
        var saved = xinYKienDocumentRepository.save(parent);
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Dung khuon ChuyenXuLyVbdenUseCase#createProcessingRecord - doc lai duoc qua QuaTrinhXuLy
    // (khoa parentid, khong doi field name de tuong thich voi component FE dang hien thi san - xem
    // fallback moi trong ViewQueryService#queryChildrenByParentId). Luu vao BANG RIENG
    // vbden_xinykien_documents (document_type=vbden_xinykien_res), KHONG con dung
    // AddDynamicDocumentUseCase (bang chung "documents") - xem javadoc lop.
    private Result<UUID> createProcessingRecord(
            ChoYKienXinYKienRequest request, String actingUsername, String status, String opinion) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_CHO_Y_KIEN);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", DEFAULT_STATUS.equalsIgnoreCase(status) ? ACTION_DONG_Y : ACTION_KHONG_DONG_Y);
        childFields.put("nguoi_thuc_hien", actingUsername);
        childFields.put("status", status);
        childFields.put("opinion", opinion);
        // Bo sung 03/09/2026 (cung gap voi ChuyenXuLyVbdenUseCase, xem javadoc lop do -
        // docs/views_domino/data_vbden_quatrinhxuly_mau*.txt): Domino ghi "identifier" tren MOI ban
        // ghi vbden_res, tab "Xem lien quan" loc theo field nay - resolve server-side, khong nhan tu FE.
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));
        childRequest.setFields(childFields);

        return addXinYKienDynamicDocumentUseCase.execute(childRequest);
    }

    private Map<String, Object> buildSnapshot(XinYKienDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    // Cung triet ly ChuyenXuLyVbdenUseCase#toStringList - doc khoan dung ca List lan chuoi ";"-joined.
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

    private void addOne(Map<String, Object> fields, String fieldName, String value) {
        List<String> list = toStringList(fields.get(fieldName));
        if (!list.contains(value)) {
            list.add(value);
        }
        fields.put(fieldName, list);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
