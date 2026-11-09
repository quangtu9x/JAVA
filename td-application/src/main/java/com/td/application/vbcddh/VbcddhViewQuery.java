package com.td.application.vbcddh;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 30/10/2026 (cutover FE vbcddh khỏi Domino qlvb/view/query?databasetype=vbcddh|vbcddh_res): lớp đứng
 * TRƯỚC {@link ViewQueryService} cho {@code POST /api/v1/vbcddh/GetView}.
 * <ul>
 *   <li>Khoá {@code $username$} / thiếu khoá → tài khoản đang đăng nhập (trước đây Java nhận nguyên
 *   chuỗi "$username$" → các danh sách "Văn bản cá nhân" luôn rỗng).</li>
 *   <li>View mới/riêng vbcddh (Java chưa có hoặc cần khoá KHÔNG phân biệt hoa/thường vì FE luôn
 *   {@code toUpperCase()} id): tiến độ/kết quả/chuyển xử lý theo idnhiemvu, nhiệm vụ theo idvanban
 *   (đã xoá/chưa duyệt/chỉ đạo), "Toàn bộ" vwUser_All (trùng tên view hồ sơ công việc).</li>
 *   <li>Còn lại uỷ quyền ViewQueryService; pageSize &gt; 200 (báo cáo/xuất tệp xin 10000) thì lặp trang.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class VbcddhViewQuery {

    private static final String DB_TYPE = "vbcddh";
    private static final String USERNAME_TOKEN = "$username$";
    private static final int DELEGATE_PAGE_SIZE = 200;
    static final int MAX_PAGE_SIZE = 10000;
    private static final List<String> NV_BY_IDVANBAN_FIELDS = List.of("idvanban", "idvbdi", "idvbden", "idduthao");
    // Văn bản: UNION 6 field danh tính (cùng vwVan_Ban_Ca_Nhan_Co_Quan - ViewQueryService).
    private static final List<String> IDENTITY_FIELDS = List.of(
        "user_creator", "nguoitheodoihide", "nguoiphutrachhide", "nguoichidaohide", "user_nguoichuyencddh", "coquanid");
    private static final String CHUYEN_XU_LY_PREDICATE =
        "(COALESCE(d.dynamic_fields->>'chuyenxuly','') = '1' OR COALESCE(d.dynamic_fields->>'tralainguoigiao','') = '1')";
    private static final List<String> DVTH_FIELDS = List.of("donvithuchienhide", "chuyenvienthuchienhide");
    private static final String KHONG_HAN = "COALESCE(d.dynamic_fields->>'hanxuly_date','') = ''";
    private static final String KHONG_QUA_VBCD = "COALESCE(d.dynamic_fields->>'idvanban','') = ''";
    private static final String TU_VBCD = "COALESCE(d.dynamic_fields->>'idvanban','') != ''";
    private static final String CAP_TREN = "COALESCE(d.dynamic_fields->>'captren','') = '1'";

    private final ViewQueryService viewQueryService;
    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final VbcddhFieldMapper fieldMapper;
    private final CurrentUserProvider currentUserProvider;

    public PaginationResponse<Map<String, Object>> execute(ViewQueryRequest request) {
        resolveUsernameKeys(request);
        String viewname = request.getViewname() == null ? "" : request.getViewname().trim().toLowerCase();
        String key = request.getKeys() == null || request.getKeys().isEmpty() ? null : request.getKeys().get(0);
        Pageable pageable = PageRequest.of(Math.max(1, request.getPageNumber()) - 1,
            Math.min(Math.max(1, request.getPageSize()), MAX_PAGE_SIZE));

        VbcddhListCriteria criteria = switch (viewname) {
            case "vwtien_do_nhiem_vu_by_idnhiemvu" -> byIdNhiemVu("tiendonhiemvu", key, null);
            case "vwket_qua_xu_ly_nhiem_vu_by_idnhiemvu" -> byIdNhiemVu("ketquaxulynhiemvu", key, null);
            case "vwtien_do_chuyen_xu_ly_nhiem_vu_by_idnhiemvu" ->
                byIdNhiemVu("tiendonhiemvu", key, CHUYEN_XU_LY_PREDICATE);
            case "vwnhiem_vu_da_xoa_by_idvanban" -> byMember(List.of("nhiemvu_Deleted"), NV_BY_IDVANBAN_FIELDS, key);
            case "vwnhiem_vu_chua_duyet_by_idvanban" -> byMember(List.of("nhiemvu_chuaduyet"), NV_BY_IDVANBAN_FIELDS, key);
            case "vwnhiem_vu_by_idvanbanchidao" -> byMember(List.of("nhiemvu"), List.of("idvanban"), key);
            case "vwuser_all" -> byMember(List.of("vanban"), IDENTITY_FIELDS, key);
            // 6 view menu "Thực hiện nhiệm vụ" KHÔNG có trong dxl vbcddh.nsf - suy theo tên/nhãn menu FE
            // (Không ấn định hạn / Không qua kênh VBCĐ / Từ VBCĐ; _captren = captren "1"), khoá như
            // vwNhiem_Vu_By_Donvithuchienhide (đơn vị hoặc cá nhân thực hiện).
            case "vwnhiemvu_donvithuchienhide_by_kandinhhan" -> byDvth(key, KHONG_HAN);
            case "vwnhiemvu_donvithuchienhide_by_kandinhhan_captren" -> byDvth(key, KHONG_HAN + " AND " + CAP_TREN);
            case "vwnhiemvu_donvithuchienhide_by_kquavbcd" -> byDvth(key, KHONG_QUA_VBCD);
            case "vwnhiemvu_donvithuchienhide_by_kquavbcd_captren" -> byDvth(key, KHONG_QUA_VBCD + " AND " + CAP_TREN);
            case "vwnhiemvu_donvithuchienhide_by_tuvbcd" -> byDvth(key, TU_VBCD);
            case "vwnhiemvu_donvithuchienhide_by_tuvbcd_captren" -> byDvth(key, TU_VBCD + " AND " + CAP_TREN);
            default -> null;
        };
        if (criteria != null) {
            if (key == null || key.isBlank()) {
                return empty(pageable);
            }
            return query(criteria, request.getItems(), pageable);
        }
        try {
            return delegate(request);
        } catch (IllegalArgumentException ex) {
            // View chưa port (vd nhóm vwNhiem_Vu_*_Theo_* của GroupTable): trả rỗng thay vì 400 - FE
            // đọc res.data như mảng, body lỗi làm bảng lỗi hiển thị.
            return empty(pageable);
        }
    }

    /** Dùng chung cho tìm kiếm (TimKiemVbcddhUseCase): truy vấn theo tiêu chí, đổi sang map field Domino. */
    PaginationResponse<Map<String, Object>> query(VbcddhListCriteria criteria, String items, Pageable pageable) {
        Page<VbcddhDocument> page = vbcddhDocumentRepository.findByCriteria(criteria, pageable);
        boolean withFiles = mentionsFiles(items);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> fieldMapper.toMap(document, items, withFiles))
            .toList();
        return new PaginationResponse<>(content, page.getNumber() + 1, page.getSize(), page.getTotalElements(),
            page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private PaginationResponse<Map<String, Object>> delegate(ViewQueryRequest request) {
        int requestedSize = Math.min(Math.max(1, request.getPageSize()), MAX_PAGE_SIZE);
        if (requestedSize <= DELEGATE_PAGE_SIZE) {
            return viewQueryService.execute(DB_TYPE, request);
        }
        // Báo cáo/xuất tệp xin 1 trang lớn: gom các trang 200 của ViewQueryService (giới hạn 200/trang).
        int requestedPage = Math.max(1, request.getPageNumber());
        long skip = (long) (requestedPage - 1) * requestedSize;
        int innerPage = (int) (skip / DELEGATE_PAGE_SIZE) + 1;
        int offsetInFirst = (int) (skip % DELEGATE_PAGE_SIZE);
        List<Map<String, Object>> collected = new ArrayList<>();
        long total = 0;
        while (collected.size() < requestedSize) {
            request.setPageNumber(innerPage);
            request.setPageSize(DELEGATE_PAGE_SIZE);
            PaginationResponse<Map<String, Object>> page = viewQueryService.execute(DB_TYPE, request);
            total = page.getTotalItems();
            List<Map<String, Object>> items = page.getItems() == null ? List.of() : page.getItems();
            for (int i = offsetInFirst; i < items.size() && collected.size() < requestedSize; i++) {
                collected.add(items.get(i));
            }
            offsetInFirst = 0;
            if (items.size() < DELEGATE_PAGE_SIZE || page.isLast()) {
                break;
            }
            innerPage++;
        }
        int totalPages = (int) Math.ceil(total / (double) requestedSize);
        return new PaginationResponse<>(collected, requestedPage, requestedSize, total, totalPages,
            requestedPage == 1, requestedPage >= totalPages);
    }

    private VbcddhListCriteria byIdNhiemVu(String form, String key, String extra) {
        Map<String, String> equals = new LinkedHashMap<>();
        equals.put("idnhiemvu", key == null ? "" : key);
        // Không lọc document_type: vết do FE ghi (databasetype=vbcddh, đúng Domino - view nằm trong
        // vbcddh.nsf) và vết do CapNhatTienDoUseCase/vbden ghi (vbcddh_res) đều phải hiện.
        return VbcddhListCriteria.builder()
            .forms(List.of(form))
            .equalsIgnoreCase(equals)
            .extraPredicateSql(extra)
            .build();
    }

    private VbcddhListCriteria byDvth(String key, String extra) {
        return VbcddhListCriteria.builder()
            .documentType(DB_TYPE)
            .forms(List.of("nhiemvu"))
            .memberFields(DVTH_FIELDS)
            .member(key)
            .extraPredicateSql(extra)
            .build();
    }

    private VbcddhListCriteria byMember(List<String> forms, List<String> fields, String key) {
        return VbcddhListCriteria.builder()
            .documentType(DB_TYPE)
            .forms(forms)
            .memberFields(fields)
            .member(key)
            .build();
    }

    private void resolveUsernameKeys(ViewQueryRequest request) {
        String username = currentUserProvider.currentUsername().orElse("");
        List<String> keys = request.getKeys() == null ? new ArrayList<>() : new ArrayList<>(request.getKeys());
        if (keys.isEmpty()) {
            keys.add(username);
        }
        for (int i = 0; i < keys.size(); i++) {
            String value = keys.get(i);
            if (value != null && USERNAME_TOKEN.equalsIgnoreCase(value.trim())) {
                keys.set(i, username);
            }
        }
        request.setKeys(keys);
    }

    private static boolean mentionsFiles(String items) {
        if (items == null) {
            return false;
        }
        for (String item : items.split(";")) {
            if ("files".equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private static PaginationResponse<Map<String, Object>> empty(Pageable pageable) {
        return new PaginationResponse<>(List.of(), pageable.getPageNumber() + 1, pageable.getPageSize(), 0L, 0, true, true);
    }
}
