package com.td.web.controllers.v1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.Result;
import com.td.application.simpki.ConvertDocumentToPdfUseCase;
import com.td.application.simpki.DownloadSignedPdfUseCase;
import com.td.application.simpki.GetSimPkiCertificateUseCase;
import com.td.application.simpki.GetSimPkiSignStatusUseCase;
import com.td.application.simpki.LocateSimPkiSignaturesRequest;
import com.td.application.simpki.LocateSimPkiSignaturesUseCase;
import com.td.application.simpki.SimPkiCertificateResult;
import com.td.application.simpki.SimPkiSignatureLocation;
import com.td.application.simpki.SimPkiSignStatusResult;
import com.td.application.simpki.SimPkiSignSubmissionResult;
import com.td.application.simpki.SimPkiSignaturePlacement;
import com.td.application.simpki.SubmitSimPkiSignatureRequest;
import com.td.application.simpki.SubmitSimPkiSignatureUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Proxy AN TOÀN tới TDSimPki ({@code C:\code_projects\HSCV\TDSimPki}) — giữ {@code X-API-Key}/
 * {@code X-Tenant-Id} phía SERVER (xem {@link com.td.infrastructure.simpki.TdSimPkiProperties}),
 * Frontend KHÔNG BAO GIỜ gọi thẳng TDSimPki hay biết API key (khác biến {@code VITE_*} bị nhúng công
 * khai vào bundle JS). Cùng mẫu 2-tầng controller→use case với {@link OnlyOfficeController}.
 *
 * <p><b>Generic theo thiết kế</b> — endpoint KHÔNG gắn với module nghiệp vụ nào (không đọc
 * documentId/fileId của hệ thống này): caller tự tải bytes PDF từ đâu cũng được (Java/MinIO, Domino,
 * hay ứng dụng khác) rồi gửi thẳng multipart — đúng tinh thần "hướng mở rộng cho ứng dụng khác tích
 * hợp dễ dàng". Icon test "Ký SIM (PDF Viewer mới)" ở ThanhHoaDEV (Dự thảo) là 1 caller trong số đó,
 * không phải caller duy nhất được thiết kế cho.</p>
 */
@RestController
@RequestMapping("/api/v1/simpki")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SimPKI", description = "Proxy an toàn tới TDSimPki - ký PDF qua SIM-PKI (kèm vị trí nhìn thấy tuỳ chọn) và chuyển đổi Office sang PDF")
public class SimPkiSignController extends BaseController {

    private final SubmitSimPkiSignatureUseCase submitSimPkiSignatureUseCase;
    private final GetSimPkiSignStatusUseCase getSimPkiSignStatusUseCase;
    private final DownloadSignedPdfUseCase downloadSignedPdfUseCase;
    private final ConvertDocumentToPdfUseCase convertDocumentToPdfUseCase;
    private final GetSimPkiCertificateUseCase getSimPkiCertificateUseCase;
    private final LocateSimPkiSignaturesUseCase locateSimPkiSignaturesUseCase;
    private final com.td.application.simpki.CompleteExternalPdfSignatureUseCase completeExternalPdfSignatureUseCase;
    private final com.td.application.simpki.PrepareExternalPdfSignatureUseCase prepareExternalPdfSignatureUseCase;
    private final ObjectMapper objectMapper;

