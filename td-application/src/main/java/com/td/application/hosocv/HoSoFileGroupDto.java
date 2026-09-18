package com.td.application.hosocv;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.td.application.documents.FileDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BL-240 (HoSoCV Core) — DoD (4): danh sách file đính kèm của 1 văn bản liên quan trong hồ sơ.
 * Tương ứng {@code obj_parent} thật trong {@code TDAttachment.GetAttachmentByHSCV()}
 * ({@code {databasetype, id, files}}) — khác biệt: {@code files} ở đây là metadata
 * ({@link FileDto}, có {@code fileId} để tải qua endpoint chung
 * {@code GET /api/v1/documents/{documentId}/files/{fileId}/download}) thay vì nội dung base64
 * inline như Domino — do kiến trúc lưu trữ file khác (MinIO object storage, không phải NSF
 * attachment nhúng trực tiếp trong document).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HoSoFileGroupDto {

    // Bugfix 24/08/2026: doi JSON key sang "databasetype" (khop dung thuat ngu Domino - xem javadoc
    // lop), tranh dung ten voi field nghiep vu "document_type" ("Loai van ban") o cac module khac.
    @JsonProperty("databasetype")
    private String documentType;

    @JsonProperty("document_id")
    private UUID documentId;

    private List<FileDto> files = new ArrayList<>();
}
