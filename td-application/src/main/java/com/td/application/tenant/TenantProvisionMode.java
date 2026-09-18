package com.td.application.tenant;

// BL-117.
public enum TenantProvisionMode {

    /** Tự động CREATE DATABASE + chạy Flyway đầy đủ vào database mới hoàn toàn trống. */
    CREATE_NEW,

    /**
     * Đăng ký 1 database ĐÃ TỒN TẠI/ĐÃ CÓ DỮ LIỆU (không CREATE DATABASE) — chỉ chạy Flyway
     * (idempotent, bắt kịp migration mới nếu có) rồi hot-add. Dùng để đưa tenant hiện tại (H05,
     * đang chạy trên database chính) vào registry mới mà KHÔNG di chuyển dữ liệu.
     */
    REGISTER_EXISTING,

    /**
     * Chỉ xoay vòng credential (host/port/username/password) của 1 tenant ĐÃ CÓ SẴN — không
     * CREATE DATABASE, không chạy Flyway, chỉ test connection rồi hot-add lại pool.
     */
    ROTATE_CREDENTIAL_ONLY
}
