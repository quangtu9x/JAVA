package com.td.application.hosocv;

import lombok.Data;

import java.util.UUID;

/**
 * BL-240 (HoSoCV Core) — DoD (4): 1 tham chiếu văn bản liên quan trong hồ sơ (đúng phần tử
 * {@code formDataVBLQ} thật — {@code {databasetype, id}}) cần lấy danh sách file đính kèm. FE tự
 * tổng hợp danh sách này từ các field liên kết của hồ sơ ({@code duthao_attached_id},
 * {@code document_out_attached_id}, {@code document_in_attached_id},
 * {@code submission_dossier_attached_id} — ghi bởi {@link GanKemVanBanUseCase}) rồi gửi kèm chính
 * hồ sơ, giống hệt cơ chế thật.
 */
@Data
public class HoSoFileRefDto {

    private String documentType;

    private UUID documentId;
}
