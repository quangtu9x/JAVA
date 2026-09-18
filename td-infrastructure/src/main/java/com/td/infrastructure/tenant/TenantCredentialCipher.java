package com.td.infrastructure.tenant;

import com.td.application.common.crypto.CredentialCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Mã hoá/giải mã 2 CHIỀU mật khẩu Postgres của từng tenant (BL-117) — KHÁC hẳn hash 1 chiều
 * (BCrypt) dùng cho mật khẩu người dùng cuối, vì app bắt buộc phải giải mã lại để tự mở JDBC
 * connection tới database riêng của tenant đó (xem {@code PostgresTenantProvisioningService},
 * {@code TenantDataSourceConfig}).
 *
 * <p>Dùng {@code Encryptors.text(key, salt)} của {@code spring-security-crypto} (đã có sẵn
 * transitively qua {@code spring-boot-starter-security}, không cần thêm dependency) — AES/CBC với
 * key sinh từ mật khẩu + salt.
 *
 * <p><b>CẢNH BÁO VẬN HÀNH QUAN TRỌNG</b>: {@code key}/{@code salt} (env
 * {@code TENANT_CREDENTIAL_ENCRYPTION_KEY}/{@code _SALT}) phải được backup như 1 secret THẬT (giống
 * {@code JWT_SECRET}). MẤT key này = KHÔNG THỂ giải mã lại bất kỳ mật khẩu tenant nào đã lưu trong
 * bảng {@code tenants} — toàn bộ tenant hiện có sẽ không route được nữa cho tới khi nhập lại đúng
 * credential (không có cách khôi phục nào khác ngoài đăng ký lại từng tenant qua API). {@code salt}
 * phải là chuỗi HEX hợp lệ (yêu cầu của {@code Encryptors.text}, vd sinh bằng
 * {@code KeyGenerators.string()}), không phải chuỗi tuỳ ý.
 *
 * <p>Bổ sung 19/08/2026: implement thêm port {@link CredentialCipher} (định nghĩa ở
 * {@code td-application}) để dùng LẠI đúng bean này cho use case của {@code DbServer} (registry "Máy
 * chủ CSDL") — tránh phải viết 1 cipher/key/salt riêng cho từng tính năng.
 */
@Component
public class TenantCredentialCipher implements CredentialCipher {

    private final TextEncryptor encryptor;

    public TenantCredentialCipher(
            @Value("${app.multitenancy.credential-encryption-key:}") String key,
            @Value("${app.multitenancy.credential-encryption-salt:}") String salt) {
        if (key == null || key.isBlank() || salt == null || salt.isBlank()) {
            throw new IllegalStateException(
                "app.multitenancy.credential-encryption-key/-salt (env TENANT_CREDENTIAL_ENCRYPTION_KEY/_SALT) " +
                "chua duoc cau hinh - bat buoc de ma hoa mat khau Postgres cua tung tenant (BL-117)");
        }
        if (!salt.matches("^[0-9a-fA-F]+$")) {
            throw new IllegalStateException(
                "app.multitenancy.credential-encryption-salt phai la chuoi HEX hop le (yeu cau cua " +
                "Encryptors.text) - vd sinh bang org.springframework.security.crypto.keygen.KeyGenerators.string()");
        }
        this.encryptor = Encryptors.text(key, salt);
    }

    @Override
    public String encrypt(String plainPassword) {
        return encryptor.encrypt(plainPassword);
    }

    @Override
    public String decrypt(String encryptedPassword) {
        return encryptor.decrypt(encryptedPassword);
    }
}
