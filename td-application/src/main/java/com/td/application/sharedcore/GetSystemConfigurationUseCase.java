package com.td.application.sharedcore;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 16/09/2026 (yêu cầu người dùng: "modal Chuyển xử lý của văn bản đến chưa nhận cấu hình -> Chuyển
 * chủ trì có 2 lựa chọn là chọn một và chọn nhiều", cấu hình thiết lập ở "Cơ cấu tổ chức"): port
 * {@code GetCauHinh.java} thật ({@code doService()#getData()}) — trả TOÀN BỘ field bắt đầu bằng
 * {@code "cfg_"} của tổ chức người dùng, dùng bởi {@code ChuyenXuLyModalCapN.jsx} (field
 * {@code cfg_chuyenchutri} — quyết định "Chuyển chủ trì" cho phép chọn 1 hay nhiều đơn vị) và tương
 * tự các màn khác đọc {@code systemConfig?.data?.cfg_*}.
 *
 * <p><b>Đối chiếu Domino gốc</b>: {@code doc = qlvb.getDocumentByUNID(coquanid != "" ? coquanid :
 * nodeid)} — ưu tiên {@code coquanid} ("cơ quan" — tổ tiên cấp {@code agency}), chỉ dùng
 * {@code nodeid} (tổ chức TRỰC TIẾP của user) khi {@code coquanid} rỗng — rồi quét MỌI field tên bắt
 * đầu {@code "cfg_"} trên document đó (KHÔNG whitelist tên field cụ thể). Ở đây tương đương: đi lên
 * {@link Organization#getParentId()} từ {@code AppUser.getOrganizationId()} (cùng thuật toán
 * {@code GetCapBanHanhOptionsUseCase#walkAncestry}), lấy tổ tiên ĐẦU TIÊN có {@code nodeType="agency"}
 * — nếu không có tổ tiên nào khớp (vd. user gắn thẳng vào 1 node không có tổ tiên agency), fallback về
 * CHÍNH tổ chức của user (nodeid) — rồi đọc {@code dynamicFieldsJson} của tổ chức đó, lọc key bắt đầu
 * {@code "cfg_"} (case-insensitive, trả về key đã lowercase — đúng
 * {@code itemName.toLowerCase().trim()} của Domino).</p>
 *
 * <p><b>Trước bản port này</b>: {@code QLVBCompatAuthController#getCauHinh()} trả CỨNG 2 field
 * {@code cfg_chuyenvbqp}/{@code cfg_vbpq_mailin} đọc từ biến môi trường (bổ sung 03/09/2026, lúc đó
 * hệ thống CHƯA có nơi lưu {@code cfg_*} theo tổ chức) — từ khi {@code organizationApi.js}
 * (module "Cơ cấu tổ chức", {@code ChiTietModalCoQuan.jsx} tab VBDEN) đã lưu {@code cfg_*} (bao gồm cả
 * {@code cfg_chuyenchutri}/{@code cfg_chuyenphoihop}/{@code cfg_field_do_mat}/
 * {@code cfg_vbden_sovanban_rutgon}/{@code cfg_chuyen_donvi_khac}/{@code cfg_xin_y_kien}, VÀ 2 field cũ
 * {@code cfg_chuyenvbqp}/{@code cfg_vbpq_mailin}) vào {@code Organization.dynamicFieldsJson} — response
 * cứng ở trên KHÔNG BAO GIỜ phản ánh giá trị người dùng đã cấu hình, khiến MỌI field {@code cfg_*} qua
 * modal "Chuyển xử lý" luôn nhận mặc định (rỗng/"0") bất kể đã cấu hình gì ở "Cơ cấu tổ chức".</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class GetSystemConfigurationUseCase {

    private static final String CFG_PREFIX = "cfg_";
    private static final String FORM_AGENCY = "agency";

    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Map<String, Object> execute() {
        Optional<String> usernameOpt = currentUserProvider.currentUsername();
        if (usernameOpt.isEmpty()) {
            return Map.of();
        }
        Optional<AppUser> userOpt = appUserRepository.findByUsernameAndDeletedOnIsNull(usernameOpt.get());
        if (userOpt.isEmpty() || userOpt.get().getOrganizationId() == null) {
            return Map.of();
        }

        List<Organization> ancestryLeafToRoot = walkAncestry(userOpt.get().getOrganizationId());
        if (ancestryLeafToRoot.isEmpty()) {
            return Map.of();
        }

        // Uu tien to tien dau tien co nodeType="agency" (coquanid) - fallback ve chinh to chuc cua
        // user (phan tu dau tien, la nodeid) neu khong co to tien nao khop.
        Organization target = ancestryLeafToRoot.stream()
            .filter(org -> FORM_AGENCY.equalsIgnoreCase(org.getNodeType()))
            .findFirst()
            .orElse(ancestryLeafToRoot.get(0));

        Map<String, Object> allFields = dynamicDocumentFieldsService.parseDynamicFields(target.getDynamicFieldsJson());
        Map<String, Object> cfgFields = new LinkedHashMap<>();
        allFields.forEach((key, value) -> {
            if (key != null && key.toLowerCase().trim().startsWith(CFG_PREFIX)) {
                cfgFields.put(key.toLowerCase().trim(), value);
            }
        });
        return cfgFields;
    }

    // Cung thuat toan GetCapBanHanhOptionsUseCase#walkAncestry (LA->GOC).
    private List<Organization> walkAncestry(UUID organizationId) {
        List<Organization> chain = new ArrayList<>();
        UUID currentId = organizationId;
        Set<UUID> visited = new HashSet<>();
        while (currentId != null && visited.add(currentId)) {
            Optional<Organization> orgOpt = organizationRepository.findByIdAndDeletedOnIsNull(currentId);
            if (orgOpt.isEmpty()) {
                break;
            }
            Organization org = orgOpt.get();
            chain.add(org);
            currentId = org.getParentId();
        }
        return chain;
    }
}
