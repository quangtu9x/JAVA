package com.td.infrastructure.onlyoffice;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.onlyoffice.BuildOnlyOfficeEditorConfigUseCase;
import com.td.application.onlyoffice.OnlyOfficeEditorConfigRequest;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import com.td.infrastructure.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Xem javadoc {@link BuildOnlyOfficeEditorConfigUseCase}. Build đúng shape mà FE component
 * {@code OnlyOfficeEditor} (types.ts#OnlyOfficeConfig) mong đợi truyền nguyên vào
 * {@code DocsAPI.DocEditor}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BuildOnlyOfficeEditorConfigUseCaseImpl implements BuildOnlyOfficeEditorConfigUseCase {

    // Xem api.onlyoffice.com/editors/config - "documentType" chỉ nhận đúng 3 giá trị này (bỏ pdf,
    // component FE khai báo "pdf" cho tương lai nhưng luồng đầu tiên này chỉ mở doc/xls/ppt như
    // đúng phạm vi "Sửa tệp" cũ - tdeditor.js#TDSuaTepDinhKem cũng chỉ nhận doc/docx/xls/xlsx).
    private static final Set<String> WORD_EXTENSIONS = Set.of("doc", "docx", "docm", "rtf", "odt", "txt");
    private static final Set<String> CELL_EXTENSIONS = Set.of("xls", "xlsx", "xlsm", "ods", "csv");
    private static final Set<String> SLIDE_EXTENSIONS = Set.of("ppt", "pptx", "pptm", "odp");

    private final FileMetadataJpaRepository fileMetadataRepository;
    private final OnlyOfficeProperties onlyOfficeProperties;
    private final OnlyOfficeAccessTokenService accessTokenService;
    private final OnlyOfficeDocumentJwtService documentJwtService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public Result<Map<String, Object>> execute(OnlyOfficeEditorConfigRequest request) {
        if (onlyOfficeProperties.getDocserverUrl() == null || onlyOfficeProperties.getDocserverUrl().isBlank()) {
            return Result.failure("Chưa cấu hình ONLYOFFICE_DOCSERVER_URL trên Backend");
        }

        // Bugfix (15/09/2026, báo lỗi người dùng "File không tìm thấy" khi mở icon OnlyOffice từ tab
        // "Quá trình xử lý"): tra CHỈ theo fileId (khoá chính, luôn duy nhất toàn hệ thống), KHÔNG
        // bắt buộc khớp thêm documentId FE gửi lên như GetFileUseCase vẫn làm — component
        // ListIconFiles (iconlistduthao.jsx) được nhiều màn khác nhau tái dùng, và ở tab "Quá trình xử
        // lý" (duthaofile.jsx) prop "resdata" truyền vào là 1 dòng LỊCH SỬ XỬ LÝ (draft_process_documents),
        // KHÔNG phải chính dự thảo - "resdata.id" FE gửi lên vì vậy KHÁC document_id thật của tệp, dù
        // tệp hoàn toàn hợp lệ. document_id THẬT lấy trực tiếp từ chính dòng file_metadata tìm được
        // (nguồn duy nhất đáng tin), không tin theo documentId phía FE gửi lên.
        Optional<FileMetadataEntity> fileOpt = fileMetadataRepository.findById(request.getFileId());
        if (fileOpt.isEmpty()) {
            log.warn("OnlyOffice editor-config: khong tim thay file (tenant={}, fileId={})",
                    TenantContext.getCurrentTenant(), request.getFileId());
            return Result.failure("File không tồn tại");
        }
        FileMetadataEntity file = fileOpt.get();
        UUID documentId = file.getDocumentId();

        String displayName = file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getStoredFilename();
        String extension = extractExtension(displayName);
        String documentType = resolveDocumentType(extension);
        if (documentType == null) {
            return Result.failure("Định dạng ." + extension + " chưa hỗ trợ chỉnh sửa trực tuyến (OnlyOffice)");
        }

        String tenantId = TenantContext.getCurrentTenant();
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null || username.isBlank()) {
            return Result.failure("Không xác định được người dùng hiện tại");
        }

        String baseUrl = resolveBaseUrl(request.getFallbackCallbackBaseUrl());
        if (baseUrl == null) {
            return Result.failure("Chưa cấu hình ONLYOFFICE_CALLBACK_BASE_URL trên Backend "
                    + "(địa chỉ Document Server dùng để gọi ngược vào Backend)");
        }

        String contentToken = accessTokenService.issue(
                OnlyOfficeAccessTokenService.PURPOSE_CONTENT, tenantId, documentId, request.getFileId(),
                username, Duration.ofMinutes(onlyOfficeProperties.getContentTokenTtlMinutes()));
        String callbackToken = accessTokenService.issue(
                OnlyOfficeAccessTokenService.PURPOSE_CALLBACK, tenantId, documentId, request.getFileId(),
                username, Duration.ofMinutes(onlyOfficeProperties.getCallbackTokenTtlMinutes()));

        String documentUrl = baseUrl + "/api/v1/onlyoffice/content?token=" + urlEncode(contentToken);
        String callbackUrl = baseUrl + "/api/v1/onlyoffice/callback?token=" + urlEncode(callbackToken);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("title", displayName);
        document.put("url", documentUrl);
        document.put("fileType", extension);
        document.put("key", buildDocumentKey(file));
        document.put("permissions", Map.of("edit", true, "download", true, "print", true));

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", username);
        user.put("name", username);

        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("forcesave", true);
        customization.put("close", Map.of("visible", true, "text", "Đóng"));

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", "edit");
        editorConfig.put("lang", "vi");
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("user", user);
        editorConfig.put("customization", customization);

        Map<String, Object> configWithoutToken = new LinkedHashMap<>();
        configWithoutToken.put("document", document);
        configWithoutToken.put("documentType", documentType);
        configWithoutToken.put("editorConfig", editorConfig);

        String token = documentJwtService.signConfig(configWithoutToken);

        Map<String, Object> response = new LinkedHashMap<>(configWithoutToken);
        if (token != null) {
            response.put("token", token);
        }
        return Result.success(response);
    }

    private String resolveBaseUrl(String fallback) {
        String configured = onlyOfficeProperties.getCallbackBaseUrl();
        String baseUrl = (configured != null && !configured.isBlank()) ? configured : fallback;
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveDocumentType(String extension) {
        if (WORD_EXTENSIONS.contains(extension)) {
            return "word";
        }
        if (CELL_EXTENSIONS.contains(extension)) {
            return "cell";
        }
        if (SLIDE_EXTENSIONS.contains(extension)) {
            return "slide";
        }
        return null;
    }

    /** Đổi key mỗi khi nội dung tệp thay đổi (uploadedAt) để Document Server không dùng cache cũ. */
    private String buildDocumentKey(FileMetadataEntity file) {
        String version = file.getUploadedAt() != null ? file.getUploadedAt().toString() : String.valueOf(System.currentTimeMillis());
        String raw = "oo_" + file.getId() + "_" + version;
        String sanitized = raw.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized.length() > 128 ? sanitized.substring(0, 128) : sanitized;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
