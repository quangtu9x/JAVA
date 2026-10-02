package com.td.application.hosocv;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 02/10/2026 (cutover tab "VĂN BẢN LIÊN QUAN" → "Văn bản, tài liệu liên quan khác" của Hồ sơ công
 * việc — thay {@code qlvb/add?databasetype=hosocv} form {@code other_related_document}): {@code id}
 * rỗng = thêm mới vào hồ sơ {@code hosocvId}; có {@code id} = sửa văn bản khác đó. {@code fields}
 * chỉ nhận các ô của form {@code VBKhacModal.jsx} — xem {@link LuuVanBanKhacUseCase#EDITABLE_FIELDS}.
 */
@Data
public class LuuVanBanKhacRequest {

    private UUID id;

    private UUID hosocvId;

    private Map<String, String> fields = new LinkedHashMap<>();
}
