package com.td.web.controllers.v1;

import com.td.application.categories.CapNhatCacheRequest;
import com.td.application.categories.CategoryCacheService;
import com.td.application.categories.CategoryDto;
import com.td.application.categories.CreateCategoryRequest;
import com.td.application.categories.CategoryFileDto;
import com.td.application.categories.CreateCategoryUseCase;
import com.td.application.categories.DeleteCategoryUseCase;
import com.td.application.categories.LayDanhSachDungChungRequest;
import com.td.application.categories.ListCategoryFilesUseCase;
import com.td.application.categories.SearchCategoriesRequest;
import com.td.application.categories.SearchCategoriesUseCase;
import com.td.application.categories.UpdateCategoryRequest;
import com.td.application.categories.UpdateCategoryUseCase;
import com.td.application.categories.XoaDanhMucRequest;
import com.td.application.common.models.Result;
import com.td.application.sharedcore.CreateOrganizationRequest;
import com.td.application.sharedcore.CreateOrganizationUseCase;
import com.td.application.sharedcore.DeleteOrganizationUseCase;
import com.td.application.sharedcore.GetCapBanHanhOptionsUseCase;
import com.td.application.sharedcore.GetKyThuaLenhSignersUseCase;
import com.td.application.sharedcore.GetLegacyOrganizationFlatListUseCase;
import com.td.application.sharedcore.GetLegacyOrganizationTreeUseCase;
import com.td.application.sharedcore.GetLegacyOrgLookupForDungChungUseCase;
import com.td.application.sharedcore.LegacyOrganizationTreeResponse;
import com.td.application.sharedcore.PublishCatalogRequest;
import com.td.application.sharedcore.UpdateOrganizationRequest;
import com.td.application.sharedcore.UpdateOrganizationUseCase;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.application.workcalendars.DeleteWorkCalendarUseCase;
import com.td.application.workcalendars.GetWeekWorkCalendarRequest;
import com.td.application.workcalendars.GetWeekWorkCalendarUseCase;
import com.td.application.workcalendars.SaveWorkCalendarRequest;
import com.td.application.workcalendars.SaveWorkCalendarUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/danhmuc")
@RequiredArgsConstructor
@Validated
@Tag(name = "Legacy Danh Muc", description = "API danh muc tuong thich he thong cu")
public class LegacyDanhMucController extends BaseController {

    private final GetLegacyOrganizationTreeUseCase getLegacyOrganizationTreeUseCase;
    private final GetLegacyOrganizationFlatListUseCase getLegacyOrganizationFlatListUseCase;
    private final GetLegacyOrgLookupForDungChungUseCase getLegacyOrgLookupForDungChungUseCase;
    private final CreateOrganizationUseCase createOrganizationUseCase;
    private final UpdateOrganizationUseCase updateOrganizationUseCase;
    private final DeleteOrganizationUseCase deleteOrganizationUseCase;
    private final GetCapBanHanhOptionsUseCase getCapBanHanhOptionsUseCase;
    private final GetKyThuaLenhSignersUseCase getKyThuaLenhSignersUseCase;

    // BL-105: LuuDanhMuc/XoaDanhMuc/CapNhatCache dung chung du lieu + cache voi CategoriesController
    private final CreateCategoryUseCase createCategoryUseCase;
    private final UpdateCategoryUseCase updateCategoryUseCase;
    private final DeleteCategoryUseCase deleteCategoryUseCase;
    private final CategoryCacheService categoryCacheService;
    private final ViewQueryService viewQueryService;
    private final SearchCategoriesUseCase searchCategoriesUseCase;
    // Bo sung 09/09/2026 (cutover Footer.tsx/Banner.tsx/ListItemHDSD.tsx) - nhung tep dinh kem
    // Category (logo footer/banner) can nhung base64 vao response, xem toLegacyDungChungShape.
    private final ListCategoryFilesUseCase listCategoryFilesUseCase;
    private final com.td.infrastructure.config.MinioService minioService;

    // Port lichct (08/09/2026, docs/analysis/09-phan-tich-port-lich-cong-tac-domino-sang-java.md)
    private final GetWeekWorkCalendarUseCase getWeekWorkCalendarUseCase;
    private final SaveWorkCalendarUseCase saveWorkCalendarUseCase;
    private final DeleteWorkCalendarUseCase deleteWorkCalendarUseCase;

