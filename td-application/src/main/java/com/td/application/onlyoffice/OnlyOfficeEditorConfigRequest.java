package com.td.application.onlyoffice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Yêu cầu build cấu hình OnlyOffice DocsAPI cho 1 tệp cụ thể (xem
 * {@link BuildOnlyOfficeEditorConfigUseCase}) — dùng khi FE mở icon "Sửa trực tuyến (OnlyOffice)".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeEditorConfigRequest {

    private UUID documentId;
    private UUID fileId;

    /**
     * Base URL Document Server dùng để gọi ngược Backend (vd {@code http://10.10.10.x:8111}) — nếu
     * {@code app.onlyoffice.callback-base-url} chưa cấu hình, Controller tự suy ra từ request hiện
     * tại (best-effort) rồi truyền xuống đây; use case KHÔNG tự đọc {@code HttpServletRequest} (thuộc
     * tầng web) để giữ đúng ranh giới kiến trúc hiện có.
     */
    private String fallbackCallbackBaseUrl;
}
