package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p><b>15/10/2026 — cache Redis</b> (cây thật ~50k node; Domino từng treo khi không cache): cây ĐẦY ĐỦ
 * (không lọc, chưa gắn ủy quyền) dựng 1 lần và lưu qua {@link OrganizationTreeCache} (Redis + JVM),
 * tự vô hiệu khi tổ chức/người dùng đổi ({@code OrganizationTreeCacheInvalidationListener}, td-infrastructure).
 * Mỗi request chỉ còn SAO CHÉP cây trong bộ nhớ, đồng thời: (1) cắt nhánh cơ quan khác
 * ({@code includeFullTree=false}) và (2) gắn {@code delegation_obj} (ủy quyền phụ thuộc NGÀY — không
 * cache cứng được). Bỏ luôn N+1 tra "người đại diện nhận" từng node (nạp theo lô).
 * KHÔNG còn {@code @Transactional} ở mức lớp: lần dựng cây chạy trong transaction riêng
 * (xem {@link OrganizationTreeCache}), request chờ không giữ kết nối DB.</p>
 */
@Service
@RequiredArgsConstructor
public class GetLegacyOrganizationTreeUseCase {

    private static final String DEFAULT_JEDIS_KEY = "qlvb_CoCauToChuc_#";
    private static final String FORM_AGENCY = "agency";
    private static final String FORM_USER = "user";
    private static final String DELEGATION_OBJ = "delegation_obj";
    static final String VARIANT_ORGANIZATIONS = "org";
    static final String VARIANT_WITH_USERS = "org+users";
    // PostgreSQL giới hạn 32767 tham số / câu lệnh — chia lô mệnh đề IN.
    private static final int IN_CLAUSE_BATCH_SIZE = 1000;

    private final OrganizationRepository organizationRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final AppUserRepository appUserRepository;
    private final OrganizationAgencyResolver organizationAgencyResolver;
    private final UserDelegationResolver userDelegationResolver;
    private final OrganizationTreeCache organizationTreeCache;

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
            String scopeAgencyId = includeFullTree ? null : resolveCurrentUserAgencyId();
            List<LegacyOrganizationTreeNode> fullTree = organizationTreeCache.getFullTree(
                includeUsers ? VARIANT_WITH_USERS : VARIANT_ORGANIZATIONS,
                () -> buildFullTree(includeUsers));
            List<LegacyOrganizationTreeNode> nodes = materialize(
                fullTree, scopeAgencyId, userDelegationResolver.allActiveToday());
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
    // Cây ĐẦY ĐỦ đưa vào cache: không lọc theo cơ quan người xem, delegation_obj để "" (gắn lúc đọc).
    private List<LegacyOrganizationTreeNode> buildFullTree(boolean includeUsers) {
        List<Organization> organizations = loadOrganizationsForTree();
        Map<UUID, List<AppUser>> usersByOrganizationId = includeUsers
            ? loadUsersByOrganizationId(organizations)
            : Map.of();
        return buildTree(organizations, usersByOrganizationId, loadReceiverUsernames(organizations));
    }

