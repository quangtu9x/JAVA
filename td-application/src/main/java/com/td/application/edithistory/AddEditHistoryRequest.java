package com.td.application.edithistory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 30/10/2026 — request cho {@code POST /api/v1/vethieuchinh/Luu} (thay FE tự POST {@code form="log"}
 * lên Domino {@code qlvb/add}). Người hiệu chỉnh do server gán, {@code creatorName} chỉ để hiển thị.
 */
@Data
public class AddEditHistoryRequest {

    @NotBlank(message = "parent_id không được để trống")
    @Size(max = 100, message = "parent_id tối đa 100 ký tự")
    private String parentId;

    @Size(max = 50, message = "database_type tối đa 50 ký tự")
    private String databaseType;

    @Size(max = 100, message = "form_edit tối đa 100 ký tự")
    private String formEdit;

    @Size(max = 300, message = "creator_name tối đa 300 ký tự")
    private String creatorName;

    /** Map thay đổi {@code {field:{oldvalue,newvalue}}} — bắt buộc khác rỗng. */
    private Map<String, Object> editObj = new LinkedHashMap<>();
}
