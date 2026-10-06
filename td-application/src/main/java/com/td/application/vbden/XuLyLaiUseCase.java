package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * N10 (01/09/2026 — "Xử lý lại" văn bản đến, xem {@code choxuly/components/ItemsActiveList.jsx}
 * hàm {@code handleXuLyLai}/{@code thuhoivanban}): tương đương {@code XuLyLai.java} thật — người
 * dùng ĐÃ xử lý xong (username hiện có trong {@code user_processed}) muốn LẤY LẠI văn bản để xử lý
 * lại (vd. ghi sai ý kiến, cần sửa lại quyết định phân công) — đơn giản hơn HẲN "Thu hồi"
 * ({@link ChoYKienXinYKienUseCase}/{@code ThuHoiVanBan.java} — xem lý do CHƯA port ở
 * {@code docs/analysis/02-checklist-port-vbden-con-lai.md}):
 * <ol>
 *   <li>Loại {@code actingUsername} khỏi {@code user_processed}, thêm lại vào {@code user_process}
 *   (đưa văn bản trở lại hàng đợi "chờ xử lý" của CHÍNH người đó — KHÔNG đụng người khác, KHÔNG có
 *   khái niệm "thu hồi từ người nhận" như {@code ThuHoiVanBan.java}).</li>
 *   <li>Tính lại {@code status}: {@code tinhSoNgayConLai<0 → "1"} (quá hạn đang xử lý),
 *   ngược lại {@code "2"} (còn hạn đang xử lý) — cùng công thức
 *   {@code vbdenStatusService.determineStatus(remainingDays, hasPendingAssignee=true, hasProcessed=false)}
 *   đã dùng ở {@code ChuyenXuLyVbdenUseCase#recomputeStatusOnFirstAssignment} (cùng bugfix D.35 —
 *   {@code deadline_date} định dạng {@code dd/MM/yyyy}, KHÔNG phải ISO). Bugfix 04/09/2026: nếu thiếu/không parse được
 *   {@code deadline_date}, mặc định {@code remainingDays=0} (KHÔNG bỏ qua bước ghi status như trước
 *   — đọc lại {@code TDDocumentUlties.tinhSoNgayConLai()} thật xác nhận Domino bắt mọi lỗi tính ngày
 *   và trả về {@code 0}, {@code capNhatTrangThaiVBden()} luôn ghi đè status không điều kiện, cùng
 *   phát hiện với {@link KetThucXuLyUseCase}).</li>
 *   <li>Tạo document con {@code vbden_res} ({@code action="xulylai"}, {@code opinion}=lý do,
 *   {@code nguoi_thuc_hien}=actingUsername — cùng field {@link LichSuXuLyUseCase} vừa port dùng để
 *   tra "Lịch sử xử lý" cá nhân) — đọc lại được qua {@code QuaTrinhXuLy} (BL-210) VÀ hiện luôn trong
 *   "Lịch sử xử lý" (N09) của người thực hiện.</li>
 * </ol>
 *
 * <p><b>KHÔNG port</b> (Domino gốc có, comment sẵn trong {@code XuLyLai.java}, chưa có bằng chứng
 * FE/người dùng cần): thông báo WebSocket ({@code sendSocketNotSave}, đã bị Domino tự comment out).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class XuLyLaiUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_XU_LY_LAI = "xulylai";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_PROCESSED_FIELD = "user_processed";
    private static final String DEADLINE_DATE_FIELD = "deadline_date";
    private static final DateTimeFormatter DEADLINE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_PROCESS_FIELD, USER_PROCESSED_FIELD);

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final VbdenStatusService vbdenStatusService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;
    // 05/10/2026: ghi "creator" (tên đầy đủ) + "position" (chức danh) như mọi thao tác vbden_res khác.
    private final com.td.application.sharedcore.UserFullNameResolver userFullNameResolver;
    private final com.td.application.sharedcore.GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    public Result<UUID> execute(XuLyLaiRequest request) {
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

        updateParentDocument(parent, actingUsername);

        return createProcessingRecord(request, actingUsername);
    }

    // Domino: TDDocumentUlties.removeToFieldListStringHideVBden(docVBden, "user_processed", username);
    // TDDocumentUlties.addToFieldListStringHideVBden(docVBden, "user_process", username);
    private void updateParentDocument(BusinessDocument parent, String actingUsername) {
        var before = buildSnapshot(parent);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        List<String> userProcessed = toStringList(fields.get(USER_PROCESSED_FIELD));
        userProcessed.remove(actingUsername);
        fields.put(USER_PROCESSED_FIELD, userProcessed);

        addOne(fields, USER_PROCESS_FIELD, actingUsername);

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));
        recomputeStatus(parent, fields);

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Bugfix 04/09/2026 (cung phat hien voi KetThucXuLyUseCase, bao loi nguoi dung) - doc lai truc
    // tiep TDDocumentUlties.tinhSoNgayConLai() that (dong 2208-2215): BAT KY LOI NAO khi tinh so ngay
    // con lai (thieu/khong parse duoc deadline_date) -> tra ve 0 (KHONG bo qua/giu nguyen status cu
    // nhu truoc day) - capNhatTrangThaiVBden() (dong 2111-2186) LUON ghi de "status" khong dieu kien.
    private void recomputeStatus(BusinessDocument parent, Map<String, Object> fields) {
        LocalDate deadlineDate = parseDate(asString(fields.get(DEADLINE_DATE_FIELD)));
        int remainingDays = deadlineDate != null
            ? vbdenStatusService.computeRemainingDays(deadlineDate, null)
            : 0;
        String newStatus = vbdenStatusService.determineStatus(remainingDays, true, false);
        parent.update(null, null, newStatus, null, null, null, null);
    }

    // Domino TaoQTXL(): parentid/action="xulylai"/opinion/user_creator - dung ten field Java thuc su
    // dung (nguoi_thuc_hien, khong phai user_creator) - xem javadoc LichSuXuLyUseCase.
    private Result<UUID> createProcessingRecord(XuLyLaiRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_XU_LY_LAI);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", ACTION_XU_LY_LAI);
        childFields.put("nguoi_thuc_hien", actingUsername);
        // 05/10/2026 (lpha báo "Xử lý lại -> quá trình xử lý thiếu chức danh"): trước đây chỉ ghi
        // nguoi_thuc_hien. Tên đầy đủ tra server-side (quy ước vbden_res creator = tên đầy đủ); chức danh
        // ưu tiên giá trị FE gửi (cùng cách KetThucXuLy/LayLaiVanBan), không có thì lấy hồ sơ tổ chức.
        childFields.put("creator", userFullNameResolver.resolveForWrite(actingUsername, request.getCreatorName()));
        String position = !isBlank(request.getPosition())
            ? request.getPosition().trim()
            : getUserOrganizationProfileUseCase.execute(actingUsername).getPosition();
        if (!isBlank(position)) {
            childFields.put("position", position);
        }
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            childFields.put("opinion", request.getOpinion());
        }
        // Bo sung 03/09/2026 (cung gap voi ChuyenXuLyVbdenUseCase, xem javadoc lop do -
        // docs/views_domino/data_vbden_quatrinhxuly_mau*.txt): Domino ghi "identifier" tren MOI ban
        // ghi vbden_res, tab "Xem lien quan" loc theo field nay - resolve server-side, khong nhan tu FE.
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));
        childRequest.setFields(childFields);

        return addVbdenResDocumentUseCase.execute(childRequest);
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
