package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 27/08/2026 — request cho {@code POST /api/v1/duthao/LuuVetHieuChinh}: ghi 1
 * vết hiệu chỉnh của dự thảo, port phần side-effect "lưu vết" của
 * {@code LogVariable.getChangedFieldDetails} (trước đây tự POST
 * {@code qlvb/add[?databasetype=duthao]} phía Domino với {@code form=log},
 * {@code res=<id cha>}, {@code edit_obj}=JSON, {@code creator}).
 *
 * <p>Danh tính người hiệu chỉnh KHÔNG nhận từ client: server tự gán {@code user_creator} bằng
 * username đăng nhập (SERVER-AUTHORITATIVE, cùng nguyên tắc đã áp dụng cho
 * {@code TaoDuThaoUseCase#stampUserCreator}); {@code creatorName} chỉ là tên hiển thị tuỳ chọn
 * phục vụ render {@code VetHieuChinh.jsx} (record.creator || record.user_creator).</p>
 */
@Data
public class AddDuThaoEditHistoryRequest {

    /** Id dự thảo bị hiệu chỉnh — bắt buộc. */
    @NotNull(message = "parent_id không được để trống")
    private UUID parentId;

    /** Form của bản ghi gốc lúc ghi vết — bỏ trống mặc định "draft_document". */
    @Size(max = 100, message = "form_edit tối đa 100 ký tự")
    private String formEdit;

    /** Tên hiển thị người hiệu chỉnh (tuỳ chọn, chỉ để render — không phải danh tính). */
    @Size(max = 300, message = "creator_name tối đa 300 ký tự")
    private String creatorName;

    /**
     * Map thay đổi {@code {field:{oldvalue,newvalue}}} — bắt buộc khác rỗng (khớp Domino: chỉ
     * những lần Lưu có field đổi mới sinh vết); giá trị old/new có thể là chuỗi/số/boolean/
     * mảng/object tuỳ field.
     */
    private Map<String, Object> editObj = new LinkedHashMap<>();
}
