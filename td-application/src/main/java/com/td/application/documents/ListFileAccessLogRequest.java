package com.td.application.documents;

import lombok.Data;

import java.util.UUID;

/**
 * 09/09/2026 — request nội bộ cho {@link ListFileAccessLogUseCase}: đọc lại "Lịch sử xem/tải tệp
 * đính kèm" theo văn bản, lọc CẢ {@code module} lẫn {@code documentId} (xem javadoc {@link
 * com.td.domain.documents.FileAccessLog} về lý do cần {@code module}).
 */
@Data
public class ListFileAccessLogRequest {

    private String module;

    private UUID documentId;

    private Integer pageNumber = 1;

    private Integer pageSize = 200;
}
