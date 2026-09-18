package com.td.application.documents;

import java.util.List;
import java.util.UUID;

/**
 * Bổ sung 08/09/2026 (yêu cầu người dùng, module {@code vbden.forward_unit} — "chuyển xử lý cho đơn
 * vị khác tenant" — xem {@code ChuyenXuLyVbdenUseCase#publishForwardUnitIfCrossTenant}): đọc lại tệp
 * đính kèm HIỆN CÓ của 1 document ({@code objectKey}/{@code bucketName} — KHÔNG copy MinIO gì cả, chỉ
 * đọc metadata) để forward NGUYÊN VẸN sang tenant đích, tenant đích tự tạo 1 dòng
 * {@code file_metadata} RIÊNG trỏ THẲNG về CÙNG object — "đọc chung" (không nhân bản vật lý), đúng
 * yêu cầu người dùng "không phải nhân ra mà đọc chung với đơn vị chuyển xử lý luôn" (KHÁC quyết định
 * {@link PrepareVbdiDeliveryFilesUseCase} cho vbdi — module đó CÓ copy server-side sang namespace
 * riêng để tách vòng đời, module này thì KHÔNG — 2 quyết định riêng biệt cho 2 nghiệp vụ khác nhau).
 *
 * <p>Tái dùng {@link VbdiDeliveryFileRef} (hình dạng {@code objectKey/bucketName/originalFilename/
 * size/contentType/fileExtension} — generic, không riêng gì vbdi) thay vì tạo DTO trùng lặp.</p>
 */
public interface GetDocumentFileRefsUseCase {
    List<VbdiDeliveryFileRef> execute(UUID documentId);
}
