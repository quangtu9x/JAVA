package com.td.application.documents;

import lombok.Data;

import java.util.UUID;

/**
 * 09/09/2026 (yêu cầu người dùng "log xem tệp đính kèm cần thiết cho các module khác nữa"): request
 * nội bộ cho {@link RecordFileAccessUseCase} — {@code documentId}/{@code fileId} do CONTROLLER gán
 * từ path variable (KHÔNG validate {@code @Valid} ở tầng controller vì lúc bind JSON body
 * ({@code module}/{@code action}) 2 field này CHƯA được gán — validate thủ công trong use case, xem
 * javadoc đó).
 */
@Data
public class RecordFileAccessRequest {

    private UUID documentId;

    private UUID fileId;

    /** Module sở hữu {@code documentId} (vbdi/vbden/duthao/hosocv/...) — client-supplied, không
     * security-sensitive (chỉ để phân loại hiển thị, xem javadoc {@link
     * com.td.domain.documents.FileAccessLog}). */
    private String module;

    /** {@value RecordFileAccessUseCase#ACTION_VIEW} hoặc {@value RecordFileAccessUseCase#ACTION_DOWNLOAD}. */
    private String action;
}
