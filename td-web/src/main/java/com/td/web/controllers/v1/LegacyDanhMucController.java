package com.td.web.controllers.v1;

import com.td.application.sharedcore.GetLegacyOrganizationTreeUseCase;
import com.td.application.sharedcore.LegacyOrganizationTreeResponse;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/danhmuc")
@RequiredArgsConstructor
@Validated
@Tag(name = "Legacy Danh Muc", description = "API danh muc tuong thich he thong cu")
public class LegacyDanhMucController extends BaseController {

    private final GetLegacyOrganizationTreeUseCase getLegacyOrganizationTreeUseCase;

    @GetMapping("/CayCoCauToChuc")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cay co cau to chuc theo duong dan legacy")
    public ResponseEntity<LegacyOrganizationTreeResponse> getCayCoCauToChuc(
            @RequestParam(name = "jedis_key", required = false) String jedisKey) {
        return ok(getLegacyOrganizationTreeUseCase.execute(jedisKey));
    }
}
