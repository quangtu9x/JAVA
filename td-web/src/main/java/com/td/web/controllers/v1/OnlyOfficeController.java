package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.documents.DownloadFileRequest;
import com.td.application.documents.GetFileUseCase;
import com.td.application.onlyoffice.BuildOnlyOfficeEditorConfigUseCase;
import com.td.application.onlyoffice.OnlyOfficeEditorConfigRequest;
import com.td.application.onlyoffice.SaveOnlyOfficeCallbackRequest;
import com.td.application.onlyoffice.SaveOnlyOfficeCallbackUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.onlyoffice.OnlyOfficeAccessClaims;
import com.td.infrastructure.onlyoffice.OnlyOfficeAccessTokenException;
import com.td.infrastructure.onlyoffice.OnlyOfficeAccessTokenService;
import com.td.infrastructure.onlyoffice.OnlyOfficeDocumentJwtException;
import com.td.infrastructure.onlyoffice.OnlyOfficeDocumentJwtService;
import com.td.infrastructure.onlyoffice.OnlyOfficeProperties;
import com.td.infrastructure.tenant.TenantContext;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Tích hợp chỉnh sửa tài liệu trực tuyến OnlyOffice (THỬ NGHIỆM — song song "Sửa tệp" cũ qua
 * BTEAdapter, xem {@code tdeditor.js#TDSuaTepDinhKem} và icon mới ở FE {@code duthaofile.jsx}).
 * Độc lập hoàn toàn với {@link DocumentsController}/{@link DuThaoController} — chỉ ĐỌC dữ liệu qua
 * {@link GetFileUseCase} (dùng lại, không sửa) và ghi qua {@link SaveOnlyOfficeCallbackUseCase}
 * (từ 25/09/2026 lưu bản sửa thành TỆP MỚI, không ghi đè tệp gốc — xem javadoc
 * {@link SaveOnlyOfficeCallbackRequest}).
 *
 * <p>3 endpoint, 2 mô hình xác thực KHÁC NHAU (xem {@code docs/architecture} nếu cần, hoặc javadoc
 * {@code OnlyOfficeAccessTokenService}):</p>
 * <ul>
 *   <li>{@code GET .../editor-config} — người dùng thật gọi (Keycloak/Tdtoken như mọi endpoint
 *       khác), trả cấu hình đã ký cho FE truyền thẳng vào component {@code OnlyOfficeEditor}.</li>
 *   <li>{@code GET /content}, {@code POST /callback} — Document Server (dịch vụ ngoài) gọi thẳng,
 *       KHÔNG mang Authorization/Tdtoken người dùng → {@code permitAll} ở {@code SecurityConfig},
 *       tự bảo vệ bằng token nội bộ ngắn hạn nhúng sẵn trong URL (query {@code token}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/onlyoffice")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "OnlyOffice", description = "Chỉnh sửa tài liệu trực tuyến OnlyOffice (thử nghiệm, song song BTEAdapter)")
public class OnlyOfficeController extends BaseController {

    private final BuildOnlyOfficeEditorConfigUseCase buildOnlyOfficeEditorConfigUseCase;
    private final SaveOnlyOfficeCallbackUseCase saveOnlyOfficeCallbackUseCase;
    private final com.td.application.onlyoffice.GetOnlyOfficeEditedFileUseCase getOnlyOfficeEditedFileUseCase;
    private final com.td.application.onlyoffice.LinkOnlyOfficeEditedFileToProcessUseCase linkOnlyOfficeEditedFileToProcessUseCase;
    private final GetFileUseCase getFileUseCase;
    private final MinioService minioService;
    private final OnlyOfficeAccessTokenService accessTokenService;
    private final OnlyOfficeDocumentJwtService documentJwtService;
    private final OnlyOfficeProperties onlyOfficeProperties;

    private final HttpClient callbackFileHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @GetMapping("/documents/{documentId}/files/{fileId}/editor-config")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cấu hình OnlyOffice DocsAPI cho 1 tệp (thử nghiệm)",
            description = "Trả nguyên payload (đã ký JWT ở field token nếu Document Server bật JWT) "
                + "để FE truyền thẳng vào prop 'config' của component OnlyOfficeEditor.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEditorConfig(
            @Parameter(description = "Document ID", required = true) @PathVariable UUID documentId,
            @Parameter(description = "File ID", required = true) @PathVariable UUID fileId,
            @Parameter(description = "\"view\" = chỉ xem (không sửa/lưu); bỏ trống = sửa trực tuyến")
            @RequestParam(name = "mode", required = false) String mode,
            HttpServletRequest servletRequest) {
        var request = OnlyOfficeEditorConfigRequest.builder()
                .documentId(documentId)
                .fileId(fileId)
                .fallbackCallbackBaseUrl(resolveFallbackBaseUrl(servletRequest))
                .viewOnly("view".equalsIgnoreCase(mode))
                .build();
        var result = buildOnlyOfficeEditorConfigUseCase.execute(request);
        return ok(result);
    }

    // Yêu cầu người dùng 25/09/2026 (OnlyOffice lưu bản sửa thành TỆP MỚI): FE hỏi phiên soạn thảo vừa
    // đóng đã lưu ra tệp mới nào — xem javadoc GetOnlyOfficeEditedFileUseCase. data = null khi chưa có.
    @GetMapping("/files/{fileId}/edited-file")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tệp mới do 1 phiên sửa trực tuyến (OnlyOffice) lưu ra từ tệp gốc",
            description = "key = document.key của editor-config phiên đó. Trả data=null nếu phiên chưa lưu ra tệp nào.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEditedFile(
            @Parameter(description = "File ID tệp gốc", required = true) @PathVariable UUID fileId,
            @Parameter(description = "document.key của phiên soạn thảo", required = true) @RequestParam String key) {
        return ResponseEntity.ok(ApiResponse.success(getOnlyOfficeEditedFileUseCase.execute(fileId, key).orElse(null)));
    }

    // Yêu cầu người dùng 25/09/2026: gắn tệp mới (do sửa trực tuyến tạo ra) vào dòng "Quá trình xử lý" vừa
    // ghi — để tệp hiện ngay dưới vết "Đã sửa". Xem javadoc LinkOnlyOfficeEditedFileToProcessUseCase.
    @PostMapping("/files/{fileId}/link-process")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Gắn tệp đã sửa trực tuyến (OnlyOffice) vào 1 dòng quá trình xử lý dự thảo")
    public ResponseEntity<ApiResponse<Void>> linkEditedFileToProcess(
            @Parameter(description = "File ID tệp MỚI (đã sửa)", required = true) @PathVariable UUID fileId,
            @RequestBody Map<String, Object> body) {
        // body: {processId, description} — description = chuỗi chi tiết sửa từ tệp nào/thay bằng tệp nào
        // (lưu vào file_metadata.description, cùng cách luồng ký số tdsignJavaFile.js).
        UUID processId;
        try {
            processId = body == null || body.get("processId") == null ? null : UUID.fromString(String.valueOf(body.get("processId")));
        } catch (IllegalArgumentException ex) {
            processId = null;
        }
        String description = body == null || body.get("description") == null ? null : String.valueOf(body.get("description"));
        return ok(linkOnlyOfficeEditedFileToProcessUseCase.execute(fileId, processId, description));
    }

    @GetMapping("/content")
    @Operation(summary = "[Document Server gọi] Tải nội dung tệp đang chỉnh sửa",
            description = "KHÔNG dùng Authorization/Tdtoken — tự bảo vệ bằng token nội bộ ngắn hạn ở "
                + "query 'token' (cấp kèm trong editor-config, xem OnlyOfficeAccessTokenService).")
    public ResponseEntity<Object> getContent(@RequestParam String token) {
        OnlyOfficeAccessClaims claims;
        try {
            claims = accessTokenService.verify(token, OnlyOfficeAccessTokenService.PURPOSE_CONTENT);
        } catch (OnlyOfficeAccessTokenException ex) {
            log.warn("OnlyOffice /content: token khong hop le - {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).<Object>build();
        }

        return TenantContext.<ResponseEntity<Object>>runAsWithResult(claims.tenantId(), () -> {
            var fileResult = getFileUseCase.execute(DownloadFileRequest.builder()
                    .documentId(claims.documentId())
                    .fileId(claims.fileId())
                    .build());
            if (!fileResult.isSuccess() || fileResult.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).<Object>build();
            }
            var file = fileResult.getData();
            try {
                InputStream inputStream = minioService.getObject(file.getStoragePath());
                MediaType mediaType = (file.getMimeType() != null && !file.getMimeType().isBlank())
                        ? MediaType.parseMediaType(file.getMimeType())
                        : MediaType.APPLICATION_OCTET_STREAM;
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .contentLength(file.getFileSize() != null ? file.getFileSize() : -1)
                        .body((Object) new InputStreamResource(inputStream));
            } catch (Exception ex) {
                log.error("OnlyOffice /content: loi tai tep tu MinIO (fileId={})", claims.fileId(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Object>build();
            }
        });
    }

    @PostMapping("/callback")
    @Operation(summary = "[Document Server gọi] Lưu tệp sau khi chỉnh sửa",
            description = "Theo đúng hợp đồng callback OnlyOffice (api.onlyoffice.com/editors/callback) "
                + "- LUÔN trả {error:0} khi đã xử lý xong (kể cả bỏ qua status không cần lưu), "
                + "{error:1} khi từ chối/lỗi để Document Server biết và có thể thử lại.")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestParam String token,
            @RequestBody(required = false) Map<String, Object> rawBody,
            HttpServletRequest servletRequest) {
        OnlyOfficeAccessClaims claims;
        try {
            claims = accessTokenService.verify(token, OnlyOfficeAccessTokenService.PURPOSE_CALLBACK);
        } catch (OnlyOfficeAccessTokenException ex) {
            log.warn("OnlyOffice /callback: token noi bo khong hop le - {}", ex.getMessage());
            return ResponseEntity.ok(Map.<String, Object>of("error", 1));
        }

        Map<String, Object> payload;
        try {
            String headerValue = servletRequest.getHeader(onlyOfficeProperties.getJwtHeader());
            payload = documentJwtService.resolveCallbackPayload(headerValue, rawBody);
        } catch (OnlyOfficeDocumentJwtException ex) {
            log.warn("OnlyOffice /callback: JWT Document Server khong hop le - {}", ex.getMessage());
            return ResponseEntity.ok(Map.<String, Object>of("error", 1));
        }

        Object statusRaw = payload != null ? payload.get("status") : null;
        int status = statusRaw instanceof Number number ? number.intValue() : -1;

        // Status theo tài liệu OnlyOffice: 2 = MustSave, 6 = Corrupted-but-force-saved (autosave khi
        // vẫn đang mở) - CHỈ 2 trạng thái này cần tải lại nội dung. Các status khác (1 đang sửa, 3
        // lỗi lưu, 4 đóng không đổi, 7 lỗi force-save) không có gì để lưu ở phía Backend nhưng VẪN
        // phải trả {error:0} - trả khác 0 cho status Document Server không mong đợi xử lý sẽ bị hiểu
        // nhầm là lỗi và bị retry vô ích.
        if (status == 2 || status == 6) {
            Object urlRaw = payload.get("url");
            if (!(urlRaw instanceof String editedFileUrl) || editedFileUrl.isBlank()) {
                log.warn("OnlyOffice /callback: status={} nhung thieu field 'url' - bo qua", status);
                return ResponseEntity.ok(Map.<String, Object>of("error", 1));
            }
            if (!isTrustedEditedFileUrl(editedFileUrl)) {
                log.warn("OnlyOffice /callback: tu choi url ngoai Document Server khi JWT tat (documentId={})",
                        claims.documentId());
                return ResponseEntity.ok(Map.<String, Object>of("error", 1));
            }
            try {
                byte[] content = downloadEditedFile(editedFileUrl);
                var saveResult = TenantContext.runAsWithResult(claims.tenantId(), () ->
                        saveOnlyOfficeCallbackUseCase.execute(SaveOnlyOfficeCallbackRequest.builder()
                                .documentId(claims.documentId())
                                .fileId(claims.fileId())
                                .content(new ByteArrayInputStream(content))
                                .contentLength(content.length)
                                // 25/09/2026: lưu thành TỆP MỚI theo phiên (document.key) — xem javadoc
                                // SaveOnlyOfficeCallbackRequest.
                                .editSessionKey(payload.get("key") instanceof String key ? key : null)
                                .username(claims.username())
                                .build()));
                if (!saveResult.isSuccess()) {
                    log.error("OnlyOffice /callback: luu tep that bai - {}", saveResult.getError());
                    return ResponseEntity.ok(Map.<String, Object>of("error", 1));
                }
            } catch (Exception ex) {
                log.error("OnlyOffice /callback: loi xu ly status={} (documentId={}, fileId={})",
                        status, claims.documentId(), claims.fileId(), ex);
                return ResponseEntity.ok(Map.<String, Object>of("error", 1));
            }
        }

        return ResponseEntity.ok(Map.<String, Object>of("error", 0));
    }

    /**
     * 28/10/2026 (rà soát bảo mật — chống SSRF): {@code token} callback nằm trong editor-config mà người dùng
     * nhận được, nên khi JWT Document Server TẮT thì bất kỳ ai cũng tự POST callback với {@code url} trỏ vào
     * dịch vụ nội bộ (Elasticsearch, MinIO, Keycloak admin...) và Backend sẽ tải về, lưu thành tệp đính kèm
     * để tải xuống. Khi JWT BẬT, payload do Document Server ký nên tin được {@code url} (Document Server có
     * thể trả url theo IP nội bộ khác {@code docserver-url}, không ép trùng host để khỏi hỏng lưu tệp). Khi
     * JWT TẮT: chỉ nhận url http(s) cùng host + cổng với {@code app.onlyoffice.docserver-url}.
     */
    private boolean isTrustedEditedFileUrl(String editedFileUrl) {
        URI target;
        try {
            target = URI.create(editedFileUrl);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return false;
        }
        if (onlyOfficeProperties.isJwtEnabled()) {
            return true;
        }
        String docserverUrl = onlyOfficeProperties.getDocserverUrl();
        if (docserverUrl == null || docserverUrl.isBlank()) {
            return false;
        }
        try {
            URI docserver = URI.create(docserverUrl.trim());
            return docserver.getHost() != null
                    && docserver.getHost().equalsIgnoreCase(target.getHost())
                    && effectivePort(docserver) == effectivePort(target);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private byte[] downloadEditedFile(String editedFileUrl) throws java.io.IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(editedFileUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> response = callbackFileHttpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new java.io.IOException("Tai tep da sua tu Document Server that bai: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String resolveFallbackBaseUrl(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
    }
}
