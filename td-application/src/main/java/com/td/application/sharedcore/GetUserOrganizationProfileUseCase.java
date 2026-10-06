package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// BL-118 (19/08/2026): qlvb/profile trước đây lấy fullName từ claim Keycloak (không phải hồ sơ
// thật) và hardcode position/coquan/donvi/caplanhdao rỗng/hằng số cố định cho MỌI tài khoản (xem
// QLVBCompatAuthController cũ) — sai với đúng dữ liệu tổ chức thật của từng người dùng. Use case
// này tra cứu ĐÚNG 1 lần: AppUser theo username, rồi suy ra tên hiển thị agency/unit/department/
// agency_level bằng cách đi lên Organization.parentId (khớp cơ chế "form"-matching Domino thật —
// BE_THANHHOA ImportNguoiDung.java dòng ~264-311/LuuDanhMuc.java dòng ~1124-1187 — chỉ khác là
// Domino denormalize 1 lần lúc tạo/sửa hồ sơ còn ở đây suy luận runtime từ organizationId, vì
// AppUser modern KHÔNG lưu trùng dữ liệu tổ chức, xem javadoc AppUser.java).
//
// Cùng lần này, xử lý luôn "Kiêm nhiệm" (BL-112, AppUser.parentUserId) — đối chiếu BE_THANHHOA
// TDDatabaseConnection.getJackSonUserInformationAndPermissions() xác nhận payload qlvb/profile
// THẬT có nhúng field "user_other_obj" (mảng hồ sơ đầy đủ của từng chức vụ kiêm nhiệm, kèm token
// riêng để FE chuyển vai trò ngay — xem HeaderUserMenu.jsx `handleClickCanhan`) — không phải khái
// niệm chỉ dùng riêng ở màn quản trị "Người dùng" như suy đoán ban đầu.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(value = "identityTransactionManager", readOnly = true)
public class GetUserOrganizationProfileUseCase {

    // Đúng 4 "form" tổ chức mà Domino thật dùng để denormalize tên hiển thị lên hồ sơ người dùng —
    // xem ImportNguoiDung.java. Khớp giá trị `Organization.nodeType`/cột "form" đang dùng ở
    // OrganizationDtoMapper/organizationApi.js (ORG_FORM_ICON_MAP).
    private static final String FORM_DEPARTMENT = "department";
    private static final String FORM_UNIT = "unit";
    private static final String FORM_AGENCY = "agency";
    private static final String FORM_AGENCY_LEVEL = "agency_level";
    private static final String POSITION_FIELD = "position";

    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public UserOrganizationProfileDto execute(String username) {
        var empty = new UserOrganizationProfileDto();
        if (username == null || username.isBlank()) {
            return empty;
        }

        var userOpt = appUserRepository.findByUsernameAndDeletedOnIsNull(username);
        if (userOpt.isEmpty()) {
            return empty;
        }
        AppUser user = userOpt.get();
        var dto = buildProfileForUser(user);

        // "Kiêm nhiệm": các AppUser khác trỏ parentUserId về đúng user này — mỗi mục có hồ sơ tổ
        // chức riêng (không đệ quy sâu hơn 1 cấp — 1 tài khoản kiêm nhiệm không có kiêm-nhiệm-con).
        //
        // Gap #1 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026): lọc
        // isActive=true TRƯỚC khi build profile — khớp hành vi Domino gốc
        // TDAuthentication.CreateKiemNhiemAuthenToken() (chặn cấp token khi status != "1"). Trước
        // đây hồ sơ kiêm nhiệm đang bị Khoá vẫn xuất hiện trong user_other_obj kèm tdtoken hợp lệ ở
        // QLVBCompatAuthController#buildKiemNhiemProfile() — loại bỏ ngay từ nguồn (thay vì chỉ chặn
        // lúc phát token) để FE cũng không hiển thị mục kiêm nhiệm đang Khoá như 1 lựa chọn chuyển
        // vai hợp lệ.
        List<AppUser> kiemNhiemUsers = appUserRepository.findByParentUserIdAndDeletedOnIsNull(user.getId());
        dto.setKiemNhiem(kiemNhiemUsers.stream().filter(AppUser::isActive).map(this::buildProfileForUser).toList());

        return dto;
    }

