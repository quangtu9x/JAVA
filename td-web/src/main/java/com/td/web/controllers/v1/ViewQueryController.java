package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * BL-102 (View Query Engine): tương thích {@code qlvb/view/query} — {@code POST /api/v1/{dbType}/query}
 * với {@code {viewname, keys, items, pageNumber, pageSize}}.
 */
@RestController
@RequestMapping("/api/v1/{dbType}")
@RequiredArgsConstructor
@Validated
@Tag(name = "ViewQuery", description = "View Query Engine (tương thích qlvb/view/query)")
public class ViewQueryController extends BaseController {

    private final ViewQueryService viewQueryService;

    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Truy vấn theo view (BL-102)",
        description = "View hỗ trợ: vwDocument_ChoXuLy (văn bản chờ xử lý theo username), "
            + "vwuserbyusername (tra người dùng theo username), vwuserbyparentid (danh sách người dùng theo đơn vị cha)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> query(
            @Parameter(description = "Loại database/module (vd. vbden, vbdi, qlvb)", required = true)
            @PathVariable String dbType,
            @Valid @RequestBody ViewQueryRequest request) {
        return ok(viewQueryService.execute(dbType, request));
    }
}
