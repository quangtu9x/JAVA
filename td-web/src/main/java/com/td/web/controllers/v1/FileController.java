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
@Tag(name = "File Management", description = "API for file upload, download, and management operations")
@SecurityRequirement(name = "bearer-jwt")
public class FileController extends BaseController {

    private final UploadFileUseCase uploadFileUseCase;
    private final DownloadFileUseCase downloadFileUseCase;
    private final DeleteFileUseCase deleteFileUseCase;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload a file",
        description = "Upload a file to MinIO storage with metadata",
        responses = {
            @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "413", description = "File too large"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
        }
    )
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    public ResponseEntity<UploadFileResponse> uploadFile(
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(description = "File category", required = true)
            @RequestParam("category") FileCategory category,
            
            @Parameter(description = "File description")
            @RequestParam(value = "description", required = false) String description,
            
            @Parameter(description = "Tags (comma-separated)")
            @RequestParam(value = "tags", required = false) String tags,
            
            @Parameter(description = "Make file publicly accessible")
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
            log.warn("Invalid upload request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(UploadFileResponse.failure("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UploadFileResponse.failure("Internal server error"));
        }
    }

    @GetMapping("/download/{fileId}")
    @Operation(
        summary = "Download a file",
        description = "Download a file by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "File downloaded successfully"),
            @ApiResponse(responseCode = "404", description = "File not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
        }
    )
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    public ResponseEntity<InputStreamResource> downloadFile(
            @Parameter(description = "File ID", required = true)
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
            log.warn("File not found: {}", fileId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to download file: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{fileId}")
    @Operation(
        summary = "Delete a file",
        description = "Delete a file by ID (Admin or file owner only)",
        responses = {
            @ApiResponse(responseCode = "200", description = "File deleted successfully"),
            @ApiResponse(responseCode = "404", description = "File not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
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
                return ResponseEntity.ok("File deleted successfully");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete file");
            }

        } catch (IllegalArgumentException e) {
            log.warn("File not found: {}", fileId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to delete file: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error");
        }
    }

    @GetMapping("/info/{fileId}")
    @Operation(
        summary = "Get file information",
        description = "Get file metadata by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "File information retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "File not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
        }
    )
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    public ResponseEntity<FileMetadataDto> getFileInfo(
            @Parameter(description = "File ID", required = true)
            @PathVariable UUID fileId) {

        // This would require a GetFileInfoUseCase - simplified for now
        return ResponseEntity.ok().build();
    }

    // Helper method to extract user ID from authentication
    private UUID getCurrentUserId(Authentication authentication) {
        try {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UUID) {
                return (UUID) principal;
            }
            // Handle other principal types as needed
            return UUID.randomUUID(); // Fallback - should be improved
        } catch (Exception e) {
            log.warn("Failed to extract user ID from authentication", e);
            return UUID.randomUUID(); // Fallback - should be improved
        }
    }
}