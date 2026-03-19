package com.td.web.controllers.v1;

import com.td.application.common.models.Result;
import com.td.application.sharedcore.CreateOrganizationRequest;
import com.td.application.sharedcore.CreateOrganizationUseCase;
import com.td.application.sharedcore.DeleteOrganizationUseCase;
import com.td.application.sharedcore.GetLegacyOrganizationTreeUseCase;
import com.td.application.sharedcore.LegacyOrganizationTreeResponse;
import com.td.application.sharedcore.UpdateOrganizationRequest;
import com.td.application.sharedcore.UpdateOrganizationUseCase;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/danhmuc")
@RequiredArgsConstructor
@Validated
@Tag(name = "Legacy Danh Muc", description = "API danh muc tuong thich he thong cu")
public class LegacyDanhMucController extends BaseController {

    private final GetLegacyOrganizationTreeUseCase getLegacyOrganizationTreeUseCase;
    private final CreateOrganizationUseCase createOrganizationUseCase;
    private final UpdateOrganizationUseCase updateOrganizationUseCase;
    private final DeleteOrganizationUseCase deleteOrganizationUseCase;

    @GetMapping("/CayCoCauToChuc")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cay co cau to chuc theo duong dan legacy")
    public ResponseEntity<LegacyOrganizationTreeResponse> getCayCoCauToChuc(
            @RequestParam(name = "jedis_key", required = false) String jedisKey) {
        return ok(getLegacyOrganizationTreeUseCase.execute(jedisKey));
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
    public ResponseEntity<Result<UUID>> createCayCoCauToChuc(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                description = "Payload tao node. Truong form bat buoc, ho tro parent_id cho parent",
                content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(
                        name = "Create root agency_level",
                        value = """
                            {
                              "code": "CO_QUAN_CAP_TINH",
                              "name": "Co quan cap tinh",
                              "description": "Nut goc cap co quan",
                              "form": "agency_level",
                              "sortOrder": 1,
                              "isActive": true
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Create child agency",
                        value = """
                            {
                              "code": "SO_NOI_VU",
                              "name": "So Noi Vu",
                              "description": "Co quan truc thuoc",
                              "parent_id": "49d9f76a-07b4-4b44-9c62-a20b0b3ef62f",
                              "form": "agency",
                              "sortOrder": 10,
                              "isActive": true
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
    public ResponseEntity<Result<UUID>> updateCayCoCauToChuc(
            @Parameter(description = "Organization ID", required = true, example = "49d9f76a-07b4-4b44-9c62-a20b0b3ef62f")
            @PathVariable("id") UUID id,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                description = "Payload cap nhat node. Truong form bat buoc, ho tro parent_id cho parent",
                content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(
                        name = "Update name and order",
                        value = """
                            {
                              "name": "Phong To Chuc - Hanh Chinh",
                              "description": "Cap nhat ten phong ban",
                              "form": "department",
                              "sortOrder": 20,
                              "isActive": true
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Move node to new parent",
                        value = """
                            {
                              "parent_id": "6dc8d538-6f9e-42d7-8e58-30b58c31beff",
                              "form": "unit"
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
    public ResponseEntity<Result<UUID>> deleteCayCoCauToChuc(
            @Parameter(description = "Organization ID", required = true, example = "49d9f76a-07b4-4b44-9c62-a20b0b3ef62f")
            @PathVariable("id") UUID id) {
        return ok(deleteOrganizationUseCase.execute(id));
    }
}