    /**
     * BL-118 (tiếp, 19/08/2026): expose PUBLIC để {@link ListUserDirectoryUseCase} tái dùng ĐÚNG
     * logic suy luận tổ chức này cho "Danh sách người dùng" (tương đương
     * {@code qlvb/danhmuc/DanhSachNguoiDung}) — tránh viết lại thuật toán đi lên
     * {@code Organization.parentId} lần thứ 2.
     */
    public UserOrganizationProfileDto buildProfileForUser(AppUser user) {
        var dto = new UserOrganizationProfileDto();
        dto.setUsername(user.getUsername());
        dto.setFullName(user.getFullName());
        dto.setAppUserId(user.getId() == null ? "" : user.getId().toString());
        dto.setActive(user.isActive());
        dto.setPhoneNumber(nullToEmpty(user.getPhoneNumber()));
        dto.setCapLanhDao(nullToEmpty(user.getCapLanhDao()));
        dto.setIdentifier(nullToEmpty(user.getIdentifier()));
        dto.setLevel(user.getLevel() == null ? "" : String.valueOf(user.getLevel()));

        // "position" (chức vụ): field tự do, chưa có cột tường minh (positionId hiện "treo" — chưa
        // có bảng Position master-data, xem docs/field-mapping/03-user-field-mapping.md #5) — lưu
        // qua dynamicFields cùng cơ chế Dynamic Data Engine (BL-101), giống Domino gốc field tự do
        // trên hồ sơ, không suy ra từ cấp tổ chức.
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(user.getDynamicFieldsJson());
        Object position = dynamicFields.get(POSITION_FIELD);
        dto.setPosition(position == null ? "" : String.valueOf(position));

        resolveOrganizationAncestry(user.getOrganizationId(), dto);
        return dto;
    }

    /**
     * Đi lên {@code Organization.parentId} từ {@code organizationId}, khớp form ("department"/
     * "unit"/"agency"/"agency_level") của từng tổ tiên vào đúng field tương ứng — dừng ở tổ tiên
     * ĐẦU TIÊN khớp mỗi form (gần nhất), bỏ qua nếu form đã có giá trị hoặc không khớp form nào
     * trong 4 form quan tâm. Có chặn vòng lặp (visited set) đề phòng dữ liệu tổ chức bị lỗi
     * (parentId trỏ vòng) — dù về lý thuyết không nên xảy ra.
     *
     * <p>Cùng lượt đi lên này, gom {@code Organization.identifier} của TỪNG tổ tiên (không chỉ 4
     * form nhận diện được) vào {@code dto.identifiers} theo thứ tự LÁ → GỐC (org trực tiếp của user
     * trước, root/tỉnh sau cùng) — đúng thứ tự đã xác nhận từ BE_THANHHOA thật (xem javadoc
     * {@link UserOrganizationProfileDto#getIdentifiers()}).
     */
    private void resolveOrganizationAncestry(UUID organizationId, UserOrganizationProfileDto dto) {
        UUID currentId = organizationId;
        Set<UUID> visited = new HashSet<>();
        StringBuilder identifiers = new StringBuilder();
        StringBuilder parentIds = new StringBuilder();
        StringBuilder parents = new StringBuilder();
        while (currentId != null && visited.add(currentId)) {
            var orgOpt = organizationRepository.findByIdAndDeletedOnIsNull(currentId);
            if (orgOpt.isEmpty()) {
                break;
            }
            Organization org = orgOpt.get();
            applyIfMatch(dto, org, FORM_DEPARTMENT);
            applyIfMatch(dto, org, FORM_UNIT);
            applyIfMatch(dto, org, FORM_AGENCY);
            applyIfMatch(dto, org, FORM_AGENCY_LEVEL);
            if (org.getIdentifier() != null) {
                identifiers.append(org.getIdentifier()).append(";");
            }
            // "parentids" (Domino): chuỗi UNID tổ tiên, CÙNG thứ tự/lượt đi lên với "identifiers" —
            // tương đương Java dùng UUID của chính node (không có UNID Domino thật để tái tạo).
            parentIds.append(org.getId()).append(";");
            // "parents" (Domino): chuỗi TÊN tổ tiên, cùng lượt/thứ tự với "parentids" (luôn giữ chỗ kể cả
            // tên rỗng, để vị trí theo cấp khớp parentids) — xem javadoc UserOrganizationProfileDto#getParents.
            parents.append(org.getName() == null ? "" : org.getName()).append(";");
            currentId = org.getParentId();
        }
        dto.setIdentifiers(identifiers.toString());
        dto.setParentIds(parentIds.toString());
        dto.setParents(parents.toString());
    }

    private void applyIfMatch(UserOrganizationProfileDto dto, Organization org, String form) {
        if (!form.equals(org.getNodeType())) {
            return;
        }
        String id = org.getId().toString();
        switch (form) {
            case FORM_DEPARTMENT -> {
                if (dto.getDepartmentName().isEmpty()) {
                    dto.setDepartmentName(org.getName());
                    dto.setDepartmentId(id);
                }
            }
            case FORM_UNIT -> {
                if (dto.getUnitName().isEmpty()) {
                    dto.setUnitName(org.getName());
                    dto.setUnitId(id);
                }
            }
            case FORM_AGENCY -> {
                if (dto.getAgencyName().isEmpty()) {
                    dto.setAgencyName(org.getName());
                    dto.setAgencyId(id);
                }
            }
            case FORM_AGENCY_LEVEL -> {
                if (dto.getAgencyLevelName().isEmpty()) {
                    dto.setAgencyLevelName(org.getName());
                    dto.setAgencyLevelId(id);
                }
            }
            default -> log.debug("Form to chuc khong xac dinh: {}", form);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
