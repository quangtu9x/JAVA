package com.td.application.hosocv;

import com.td.application.common.models.Result;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G8: port (interface) cho việc gọi lấy token LGSP — implementation
 * thật (HTTP client) nằm ở {@code td-infrastructure} (Dependency Inversion — use case
 * {@link GetTokenLgspUseCase} chỉ phụ thuộc interface này, không phụ thuộc trực tiếp
 * {@code RestTemplate}/thư viện HTTP cụ thể, đúng nguyên tắc kiến trúc
 * {@code docs/architecture/01-...md} mục 8.1).
 */
public interface LgspTokenClient {

    Result<String> fetchToken();
}
