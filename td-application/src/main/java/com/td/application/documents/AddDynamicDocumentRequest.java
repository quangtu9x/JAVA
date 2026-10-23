package com.td.application.documents;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BL-101: request cho {@code POST /api/v1/documents/add} — tương thích API legacy {@code qlvb/add}
 * (multipart/form-data với field động + {@code date_fields}/{@code number_fields}/{@code richtext_fields}).
 */
@Data
public class AddDynamicDocumentRequest {

    /** Độ dài cột tĩnh title (VARCHAR(300)) ở mọi bảng văn bản. */
    public static final int TITLE_MAX_LENGTH = 300;

    /**
     * 19/10/2026: CHỈ dùng cho bản ghi con/vết mà title là NHÃN lấy từ nội dung tự do (ý kiến, tên đơn vị…) —
     * nội dung đầy đủ đã lưu ở field riêng; cắt để không vượt cột (trước đây ý kiến dài làm lỗi cả thao tác).
     * KHÔNG dùng cho Trích yếu của văn bản chính (cắt = mất dữ liệu — FE/BE phải báo người dùng rút gọn).
     */
    public static String labelTitle(String value) {
        if (value == null || value.length() <= TITLE_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, TITLE_MAX_LENGTH - 1) + "…";
    }

    private String title;

    private String documentType;

    private String status;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
