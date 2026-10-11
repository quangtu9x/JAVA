package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.sharedcore.AppUserDto;
import com.td.application.sharedcore.BulkUserIdsRequest;
import com.td.application.sharedcore.CheckUsernameExistsUseCase;
import com.td.application.sharedcore.CreateAppUserRequest;
import com.td.application.sharedcore.CreateAppUserUseCase;
import com.td.application.sharedcore.DeleteAppUserUseCase;
import com.td.application.sharedcore.GetAppUserUseCase;
import com.td.application.sharedcore.GetKiemNhiemPendingCountUseCase;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.sharedcore.GetUserSignatureImagesUseCase;
import com.td.application.sharedcore.ListUserDirectoryUseCase;
import com.td.application.sharedcore.LockAppUsersUseCase;
import com.td.application.sharedcore.SearchAppUsersRequest;
import com.td.application.sharedcore.SearchAppUsersUseCase;
import com.td.application.sharedcore.SwitchUserRequest;
import com.td.application.sharedcore.SwitchUserUseCase;
import com.td.application.sharedcore.TransferAppUsersRequest;
import com.td.application.sharedcore.TransferAppUsersUseCase;
import com.td.application.sharedcore.UnlockAppUsersUseCase;
import com.td.application.sharedcore.UpdateAppUserRequest;
import com.td.application.sharedcore.UpdateAppUserUseCase;
import com.td.application.sharedcore.UploadUserSignatureImageRequest;
import com.td.application.sharedcore.UploadUserSignatureImageUseCase;
import com.td.application.sharedcore.UserDirectoryEntryDto;
import com.td.application.sharedcore.UserOrganizationProfileDto;
import com.td.application.sharedcore.UserRoleGroupConfigDto;
import com.td.application.sharedcore.UserSignatureImageType;
import com.td.application.sharedcore.UserSignatureImagesDto;
import com.td.application.common.models.Result;
import com.td.infrastructure.security.AuthenticatedUser;
import com.td.infrastructure.security.TdTokenService;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shared-core/users")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Shared Core - Users", description = "Tra cứu người dùng trong shared core")
public class SharedCoreUsersController extends BaseController {

    // Gap #... TDSwitchUser (docs/analysis/19-checklist-port-tdswitchuser-domino-sang-java.md):
    // token switch ngắn hạn hơn token thường (8 giờ, TdTokenService.DEFAULT_TOKEN_TTL) — dùng cho
    // mục đích kiểm tra/hỗ trợ kỹ thuật, không nên tồn tại lâu (Nhóm 3 checklist).
    private static final Duration SWITCH_TOKEN_TTL = Duration.ofHours(1);

    private final SearchAppUsersUseCase searchAppUsersUseCase;
    private final GetAppUserUseCase getAppUserUseCase;

    // BL-108: User Management — Core CRUD
    private final CreateAppUserUseCase createAppUserUseCase;
    private final UpdateAppUserUseCase updateAppUserUseCase;
    private final DeleteAppUserUseCase deleteAppUserUseCase;
    private final LockAppUsersUseCase lockAppUsersUseCase;
    private final UnlockAppUsersUseCase unlockAppUsersUseCase;
    private final TransferAppUsersUseCase transferAppUsersUseCase;
    private final CheckUsernameExistsUseCase checkUsernameExistsUseCase;
    private final ListUserDirectoryUseCase listUserDirectoryUseCase;
    private final GetKiemNhiemPendingCountUseCase getKiemNhiemPendingCountUseCase;

    // Hoàn thiện "gắn hình ảnh chữ ký" người dùng (2 loại — xem javadoc UserSignatureImageType),
    // trước đây bị ẩn có chủ đích ở ChiTietModalNguoiDung.jsx (FE) vì backend chưa có chỗ lưu.
    private final UploadUserSignatureImageUseCase uploadUserSignatureImageUseCase;
    private final GetUserSignatureImagesUseCase getUserSignatureImagesUseCase;

