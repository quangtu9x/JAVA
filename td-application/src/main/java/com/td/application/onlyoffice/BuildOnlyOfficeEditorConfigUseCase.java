package com.td.application.onlyoffice;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;

import java.util.Map;

/**
 * Build payload {@code OnlyOfficeConfig} (xem {@code api.onlyoffice.com/editors/config}) cho tệp
 * {@code documentId}/{@code fileId} — dùng nguyên trạng làm prop {@code config} của component FE
 * {@code OnlyOfficeEditor} (xem {@code ThanhHoaDEV/src/app/components/OnlyOfficeEditor}).
 *
 * <p>Trả về {@link Map} (thay vì DTO cứng) vì hình dạng JSON config OnlyOffice có nhiều field lồng
 * tuỳ chọn, đúng cách FE component đã khai báo kiểu ({@code [key: string]: unknown} ở mọi cấp) —
 * tránh phải đồng bộ 2 phía mỗi khi thêm 1 field tuỳ chỉnh mới (vd customization).</p>
 */
public interface BuildOnlyOfficeEditorConfigUseCase extends UseCase<OnlyOfficeEditorConfigRequest, Result<Map<String, Object>>> {
}
