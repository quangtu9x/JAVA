package com.td.domain.dbserver;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// BL-117 (bổ sung 19/08/2026): registry "Máy chủ CSDL" (host+port Postgres) — CHỈ để tiện chọn nhanh
// lúc khởi tạo đơn vị (Tenant) thay vì phải nhớ/gõ tay host+port mỗi lần, KHÔNG có quan hệ JPA/FK
// nào với Tenant (Tenant vẫn tự lưu host/port của riêng nó tại thời điểm tạo — chọn từ danh sách này
// chỉ là autofill 1 lần ở FE, đổi/xoá 1 DbServer sau đó KHÔNG ảnh hưởng tenant đã tạo trước đó).
//
// LƯU Ý: KHÔNG được nhầm với "Máy chủ" (menu Hệ thống > Máy chủ, form Domino "server" cũ) — đó là
// registry mail-in/file/search/cache/log server của chính hệ thống Domino QLVB (đã khảo sát: ~20
// field, không có khái niệm port/database quan hệ, đang được 7 màn hình khác dùng làm lookup: Cơ
// quan, Cấp cơ quan, Bản triển khai...). Hai khái niệm hoàn toàn khác nhau, cố tình KHÔNG gộp/tái sử
// dụng để tránh đụng vào 7 nơi không liên quan (xem docs/architecture/06-database-per-tenant-multi-tenant.md).
//
// Sống ở database "control-plane" (giống Category/Tenant, EntityManagerFactory riêng — xem
// MultiPersistenceUnitJpaConfig) vì đây là dữ liệu nền tảng dùng chung cho MỌI tenant, không thuộc
// riêng tenant nào.
//
// Bổ sung 19/08/2026: `username`/`encryptedPassword` — TUỲ CHỌN (nullable, khác Tenant BẮT BUỘC phải
// có), lưu credential ĐĂNG NHẬP chung (thường là tài khoản superuser dùng để CREATE DATABASE) của
// server này để tiện autofill lúc khởi tạo đơn vị (chọn 1 DbServer → điền luôn host+port+username+
// password vào form tạo Tenant thay vì gõ tay). Mã hoá 2 CHIỀU bằng CredentialCipher (implement bởi
// TenantCredentialCipher, DÙNG CHUNG key/salt với Tenant — xem docs/architecture/06-database-per-tenant-multi-tenant.md
// mục 7) — CHỦ Ý reverse nguyên tắc "API không bao giờ trả mật khẩu" áp dụng cho Tenant: đây là
// credential QUẢN TRỊ dùng LẶP LẠI cho nhiều tenant trên cùng server (không phải mật khẩu tenant cuối
// cùng đã lưu), nên API `GET /{id}/credential` (PLATFORM_ADMIN-only) CÓ trả plaintext (giải mã) khi
// admin chủ động chọn để autofill — xem GetDbServerCredentialUseCase.
@Entity
@Table(name = "db_servers")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DbServer extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(length = 1000)
    private String description;

    @Column(length = 100)
    private String username;

    @Column(name = "encrypted_password", columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(nullable = false)
    private boolean active = true;

    public DbServer(String name, String host, int port, String description, String username, String encryptedPassword) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.host = host;
        this.port = port;
        this.description = description;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.active = true;
    }

    public DbServer update(String name, String host, Integer port, String description) {
        if (name != null)        this.name = name;
        if (host != null)        this.host = host;
        if (port != null)        this.port = port;
        this.description = description;
        return this;
    }

    // KHÔNG gộp vào update(...) ở trên — cùng lý do Tenant.update() tách riêng credential: chỉ đổi
    // khi admin CHỦ Ý gửi cả username+password cùng lúc (xem UpdateDbServerRequest), tránh vô tình
    // xoá/ghi đè credential đã lưu khi chỉ sửa tên/host/port.
    public void updateCredential(String username, String encryptedPassword) {
        this.username = username;
        this.encryptedPassword = encryptedPassword;
    }

    public boolean hasCredential() {
        return username != null && !username.isBlank() && encryptedPassword != null;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
