package com.td.application.vbdi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 21/09/2026 (yêu cầu người dùng "kiểm tra port qlvb/vbdi/addimportant?databasetype=vbdi"): request
 * cho {@code POST /api/v1/vbdi/AddImportant} — đánh dấu/bỏ đánh dấu văn bản đi {@code documentId} là
 * "quan trọng" RIÊNG cho người gọi (xem javadoc {@link AddVbdiImportantDocumentUseCase}).
 *
 * <p>{@code action}: {@code "add"} (đánh dấu) hoặc {@code "delete"} (bỏ đánh dấu) — giữ nguyên tên
 * tham số {@code type} của Domino gốc ({@code DocumentOutProcesses.java#HandleAddImportant}) nhưng
 * đổi tên field thành {@code action} cho khớp quy ước request DTO khác trong module này.</p>
 *
 * <p>KHÔNG nhận username từ client — server-authoritative qua
 * {@link com.td.application.common.security.CurrentUserProvider}, cùng nguyên tắc
 * {@link AddVbdiSeenDocumentRequest}.</p>
 *
 * <p>Chỉ nhận 1 {@code documentId}/lần gọi (khác Domino gốc nhận {@code id} dạng chuỗi nhiều UNID
 * phân tách dấu phẩy) — FE gọi bulk bằng N request song song (cùng cách
 * {@code VBDIButtonActionPage.jsx#handleDeleteImportant} hiện đã làm với endpoint Domino cũ), không
 * cần port đúng hình dạng "batch 1 request" của Domino.</p>
 */
@Data
public class AddVbdiImportantDocumentRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;

    @NotBlank(message = "action khong duoc de trong")
    private String action;
}
