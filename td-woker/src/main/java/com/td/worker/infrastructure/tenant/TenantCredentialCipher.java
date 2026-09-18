package com.td.worker.infrastructure.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Giải mã mật khẩu Postgres của từng tenant (mã hoá 2 chiều, KHÔNG phải hash) — CÙNG
 * thuật toán/key/salt với td-web (docs/architecture/11 §13.3): bắt buộc dùng CÙNG
 * {@code TENANT_CREDENTIAL_ENCRYPTION_KEY}/{@code _SALT}, khác giá trị = không giải mã được.
 */
@Slf4j
public class TenantCredentialCipher {

    private final TextEncryptor encryptor;

    public TenantCredentialCipher(String key, String salt) {
        if (key == null || key.isBlank() || salt == null || salt.isBlank()) {
            throw new IllegalStateException(
                "worker.control-plane.credential-encryption-key/-salt (env TENANT_CREDENTIAL_ENCRYPTION_KEY/_SALT) "
                    + "chưa được cấu hình - bắt buộc để giải mã mật khẩu Postgres từng tenant "
                    + "(PHẢI CÙNG GIÁ TRỊ với td-web)");
        }
        if (!salt.matches("^[0-9a-fA-F]+$")) {
            throw new IllegalStateException(
                "worker.control-plane.credential-encryption-salt phải là chuỗi HEX hợp lệ (yêu cầu của Encryptors.text)");
        }
        this.encryptor = Encryptors.text(key, salt);
    }

    public String decrypt(String encryptedPassword) {
        try {
            return encryptor.decrypt(encryptedPassword);
        } catch (RuntimeException e) {
            log.error("Không giải mã được mật khẩu tenant - key/salt có thể KHÁC td-web (docs/architecture/11 §13.3)");
            throw e;
        }
    }
}