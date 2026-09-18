package com.td.application.recordsstorage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.UUID;

@Data
public class SearchRecordsStorageCatalogsRequest {

    private String keyword;
    private String identifier;
    // ID của node cây (Phông/Đề mục lớn/Đề mục nhỏ) đang được chọn ở FE — khớp với BẤT KỲ cột nào
    // trong 3 cột phongId/firstTopicId/secondTopicId (đúng hành vi bản gốc: 1 view Domino duy nhất
    // `danhmuchoso_by_identifier_demuchoso_id` lọc theo 1 id bất kể id đó thuộc cấp nào).
    private UUID selectedNodeId;
    private Integer yearInception;

    @Min(value = 0, message = "Page number phải >= 0")
    private int pageNumber = 0;

    @Min(value = 1, message = "Page size phải >= 1")
    @Max(value = 200, message = "Page size tối đa 200")
    private int pageSize = 20;

    private String sortBy = "category";
    private String sortDirection = "asc";
}