    @GetMapping("/CayCoCauToChuc")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cay co cau to chuc theo duong dan legacy",
        description = "Bugfix 03/09/2026 (bao loi nguoi dung, xem javadoc GetLegacyOrganizationTreeUseCase): "
            + "user thuong (khong co role ADMIN/" + com.td.infrastructure.security.QlvbRoles.QUAN_TRI
            + ") chi nhan cay da loc theo co quan cua CHINH ho - dung cach Domino that "
            + "(CayCoCauToChuc.java, is_full_cay chi true voi ACL Manager hoac role CVQuanTri). "
            + "Bugfix 07/09/2026: them tuy chon full_tree=true de nguoi goi (vd modal 'Phat hanh van "
            + "ban di', chon nhan 'Co quan, don vi thuoc Tinh') xin CAY DAY DU du KHONG phai ADMIN - "
            + "xem javadoc GetLegacyOrganizationTreeUseCase muc 'Bugfix 07/09/2026'.")
    public ResponseEntity<com.td.application.common.models.ApiResponse<LegacyOrganizationTreeResponse>> getCayCoCauToChuc(
            @RequestParam(name = "jedis_key", required = false) String jedisKey,
            @RequestParam(name = "full_tree", required = false) Boolean fullTree,
            org.springframework.security.core.Authentication authentication) {
        boolean includeFullTree = isFullTreeAdmin(authentication) || Boolean.TRUE.equals(fullTree);
        return ok(getLegacyOrganizationTreeUseCase.execute(jedisKey, includeFullTree));
    }

    // Domino is_full_cay: access=="Manager" hoac (level=="1" && roles chua "CVQuanTri") -
    // tuong duong Java: role ADMIN hoac QlvbRoles.QUAN_TRI (da danh san lam ghi chu tai QlvbRoles).
    private boolean isFullTreeAdmin(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(authority ->
            "ROLE_ADMIN".equals(authority.getAuthority())
                || ("ROLE_" + com.td.infrastructure.security.QlvbRoles.QUAN_TRI).equals(authority.getAuthority()));
    }

    // ================= Port lichct (08/09/2026): GetLichCongTac / CayCoCauXemLichTongHop =========
    // Xem docs/analysis/09-phan-tich-port-lich-cong-tac-domino-sang-java.md muc 7.4/11.

    @PostMapping("/GetLichCongTac")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lay lich cong tac 1 tuan cua 1 chu the "
            + "(tuong thich qlvb/danhmuc/GetLichCongTac?databasetype=lichct)",
        description = "Body tai dung ViewQueryRequest: keys=[thu2 dd/MM/yyyy, chu the (identifier hoac "
            + "username)], viewname=canhan|phong|donvi|coquan|lanhdaotinh. items/pageNumber/pageSize "
            + "khong dung. mobile/cache CHUA port (xem muc 11 tai lieu 09) - LUON doc DB truc tiep, "
            + "khong nhan ipserver/dbpath tuy y tu client (bao mat, xem javadoc GetWeekWorkCalendarUseCase).")
    public ResponseEntity<com.td.application.common.models.ApiResponse<List<Map<String, Object>>>> getLichCongTac(
            @Valid @RequestBody ViewQueryRequest request) {
        var weekRequest = new GetWeekWorkCalendarRequest();
        List<String> keys = request.getKeys();
        weekRequest.setWeekStart(keys != null && keys.size() > 0 ? keys.get(0) : null);
        weekRequest.setSubject(keys != null && keys.size() > 1 ? keys.get(1) : null);
        weekRequest.setScope(request.getViewname());
        return ok(getWeekWorkCalendarUseCase.execute(weekRequest));
    }

    @PostMapping("/CayCoCauXemLichTongHop")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cay co cau xem lich tong hop (tuong thich qlvb/danhmuc/CayCoCauXemLichTongHop)",
        description = "Alias cua CayCoCauToChuc: FE goi endpoint nay KHONG kem databasetype (xem "
            + "muc 4.3 tai lieu 09 - updateURLWithDatabaseType fallback ve qlvb) - dung chung "
            + "GetLegacyOrganizationTreeUseCase (cay to chuc) NHUNG voi includeUsers=true (bo sung "
            + "09/09/2026, yeu cau nguoi dung) de moi node co them con la \"user\" (tung nguoi dung "
            + "thuoc don vi do) - khop Domino that (CayCoCauXemLichTongHop.java cho phep chon TUNG "
            + "NGUOI, khong chi chon theo don vi) - xem javadoc GetLegacyOrganizationTreeUseCase ve "
            + "gioi han da biet (bo qua nhom \"Lanh dao\" tong hop rieng cua Domino).")
    public ResponseEntity<com.td.application.common.models.ApiResponse<LegacyOrganizationTreeResponse>> cayCoCauXemLichTongHop(
            @RequestParam(name = "jedis_key", required = false) String jedisKey,
            @RequestParam(name = "full_tree", required = false) Boolean fullTree,
            org.springframework.security.core.Authentication authentication) {
        boolean includeFullTree = isFullTreeAdmin(authentication) || Boolean.TRUE.equals(fullTree);
        return ok(getLegacyOrganizationTreeUseCase.execute(jedisKey, includeFullTree, true));
    }

    // Port danhmuc (09/09/2026, xem javadoc GetLegacyOrganizationFlatListUseCase): danh sach PHANG
    // moi node co cau to chuc (Domino DanhSachCoCauToChuc.java, view cocautochuc_by_identifier) -
    // KHAC CayCoCauToChuc (cay, co loc pham vi theo co quan cua user). FE goi qua getCoCauToChuc()
    // (global.jsx), thuc te dung o hau het man vbden (16 call site, khao sat 09/09/2026).
    @PostMapping("/DanhSachCoCauToChuc")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sach phang co cau to chuc (tuong thich qlvb/danhmuc/DanhSachCoCauToChuc)",
        description = "Tra ve MOI node (agency_level/agency/unit/department), KHONG loc theo user/is_active - "
            + "dung nguyen view Domino goc. Field: id/name/identifier/identifiers/identifier_other/"
            + "clerical/user_receiver/dbpath/ipserver/user_manage (user_manage luon rong - xem javadoc use case).")
    public ResponseEntity<com.td.application.common.models.ApiResponse<List<Map<String, Object>>>> danhSachCoCauToChuc() {
        return ok(getLegacyOrganizationFlatListUseCase.execute());
    }

    @PostMapping("/CayCoCauToChuc")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(
        summary = "Tao moi node cay co cau to chuc theo duong dan legacy",
        description = "Rule: agency_level -> (agency_level|agency), agency -> unit, unit -> department"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Tao node thanh cong",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(
                    name = "Success",
                    value = """
                        {
                          "success": true,
                          "data": "2c2d0789-b2b9-4e2a-bf64-68aaadf27f7d",
                          "error": null
                        }
                        """
                )
            })
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Du lieu khong hop le",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(
                    name = "Failure",
                    value = """
                        {
                          "success": false,
                          "data": null,
                          "error": "Quan he cha-con khong hop le: parent=agency, child=agency"
                        }
                        """
                )
            })
        )
    })
    public ResponseEntity<com.td.application.common.models.ApiResponse<UUID>> createCayCoCauToChuc(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                                description = "Payload tao node theo dung field FE (identifier, parentid, sort_order, Form)",
                content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(
                                                name = "Create root agency_level",
                        value = """
                            {
                                                            "name": "UBND tinh Thanh Hoa",
                                                            "sort_order": 1,
                                                            "system": 0,
                                                            "receiver_id": "user_receiver",
                                                            "receiver": "receiver_position",
                                                            "parent": "",
                                                            "parentid": "",
                                                            "level": 1,
                                                            "servername": "tdoffice2/tandan853/VN",
                                                            "server_id": "F6652A67508B82A647258B2E000FB206",
                                                            "ipserver": "10.10.10.77",
                                                            "dbpath": "_ubnd",
                                                            "identifier": "H05",
                                                            "Form": "agency_level"
                            }
                            """
                    ),
                    @ExampleObject(
                                                name = "Create child agency",
                        value = """
                            {
                              "name": "So Noi Vu",
                                                            "sort_order": 10,
                                                            "parent": "UBND tinh Thanh Hoa",
                                                            "parentid": "49d9f76a07b44b449c62a20b0b3ef62f",
                                                            "identifier": "SNV",
                                                            "Form": "agency"
                            }
                            """
                    )
                })
            )
            @RequestBody CreateOrganizationRequest request) {
        return created(createOrganizationUseCase.execute(request));
    }

    @PutMapping("/CayCoCauToChuc/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cap nhat node cay co cau to chuc theo duong dan legacy")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Cap nhat thanh cong",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(
                    name = "Success",
                    value = """
                        {
                          "success": true,
                          "data": "49d9f76a-07b4-4b44-9c62-a20b0b3ef62f",
                          "error": null
                        }
                        """
                )
            })
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Khong the cap nhat do vi pham rule",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(
                    name = "Failure",
                    value = """
                        {
                          "success": false,
                          "data": null,
                          "error": "Khong the doi node hien tai vi node con 'Don vi A' co loai unit khong tuong thich voi cha loai agency"
                        }
                        """
                )
            })
        )
    })
    public ResponseEntity<com.td.application.common.models.ApiResponse<UUID>> updateCayCoCauToChuc(
            @Parameter(description = "Organization ID", required = true, example = "49d9f76a-07b4-4b44-9c62-a20b0b3ef62f")
            @PathVariable("id") UUID id,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                                description = "Payload cap nhat node theo dung field FE (identifier, parentid, sort_order, Form)",
                content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(
                        name = "Update name and order",
                        value = """
                            {
                              "name": "Phong To Chuc - Hanh Chinh",
                                                            "sort_order": 20,
                                                            "identifier": "PTCHC",
                                                            "Form": "department",
                                                            "is_active": true
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Move node to new parent",
                        value = """
                            {
                                                            "parent": "So Noi Vu",
                                                            "parentid": "6dc8d5386f9e42d78e5830b58c31beff",
                                                            "Form": "unit"
                            }
                            """
                    )
                })
            )
            @RequestBody UpdateOrganizationRequest request) {
        return ok(updateOrganizationUseCase.execute(id, request));
    }

    @DeleteMapping("/CayCoCauToChuc/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xoa mem node cay co cau to chuc theo duong dan legacy")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Xoa thanh cong",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(
                    name = "Success",
                    value = """
                        {
                          "success": true,
                          "data": "49d9f76a-07b4-4b44-9c62-a20b0b3ef62f",
                          "error": null
                        }
                        """
                )
            })
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Khong the xoa do dang co node con",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(
                    name = "Failure",
                    value = """
                        {
                          "success": false,
                          "data": null,
                          "error": "To chuc dang co node con, vui long xoa hoac chuyen node con truoc"
                        }
                        """
                )
            })
        )
    })
    public ResponseEntity<com.td.application.common.models.ApiResponse<UUID>> deleteCayCoCauToChuc(
            @Parameter(description = "Organization ID", required = true, example = "49d9f76a-07b4-4b44-9c62-a20b0b3ef62f")
            @PathVariable("id") UUID id) {
        return ok(deleteOrganizationUseCase.execute(id));
    }

    // BL-118 (tiếp, 20/08/2026): "danh mục dùng chung theo form" (document_type/sector/priority/
    // method... — các control-select ở màn Nhập mới văn bản đến/đi) — trước đây FE gọi thẳng
    // qlvb/danhmuc/LayDanhSachDungChung (Domino), dữ liệu ĐÃ lạc hậu từ khi Category/BL-113 trở
    // thành nguồn thật (giống lý do đã vá DanhSachSo/DanhSachNguoiDung). Cùng bảng "categories" với
    // CategoriesController — tái dùng nguyên SearchCategoriesUseCase (đã hỗ trợ lọc theo "forms"),
    // không xây use case mới.
    //
    // Bo sung 09/09/2026 (khao sat toan bo FE con goi URL Domino LayDanhSachDungChung, xem javadoc
    // GetLegacyOrgLookupForDungChungUseCase): Domino goc la servlet TONG QUAT (query BAT KY view nao
    // theo "viewname") - ngoai nhanh "danh muc theo form" o tren, con 3 viewname THAT SU con dung:
    // vwcocautochuc (con truc tiep 1 to chuc), user_by_parentid (thanh vien truc tiep),
    // users_by_parentids (thanh vien CA CAY CON). Dinh tuyen theo request.getViewname() TRUOC KHI
    // roi vao nhanh Category mac dinh - KHONG doi shape/route cua nhanh Category (van la default).
    @PostMapping("/LayDanhSachDungChung")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Lay danh sach dung chung theo form (tuong thich qlvb/danhmuc/LayDanhSachDungChung)",
        description = "keys[0] = Category.form (vd document_type/sector/priority/method) - du lieu dung chung "
            + "voi CategoriesController (bang categories, BL-113). viewname/items nhan cho du hop dong cu "
            + "nhung KHONG dung (tru 3 viewname to chuc/nguoi dung o duoi) - mac dinh tra ve 1 tap field "
            + "co dinh: id/name/sort_order/short_name. Rieng viewname=vwcocautochuc|user_by_parentid|"
            + "users_by_parentids: xem javadoc GetLegacyOrgLookupForDungChungUseCase.")
    public ResponseEntity<com.td.application.common.models.ApiResponse<List<Map<String, Object>>>> layDanhSachDungChung(
            @RequestBody LayDanhSachDungChungRequest request) {
        String viewname = request.getViewname() == null ? "" : request.getViewname().trim();
        String firstKey = request.getKeys() == null || request.getKeys().isEmpty() ? null : request.getKeys().get(0);
        if ("vwcocautochuc".equalsIgnoreCase(viewname)) {
            return ok(getLegacyOrgLookupForDungChungUseCase.getOrganizationChildren(firstKey));
        }
        if ("vwcocautochuc_cay".equalsIgnoreCase(viewname)) {
            return ok(getLegacyOrgLookupForDungChungUseCase.getOrganizationSubtree(firstKey));
        }
        if ("user_by_parentid".equalsIgnoreCase(viewname)) {
            return ok(getLegacyOrgLookupForDungChungUseCase.getOrganizationMembers(firstKey, false));
        }
        if ("users_by_parentids".equalsIgnoreCase(viewname)) {
            return ok(getLegacyOrgLookupForDungChungUseCase.getOrganizationMembers(firstKey, true));
        }
        // 06/10/2026: vwunitleaders (truoc la Domino qlvb/danhmuc/GetLanhDao) - keys [orgId, level, nhom cap lanh dao].
        if ("vwunitleaders".equalsIgnoreCase(viewname)) {
            List<String> keys = request.getKeys() == null ? List.of() : request.getKeys();
            return ok(getLegacyOrgLookupForDungChungUseCase.getUnitLeaders(
                firstKey, keys.size() > 1 ? keys.get(1) : null, keys.size() > 2 ? keys.get(2) : null));
        }

        var searchRequest = new SearchCategoriesRequest();
        searchRequest.setForms(request.getKeys());
        searchRequest.setKeyword(request.getKeyword());
        int pageSize = request.getPageSize() == null || request.getPageSize() <= 0 ? 200 : request.getPageSize();
        searchRequest.setPageSize(Math.min(pageSize, 200));
        searchRequest.setPageNumber(request.getPageNumber() == null || request.getPageNumber() <= 0
            ? 0 : request.getPageNumber() - 1);
        searchRequest.setSortBy("sortOrder");
        searchRequest.setSortDirection("asc");
        // Bo sung 09/09/2026 (Banner.tsx): search.isactive - Domino goc dung field nay de chi lay
        // banner dang bat, khac cfg "isActive" ("true"/"false" dang chuoi, giong quy uoc legacy
        // parseLegacyBoolean da dung o buildUpdateCategoryRequest ben duoi).
        if (request.getSearch() != null && request.getSearch().get("isactive") != null) {
            searchRequest.setIsActive(parseLegacyBoolean(String.valueOf(request.getSearch().get("isactive"))));
        }

        boolean includeFiles = Boolean.TRUE.equals(request.getImage());
        var page = searchCategoriesUseCase.execute(searchRequest);
        List<Map<String, Object>> items = page.getItems().stream()
            .map(dto -> toLegacyDungChungShape(dto, includeFiles))
            .toList();
        return ok(items);
    }

    // Bo sung 09/09/2026 (cutover Footer.tsx/Banner.tsx/ListItemHDSD.tsx, form footer/banner/help):
    // "content" = Category.description, "notification" = dynamicFields (khong co cot tuong minh -
    // Category chua tung co khai niem nay truoc do). "files" (base64, CHI khi includeFiles=true - xem
    // loadCategoryFilesAsDataUri) - Footer.tsx/Banner.tsx doc thang files[0].content lam <img src>,
    // KHONG the tro thang toi endpoint /files/{id}/download (yeu cau Authorization header ma <img>
    // khong gui duoc) nen phai nhung san base64, dung nhu Domino goc tra ve.
    private Map<String, Object> toLegacyDungChungShape(CategoryDto dto, boolean includeFiles) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("id", dto.getId());
        item.put("name", dto.getName());
        item.put("sort_order", dto.getSortOrder());
        item.put("short_name", dto.getCode());
        item.put("content", dto.getDescription() == null ? "" : dto.getDescription());
        Object notification = dto.getDynamicFields() == null ? null : dto.getDynamicFields().get("notification");
        item.put("notification", notification == null ? "" : String.valueOf(notification));
        if (includeFiles) {
            item.put("files", loadCategoryFilesAsDataUri(dto.getId()));
        }
        return item;
    }

    // Tai NGUYEN VEN noi dung tung tep dinh kem (qua MinioService, cung storagePath da dung o
    // CategoriesController#downloadCategoryFile) roi ma hoa base64 nhung thang vao response - CHI
    // goi khi includeFiles=true (image=true tu FE) de khong ton I/O cho 30+ call site "danh muc
    // theo form" khac (document_type/sector/priority...) khong dung field nay. Loi tung tep (vd file
    // vat ly bi mat tren MinIO) KHONG chan ca response - bo qua tep do, tra ve nhung tep doc duoc.
    private List<Map<String, Object>> loadCategoryFilesAsDataUri(UUID categoryId) {
        List<Map<String, Object>> files = new java.util.ArrayList<>();
        try {
            for (CategoryFileDto file : listCategoryFilesUseCase.execute(categoryId).getItems()) {
                try (var inputStream = minioService.getObject(file.getStoragePath())) {
                    byte[] bytes = inputStream.readAllBytes();
                    String mimeType = file.getMimeType() == null || file.getMimeType().isBlank()
                        ? "application/octet-stream" : file.getMimeType();
                    Map<String, Object> fileRow = new java.util.LinkedHashMap<>();
                    fileRow.put("name", file.getOriginalFileName() != null ? file.getOriginalFileName() : file.getFileName());
                    fileRow.put("content", "data:" + mimeType + ";base64,"
                        + java.util.Base64.getEncoder().encodeToString(bytes));
                    files.add(fileRow);
                } catch (Exception fileEx) {
                    // bo qua 1 tep loi, khong chan ca response - xem javadoc phuong thuc.
                }
            }
        } catch (Exception ex) {
            // category khong co tep/loi tra cuu - tra danh sach rong (an toan, giong hanh vi "chua
            // co logo" hien tai).
        }
        return files;
    }

    // Slice 1 duthao (24/08/2026), mo them vbdi (28/08/2026): "Cap ban hanh" cho man ChiTietPage.jsx
    // (du thao) / SubFormThongTinCoBan.jsx (vbdi) - tuong thich qlvb/danhmuc/publish?get=capbanhanh.
    // CHI ho tro get="capbanhanh" - cac mode khac cua Domino thuc (kythualenh/kyuyquyen/nguoiduyet/
    // capbanhanhhdndtinh) CHUA port, tra 400 ro rang thay vi im lang tra sai du lieu (xem javadoc
    // GetCapBanHanhOptionsUseCase ve pham vi/quyet dinh don gian hoa da xac nhan voi nguoi dung).
    @PostMapping("/publish")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh muc dung chung 'publish' (tuong thich qlvb/danhmuc/publish)",
        description = "CHI ho tro get=\"capbanhanh\" (Cap ban hanh - databasetype=duthao|vbdi) - "
            + "tra ve danh sach to chuc to tien cua user hien tai (agency_level/agency/unit/"
            + "department), thu tu GOC->LA. Cac mode khac CHUA ho tro, tra 400")
    public ResponseEntity<com.td.application.common.models.ApiResponse<List<Map<String, Object>>>> publish(
            @RequestBody PublishCatalogRequest request) {
        // 01/10/2026: + get=kythualenh (Hinh thuc ky "Ky thua lenh" - du thao/van ban di), xem
        // javadoc GetKyThuaLenhSignersUseCase.
        if ("kythualenh".equalsIgnoreCase(request.getGet())) {
            return ok(getKyThuaLenhSignersUseCase.execute(request.getCapbanhanhid()));
        }
        if (!"capbanhanh".equalsIgnoreCase(request.getGet())) {
            return badRequest(Result.failure(
                "get=" + request.getGet() + " chua duoc ho tro o Java (chi co capbanhanh, kythualenh)"));
        }
        return ok(getCapBanHanhOptionsUseCase.execute(request.getDatabasetype()));
    }

    // ================= BL-105: GetView / LuuDanhMuc / XoaDanhMuc / CapNhatCache =================

    @PostMapping("/GetView")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Lay danh sach theo view (tuong thich qlvb/danhmuc/GetView)",
        description = "Uy quyen cho ViewQueryService (BL-102) - vi du viewname=vwuserbyparentid de lay "
            + "danh sach nguoi dung theo node co cau to chuc. Rieng databasetype=tudien&"
            + "viewname=nhom_xem_lich_tong_hop (port lichct, 08/09/2026) duoc xu ly RIENG qua "
            + "SearchCategoriesUseCase (form=xem_lich) thay vi ViewQueryService - xem javadoc "
            + "getNhomXemLichTongHop ve ly do (ViewQueryService chua co case nay - da xac nhan bang "
            + "cach doc code that, KHONG suy doan - va Category hien song o control-plane datasource "
            + "rieng, tranh troi 2 datasource trong 1 switch dung chung cua ViewQueryService).")
    public ResponseEntity<com.td.application.common.models.ApiResponse<List<Map<String, Object>>>> getView(
            @RequestParam(name = "databasetype", required = false) String databasetype,
            @Valid @RequestBody ViewQueryRequest request) {
        if ("tudien".equalsIgnoreCase(databasetype)
                && "nhom_xem_lich_tong_hop".equalsIgnoreCase(request.getViewname())) {
            return ok(getNhomXemLichTongHop(request));
        }
        return ok(viewQueryService.execute("danhmuc", request));
    }

    // Port lichct (08/09/2026) - nhom xem lich tong hop (Domino: DB "tudien", Form="xem_lich") -
    // dung lai Category (bang dung chung, da co CRUD+cache hoan chinh) loc theo form="xem_lich",
    // cung tien le layDanhSachDungChung ben duoi (khong di qua ViewQueryService, xem javadoc getView).
    private List<Map<String, Object>> getNhomXemLichTongHop(ViewQueryRequest request) {
        var searchRequest = new SearchCategoriesRequest();
        searchRequest.setForms(List.of("xem_lich"));
        int pageSize = request.getPageSize() <= 0 ? 100 : Math.min(request.getPageSize(), 200);
        searchRequest.setPageSize(pageSize);
        searchRequest.setPageNumber(Math.max(0, request.getPageNumber() - 1));
        searchRequest.setSortBy("sortOrder");
        searchRequest.setSortDirection("asc");

        var page = searchCategoriesUseCase.execute(searchRequest);
        return page.getItems().stream().map(this::toNhomXemLichShape).toList();
    }

    private Map<String, Object> toNhomXemLichShape(CategoryDto dto) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("id", dto.getId());
        item.put("name", dto.getName());
        item.put("sort_order", dto.getSortOrder());
        Object receiverObj = dto.getDynamicFields() == null ? null : dto.getDynamicFields().get("receiver_obj");
        item.put("receiver_obj", receiverObj == null ? "" : receiverObj);
        return item;
    }

    @PostMapping("/LuuDanhMuc")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Luu danh muc - tao/cap nhat (tuong thich qlvb/danhmuc/LuuDanhMuc)",
        description = "form-data giong CreateOrUpdateDocument phia Domino: co query param ?id= -> cap nhat, "
            + "khong co -> tao moi. Du lieu dung chung voi CategoriesController (bang categories) - "
            + "cache tu dong dong bo vi cung mot CategoryCacheService. Rieng databasetype=lichct (hoac "
            + "params.form=\"lich\") - port lichct 08/09/2026, xem docs/analysis/09-... - route sang "
            + "SaveWorkCalendarUseCase (bang work_calendars rieng) thay vi categories.")
    public ResponseEntity<com.td.application.common.models.ApiResponse<UUID>> luuDanhMuc(
            @Parameter(description = "Category ID - co gia tri thi la cap nhat, khong thi la tao moi")
            @RequestParam(name = "id", required = false) UUID id,
            @RequestParam(name = "databasetype", required = false) String databasetype,
            @RequestParam Map<String, String> params) {
        if ("lichct".equalsIgnoreCase(databasetype) || "lich".equals(params.get("form"))) {
            var request = buildSaveWorkCalendarRequest(id, params);
            return id == null
                ? created(saveWorkCalendarUseCase.execute(request))
                : ok(saveWorkCalendarUseCase.execute(request));
        }
        if (id == null) {
            return created(createCategoryUseCase.execute(buildCreateCategoryRequest(params)));
        }
        return ok(updateCategoryUseCase.execute(id, buildUpdateCategoryRequest(params)));
    }

    // Port lichct (08/09/2026) - tuong duong nhanh Form="lich" cua LuuDanhMuc.java Domino - tach
    // field tinh (thu2/type/identifier/ghichu) khoi field dong (luoi o lich), cung cach
    // buildTaoDuThaoRequest tach field trong DuThaoController.
    private SaveWorkCalendarRequest buildSaveWorkCalendarRequest(UUID id, Map<String, String> params) {
        var request = new SaveWorkCalendarRequest();
        request.setId(id);
        request.setScope(params.get("type"));
        request.setWeekStart(params.get("thu2"));
        request.setIdentifier(params.get("identifier"));
        request.setNote(params.get("ghichu"));

        Map<String, String> fields = new java.util.LinkedHashMap<>(params);
        for (String metaKey : List.of(
                "id", "databasetype", "type", "thu2", "identifier", "ghichu", "form", "user_creator")) {
            fields.remove(metaKey);
        }
        request.setWeekFields(fields);
        return request;
    }

    @DeleteMapping("/XoaDanhMuc")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xoa danh muc hoac lam moi cache (tuong thich qlvb/danhmuc/XoaDanhMuc)",
        description = "{id, deleteoption} -> xoa mem danh muc (dung chung du lieu voi CategoriesController). "
            + "{deletetype:\"cache\"} -> lam moi cache danh muc. KHONG ho tro xoa truc tiep 1 Redis key tuy y "
            + "(key_cache) nhu ban goc vi ly do bao mat. Rieng databasetype=lichct (port lichct 08/09/2026) - "
            + "route sang DeleteWorkCalendarUseCase. LUU Y: endpoint nay van yeu cau vai tro ADMIN/"
            + "PRODUCT_MANAGER/BRAND_MANAGER (ke thua @PreAuthorize dung chung) - CHUA ap dung logic "
            + "'chu lich tu xoa duoc' cua Domino goc cho lichct (ghi nhan la han che, xem muc 11 tai lieu 09) "
            + "- an toan hon la noi long nham bao mat cua nhanh Category.")
    public ResponseEntity<com.td.application.common.models.ApiResponse<UUID>> xoaDanhMuc(
            @RequestParam(name = "databasetype", required = false) String databasetype,
            @RequestBody XoaDanhMucRequest request) {
        if ("cache".equalsIgnoreCase(request.getDeletetype())) {
            categoryCacheService.evictAllListCaches();
            if (request.getId() != null) {
                categoryCacheService.evict(request.getId());
            }
            return ok(Result.success(request.getId()));
        }
        if (request.getId() == null) {
            return badRequest(Result.failure("id không được để trống"));
        }
        if ("lichct".equalsIgnoreCase(databasetype)) {
            return ok(deleteWorkCalendarUseCase.execute(request.getId()));
        }
        return ok(deleteCategoryUseCase.execute(request.getId()));
    }

    @PostMapping("/CapNhatCache")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Lam moi cache danh muc (tuong thich qlvb/danhmuc/CapNhatCache)",
        description = "Phien ban Java chi ho tro lam moi (evict) cache danh muc theo id (tuy chon) va toan bo "
            + "danh sach - KHONG ho tro ghi tuy y 1 key-value Redis nhu ban goc (rui ro bao mat neu client "
            + "tuy y ghi de cache).")
    public ResponseEntity<com.td.application.common.models.ApiResponse<Void>> capNhatCache(
            @RequestBody(required = false) CapNhatCacheRequest request) {
        categoryCacheService.evictAllListCaches();
        if (request != null && request.getId() != null) {
            categoryCacheService.evict(request.getId());
        }
        return noContent();
    }

    private CreateCategoryRequest buildCreateCategoryRequest(Map<String, String> params) {
        var request = new CreateCategoryRequest();
        String name = params.get("name");
        request.setName(name);
        request.setCode(firstNonBlank(params.get("code"), name));
        request.setDescription(params.get("description"));
        request.setForm(params.get("form"));
        request.setParentId(parseUuid(firstNonBlank(params.get("parentId"), params.get("parentid"))));
        request.setSortOrder(parseInt(firstNonBlank(params.get("sortOrder"), params.get("sort_order")), CreateCategoryRequest.DEFAULT_SORT_ORDER));
        // Bo sung 08/09/2026 (port lichct - nhom xem lich tong hop, muc 5.3 tai lieu 09):
        // "receiver_obj" (chuoi JSON mang thanh vien nhom) khong co cot tuong minh tren Category -
        // luu vao dynamic_fields (JSONB da co san). Chi anh huong khi client THUC SU gui receiver_obj
        // (additive, khong pha cac form Category khac).
        if (params.get("receiver_obj") != null) {
            request.getDynamicFields().put("receiver_obj", params.get("receiver_obj"));
        }
        return request;
    }

    private UpdateCategoryRequest buildUpdateCategoryRequest(Map<String, String> params) {
        var request = new UpdateCategoryRequest();
        if (params.containsKey("name")) {
            request.setName(params.get("name"));
        }
        if (params.containsKey("code")) {
            request.setCode(params.get("code"));
        }
        if (params.containsKey("description")) {
            request.setDescription(params.get("description"));
        }
        if (params.containsKey("form")) {
            request.setForm(params.get("form"));
        }

        String parentIdRaw = firstNonBlank(params.get("parentId"), params.get("parentid"));
        if (parentIdRaw != null) {
            request.setParentIdFromJson(parseUuid(parentIdRaw));
        }

        String sortOrderRaw = firstNonBlank(params.get("sortOrder"), params.get("sort_order"));
        if (sortOrderRaw != null) {
            request.setSortOrder(parseInt(sortOrderRaw, 0));
        }

        String statusRaw = firstNonBlank(params.get("isActive"), params.get("status"));
        if (statusRaw != null) {
            request.setIsActive(parseLegacyBoolean(statusRaw));
        }

        // Bo sung 08/09/2026 (port lichct - xem buildCreateCategoryRequest) - merge (khong ghi de
        // toan bo dynamicFields, dung guard UpdateCategoryUseCase muc 6 tai lieu 04).
        if (params.get("receiver_obj") != null) {
            Map<String, String> dynamicFields = new java.util.LinkedHashMap<>();
            dynamicFields.put("receiver_obj", params.get("receiver_obj"));
            request.setDynamicFields(dynamicFields);
        }

        return request;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private UUID parseUuid(String value) {
        try {
            return (value == null || value.isBlank()) ? null : UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return (value == null || value.isBlank()) ? fallback : Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private boolean parseLegacyBoolean(String value) {
        String normalized = value.trim().toLowerCase();
        return normalized.equals("1") || normalized.equals("true");
    }
}
