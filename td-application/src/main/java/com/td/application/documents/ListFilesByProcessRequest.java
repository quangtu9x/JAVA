package com.td.application.documents;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * DT-007 (27/08/2026, "quản lý tệp đính kèm theo id cha, draft_process vẫn thể hiện đúng lần gắn
 * kèm"): lọc tệp đính kèm CHỈ thuộc 1 lần xử lý cụ thể ({@code processId}) trong CÙNG 1 tài liệu
 * cha ({@code documentId}) — xem javadoc {@link ListDocumentFilesByProcessUseCase}.
 */
@Data
@Builder
public class ListFilesByProcessRequest {

    private UUID documentId;

    private UUID processId;
}
