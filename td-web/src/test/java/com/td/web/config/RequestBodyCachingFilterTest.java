package com.td.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodyCachingFilterTest {

    private final RequestBodyCachingFilter filter = new RequestBodyCachingFilter();

    @Test
    void doFilter_shouldWrapWithContentCachingRequestWrapper_forJsonRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/categories");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"abc\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> requestSeenByChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> requestSeenByChain.set(req));

        assertThat(requestSeenByChain.get()).isInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    void doFilter_shouldNotWrap_forMultipartRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents/1/files");
        request.setContentType("multipart/form-data; boundary=abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> requestSeenByChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> requestSeenByChain.set(req));

        assertThat(requestSeenByChain.get())
            .as("Request multipart (file upload) khong duoc bi wrap - tranh anh huong MultipartFile binding")
            .isNotInstanceOf(ContentCachingRequestWrapper.class);
        assertThat(requestSeenByChain.get()).isSameAs(request);
    }

    @Test
    void doFilter_shouldNotWrap_whenNoContentType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/categories");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> requestSeenByChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> requestSeenByChain.set(req));

        assertThat(requestSeenByChain.get()).isSameAs(request);
    }
}