    // Bổ sung 23/09/2026: TDPdfViewer (SimPkiCertModal.jsx, "Thông tin chứng thư số") gọi THẲNG
    // TDSimPki (X-API-Key nằm trong window.TD_PDF_VIEWER_CONFIG/VITE_* phía FE - lộ ra được), khác hẳn
    // MỌI endpoint khác trong controller này (đều qua proxy giữ key phía server). Endpoint mới này
    // cho host nào cần giữ đúng nguyên tắc "key không rời server" (như ThanhHoaDEV) một lựa chọn thay
    // thế - CHƯA nối FE (TDPdfViewer vẫn gọi thẳng TDSimPki như cũ, theo yêu cầu người dùng: "chỉ thêm
    // endpoint proxy, chưa nối FE ngay" - việc nối dây (postMessage bridge hoặc cấu hình apiBase) để
    // sau, vì fetch() trong TDPdfViewer không có sẵn token đăng nhập của host để gọi endpoint có
    // @PreAuthorize này).
    @PostMapping("/certificates")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tra cứu chứng thư số SIM-PKI theo số điện thoại (proxy TDSimPki)",
        description = "Đồng bộ, KHÔNG ký. Trả về đầy đủ: base64 DER (leaf + chuỗi CA), serial, "
            + "subject/issuer DN, hạn hiệu lực, signature algorithm, public key info.")
    public ResponseEntity<ApiResponse<SimPkiCertificateResult>> certificates(
            @RequestParam("msisdn") String msisdn) {
        var result = getSimPkiCertificateUseCase.execute(msisdn);
        return ok(result);
    }

    // 07/10/2026: ký USB Token (TDPdfViewer + TDClientAgent) — bước 9 "đóng gói CMS vào PDF" đi qua
    // td-web thay vì viewer gọi thẳng /api/pdf/external-signature/complete (qlvbv6 không định tuyến
    // đường đó tới TDSimPki -> HTTP 500). Xem javadoc CompleteExternalPdfSignatureUseCase.
    @PostMapping(value = "/external-signature/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Hoàn tất chữ ký USB Token: nhúng CMS vào PDF (proxy TDSimPki)",
        description = "Body giữ nguyên hợp đồng TDSimPki /api/pdf/external-signature/complete; trả {fileBase64, fileName}.")
    public ResponseEntity<ApiResponse<com.td.application.simpki.ExternalPdfSignatureResult>> completeExternalSignature(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> request) {
        return ok(completeExternalPdfSignatureUseCase.execute(request));
    }

    // 08/10/2026: ký USB Token - thêm 1 chữ ký CÓ HÌNH tại 1 vị trí tem (gọi lặp cho từng tem) - xem
    // PrepareExternalPdfSignatureUseCase. API riêng, không dùng chung luồng SIM.
    @PostMapping(value = "/external-signature/prepare", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chuẩn bị 1 chữ ký USB Token có hình tại 1 vị trí tem (proxy TDSimPki)",
        description = "Body {requestId, pdfBase64, placement{page,x,y,width,height,imageBase64,type,text,color}, certBase64, fileName}; "
            + "trả {preparedPdfBase64, byteRange, contentsOffset, contentsLength, hashAlgorithm, hashBase64}.")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> prepareExternalSignature(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> request) {
        return ok(prepareExternalPdfSignatureUseCase.execute(request));
    }

    @PostMapping(value = "/sign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Ký PDF qua SIM-PKI (proxy TDSimPki)",
        description = "Multipart: 'file' (PDF), 'msisdn', 'displayMessage' (tuỳ chọn), 'signatures' "
            + "(tuỳ chọn, chuỗi JSON mảng vị trí tem nhìn thấy - khớp payload.signatures[] của "
            + "TDPdfViewer). Async ở TDSimPki - trả về ngay {simPkiFileId, status:'SIGNING'}, gọi "
            + "/{simPkiFileId}/status để theo dõi.")
    public ResponseEntity<ApiResponse<SimPkiSignSubmissionResult>> sign(
            @RequestPart("file") MultipartFile file,
            @RequestParam("msisdn") String msisdn,
            @RequestParam(value = "displayMessage", required = false) String displayMessage,
            @RequestParam(value = "signatures", required = false) String signaturesJson) {
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException ex) {
            return badRequest(Result.failure("Khong doc duoc tep da tai len"));
        }
        List<SimPkiSignaturePlacement> signatures = parseSignatures(signaturesJson);
        var request = SubmitSimPkiSignatureRequest.builder()
            .pdfBytes(pdfBytes)
            .fileName(file.getOriginalFilename())
            .msisdn(msisdn)
            .displayMessage(displayMessage)
            .signatures(signatures)
            .build();
        var result = submitSimPkiSignatureUseCase.execute(request);
        return ok(result);
    }

    // Bổ sung 29/09/2026 - TỰ ĐẶT CHỮ KÝ theo tên người ký ở modal Ký SIM (TDPdfViewer): FE gửi bytes
    // PDF đang mở + tên người ký, nhận hộp vùng chữ "tên" (pt, gốc dưới-trái) rồi tự đặt tem phía trên
    // qua kênh 'place-signatures' của TDPdfViewer. Stateless ở TDSimPki (không lưu tệp). Không tìm
    // thấy -> 200 với mảng rỗng (không phải lỗi).
    @PostMapping(value = "/locate-signatures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Định vị vị trí chữ ký trong PDF (proxy TDSimPki, không lưu tệp)",
        description = "Multipart: 'file' (PDF), 'signerName' (tên người ký - server tự dựng regex an toàn, "
            + "mỗi trang có tên trả 1 vị trí) và/hoặc 'signatureLocators' (tuỳ chọn, chuỗi JSON mảng "
            + "regex), 'occurrence' (tuỳ chọn FIRST|LAST|ALL_PAGES - mặc định ALL_PAGES khi có signerName: mỗi trang có tên trả 1 vị trí). Trả mảng {regex, matchedText, page, x, y, "
            + "width, height} - PDF points, gốc dưới-trái; rỗng nếu không tìm thấy.")
    public ResponseEntity<ApiResponse<List<SimPkiSignatureLocation>>> locateSignatures(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "signerName", required = false) String signerName,
            @RequestParam(value = "signatureLocators", required = false) String signatureLocatorsJson,
            @RequestParam(value = "occurrence", required = false) String occurrence) {
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException ex) {
            return badRequest(Result.failure("Khong doc duoc tep da tai len"));
        }
        var request = LocateSimPkiSignaturesRequest.builder()
            .pdfBytes(pdfBytes)
            .fileName(file.getOriginalFilename())
            .signerName(signerName)
            .signatureLocators(parseLocators(signatureLocatorsJson))
            .occurrence(occurrence)
            .build();
        var result = locateSimPkiSignaturesUseCase.execute(request);
        return ok(result);
    }

    @GetMapping("/{simPkiFileId}/status")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tra trạng thái ký SIM-PKI", description = "status: READY|SIGNING|SIGNED|FAILED.")
    public ResponseEntity<ApiResponse<SimPkiSignStatusResult>> status(
            @Parameter(description = "ID trả về từ /sign") @PathVariable String simPkiFileId) {
        var result = getSimPkiSignStatusUseCase.execute(simPkiFileId);
        return ok(result);
    }

    @GetMapping("/{simPkiFileId}/download")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tải PDF đã ký", description = "Chỉ có nội dung khi status=SIGNED (hoặc READY nếu chưa ký).")
    public ResponseEntity<byte[]> download(@PathVariable String simPkiFileId) {
        var result = downloadSignedPdfUseCase.execute(simPkiFileId);
        if (!result.isSuccess()) {
            log.warn("Tai PDF da ky that bai: simPkiFileId={} - {}", simPkiFileId, result.getError());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        byte[] content = result.getData();
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(simPkiFileId + ".pdf", StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentLength(content.length)
            .body(content);
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chuyển đổi Word/Excel/PPT sang PDF (proxy TDSimPki)",
        description = "Đồng bộ - trả thẳng bytes PDF trong response, không cần theo dõi trạng thái.")
    public ResponseEntity<byte[]> convert(@RequestPart("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            return ResponseEntity.badRequest().build();
        }
        var result = convertDocumentToPdfUseCase.execute(content, file.getOriginalFilename());
        if (!result.isSuccess()) {
            log.warn("Chuyen doi PDF that bai: fileName={} - {}", file.getOriginalFilename(), result.getError());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        byte[] pdfBytes = result.getData();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdfBytes.length)
            .body(pdfBytes);
    }

    private List<String> parseLocators(String locatorsJson) {
        if (locatorsJson == null || locatorsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(locatorsJson, new TypeReference<List<String>>() {
            });
        } catch (IOException ex) {
            log.warn("Khong parse duoc 'signatureLocators' JSON - bo qua");
            return null;
        }
    }

    private List<SimPkiSignaturePlacement> parseSignatures(String signaturesJson) {
        if (signaturesJson == null || signaturesJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(signaturesJson, new TypeReference<List<SimPkiSignaturePlacement>>() {
            });
        } catch (IOException ex) {
            log.warn("Khong parse duoc 'signatures' JSON - bo qua vi tri tem, ky an", ex);
            return null;
        }
    }
}