    // Sao chép cây dùng chung (KHÔNG sửa node trong cache): cắt nhánh "agency" khác scopeAgencyId (xem
    // javadoc buildNode cũ — bugfix 03/09/2026) và gắn delegation_obj của người đại diện nhận (node tổ
    // chức) hoặc của chính người dùng (node "user").
    private List<LegacyOrganizationTreeNode> materialize(
            List<LegacyOrganizationTreeNode> nodes,
            String scopeAgencyId,
            Map<String, Map<String, Object>> delegations) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<LegacyOrganizationTreeNode> result = new ArrayList<>(nodes.size());
        for (LegacyOrganizationTreeNode node : nodes) {
            if (scopeAgencyId != null && FORM_AGENCY.equals(node.getForm()) && !scopeAgencyId.equals(node.getId())) {
                continue;
            }
            Map<String, String> data = node.getData() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(node.getData());
            String delegator = FORM_USER.equals(node.getForm()) ? data.get("username") : data.get("user_receiver");
            data.put(DELEGATION_OBJ, userDelegationResolver.activeJsonOrEmpty(delegations, safe(delegator)));
            result.add(new LegacyOrganizationTreeNode(
                node.getId(),
                node.getText(),
                node.getForm(),
                data,
                materialize(node.getChildren(), scopeAgencyId, delegations)));
        }
        return result;
    }

    private Map<UUID, List<AppUser>> loadUsersByOrganizationId(List<Organization> organizations) {
        List<UUID> organizationIds = organizations.stream().map(Organization::getId).toList();
        if (organizationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<AppUser>> byOrganizationId = new HashMap<>();
        for (List<UUID> batch : partition(organizationIds)) {
            for (AppUser user : appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(batch)) {
                // 06/10/2026: cay chon nguoi - bo nguoi dang bi Khoa.
                if (user.getOrganizationId() == null || !user.isActive()) {
                    continue;
                }
                byOrganizationId.computeIfAbsent(user.getOrganizationId(), key -> new ArrayList<>()).add(user);
            }
        }
        return byOrganizationId;
    }

    // receiverId (UUID dạng String) -> username, nạp theo lô cho CẢ cây (thay N+1 từng node).
    private Map<UUID, String> loadReceiverUsernames(List<Organization> organizations) {
        Set<UUID> receiverIds = new LinkedHashSet<>();
        for (Organization organization : organizations) {
            UUID receiverId = parseUuid(organization.getReceiverId());
            if (receiverId != null) {
                receiverIds.add(receiverId);
            }
        }
        Map<UUID, String> usernames = new HashMap<>();
        for (List<UUID> batch : partition(new ArrayList<>(receiverIds))) {
            for (AppUser user : appUserRepository.findByIdInAndDeletedOnIsNull(batch)) {
                usernames.put(user.getId(), safe(user.getUsername()));
            }
        }
        return usernames;
    }

    private static List<List<UUID>> partition(List<UUID> ids) {
        List<List<UUID>> batches = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += IN_CLAUSE_BATCH_SIZE) {
            batches.add(ids.subList(from, Math.min(from + IN_CLAUSE_BATCH_SIZE, ids.size())));
        }
        return batches;
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
            List<Organization> organizations,
            Map<UUID, List<AppUser>> usersByOrganizationId,
            Map<UUID, String> receiverUsernames) {
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
            result.add(buildNode(
                root, childrenByParent, new NodeContext(null, null, null, receiverUsernames), usersByOrganizationId));
        }

        return result;
    }

    // 15/10/2026: dựng cây ĐẦY ĐỦ (đưa vào cache) — việc cắt nhánh "agency" khác cơ quan người xem đã
    // chuyển sang materialize() (cùng quy tắc: bất kỳ node form "agency" nào khác scopeAgencyId bị cắt
    // CẢ NHÁNH, dù ở root hay lồng sâu).
    private LegacyOrganizationTreeNode buildNode(
            Organization organization,
            Map<UUID, List<Organization>> childrenByParent,
            NodeContext context,
            Map<UUID, List<AppUser>> usersByOrganizationId) {
        String form = resolveForm(organization);
        NodeContext nextContext = nextContext(organization, form, context);

        List<LegacyOrganizationTreeNode> children = new ArrayList<>(childrenByParent
            .getOrDefault(organization.getId(), List.of())
            .stream()
            .map(child -> buildNode(child, childrenByParent, nextContext, usersByOrganizationId))
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
        data.put(DELEGATION_OBJ, ""); // gắn lúc đọc (materialize) — ủy quyền đổi theo ngày
        data.put("agency_id", safe(context.agencyId()));
        data.put("unit_id", safe(context.unitId()));

        return new LegacyOrganizationTreeNode(
            user.getId().toString(),
            safe(user.getFullName()),
            FORM_USER,
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
        String receiverUsername = resolveReceiverUsername(organization.getReceiverId(), context.receiverUsernames());

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
        // 09/10/2026 (port Ủy quyền): thay Domino UyQuyen#capnhat_daidien_nhan_vanban (chép delegation_obj
        // sang bản ghi tổ chức) — gắn LÚC ĐỌC ủy quyền đang hiệu lực của người đại diện nhận (chuỗi JSON).
        // 15/10/2026: gắn lúc đọc (materialize) — ủy quyền đổi theo ngày, không cache cứng được.
        data.put(DELEGATION_OBJ, "");

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
    // 15/10/2026: tra trong bản đồ đã nạp theo lô (loadReceiverUsernames), không truy vấn từng node.
    private String resolveReceiverUsername(String receiverId, Map<UUID, String> receiverUsernames) {
        UUID id = parseUuid(receiverId);
        return id == null ? "" : receiverUsernames.getOrDefault(id, "");
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
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

        return new NodeContext(agencyId, agencyIdentifier, unitId, current.receiverUsernames());
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

    // 15/10/2026: ủy quyền (delegation_obj) không còn đi qua context — gắn ở materialize() lúc đọc.
    // "receiverUsernames" = receiverId -> username, nạp theo lô 1 lần cho CẢ cây.
    private record NodeContext(String agencyId, String agencyIdentifier, String unitId,
                               Map<UUID, String> receiverUsernames) {
    }
}
