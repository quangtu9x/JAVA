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
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 16/09/2026 (bugfix người dùng, TIẾP theo yêu cầu "route vbden/xuly/theodoi -> Bỏ theo dõi -> port
 * sang JAVA và cutoverFE" — người dùng test lại báo "thành công nhưng không mất khỏi danh sách"):
 * port {@code CapNhatVanBanDen.java#huytheodoi()}/{@code #theodoilai()} thật (dòng ~335-368) — nút
 * "Bỏ theo dõi"/"Theo dõi lại" ở {@code vbden/theodoi/components/ItemsActiveList.jsx#action_theodoi}
 * (route {@code vbden/xuly/theodoi}, 2 tab "theodoi"/"datheodoi").
 *
 * <p><b>QUAN TRỌNG — KHÔNG PHẢI cùng tính năng với {@link TheoDoiNhiemVuUseCase}</b> (lần port trước
 * đã NHẦM, gọi nhầm {@code POST /api/v1/vbden/TheoDoiNhiemVu} cho route này): dù cùng tên nút "Bỏ
 * theo dõi"/tên action FE "huytheodoi", đây là 2 servlet Domino HOÀN TOÀN KHÁC NHAU đụng 2 field
 * khác nhau:</p>
 * <ul>
 *   <li>{@link TheoDoiNhiemVuUseCase} — servlet {@code TheoDoiNhiemVu.java}, field {@code tasks},
 *   khoá bởi {@code identifier} PHẲNG của người thao tác, dùng cho tab "Văn bản cần theo dõi"
 *   ({@code toanbovanban/components/ItemsListNhiemVuCanTheoDoi.jsx}, view
 *   {@code vanbancantheodoi}/{@code vanbancuabocantheodoi}).</li>
 *   <li>Use case NÀY — servlet {@code CapNhatVanBanDen.java} (route {@code qlvb/vbden/
 *   CapNhatVanBanDen?action=huytheodoi|theodoilai}), field {@code user_track}/{@code user_tracked},
 *   khoá bởi USERNAME (không phải identifier), dùng cho view {@code theodoi}/{@code datheodoi}
 *   ({@link ViewQueryService#VIEW_VBDEN_THEODOI}/{@link ViewQueryService#VIEW_VBDEN_DATHEODOI} —
 *   ĐÃ port từ trước, chỉ thiếu chiều GHI nên gọi nhầm sang use case kia không cập nhật đúng field
 *   view đang lọc, khiến văn bản KHÔNG BAO GIỜ biến mất khỏi tab "theodoi" dù server trả thành công).</li>
 * </ul>
 *
 * <p><b>Thuật toán ({@code huytheodoi()}/{@code theodoilai()} thật)</b>:</p>
 * <ol>
 *   <li>{@code action=huytheodoi}: CHỈ chạy khi {@code user_track} hiện tại KHÔNG rỗng — loại
 *   {@code actingUsername} khỏi {@code user_track}, thêm vào {@code user_tracked}.</li>
 *   <li>{@code action=theodoilai}: CHỈ chạy khi {@code user_tracked} hiện tại KHÔNG rỗng — thêm
 *   {@code actingUsername} vào {@code user_track}.</li>
 *   <li>Action khác/không xác định: Domino gốc rơi vào {@code default} rỗng của switch-case chính —
 *   không mutate {@code user_track}/{@code user_tracked}, nhưng vẫn tạo bản ghi quá trình xử lý bên
 *   dưới (xem điểm tiếp theo).</li>
 * </ol>
 *
 * <p><b>Tạo bản ghi {@code vbden_res} — LUÔN LUÔN, không điều kiện</b>: giống mọi action khác của
 * {@code CapNhatVanBanDen.java} — {@code CapNhatThongTin()} chạy {@code CreateOrUpdateDocument()}
 * TRƯỚC switch-case cho MỌI action (cùng khuôn {@link CapNhatDaXemDeBietUseCase}/
 * {@link TheoDoiNhiemVuUseCase}). FE gọi với body RỖNG ({@code requestPOST_FORM(url, {})}) nên bản
 * ghi này chỉ có {@code parentid}/{@code action}/{@code nguoi_thuc_hien}.</p>
 *
 * <p><b>KHÔNG port</b> ({@code TDInit.InitTang}/{@code InitGiam} — tăng/giảm badge đếm trên Redis
 * cache, hạ tầng chưa tồn tại trong Java, cùng quyết định R10/R11 đã áp dụng cho mọi use case anh em
 * khác trong module này).</p>
 *
 * <p><b>Đính chính 16/09/2026 (khác Domino gốc, CÓ CHỦ ĐÍCH, đã hỏi xác nhận người dùng qua
 * AskUserQuestion)</b>: Domino thật {@code theodoilai()} CHỈ thêm vào {@code user_track}, KHÔNG loại
 * {@code actingUsername} khỏi {@code user_tracked} — vì view {@code datheodoi} chỉ lọc
 * {@code user_tracked!=""} (không loại trừ theo {@code user_track}), văn bản sẽ xuất hiện ở CẢ 2 tab
 * "Đang theo dõi"/"Đã bỏ theo dõi" cùng lúc sau khi "Theo dõi lại" — người dùng xác nhận đây là thiếu
 * sót của hệ thống Domino gốc, muốn Java sửa luôn cho đúng UX (1 văn bản chỉ ở 1 trong 2 tab tại 1
 * thời điểm): {@code action=theodoilai} giờ CŨNG loại {@code actingUsername} khỏi
 * {@code user_tracked}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TheoDoiVanBanDenUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_HUY_THEO_DOI = "huytheodoi";
    public static final String ACTION_THEO_DOI_LAI = "theodoilai";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_TRACK_FIELD = "user_track";
    private static final String USER_TRACKED_FIELD = "user_tracked";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_TRACK_FIELD, USER_TRACKED_FIELD);

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(TheoDoiVanBanDenRequest request) {
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

        applyAction(parent, request.getAction(), actingUsername);

        return createProcessingRecord(request, actingUsername);
    }

    // Domino huytheodoi()/theodoilai(): moi ham CHI chay khi field dieu kien tuong ung KHONG rong.
    private void applyAction(BusinessDocument parent, String action, String actingUsername) {
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        if (ACTION_HUY_THEO_DOI.equalsIgnoreCase(action)) {
            List<String> userTrack = toStringList(fields.get(USER_TRACK_FIELD));
            if (userTrack.isEmpty()) {
                return;
            }
            var before = buildSnapshot(parent);
            userTrack.remove(actingUsername);
            fields.put(USER_TRACK_FIELD, userTrack);
            addOne(fields, USER_TRACKED_FIELD, actingUsername);
            saveParent(parent, fields, before);
        } else if (ACTION_THEO_DOI_LAI.equalsIgnoreCase(action)) {
            List<String> userTracked = toStringList(fields.get(USER_TRACKED_FIELD));
            if (userTracked.isEmpty()) {
                return;
            }
            var before = buildSnapshot(parent);
            // Dinh chinh 16/09/2026 (khac Domino goc, CO CHU DICH - xem javadoc lop): loai luon khoi
            // user_tracked, khong chi them vao user_track - de van ban khong con nam ca 2 tab.
            userTracked.remove(actingUsername);
            fields.put(USER_TRACKED_FIELD, userTracked);
            addOne(fields, USER_TRACK_FIELD, actingUsername);
            saveParent(parent, fields, before);
        }
        // Action khac: Domino default rong - khong mutate gi.
    }

    private void saveParent(BusinessDocument parent, Map<String, Object> fields, Map<String, Object> before) {
        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));
        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Domino CapNhatThongTin(): docQTXL = CreateOrUpdateDocument()... docQTXL.save() - chay TRUOC
    // switch-case cho MOI action. FE gui body RONG ({}) nen khong co opinion/creator/position de gan,
    // cung khuon TheoDoiNhiemVuUseCase/CapNhatDaXemDeBietUseCase.
    private Result<UUID> createProcessingRecord(TheoDoiVanBanDenRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(request.getAction());
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", request.getAction());
        childFields.put("nguoi_thuc_hien", actingUsername);
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

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
