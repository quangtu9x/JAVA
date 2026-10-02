package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * BL-105 (Cây cơ cấu tổ chức).
 *
 * <p><b>Bugfix 03/09/2026 (báo lỗi người dùng: "ở danh sách cây cơ cấu đang hiển thị thừa thông tin
 * của [1 cơ quan khác], đúng chỉ hiển thị cơ quan và đơn vị, phòng trong cơ quan" — người dùng thấy
 * cây chọn ở modal "Chuyển xử lý" lẫn cả 1 cơ quan/Sở KHÁC không liên quan)</b>: đối chiếu Domino
 * thật ({@code CayCoCauToChuc.java}, cùng URL path {@code danhmuc/CayCoCauToChuc}) xác nhận đây là
 * bug thật — Domino gốc CHỈ trả full cây (mọi cơ quan) cho user có ACL Manager HOẶC role
 * {@code CVQuanTri} ({@code is_full_cay}); người dùng thường chỉ thấy cây đã lọc theo
 * {@code dbpath} (cơ quan) của CHÍNH họ ({@code vwdbpath.getAllEntriesByKey}). Java trước đây hoàn
 * toàn KHÔNG có cơ chế lọc này — mọi user (kể cả role thường) đều nhận nguyên cây TOÀN BỘ hệ thống
 * (mọi cơ quan/Sở), lộ dữ liệu tổ chức không liên quan. Đã bổ sung {@code includeFullTree} (do
 * {@code LegacyDanhMucController} quyết định — {@code true} nếu user có role {@code ADMIN}/
 * {@link com.td.infrastructure.security.QlvbRoles#QUAN_TRI} nếu module security có expose, xem
 * controller — {@code td-application} không phụ thuộc security nên tự kiểm tra ở tầng controller)
 * — khi {@code false}, cắt bỏ MỌI nhánh "agency" (cơ quan/Sở) KHÁC với cơ quan của user hiện tại
 * (suy ra bằng cách đi lên {@code AppUser.organizationId} tới tổ tiên gần nhất có form "agency" —
 * KHÔNG dùng {@code cfg_chuyen_donvi_khac}, đây là 1 cấu hình KHÁC HẲN, kiểm soát chuyển nội bộ
 * PHÒNG/ĐƠN VỊ khác NHAU trong CÙNG 1 cơ quan, không liên quan tới việc ẩn/hiện cơ quan khác — xem
 * ghi chú tại {@code CayChonNoiXuLyCapN.jsx}). Không resolve được (thiếu AppUser/organizationId,
 * hoặc không tìm thấy tổ tiên "agency" nào) → fail-open, KHÔNG lọc gì (an toàn hơn ẩn nhầm dữ liệu
 * hợp lệ, cùng triết lý fail-open đã dùng ở {@link ResolveIdentifierByLevelUseCase}).</p>
 *
 * <p><b>Bugfix 07/09/2026</b> (người dùng báo lỗi: modal "Phát hành văn bản đi", tab "Cơ quan, đơn
 * vị thuộc Tỉnh" — KHÔNG thấy "Sở Nông nghiệp và môi trường" dù đã tồn tại/active/đúng cấu trúc cha-
 * con trong DB, xác nhận trực tiếp bằng query): endpoint {@code api/v1/danhmuc/CayCoCauToChuc} này
 * dùng CHUNG cho 2 NHU CẦU MÂU THUẪN NHAU: (1) {@code JSTree.jsx} ("Chuyển xử lý" — chọn đơn vị/
 * phòng ban XỬ LÝ nội bộ, ĐÚNG NHƯ bugfix 03/09/2026 mô tả ở trên: PHẢI ẩn cơ quan khác) và (2)
 * {@code JSTreePhatHanhCQLH.jsx} ("Phát hành văn bản đi" — chọn NƠI NHẬN thuộc nhóm
 * {@code contact_unit}, đúng bản chất PHẢI hiện MỌI cơ quan KHÁC trong tỉnh, không chỉ cơ quan của
 * người gửi). Với user THƯỜNG (không phải ADMIN/{@code QUAN_TRI}), {@code includeFullTree} trước đây
 * LUÔN {@code false} cho CẢ 2 nhu cầu — vô tình cắt bỏ MỌI cơ quan khác khỏi picker "Cơ quan, đơn vị
 * thuộc Tỉnh" (chỉ còn thấy cơ quan CỦA CHÍNH MÌNH — vô nghĩa với nhu cầu "gửi cho đơn vị khác").
 * Đã thêm tham số {@code full_tree} ở {@code LegacyDanhMucController#getCayCoCauToChuc} — mặc định
 * KHÔNG truyền (giữ nguyên hành vi cũ cho {@code JSTree.jsx} và mọi nơi gọi khác đang dùng
 * {@code fetchOrganizationTree()} không tham số — xem {@code organizationApi.js}), CHỈ
 * {@code JSTreePhatHanhCQLH.jsx} truyền {@code true} khi {@code danhsach === "cocautochuc"} (tab
 * "Cơ quan, đơn vị thuộc Tỉnh") để xin cây ĐẦY ĐỦ bất kể vai trò.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(value = "identityTransactionManager", readOnly = true)
public class GetLegacyOrganizationTreeUseCase {

    private static final String DEFAULT_JEDIS_KEY = "qlvb_CoCauToChuc_#";
    private static final String FORM_AGENCY = "agency";

    private final OrganizationRepository organizationRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final AppUserRepository appUserRepository;
    private final OrganizationAgencyResolver organizationAgencyResolver;

    public LegacyOrganizationTreeResponse execute(String jedisKey, boolean includeFullTree) {
        return execute(jedisKey, includeFullTree, false);
    }

    /**
     * Bo sung 09/09/2026 (yeu cau nguoi dung, sau khi phat hien
     * {@code CayCoCauXemLichTongHop} - dung chung use case nay - thieu node la "user" ca nhan so
     * voi Domino that {@code CayCoCauXemLichTongHop.java}, khien man "Nhom xem lich tong hop" mat
     * kha nang chon TUNG NGUOI CU THE, chi con chon duoc theo don vi): {@code includeUsers=true}
     * gan them 1 node con {@code form="user"} cho MOI {@link AppUser} co
     * {@code organizationId == node.id} (bat ke node do la agency/unit/department) - don gian hoa
     * co chu dich so voi Domino (bo qua nhom "Lanh dao" tong hop rieng tu view
     * {@code vwuserleaderbyparentid}/co "caplanhdao" - GHI NHAN la han che, xem
     * {@link #buildUserNodeData}) vi muc tieu chinh la "chon duoc tung nguoi", khong phai tai tao
     * y het cay Domino. Mac dinh {@code false} - {@code getCayCoCauToChuc} (JSTree.jsx va cac
     * picker "Chuyen xu ly"/"Phat hanh van ban di") GIU NGUYEN hanh vi cu, khong bi anh huong.
     */
    public LegacyOrganizationTreeResponse execute(String jedisKey, boolean includeFullTree, boolean includeUsers) {
        String normalizedJedisKey = normalizeJedisKey(jedisKey);

        try {
            List<Organization> organizations = loadOrganizationsForTree();
            String scopeAgencyId = includeFullTree ? null : resolveCurrentUserAgencyId();
            Map<UUID, List<AppUser>> usersByOrganizationId = includeUsers
                ? loadUsersByOrganizationId(organizations)
                : Map.of();
            List<LegacyOrganizationTreeNode> nodes = buildTree(organizations, scopeAgencyId, usersByOrganizationId);
            return LegacyOrganizationTreeResponse.success(nodes, normalizedJedisKey);
        } catch (Exception ex) {
            return LegacyOrganizationTreeResponse.failure(
                "Load organization tree failed",
                normalizedJedisKey,
                ex.getMessage()
            );
        }
    }

    // Nap 1 lan CHO CA CAY (tranh N+1 - cung convention voi
    // AppUserRepository#findByParentUserIdInAndDeletedOnIsNull), gom san theo organizationId.
    private Map<UUID, List<AppUser>> loadUsersByOrganizationId(List<Organization> organizations) {
        List<UUID> organizationIds = organizations.stream().map(Organization::getId).toList();
        if (organizationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<AppUser>> byOrganizationId = new HashMap<>();
        for (AppUser user : appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(organizationIds)) {
            if (user.getOrganizationId() == null) {
                continue;
            }
            byOrganizationId.computeIfAbsent(user.getOrganizationId(), key -> new ArrayList<>()).add(user);
        }
        return byOrganizationId;
    }

    // Bugfix 18/09/2026 (hoan thien "phat hanh vbdi -> den noi bo"): thuat toan di len parentId toi
    // to tien gan nhat co form "agency" da duoc tach sang OrganizationAgencyResolver (dung chung voi
    // SendVbdiDocumentUseCase, xem javadoc lop do) - method nay chi con uy quyen lai, GIU NGUYEN hanh
    // vi cu (null neu khong resolve duoc - fail-open o execute()).
    private String resolveCurrentUserAgencyId() {
        return organizationAgencyResolver.resolveCurrentUserAgencyId().map(UUID::toString).orElse(null);
    }

    private List<Organization> loadOrganizationsForTree() {
        List<Organization> organizations = new ArrayList<>();

        int pageNumber = 0;
        int pageSize = 200;
        while (true) {
            var request = new SearchOrganizationsRequest();
            request.setPageNumber(pageNumber);
            request.setPageSize(pageSize);
            request.setIsActive(true);
            request.setSortBy("sortOrder");
            request.setSortDirection("asc");

            var pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(
                    Sort.Order.asc("level"),
                    Sort.Order.asc("sortOrder"),
                    Sort.Order.asc("name")
                )
            );

            var page = organizationRepository.search(request, pageable);
            organizations.addAll(page.getContent());

            if (page.isLast()) {
                break;
            }

            pageNumber++;
        }

        return organizations;
    }

    private List<LegacyOrganizationTreeNode> buildTree(
            List<Organization> organizations, String scopeAgencyId, Map<UUID, List<AppUser>> usersByOrganizationId) {
        if (organizations == null || organizations.isEmpty()) {
            return List.of();
        }

        Map<UUID, Organization> byId = new HashMap<>();
        Map<UUID, List<Organization>> childrenByParent = new HashMap<>();

        for (Organization organization : organizations) {
            byId.put(organization.getId(), organization);
            childrenByParent
                .computeIfAbsent(organization.getParentId(), key -> new ArrayList<>())
                .add(organization);
        }

        Comparator<Organization> comparator = Comparator
            .comparingInt(Organization::getSortOrder)
            .thenComparing(organization -> safe(organization.getName()));

        for (List<Organization> children : childrenByParent.values()) {
            children.sort(comparator);
        }

        List<Organization> roots = organizations.stream()
            .filter(organization -> organization.getParentId() == null || !byId.containsKey(organization.getParentId()))
            .sorted(comparator)
            .toList();

        List<LegacyOrganizationTreeNode> result = new ArrayList<>();
        for (Organization root : roots) {
            LegacyOrganizationTreeNode node = buildNode(
                root, childrenByParent, new NodeContext(null, null, null), scopeAgencyId, usersByOrganizationId);
            if (node != null) {
                result.add(node);
            }
        }

        return result;
    }

    // scopeAgencyId == null -> khong loc gi (full tree, dung cho admin hoac khi khong resolve duoc
    // co quan cua nguoi dung - xem javadoc lop). scopeAgencyId co gia tri -> BAT KY node nao co
    // form == "agency" MA KHAC scopeAgencyId se bi cat bo CA NHANH (return null) - ap dung du node do
    // la root hay long sau trong cay (khong gia dinh "agency" luon o 1 cap co dinh), giu nguyen moi
    // node khac (agency_level/unit/department khong bi loc rieng - chi loc tai diem "agency").
    private LegacyOrganizationTreeNode buildNode(
            Organization organization,
            Map<UUID, List<Organization>> childrenByParent,
            NodeContext context,
            String scopeAgencyId,
            Map<UUID, List<AppUser>> usersByOrganizationId) {
        String form = resolveForm(organization);
        if (scopeAgencyId != null && FORM_AGENCY.equals(form)
                && !organization.getId().toString().equals(scopeAgencyId)) {
            return null;
        }
        NodeContext nextContext = nextContext(organization, form, context);

        List<LegacyOrganizationTreeNode> children = new ArrayList<>(childrenByParent
            .getOrDefault(organization.getId(), List.of())
            .stream()
            .map(child -> buildNode(child, childrenByParent, nextContext, scopeAgencyId, usersByOrganizationId))
            .filter(Objects::nonNull)
            .toList());
        for (AppUser user : usersByOrganizationId.getOrDefault(organization.getId(), List.of())) {
            children.add(buildUserNode(user, nextContext));
        }

        return new LegacyOrganizationTreeNode(
            organization.getId().toString(),
            safe(organization.getName()),
            form,
            buildNodeData(organization, form, nextContext),
            children
        );
    }

    // Xem javadoc execute(jedisKey, includeFullTree, includeUsers) - node la "user", KHONG dung
    // buildNodeData (danh rieng cho Organization). "cap_lanh_dao" tra nguyen gia tri AppUser.capLanhDao
    // (KHONG suy dien nhom "Lanh dao" rieng nhu Domino - xem javadoc execute() ve gioi han da biet).
    private LegacyOrganizationTreeNode buildUserNode(AppUser user, NodeContext context) {
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(user.getDynamicFieldsJson());

        Map<String, String> data = new LinkedHashMap<>();
        data.put("identifier", safe(user.getIdentifier()));
        data.put("username", safe(user.getUsername()));
        data.put("position", asString(dynamicFields.get("position")));
        data.put("cap_lanh_dao", safe(user.getCapLanhDao()));
        data.put("agency_id", safe(context.agencyId()));
        data.put("unit_id", safe(context.unitId()));

        return new LegacyOrganizationTreeNode(
            user.getId().toString(),
            safe(user.getFullName()),
            "user",
            data,
            List.of()
        );
    }

    private Map<String, String> buildNodeData(Organization organization, String form, NodeContext context) {
        String orgId = organization.getId().toString();
        String identifier = safe(organization.getIdentifier());
        String dbPath = safe(organization.getDbpath());
        // Bugfix 03/09/2026 (báo lỗi người dùng: modal "Chuyển xử lý" — chọn đơn vị làm chủ trì/
        // phối hợp, đơn vị có cấu hình "Người đại diện nhận" nhưng KHÔNG được đưa vào user_process):
        // trước đây hardcode "" cho MỌI node, dù Organization đã có cột receiverId/receiver/
        // receiverPosition (xem ChiTietModalDonVi.jsx picker "Người đại diện nhận" — lưu 4 field
        // receiver_id/user_receiver/receiver/receiver_position, nhưng chỉ 3 field ĐẦU/CUỐI có cột
        // tường minh trên Organization — "user_receiver", tức USERNAME thật, KHÔNG có cột riêng, chỉ
        // có receiverId trỏ tới AppUser.id). Resolve username thật từ receiverId ở đây — nguồn DUY
        // NHẤT để CayChonNoiXuLyCapN.jsx/SubFormChuyenXuLy.jsx build đúng user_process/user_coordinate/
        // user_to_read khi người dùng chọn 1 ĐƠN VỊ (không phải 1 người dùng cụ thể) làm người xử lý.
        String receiverUsername = resolveReceiverUsername(organization.getReceiverId());

        Map<String, String> data = new LinkedHashMap<>();
        switch (form) {
            case "agency":
                data.put("identifier", identifier);
                data.put("identifier_other", "");
                data.put("user_receiver", receiverUsername);
                data.put("agency_id", orgId);
                data.put("dbpath", dbPath);
                break;
            case "unit":
                data.put("identifier", identifier);
                data.put("identifier_other", safe(context.agencyIdentifier()));
                data.put("user_receiver", receiverUsername);
                data.put("agency_id", safe(context.agencyId()));
                data.put("unit_id", orgId);
                data.put("dbpath", dbPath);
                break;
            case "department":
                data.put("department_id", orgId);
                data.put("unit_id", safe(context.unitId()));
                data.put("agency_id", safe(context.agencyId()));
                data.put("identifier", identifier);
                data.put("user_receiver", receiverUsername);
                data.put("dbpath", dbPath);
                break;
            default:
                data.put("identifier", identifier);
                data.put("identifier_other", "");
                data.put("user_receiver", receiverUsername);
                data.put("dbpath", dbPath);
                break;
        }

        // Bổ sung 03/09/2026 (cùng bugfix "Người đại diện nhận" ở trên): "receiver"/"receiver_position"
        // (tên hiển thị/chức vụ, ĐÃ có cột tường minh trên Organization từ trước — chỉ chưa từng được
        // đưa vào node.data của cây, nên tooltip "Chọn: <chức vụ> <tên>" ở CayChonNoiXuLyCapN.jsx
        // luôn hiện "Chưa cấu hình đại diện nhận văn bản" dù đã cấu hình ở màn quản trị Đơn vị/Cơ
        // quan/Phòng ban) - áp dụng CHUNG cho mọi form, cùng cách mailin/clerical đã làm bên dưới.
        data.put("receiver", safe(organization.getReceiver()));
        data.put("receiver_position", safe(organization.getReceiverPosition()));

        // Bugfix 21/08/2026 (D.22 tài liệu phân tích vbden — tiếp): "clerical"/"mailin" - đơn vị
        // NGOÀI hệ thống, nhận văn bản qua email thay vì tài khoản (dùng ở
        // CayChonNoiXuLyCapN.jsx/ChuyenXuLyModalCapN.jsx) - đã lưu qua Organization.dynamicFields
        // (BL-109, xem ORG_DYNAMIC_FIELD_NAMES ở organizationApi.js) nhưng cây tổ chức trước đây
        // KHÔNG đưa 2 field này vào node.data - bổ sung ở đây, áp dụng CHUNG cho mọi form (không
        // riêng agency/unit/department) vì bất kỳ node nào cũng có thể được đánh dấu là "đơn vị
        // ngoài, nhận qua email".
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(organization.getDynamicFieldsJson());
        data.put("mailin", asString(dynamicFields.get("mailin")));
        data.put("clerical", asString(dynamicFields.get("clerical")));
        // Bổ sung 30/09/2026 (yêu cầu người dùng: cây modal "Chuyển xử lý" vbden — lãnh đạo cấp 1 tìm cơ
        // quan có "Mã định danh khác" = mã định danh của mình): giá trị THẬT cấu hình ở màn quản trị
        // (dynamicFields.identifier_other). Key RIÊNG — KHÔNG ghi đè "identifier_other" ở trên (node
        // "unit" đang mang nghĩa khác: mã cơ quan cha; node "agency" đang để "" — có nơi FE đọc).
        data.put("identifier_other_org", asString(dynamicFields.get("identifier_other")));

        return data;
    }

    // receiverId luu UUID (dang String) cua AppUser dai dien - "" hoac khong parse duoc/khong con
    // ton tai (da bi xoa) -> tra "" (khong chan luong doc cay, giu hanh vi cu voi node chua/khong con
    // cau hinh dai dien).
    private String resolveReceiverUsername(String receiverId) {
        if (receiverId == null || receiverId.isBlank()) {
            return "";
        }
        try {
            UUID id = UUID.fromString(receiverId.trim());
            return appUserRepository.findByIdAndDeletedOnIsNull(id).map(AppUser::getUsername).orElse("");
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private String resolveForm(Organization organization) {
        String nodeType = OrganizationHierarchyRules.resolveNodeType(organization);
        return switch (nodeType) {
            case OrganizationHierarchyRules.AGENCY_LEVEL -> "agency_level";
            case OrganizationHierarchyRules.AGENCY -> "agency";
            case OrganizationHierarchyRules.UNIT -> "unit";
            case OrganizationHierarchyRules.DEPARTMENT -> "department";
            default -> "agency_level";
        };
    }

    private NodeContext nextContext(Organization organization, String form, NodeContext current) {
        String agencyId = current.agencyId();
        String agencyIdentifier = current.agencyIdentifier();
        String unitId = current.unitId();

        if ("agency".equals(form)) {
            agencyId = organization.getId().toString();
            agencyIdentifier = safe(organization.getIdentifier());
            unitId = null;
        } else if ("unit".equals(form)) {
            unitId = organization.getId().toString();
        }

        return new NodeContext(agencyId, agencyIdentifier, unitId);
    }

    private String normalizeJedisKey(String jedisKey) {
        if (jedisKey == null || jedisKey.isBlank()) {
            return DEFAULT_JEDIS_KEY;
        }
        return jedisKey.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record NodeContext(String agencyId, String agencyIdentifier, String unitId) {
    }
}
