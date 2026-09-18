package com.td.application.common.crypto;

/**
 * Cổng (port) mã hoá/giải mã 2 CHIỀU credential hạ tầng (mật khẩu database) — implement ở
 * {@code td-infrastructure} bằng {@code TenantCredentialCipher} (BL-117, dùng chung cho cả Tenant lẫn
 * DbServer — bổ sung 19/08/2026). KHÁC hash 1 chiều (BCrypt) dùng cho mật khẩu người dùng cuối, vì
 * app bắt buộc phải giải mã lại để tự mở JDBC connection.
 */
public interface CredentialCipher {

    String encrypt(String plainPassword);

    String decrypt(String encryptedPassword);
}
