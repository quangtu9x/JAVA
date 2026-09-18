package com.td.application.categories;

import lombok.Data;

import java.util.UUID;

/**
 * BL-105: tương thích {@code qlvb/danhmuc/XoaDanhMuc} — 2 hình thái: (1) {@code {id, deleteoption}}
 * xoá mềm 1 danh mục; (2) {@code {deletetype:"cache"}} làm mới cache danh mục (KHÔNG hỗ trợ xoá
 * trực tiếp 1 Redis key tuỳ ý theo {@code key_cache} như bản gốc — rủi ro bảo mật, xem
 * {@code LegacyDanhMucController}).
 */
@Data
public class XoaDanhMucRequest {

    private UUID id;

    private String deleteoption;

    private String deletetype;
}
