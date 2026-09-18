package com.td.application.categories;

import lombok.Data;

import java.util.UUID;

/**
 * BL-105: tương thích {@code qlvb/danhmuc/CapNhatCache} — phiên bản Java chỉ hỗ trợ làm mới
 * (evict) cache danh mục theo {@code id} (tuỳ chọn) và toàn bộ danh sách, KHÔNG hỗ trợ ghi tuỳ ý
 * 1 key-value Redis như bản gốc ({@code key_cache}/{@code value}/{@code time_cache}) — rủi ro bảo
 * mật (cho phép client ghi đè cache tuỳ ý), xem {@code LegacyDanhMucController}.
 */
@Data
public class CapNhatCacheRequest {

    private UUID id;
}
