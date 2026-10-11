package com.td.infrastructure.simpki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.simpki.SimPkiSignaturePlacement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Client HTTP thuần JDK gọi TDSimPki ({@code C:\code_projects\HSCV\TDSimPki}, xem
 * {@code TDSimPki/docs/19-backend-webapi-pdf-checklist.md} cho hợp đồng API đầy đủ). Giữ
 * {@code X-API-Key}/{@code X-Tenant-Id} phía server — đây là lý do module này tồn tại thay vì để
 * Frontend gọi thẳng TDSimPki (xem javadoc {@link TdSimPkiProperties}).
 *
 * <p>Không dùng Spring bean nào khác ngoài {@link TdSimPkiProperties} — nếu cần đổi sang
 * {@code RestClient}/{@code WebClient} sau này (khi thêm {@code spring-web} vào module), chỉ cần
 * thay lớp này, không đụng tới use case/controller gọi nó.</p>
 */
@Slf4j
@Component
public class TdSimPkiClient {
    private final TdSimPkiProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TdSimPkiClient(TdSimPkiProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
            .build();
    }

    /** Đăng ký 1 PDF có sẵn (POST /api/pdf/upload) — trả về {@code fileId} phía TDSimPki. */
    public String uploadPdf(byte[] pdfBytes, String fileName) {
        ensureEnabled();
        MultipartRequestBuilder body = new MultipartRequestBuilder()
            .addFile("file", safeFileName(fileName), "application/pdf", pdfBytes);
        HttpRequest request = baseRequestBuilder("/api/pdf/upload")
            .header("Content-Type", body.contentType())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.build()))
            .build();
        JsonNode json = sendForJson(request, "upload");
        return json.path("fileId").asText();
    }

    /** Nộp yêu cầu ký (POST /api/pdf/{id}/sign) — trả về status ban đầu (thường "SIGNING"). */
    public String sign(String fileId, String msisdn, String displayMessage, List<SimPkiSignaturePlacement> signatures) {
        ensureEnabled();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("msisdn", msisdn);
        if (displayMessage != null && !displayMessage.isBlank()) {
            payload.put("displayMessage", displayMessage);
        }
        if (signatures != null && !signatures.isEmpty()) {
            ArrayNode array = payload.putArray("signatures");
            for (SimPkiSignaturePlacement placement : signatures) {
                ObjectNode node = array.addObject();
                node.put("page", placement.page());
                node.put("x", placement.x());
                node.put("y", placement.y());
                node.put("width", placement.width());
                node.put("height", placement.height());
                putIfPresent(node, "imageBase64", placement.imageBase64());
                putIfPresent(node, "type", placement.type());
                putIfPresent(node, "text", placement.text());
                putIfPresent(node, "color", placement.color());
            }
        }
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new TdSimPkiException("Khong the dung JSON yeu cau ky", ex);
        }
        HttpRequest request = baseRequestBuilder("/api/pdf/" + fileId + "/sign")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
        JsonNode json = sendForJson(request, "sign");
        return json.path("status").asText();
    }

    /**
     * Định vị vị trí chữ ký bằng regex trên 1 PDF gửi thẳng (POST /api/pdf/locate-signatures — bản
     * STATELESS, TDSimPki không lưu tệp; xem {@code TDSimPki/docs/27-dinh-vi-ten-nguoi-ky-stateless.md}).
     * Trả mảng JSON kết quả; KHÔNG khớp regex nào ({@code 400 SIGNATURE_LOCATOR_NOT_FOUND}) được coi là
     * kết quả bình thường → mảng rỗng (caller chỉ đang "thử tìm", không phải lỗi).
     *
     * <p>Regex gửi dạng JSON với ký tự ngoài ASCII được escape {@code \\uXXXX} (TDSimPki
     * {@code readTree} giải lại đúng ký tự) — tên người ký tiếng Việt không phụ thuộc charset mà
     * servlet container phía TDSimPki dùng để đọc field text của multipart.</p>
     */
    public JsonNode locateSignatures(byte[] pdfBytes, String fileName, List<String> regexes, String occurrence) {
        return locateSignatures(pdfBytes, fileName, regexes, occurrence, null, false, null);
    }

    /**
     * Như trên, kèm điều kiện "vị trí phù hợp" (30/09/2026, TDSimPki {@code SignatureLocatorConstraints}):
     * chỉ nhận lần khớp có khoảng trống phía trên ≥ {@code minClearanceAbovePt} và/hoặc đứng riêng dòng.
     */
    public JsonNode locateSignatures(byte[] pdfBytes, String fileName, List<String> regexes, String occurrence,
                                     Float minClearanceAbovePt, boolean requireAloneOnLine, Float clearanceWidthPt) {
        ensureEnabled();
        String locatorsJson;
        try {
            locatorsJson = objectMapper.writer()
                .with(com.fasterxml.jackson.core.json.JsonWriteFeature.ESCAPE_NON_ASCII)
                .writeValueAsString(regexes);
        } catch (IOException ex) {
            throw new TdSimPkiException("Khong the dung JSON signatureLocators", ex);
        }
        MultipartRequestBuilder body = new MultipartRequestBuilder()
            .addFile("file", safeFileName(fileName), "application/pdf", pdfBytes)
            .addField("signatureLocators", locatorsJson);
        if (occurrence != null && !occurrence.isBlank()) {
            body.addField("signatureLocatorOccurrence", occurrence);
        }
        if (minClearanceAbovePt != null && minClearanceAbovePt > 0) {
            body.addField("signatureLocatorMinClearanceAbove", String.valueOf(minClearanceAbovePt));
        }
        if (requireAloneOnLine) {
            body.addField("signatureLocatorRequireAloneOnLine", "true");
        }
        if (clearanceWidthPt != null && clearanceWidthPt > 0) {
            body.addField("signatureLocatorClearanceWidth", String.valueOf(clearanceWidthPt));
        }
        HttpRequest request = baseRequestBuilder("/api/pdf/locate-signatures")
            .header("Content-Type", body.contentType())
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.build()))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 400 && isErrorCode(response.body(), "SIGNATURE_LOCATOR_NOT_FOUND")) {
                return objectMapper.createArrayNode();
            }
            requireOk(response.statusCode(), "locate-signatures", response::body);
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TdSimPkiException("Goi TDSimPki (locate-signatures) that bai: " + ex.getMessage(), ex);
        }
    }

    /**
     * 08/10/2026 — POST /api/pdf/locate-text-slots (stateless): định vị ô trống để chèn chữ (xem
     * {@link com.td.application.simpki.LocateTextSlotsUseCase}). {@code slots} gửi dạng JSON escape
     * {@code \\uXXXX} ký tự ngoài ASCII (cùng lý do {@link #locateSignatures}).
     */
    public JsonNode locateTextSlots(byte[] pdfBytes, String fileName, JsonNode slots) {
        ensureEnabled();
        String slotsJson;
        try {
            slotsJson = objectMapper.writer()
                .with(com.fasterxml.jackson.core.json.JsonWriteFeature.ESCAPE_NON_ASCII)
                .writeValueAsString(slots);
        } catch (IOException ex) {
            throw new TdSimPkiException("Khong the dung JSON slots", ex);
        }
        MultipartRequestBuilder body = new MultipartRequestBuilder()
            .addFile("file", safeFileName(fileName), "application/pdf", pdfBytes)
            .addField("slots", slotsJson);
        HttpRequest request = baseRequestBuilder("/api/pdf/locate-text-slots")
            .header("Content-Type", body.contentType())
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.build()))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                String message = null;
                try {
                    message = objectMapper.readTree(response.body()).path("message").asText(null);
                } catch (IOException ignored) {
                    // body không phải JSON
                }
                throw new TdSimPkiException(message != null && !message.isBlank()
                    ? message : "TDSimPki tu choi dinh vi o trong: HTTP " + response.statusCode());
            }
            requireOk(response.statusCode(), "locate-text-slots", response::body);
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TdSimPkiException("Goi TDSimPki (locate-text-slots) that bai: " + ex.getMessage(), ex);
        }
    }

    private boolean isErrorCode(String body, String code) {
        try {
            return code.equals(objectMapper.readTree(body).path("code").asText());
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * Chuyển đổi Word/Excel/PPT/... sang PDF (POST /api/pdf/convert, KHÔNG kèm callbackUrl → TDSimPki
     * xử lý ĐỒNG BỘ, xem {@code PdfApiService#convertDocument}) rồi tải luôn kết quả về — 1 lời gọi
     * duy nhất trả thẳng bytes PDF, caller không cần biết/theo dõi {@code fileId} trung gian.
     */
    public byte[] convertToPdf(byte[] fileBytes, String fileName) {
        ensureEnabled();
        MultipartRequestBuilder body = new MultipartRequestBuilder()
            .addFile("file", fileName == null || fileName.isBlank() ? "document" : fileName,
                "application/octet-stream", fileBytes);
        HttpRequest request = baseRequestBuilder("/api/pdf/convert")
            .header("Content-Type", body.contentType())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.build()))
            .build();
        JsonNode json = sendForJson(request, "convert");
        String status = json.path("status").asText();
        if (!"READY".equals(status)) {
            throw new TdSimPkiException("TDSimPki convert tra ve trang thai khong mong doi: " + status);
        }
        return download(json.path("fileId").asText());
    }

    /**
     * Tra cứu chứng thư số (X.509) của thuê bao SIM-PKI theo MSISDN — đồng bộ, KHÔNG ký
     * (POST /api/simpki/certificates, KHÁC hẳn nhóm endpoint {@code /api/pdf/...} ở trên - xem
     * {@code TDSimPki/docs/23-simpki-certificate-api.md}).
     */
    public JsonNode getCertificate(String msisdn) {
        ensureEnabled();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("msisdn", msisdn);
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new TdSimPkiException("Khong the dung JSON yeu cau tra chung thu", ex);
        }
        HttpRequest request = baseRequestBuilder("/api/simpki/certificates")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
        return sendForJson(request, "certificates");
    }

    /**
     * 07/10/2026: hoàn tất chữ ký USB Token (POST /api/pdf/external-signature/complete) — xem javadoc
     * {@code CompleteExternalPdfSignatureUseCase}. Lỗi 4xx của TDSimPki (ByteRange/chữ ký không khớp,
     * chứng thư hết hạn...) giữ nguyên thông điệp để người dùng biết lý do, không gộp thành lỗi chung.
     */
    public JsonNode completeExternalSignature(java.util.Map<String, Object> payload) {
        return postExternalSignature("complete", payload);
    }

    /**
     * 08/10/2026: chuẩn bị 1 chữ ký USB Token CÓ HÌNH tại 1 vị trí tem (POST
     * /api/pdf/external-signature/prepare, TDSimPki ExternalPdfSignaturePrepareService) — client gọi lặp
     * cho từng tem: prepare -> Token ký -> complete.
     */
    public JsonNode prepareExternalSignature(java.util.Map<String, Object> payload) {
        return postExternalSignature("prepare", payload);
    }

    private JsonNode postExternalSignature(String action, java.util.Map<String, Object> payload) {
        ensureEnabled();
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new TdSimPkiException("Khong the dung JSON yeu cau hoan tat chu ky", ex);
        }
        HttpRequest request = baseRequestBuilder("/api/pdf/external-signature/" + action)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 400 && status < 500) {
                String message = null;
                try {
                    message = objectMapper.readTree(response.body()).path("message").asText(null);
                } catch (IOException ignored) {
                    // body không phải JSON
                }
                log.warn("TDSimPki external-signature tu choi: HTTP {} - {}", status, message);
                throw new TdSimPkiException(message != null && !message.isBlank()
                    ? message : "TDSimPki tu choi hoan tat chu ky: HTTP " + status);
            }
            requireOk(status, "external-signature/" + action, () -> "(an noi dung)");
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TdSimPkiException("Goi TDSimPki (external-signature/" + action + ") that bai: " + ex.getMessage(), ex);
        }
    }

    /** Tra trạng thái (GET /api/pdf/{id}/status). */
    public JsonNode status(String fileId) {
        ensureEnabled();
        HttpRequest request = baseRequestBuilder("/api/pdf/" + fileId + "/status").GET().build();
        return sendForJson(request, "status");
    }

    /** Tải PDF (GET /api/pdf/{id}/download). */
    public byte[] download(String fileId) {
        ensureEnabled();
        HttpRequest request = baseRequestBuilder("/api/pdf/" + fileId + "/download").GET().build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            requireOk(response.statusCode(), "download", () -> new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
            return response.body();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TdSimPkiException("Goi TDSimPki (download) that bai: " + ex.getMessage(), ex);
        }
    }

    private JsonNode sendForJson(HttpRequest request, String operation) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            requireOk(response.statusCode(), operation, response::body);
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TdSimPkiException("Goi TDSimPki (" + operation + ") that bai: " + ex.getMessage(), ex);
        }
    }

    private void requireOk(int statusCode, String operation, java.util.function.Supplier<String> bodySupplier) {
        if (statusCode < 200 || statusCode >= 300) {
            log.warn("TDSimPki {} that bai: HTTP {} - {}", operation, statusCode, bodySupplier.get());
            throw new TdSimPkiException("TDSimPki " + operation + " that bai: HTTP " + statusCode);
        }
    }

    private HttpRequest.Builder baseRequestBuilder(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create(properties.getBaseUrl() + path))
            .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
            .header("X-API-Key", properties.getApiKey())
            .header("X-Tenant-Id", properties.getTenantId());
    }

    private void ensureEnabled() {
        if (!properties.isEnabled() || properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new TdSimPkiException(
                "Tich hop TDSimPki chua duoc cau hinh (app.simpki.enabled=false hoac thieu app.simpki.base-url)");
        }
    }

    private void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document.pdf";
        }
        String value = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return value.endsWith(".pdf") ? value : value + ".pdf";
    }
}
