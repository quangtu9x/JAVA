package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.numbering.GetNumberingConfigsUseCase;
import com.td.application.numbering.NumberingConfigDto;
import com.td.application.numbering.UpdateNumberingConfigRequest;
import com.td.application.numbering.UpdateNumberingConfigUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * BL-104 (Document Numbering Service) — Definition of Done (4): API cấu hình format sinh số văn
 * bản theo từng {@code documentType}.
 */
@RestController
@RequestMapping("/api/v1/numbering-configs")
@RequiredArgsConstructor
@Validated
@Tag(name = "NumberingConfig", description = "Cấu hình format sinh số văn bản (BL-104)")
public class NumberingConfigController extends BaseController {

    private final GetNumberingConfigsUseCase getNumberingConfigsUseCase;
    private final UpdateNumberingConfigUseCase updateNumberingConfigUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách cấu hình numbering theo documentType")
    public ResponseEntity<ApiResponse<List<NumberingConfigDto>>> listConfigs() {
        return ok(getNumberingConfigsUseCase.execute());
    }

    @PutMapping("/{documentType}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật format/trạng thái numbering cho 1 documentType")
    public ResponseEntity<ApiResponse<NumberingConfigDto>> updateConfig(
            @Parameter(description = "Document type (vd. vbden, vbdi)", required = true) @PathVariable String documentType,
            @Valid @RequestBody UpdateNumberingConfigRequest request) {
        var result = updateNumberingConfigUseCase.execute(documentType, request);
        if (!result.isSuccess()) {
            return badRequest(result);
        }
        return ok(result);
    }
}
