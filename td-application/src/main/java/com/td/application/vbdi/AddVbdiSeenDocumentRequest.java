package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 09/09/2026 (yêu cầu người dùng "port qlvb/add?actionname=addlogseendocument...databasetype=vbdi
 * khi xem chi tiết văn bản đi"): request cho {@code POST /api/v1/vbdi/SeenDocument} — đánh dấu
 * CHÍNH người gọi đã xem văn bản đi {@code documentId}. Port hợp nhất 2 đoạn Domino cùng phục vụ 1
 * sự kiện "mở xem chi tiết" (xem javadoc {@link AddVbdiSeenDocumentUseCase}): {@code TDDocument.java}
 * (qua {@code qlvb/add&actionname=addlogseendocument}) và nhánh {@code key==""} của
 * {@code DocumentOutProcesses.java#Seen_document()} (qua {@code qlvb/vbdi/seendocumentout}).
 *
 * <p>KHÔNG nhận username/fullname/unit từ client (khác {@link AddVbdiUserActionLogRequest}, vốn
 * chấp nhận field trình bày từ client) — username ghi vào {@code log_seen_document}/
 * {@code coordinator_hide_seen_rtext} PHẢI server-authoritative qua
 * {@link com.td.application.common.security.CurrentUserProvider}, tránh giả mạo "ai đã xem".</p>
 */
@Data
public class AddVbdiSeenDocumentRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;
}
