package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.Result;
import com.td.application.vanbanphapquy.VanBanPhapQuyRequests;
import com.td.application.vanbanphapquy.VanBanPhapQuyUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 30/10/2026 (port module "Văn bản pháp quy" - thay qlvb/view/query|search|get|add|delete
 * ?databasetype=vanbanphapquy). Tệp đính kèm qua API tệp chung {@code /api/v1/documents/{id}/files}.
 */
@RestController
@RequestMapping("/api/v1/vanbanphapquy")
@RequiredArgsConstructor
@Tag(name = "Văn bản pháp quy", description = "Tra cứu / cập nhật văn bản pháp quy")
public class VanBanPhapQuyController extends BaseController {

    private final VanBanPhapQuyUseCase useCase;

    @PostMapping("/GetView")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách theo view (cơ quan + năm/lĩnh vực/loại văn bản) + tìm kiếm")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getView(
            @RequestBody(required = false) VanBanPhapQuyRequests.GetView request) {
        var result = useCase.getView(request == null ? new VanBanPhapQuyRequests.GetView() : request);
        if (!result.isSuccess()) {
            return badRequest(Result.failure(result.getError()));
        }
        return ok(result.getData());
    }

    @PostMapping("/XemChiTiet")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> xemChiTiet(@RequestBody VanBanPhapQuyRequests.Id request) {
        return ok(useCase.detail(request));
    }

    @PostMapping("/Luu")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UUID>> luu(@RequestBody VanBanPhapQuyRequests.Save request) {
        return ok(useCase.save(request));
    }

    @PostMapping("/Xoa")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UUID>>> xoa(@RequestBody VanBanPhapQuyRequests.Ids request) {
        return ok(useCase.delete(request.getIds()));
    }
}
