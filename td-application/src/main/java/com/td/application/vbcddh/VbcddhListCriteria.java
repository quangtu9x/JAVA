package com.td.application.vbcddh;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 30/10/2026 (cutover FE vbcddh khỏi Domino qlvb/search|view/query): tiêu chí lọc chung cho
 * {@link VbcddhDocumentRepository#findByCriteria} — dùng bởi {@link VbcddhViewQuery} (các view mới)
 * và {@link TimKiemVbcddhUseCase} (tìm kiếm nâng cao). Mọi giá trị/tên field client gửi đều đi qua
 * tham số SQL; {@code extraPredicateSql} chỉ là hằng số nội bộ.
 */
@Getter
@Builder
public class VbcddhListCriteria {

    /** document_type (vbcddh / vbcddh_res); null = không lọc. */
    private final String documentType;

    /** Field {@code form} thuộc 1 trong các giá trị (so khớp chính xác); rỗng = không lọc. */
    private final List<String> forms;

    /** OR-containment (không phân biệt hoa/thường, tách ";") của {@code member} trên các field này. */
    private final List<String> memberFields;
    private final String member;

    /** field = giá trị (không phân biệt hoa/thường) — vd idnhiemvu. */
    private final Map<String, String> equalsIgnoreCase;

    /** field chứa giá trị (không dấu, không phân biệt hoa/thường). */
    private final Map<String, String> contains;

    /** field ngày "dd/MM/yyyy[ ...]" nằm trong [từ, đến] (mỗi phần tử: mảng 2 chuỗi dd/MM/yyyy). */
    private final Map<String, String[]> dateRanges;

    /** Từ khoá tìm trên các cột {@code keywordFields} (field động) + cột title. */
    private final String keyword;
    private final List<String> keywordFields;

    /** Hằng số SQL nội bộ (KHÔNG từ client). */
    private final String extraPredicateSql;
}
