package com.td.web.controllers.v1;

import com.td.application.common.models.Result;
import com.td.application.documents.FileContentExtractService;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 17/10/2026 — "Nội dung trích xuất" của tệp đính kèm (Apache Tika + OCR, td-woker) — thay "Văn bản đã bóc tách" và
 * "Chuyển qua máy chủ bóc tách" (Domino qlvb/elasticsearch?searchtype=toanvan, qlvb/dococr). Xem
 * docs/architecture/14-trich-noi-dung-tep.md. Quyền CÙNG mức API tải tệp (tệp phải thuộc đúng văn bản).
 *
 * <p>Phụ thuộc qua {@link ObjectProvider}: context test WebMvcTest dùng chung nạp MỌI controller — phụ thuộc bắt buộc
 * mới sẽ làm hỏng toàn bộ test td-web nếu thiếu MockBean.</p>
 */
@RestController
@RequestMapping("/api/v1/documents/{documentId}/files/{fileId}/content-extract")
@Tag(name = "File Content Extract", description = "Nội dung chữ trích từ tệp đính kèm (Tika, chưa OCR)")
public class FileContentExtractsController extends BaseController {

    private final ObjectProvider<FileContentExtractService> service;

    public FileContentExtractsController(ObjectProvider<FileContentExtractService> service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Nội dung trích xuất của tệp",
        description = "status: DONE | EMPTY | SKIPPED | FAILED | PENDING (đang chờ worker) | NONE (chưa trích)")
    public ResponseEntity<?> get(@PathVariable UUID documentId, @PathVariable UUID fileId) {
        return respond(service().get(documentId, fileId));
    }

    @PostMapping("/retry")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Trích lại nội dung tệp", description = "Đưa tệp vào hàng đợi trích (worker xử lý ngay)")
    public ResponseEntity<?> retry(@PathVariable UUID documentId, @PathVariable UUID fileId) {
        return respond(service().requestReextract(documentId, fileId));
    }

    private FileContentExtractService service() {
        return service.getObject();
    }

    private ResponseEntity<?> respond(Result<Map<String, Object>> result) {
        return result.isSuccess() ? ok(result.getData()) : badRequest(result);
    }
}