    // TDSwitchUser (docs/analysis/19-checklist-port-tdswitchuser-domino-sang-java.md)
    private final SwitchUserUseCase switchUserUseCase;
    private final GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;
    private final GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;
    private final TdTokenService tdTokenService;

    @Value("${app.security.switch-user-code:123@!@#Aa}")
    private String switchUserCode;

    // BL-118 (tiếp, 19/08/2026): tương đương qlvb/danhmuc/DanhSachNguoiDung — toàn bộ người dùng
    // đang hoạt động, KHÔNG phân trang, kèm tên hiển thị đơn vị — dùng cho FE cache 1 lần
    // (utils/global.jsx#getAllUser). Route literal "/directory" được Spring ưu tiên khớp trước
    // "/{id}", không xung đột. Đặt TRƯỚC "/{id}" trong file cho dễ đọc, không ảnh hưởng routing.
    @GetMapping("/directory")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách toàn bộ người dùng kèm đơn vị (tương đương qlvb/danhmuc/DanhSachNguoiDung)",
        description = "KHÔNG phân trang — dùng cho FE cache 1 lần vào localStorage, phục vụ các "
            + "dropdown/lookup người xử lý/người ký khắp hệ thống")
    public ResponseEntity<ApiResponse<List<UserDirectoryEntryDto>>> listUserDirectory() {
        return ok(listUserDirectoryUseCase.execute());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách người dùng shared core")
    public ResponseEntity<ApiResponse<List<AppUserDto>>> listUsers(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = SearchAppUsersRequest.DEFAULT_SORT_BY) String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "BL-112: lọc theo người dùng chính — dùng để liệt kê danh sách "
                + "\"Kiêm nhiệm\" của 1 người dùng (AppUser.parentUserId)")
            @RequestParam(required = false) UUID parentUserId,
            @Parameter(description = "06/10/2026: chỉ lấy các tài khoản này (lặp tham số hoặc phân tách dấu phẩy)")
            @RequestParam(required = false) java.util.List<String> usernames) {

        var request = new SearchAppUsersRequest();
        request.setUsernames(usernames);
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setOrganizationId(organizationId);
        request.setIsActive(isActive);
        request.setParentUserId(parentUserId);

        return ok(searchAppUsersUseCase.execute(request));
    }

    // Gap #3 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026): số lượng
    // văn bản cần xử lý theo TỪNG username kiêm nhiệm (tương đương Domino
    // qlvb/vbden/SoLuongCanXuLyKiemNhiem, gọi bởi FE Notifications.tsx#getNotificationsCountOther
    // cho từng mục trong user_other_obj) — route literal đặt TRƯỚC "/{id}" như "/directory".
    @GetMapping("/pending-count-kiem-nhiem")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Số lượng văn bản cần xử lý theo username kiêm nhiệm (badge chuông thông báo)",
        description = "7/12 field gốc (VBDEN_KHAC + DUTHAO_KHAC + 3/8 PHIEUTRINH_KHAC ánh xạ thay thế "
            + "qua view thật của phieutrinhtd) — 5/8 field PHIEUTRINH_KHAC còn lại vắng mặt có chủ đích "
            + "(không có khái niệm tương đương), xem javadoc GetKiemNhiemPendingCountUseCase")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKiemNhiemPendingCount(
            @Parameter(description = "Username của hồ sơ kiêm nhiệm cần đếm", required = true)
            @RequestParam String username) {
        return ok(getKiemNhiemPendingCountUseCase.execute(username));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chi tiết người dùng shared core")
    public ResponseEntity<ApiResponse<AppUserDto>> getUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID id) {
        return ok(getAppUserUseCase.execute(id));
    }

    // ================= Ảnh chữ ký người dùng (chữ ký chính/chữ ký nháy) =================

    @PostMapping(value = "/{id}/signature-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Gắn ảnh chữ ký cho người dùng",
        description = "type=OFFICIAL tương ứng field Domino cũ \"anhkyso\" (chữ ký số/chữ ký chính), "
            + "type=DRAFT tương ứng \"anhkynhay\" (chữ ký nháy/tắt). Ảnh mới thay thế ảnh hiện hành "
            + "cùng loại (ảnh cũ không bị xoá, chỉ không còn là ảnh hiện hành). Giới hạn 1MB, chỉ "
            + "nhận tệp hình ảnh.")
    public ResponseEntity<ApiResponse<UUID>> uploadSignatureImage(
            @Parameter(description = "User ID", required = true) @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Loại ảnh chữ ký: OFFICIAL (anhkyso) hoặc DRAFT (anhkynhay)", required = true)
            @RequestParam("type") String type) {
        UserSignatureImageType parsedType;
        try {
            parsedType = UserSignatureImageType.valueOf(type == null ? "" : type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return badRequest(Result.failure("Loại ảnh chữ ký không hợp lệ (chỉ nhận OFFICIAL hoặc DRAFT)"));
        }
        // try-with-resources: đóng stream sau khi upload xong — stream còn mở giữ khoá tệp tạm multipart
        // trên Windows, Tomcat không xoá được ("Failed to deleted temporary file used for part [file]").
        try (java.io.InputStream fileStream = file.getInputStream()) {
            var request = UploadUserSignatureImageRequest.builder()
                .userId(id)
                .type(parsedType)
                .fileContent(fileStream)
                .fileName(file.getOriginalFilename())
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .build();
            return ok(uploadUserSignatureImageUseCase.execute(request));
        } catch (Exception e) {
            return badRequest(Result.failure("Lỗi tải ảnh chữ ký: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/signature-images")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Lấy ảnh chữ ký hiện hành (chính + nháy) của người dùng",
        description = "Trả kèm documentId của mỗi ảnh — dùng lại NGUYÊN endpoint xem/tải/xoá tệp chung "
            + "đã có (api/v1/documents/{documentId}/files/{fileId}/...). null nghĩa là chưa gắn ảnh loại đó.")
    public ResponseEntity<ApiResponse<UserSignatureImagesDto>> getSignatureImages(
            @Parameter(description = "User ID", required = true) @PathVariable UUID id) {
        return ok(getUserSignatureImagesUseCase.execute(id));
    }

    // ================= BL-108: User Management — Core CRUD =================

    @GetMapping("/check-username")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Kiểm tra trùng tên đăng nhập",
        description = "Hợp nhất 2 cách kiểm trùng không nhất quán của bản Domino cũ "
            + "(KiemTrungNguoiDung + query formula riêng ở modal kiêm nhiệm) thành 1 endpoint duy nhất. "
            + "data=true nghĩa là tên đăng nhập ĐÃ tồn tại (không được dùng).")
    public ResponseEntity<ApiResponse<Boolean>> checkUsername(
            @RequestParam String username,
            @Parameter(description = "Loại trừ chính user đang sửa khỏi kết quả kiểm tra")
            @RequestParam(required = false) UUID excludeId) {
        return ok(checkUsernameExistsUseCase.execute(username, excludeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo mới người dùng shared core")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Tạo thành công",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(value = """
                    {
                      "status": true,
                      "code": 201,
                      "data": "2c2d0789-b2b9-4e2a-bf64-68aaadf27f7d"
                    }
                    """)
            })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Dữ liệu không hợp lệ (vd trùng tên đăng nhập)",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(value = """
                    {
                      "status": false,
                      "code": 400,
                      "message": "Tên đăng nhập 'nguyenvana' đã tồn tại"
                    }
                    """)
            })
        )
    })
    public ResponseEntity<ApiResponse<UUID>> createUser(
            @Valid @RequestBody CreateAppUserRequest request) {
        return created(createAppUserUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật người dùng shared core",
        description = "Partial update — field null trong body nghĩa là không đổi.")
    public ResponseEntity<ApiResponse<UUID>> updateUser(
            @Parameter(description = "User ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateAppUserRequest request) {
        return ok(updateAppUserUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa mềm người dùng shared core")
    public ResponseEntity<ApiResponse<UUID>> deleteUser(
            @Parameter(description = "User ID", required = true) @PathVariable UUID id) {
        return ok(deleteAppUserUseCase.execute(id));
    }

    @PostMapping("/lock")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Khóa tài khoản hàng loạt")
    public ResponseEntity<ApiResponse<List<UUID>>> lockUsers(
            @Valid @RequestBody BulkUserIdsRequest request) {
        return ok(lockAppUsersUseCase.execute(request.getUserIds()));
    }

    @PostMapping("/unlock")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Mở khóa tài khoản hàng loạt")
    public ResponseEntity<ApiResponse<List<UUID>>> unlockUsers(
            @Valid @RequestBody BulkUserIdsRequest request) {
        return ok(unlockAppUsersUseCase.execute(request.getUserIds()));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chuyển đơn vị hàng loạt",
        description = "1 transaction rõ ràng: tất cả người dùng được chuyển thành công hoặc "
            + "không ai bị đổi (khác luồng FE cũ dùng forEach không rollback).")
    public ResponseEntity<ApiResponse<List<UUID>>> transferUsers(
            @Valid @RequestBody TransferAppUsersRequest request) {
        return ok(transferAppUsersUseCase.execute(request.getUserIds(), request.getOrganizationId()));
    }

    // ================= TDSwitchUser (docs/analysis/19-checklist-port-tdswitchuser-domino-sang-java.md) =================

    /**
     * Port {@code TDSwitchUser.java} (Domino, {@code services/danhmuc/TDSwitchUser.java}) — Manager
     * chuyển sang thao tác dưới danh tính người dùng khác để kiểm tra dữ liệu/hỗ trợ kỹ thuật, không
     * cần đăng xuất. Khác biệt có chủ đích so với Domino gốc:
     * <ul>
     *   <li>Gate bằng {@code @PreAuthorize("hasRole('ADMIN')")} và mã bảo mật riêng lấy từ cấu hình
     *       {@code app.security.switch-user-code}; không lưu mã production trong source.</li>
     *   <li>BỎ nhánh "user không tồn tại → vẫn cấp token quyền Manager" (lỗ hổng trong blueprint đề
     *       xuất ở tài liệu 10 §5.8) — trả lỗi rõ ràng thay vào đó (xem {@link SwitchUserUseCase}).</li>
     *   <li>Token phát hành CHỈ mang claim tổ chức + "roles" hợp nhất từ Nhóm vai trò (RoleGroup) của
     *       username đích — ĐÚNG cùng cơ chế đã dùng cho kiêm nhiệm ({@code
     *       QLVBCompatAuthController#buildKiemNhiemProfile}/{@code mergeRoles}), KHÔNG giả định danh
     *       tính đích có role Keycloak riêng nào (danh tính bị switch-vào không có JWT Keycloak thật,
     *       giống hệt bản chất "kiêm nhiệm"). Response CHỈ cần {@code tdtoken} — FE (
     *       {@code HeaderUserMenu.jsx#handleChuyenTaiKhoan}) reload trang sau khi switch, lượt gọi
     *       {@code GET /qlvb/profile} kế tiếp (KHÔNG sửa) sẽ tự resolve identity theo tdtoken mới và
     *       trả đầy đủ profile/menu — không cần lặp lại toàn bộ logic build profile ở đây.</li>
     * </ul>
     */
    @PostMapping("/switch")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Chuyển sang tài khoản khác (chỉ ADMIN) — port TDSwitchUser (Domino)",
        description = "Tương thích nghiệp vụ Domino qlvb/danhmuc/TDSwitchUser — yêu cầu mã bảo mật "
            + "riêng và role ADMIN. Trả về {tdtoken} — FE tự reload để lấy đầy đủ profile "
            + "qua GET /qlvb/profile với token mới.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> switchUser(
            @Valid @RequestBody SwitchUserRequest request,
            Authentication authentication) {
        if (!MessageDigest.isEqual(
                switchUserCode.getBytes(StandardCharsets.UTF_8),
                request.getMa().getBytes(StandardCharsets.UTF_8))) {
            log.warn("Switch user rejected: invalid security code (actor={})",
                authentication == null ? "unknown" : authentication.getName());
            return ResponseEntity.badRequest().body(ApiResponse.error("Mã bảo mật không đúng"));
        }
        var result = switchUserUseCase.execute(request.getUsername());
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(result.getError()));
        }

        String targetUsername = result.getData().getUsername();
        String actor = authentication == null ? "unknown" : authentication.getName();
        // Gap #4 checklist kiêm nhiệm đã kết luận: audit log dùng slf4j (app log) là đủ cho MVP, chưa
        // cần bảng riêng (docs/analysis/19-checklist-port-tdswitchuser-domino-sang-java.md §2.4).
        log.info("Switch user: {} -> {}", actor, targetUsername);

        UserOrganizationProfileDto targetProfile = getUserOrganizationProfileUseCase.execute(targetUsername);
        UserRoleGroupConfigDto roleGroupConfig = getUserRoleGroupConfigUseCase.execute(targetUsername);

        AuthenticatedUser targetUser = AuthenticatedUser.builder()
            .username(targetUsername)
            .name(targetProfile.getFullName())
            .build();
        String tdtoken = tdTokenService.issue(targetUser, SWITCH_TOKEN_TTL,
            buildSwitchTokenClaims(targetProfile, roleGroupConfig));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tdtoken", tdtoken);
        response.put("username", targetUsername);
        response.put("name", targetProfile.getFullName());
        return ok(response);
    }

    /**
     * Claim tổ chức (cùng tên field {@code TDTokenObject} Domino kỳ vọng — xem javadoc
     * {@code TdTokenService#issue}) + "roles" hợp nhất TỪ NHÓM VAI TRÒ CỦA USERNAME ĐÍCH (không có
     * base role Keycloak nào — cùng lý do {@code QLVBCompatAuthController#buildKiemNhiemProfile}
     * truyền {@code Set.of()} làm base roles).
     */
    private Map<String, Object> buildSwitchTokenClaims(UserOrganizationProfileDto target,
                                                         UserRoleGroupConfigDto roleGroupConfig) {
        Map<String, Object> claims = new LinkedHashMap<>();
        putIfNotBlank(claims, "identifier", target.getIdentifier());
        putIfNotBlank(claims, "identifiers", target.getIdentifiers());
        putIfNotBlank(claims, "level", target.getLevel());
        putIfNotBlank(claims, "unit", target.getUnitName());
        putIfNotBlank(claims, "unit_id", target.getUnitId());
        putIfNotBlank(claims, "department", target.getDepartmentName());
        putIfNotBlank(claims, "department_id", target.getDepartmentId());
        putIfNotBlank(claims, "agency", target.getAgencyName());
        putIfNotBlank(claims, "agency_id", target.getAgencyId());
        putIfNotBlank(claims, "agency_level", target.getAgencyLevelName());
        putIfNotBlank(claims, "agency_level_id", target.getAgencyLevelId());
        putIfNotBlank(claims, "caplanhdao", target.getCapLanhDao());
        putIfNotBlank(claims, "position", target.getPosition());

        Set<String> roleCodes = new LinkedHashSet<>(roleGroupConfig.getPermissionCodes());
        roleCodes.remove("");
        claims.put(TdTokenService.ROLES_CLAIM, roleCodes.isEmpty() ? "" : String.join(";", roleCodes) + ";");
        return claims;
    }

    private void putIfNotBlank(Map<String, Object> claims, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        claims.put(key, value);
    }
}
