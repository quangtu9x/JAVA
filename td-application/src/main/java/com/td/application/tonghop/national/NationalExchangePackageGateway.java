package com.td.application.tonghop.national;

import java.io.InputStream;
import java.util.Optional;

/**
 * Tải gói eDXML đã lưu trên máy TD Gateway qua endpoint nội bộ {@code GET /internal/national-exchange/package}
 * (bí mật chia sẻ, gateway ghi audit người tải) — td-web không đọc đĩa gateway, không thấy đường dẫn vật lý.
 */
public interface NationalExchangePackageGateway {

    record PackageStream(String fileName, String contentSha256, Long contentLength, InputStream content) { }

    /** Rỗng nếu gateway không có tệp cho gói này. */
    Optional<PackageStream> download(String exchangeId, String actorId, String actorName) throws Exception;
}
