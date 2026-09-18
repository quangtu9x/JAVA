package com.td.infrastructure.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantCredentialCipherTest {

    private static final String VALID_KEY = "bl-117-test-encryption-key-please-ignore";
    private static final String VALID_HEX_SALT = "deadbeef";

    @Test
    void encryptThenDecrypt_shouldReturnOriginalPlaintext() {
        TenantCredentialCipher cipher = new TenantCredentialCipher(VALID_KEY, VALID_HEX_SALT);

        String encrypted = cipher.encrypt("mat-khau-postgres-that-cua-tenant");

        assertThat(encrypted).isNotBlank().isNotEqualTo("mat-khau-postgres-that-cua-tenant");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("mat-khau-postgres-that-cua-tenant");
    }

    @Test
    void encrypt_shouldProduceDifferentCiphertext_forSamePlaintext_calledTwice() {
        // Encryptors.text dung random IV moi lan encrypt - xac nhan khong bi "deterministic" (an
        // toan hon truoc tan cong so sanh ciphertext giua cac tenant dung chung mat khau).
        TenantCredentialCipher cipher = new TenantCredentialCipher(VALID_KEY, VALID_HEX_SALT);

        String first = cipher.encrypt("cung-1-mat-khau");
        String second = cipher.encrypt("cung-1-mat-khau");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("cung-1-mat-khau");
        assertThat(cipher.decrypt(second)).isEqualTo("cung-1-mat-khau");
    }

    @Test
    void constructor_shouldThrow_whenKeyIsBlank() {
        assertThatThrownBy(() -> new TenantCredentialCipher("", VALID_HEX_SALT))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_shouldThrow_whenSaltIsBlank() {
        assertThatThrownBy(() -> new TenantCredentialCipher(VALID_KEY, ""))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_shouldThrow_whenSaltIsNotValidHex() {
        assertThatThrownBy(() -> new TenantCredentialCipher(VALID_KEY, "khong-phai-hex!"))
            .isInstanceOf(IllegalStateException.class);
    }
}
