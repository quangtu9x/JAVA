package com.td.web.controllers.v1;

import com.td.application.storage.*;
import com.td.domain.storage.FileCategory;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File Management", description = "API tải lên, tải xuống và quản lý file")
@SecurityRequirement(name = "bearer-jwt")
public class FileController extends BaseController {

    private final UploadFileUseCase uploadFileUseCase;
    private final DownloadFileUseCase downloadFileUseCase;
    private final DeleteFileUseCase deleteFileUseCase;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Tải file lên",
        description = "Tải file lên lưu trữ MinIO kèm metadata",
        responses = {
            @ApiResponse(responseCode = "200", description = "Đã tải lên thành công"),
            @ApiResponse(responseCode = "400", description = "File hoặc yêu cầu không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "413", description = "File quá lớn"),
            @ApiResponse(responseCode = "500", description = "Lỗi máy chủ")
        }
    )
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    public ResponseEntity<UploadFileResponse> uploadFile(
            @Parameter(description = "File cần tải lên", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(description = "Danh mục file", required = true)
            @RequestParam("category") FileCategory category,
            
            @Parameter(description = "Mô tả file")
            @RequestParam(value = "description", required = false) String description,
            
            @Parameter(description = "Thẻ (phân cách bằng dấu phẩy)")
            @RequestParam(value = "tags", required = false) String tags,
            
            @Parameter(description = "Cho phép truy cập công khai")
            @RequestParam(value = "isPublic", defaultValue = "false") Boolean isPublic,
            
            Authentication authentication) {

        try {
            UUID uploadedBy = getCurrentUserId(authentication);
            
            UploadFileRequest request = new UploadFileRequest();
            request.setFile(file);
            request.setFileCategory(category);
            request.setDescription(description);
            request.setTags(tags);
            request.setIsPublic(isPublic);
            request.setUploadedBy(uploadedBy);

            UploadFileResponse response = uploadFileUseCase.execute(request);
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (IllegalArgumentException e) {
            log.warn("Yêu cầu tải lên không hợp lệ: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(UploadFileResponse.failure("Yêu cầu không hợp lệ: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Tải file lên thất bại", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UploadFileResponse.failure("Lỗi máy chủ"));
        }
    }

    @GetMapping("/download/{fileId}")
    @Operation(
        summary = "Tải file xuống",
        description = "Tải file xuống theo ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Đã tải xuống thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy file"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "500", description = "Lỗi máy chủ")
        }
    )
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    public ResponseEntity<InputStreamResource> downloadFile(
            @Parameter(description = "ID file", required = true)
            @PathVariable UUID fileId,
            Authentication authentication) {

        try {
            DownloadFileResponse response = downloadFileUseCase.execute(fileId);
            
            InputStreamResource resource = new InputStreamResource(response.getInputStream());
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + response.getFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, response.getContentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(response.getFileSize()))
                .body(resource);

        } catch (IllegalArgumentException e) {
            log.warn("Không tìm thấy file: {}", fileId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Tải file xuống thất bại: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{fileId}")
    @Operation(
        summary = "Xóa file",
        description = "Xóa file theo ID (chỉ Admin hoặc chủ sở hữu)",
        responses = {
            @ApiResponse(responseCode = "200", description = "Đã xóa thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy file"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "500", description = "Lỗi máy chủ")
        }
    )
    @PreAuthorize("hasRole('ADMIN') or @fileSecurityService.canDeleteFile(#fileId, authentication.principal)")
    public ResponseEntity<String> deleteFile(
            @Parameter(description = "File ID", required = true)
            @PathVariable UUID fileId,
            Authentication authentication) {

        try {
            boolean deleted = deleteFileUseCase.execute(fileId);
            
            if (deleted) {
                return ResponseEntity.ok("Đã xóa file thành công");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Xóa file thất bại");
            }

        } catch (IllegalArgumentException e) {
            log.warn("Không tìm thấy file: {}", fileId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Xóa file thất bại: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Lỗi máy chủ");
        }
    }

    @GetMapping("/info/{fileId}")
    @Operation(
        summary = "Xem thông tin file",
        description = "Lấy metadata của file theo ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Đã lấy thông tin thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy file"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "500", description = "Lỗi máy chủ")
        }
    )
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    public ResponseEntity<FileMetadataDto> getFileInfo(
            @Parameter(description = "ID file", required = true)
            @PathVariable UUID fileId) {

        // Đây là chức năng đơn giản hoá, cần triển khai GetFileInfoUseCase
        return ResponseEntity.ok().build();
    }

    // Phương thức hỗ trợ lấy user ID từ authentication
    private UUID getCurrentUserId(Authentication authentication) {
        try {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UUID) {
                return (UUID) principal;
            }
            // Xử lý các loại principal khác nếu cần
            return UUID.randomUUID(); // Fallback - cần cải thiện
        } catch (Exception e) {
            log.warn("Không thể lấy ID người dùng từ authentication", e);
            return UUID.randomUUID(); // Fallback - cần cải thiện
        }
    }
}