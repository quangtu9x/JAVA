package com.td.application.vbcddh;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 30/10/2026 (cutover FE vbcddh): request {@code POST /api/v1/vbcddh/Luu} — thay
 * {@code qlvb/add?databasetype=vbcddh|vbcddh_res[&id=][&parentid=]}. {@code id} có = hiệu chỉnh (merge
 * field), không có = tạo mới ({@code parentId} = bản ghi cha của tiến độ/kết quả, lưu field
 * {@code parentid}).
 */
@Data
public class LuuVbcddhRequest {

    private String id;

    private String parentId;

    /** vbcddh (văn bản/nhiệm vụ) hoặc vbcddh_res (tiến độ/kết quả xử lý). */
    private String documentType;

    private String title;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
