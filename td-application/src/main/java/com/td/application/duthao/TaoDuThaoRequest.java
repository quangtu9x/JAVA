package com.td.application.duthao;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BL-230 (DuThao Core) — DoD (1): request cho {@code POST /api/v1/duthao/TaoDuThao}, cùng khuôn
 * dạng {@code qlvb/add?databasetype=duthao} (form-data field động) như
 * {@code SoanThaoVbdiRequest} bên vbdi/{@code TiepNhanVbdenRequest} bên vbden.
 *
 * <p>Không có field "liên kết văn bản nguồn" nào được evidenced trong 5 file Domino đã đọc
 * ({@code InitDraft.java} chỉ là widget đếm view, không phải servlet tạo dự thảo; việc tạo dự thảo
 * thật đi qua cơ chế {@code CreateOrUpdateDocument()} tổng quát của base class {@code TDDocument},
 * ngoài phạm vi đã đọc) — nên KHÔNG suy diễn field liên kết văn bản đến/đi nguồn; nếu nghiệp vụ cần,
 * dùng field động tuỳ ý qua {@code fields} (vd. {@code source_document_id}) giống các field khác.
 */
@Data
public class TaoDuThaoRequest {

    private String subject;

    private String status;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
