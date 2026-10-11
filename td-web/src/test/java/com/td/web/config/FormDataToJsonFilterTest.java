package com.td.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockPart;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG THẬT đã gặp (22/09/2026): {@code SimPkiSignController} (proxy TDSimPki, nhận
 * {@code MultipartFile} ở {@code /api/v1/simpki/convert} và {@code /api/v1/simpki/sign}) không có
 * "/files"/"/upload" trong path — filter coi request là form text-only, đọc {@code getParts()} rồi
 * BỎ QUA part có {@code filename=} (chính là file PDF/Office cần xử lý), tạo JSON rỗng, đổi
 * Content-Type sang {@code application/json} → controller khai báo
 * {@code consumes = MULTIPART_FORM_DATA_VALUE} không còn khớp → HTTP 415. Test này bắt đúng bug đó
 * (đã xác nhận qua DevTools + curl trực tiếp vào server thật trước khi sửa).
 */
class FormDataToJsonFilterTest {

    private final FormDataToJsonFilter filter = new FormDataToJsonFilter(new ObjectMapper());

    @Test
    void doFilter_shouldPassThroughUnchanged_forSimPkiConvertMultipartUpload() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/simpki/convert");
        request.setContentType("multipart/form-data; boundary=abc");
        request.addPart(new MockPart("file", "bao-cao.docx", "noi dung gia".getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> requestSeenByChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> requestSeenByChain.set(req));

        assertThat(requestSeenByChain.get())
            .as("/simpki/convert nhan file that - KHONG duoc bi doi sang JSON (mat het file part)")
            .isSameAs(request);
        assertThat(request.getContentType()).contains("multipart/form-data");
    }

    @Test
    void doFilter_shouldPassThroughUnchanged_forSimPkiSignMultipartUpload() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/simpki/sign");
        request.setContentType("multipart/form-data; boundary=abc");
        request.addPart(new MockPart("file", "van-ban.pdf", "noi dung gia".getBytes(StandardCharsets.UTF_8)));
        request.addPart(new MockPart("msisdn", "0900000000".getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> requestSeenByChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> requestSeenByChain.set(req));

        assertThat(requestSeenByChain.get()).isSameAs(request);
        assertThat(request.getContentType()).contains("multipart/form-data");
    }

    @Test
    void doFilter_shouldPassThroughUnchanged_forSimPkiLocateSignaturesMultipartUpload() throws Exception {
        // 29/09/2026: tu dat chu ky theo ten nguoi ky - cung bay voi /simpki/sign.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/simpki/locate-signatures");
        request.setContentType("multipart/form-data; boundary=abc");
        request.addPart(new MockPart("file", "van-ban.pdf", "noi dung gia".getBytes(StandardCharsets.UTF_8)));
        request.addPart(new MockPart("signerName", "Nguyễn Văn A".getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> requestSeenByChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> requestSeenByChain.set(req));

        assertThat(requestSeenByChain.get()).isSameAs(request);
        assertThat(request.getContentType()).contains("multipart/form-data");
    }

    @Test
    void doFilter_shouldPassThroughUnchanged_forSimPkiLocateTextSlotsMultipartUpload() throws Exception {
        // 08/10/2026: dinh vi o trong chen so/ngay (ky USB Token moi o Van ban di) - cung bay.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/simpki/locate-text-slots");
        request.setContentType("multipart/form-data; boundary=abc");
        request.addPart(new MockPart("file", "van-ban.pdf", "noi dung gia".getBytes(StandardCharsets.UTF_8)));
        request.addPart(new MockPart("slots", "[{\"id\":\"so\",\"regex\":\"Số\\\\s*:( ?)/\"}]".getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> requestSeenByChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> requestSeenByChain.set(req));

        assertThat(requestSeenByChain.get()).isSameAs(request);
        assertThat(request.getContentType()).contains("multipart/form-data");
    }

    @Test
    void doFilter_shouldPassThroughUnchanged_forUserSignatureImageMultipartUpload() throws Exception {
        // Cung bay y het SimPki o tren: SharedCoreUsersController#uploadSignatureImage
        // (/api/v1/shared-core/users/{id}/signature-images) khong co "/files"/"/upload" trong path.
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST", "/api/v1/shared-core/users/11111111-1111-1111-1111-111111111111/signature-images");
        request.setContentType("multipart/form-data; boundary=abc");
        request.addPart(new MockPart("file", "chu-ky.png", "noi dung gia".getBytes(StandardCharsets.UTF_8)));
        request.addPart(new MockPart("type", "OFFICIAL".getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> requestSeenByChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> requestSeenByChain.set(req));

        assertThat(requestSeenByChain.get()).isSameAs(request);
        assertThat(request.getContentType()).contains("multipart/form-data");
    }

    @Test
    void doFilter_shouldStillConvertToJson_forMultipartTextOnlyEndpointNotInExemptList() throws Exception {
        // Bao ve hanh vi goc cua filter (khong bi vo tinh vo hieu hoa het khi sua doi tren)
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/some-other-endpoint");
        request.setContentType("multipart/form-data; boundary=abc");
        request.addPart(new MockPart("name", "abc".getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> requestSeenByChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> requestSeenByChain.set(req));

        assertThat(((jakarta.servlet.http.HttpServletRequest) requestSeenByChain.get()).getContentType())
            .startsWith("application/json");
    }
}
