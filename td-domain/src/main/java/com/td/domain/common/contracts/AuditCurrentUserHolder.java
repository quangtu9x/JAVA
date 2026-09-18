package com.td.domain.common.contracts;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Cầu nối cho phép tầng domain lấy "người dùng hiện tại" khi ghi audit trail mà KHÔNG phụ thuộc
 * Spring Security/Spring Data (domain layer không được kéo dependency hạ tầng).
 *
 * <p><b>Bối cảnh bugfix 26/08/2026</b> (người dùng báo cáo "created_by/last_modified_by chưa thấy
 * lưu giá trị" sau khi đã thử wiring Spring Data JPA auditing): cơ chế {@code @EntityListeners(
 * AuditingEntityListener.class)} + {@code @CreatedBy}/{@code @LastModifiedBy} của spring-data-jpa
 * KHÔNG hoạt động trong project này vì (1) {@code AuditingEntityListener} bị weave AspectJ và cần
 * được tiêm {@code AuditingHandler} qua {@code SpringBeanContainer}, (2) cả 3 persistence unit đều
 * dựng tay trong {@code MultiPersistenceUnitJpaConfig} (không đi đường auto-configure của Boot nên
 * không có {@code hibernate.resource.beans.container}) → Hibernate new listener bằng reflection,
 * handler null → im lặng bỏ qua. Thay vào đó: tầng infrastructure gọi
 * {@link #register(Supplier)} một lần lúc khởi động (xem {@code AuditingConfig}), listener JPA
 * thuần {@link AuditableEntityListener} đọc giá trị qua {@link #currentUserUuid()}.</p>
 *
 * <p>Thread-safe (AtomicReference); mặc định trả rỗng nếu chưa đăng ký — hành vi an toàn khi chạy
 * ngoài request HTTP (background job/cron: audit fields bỏ trống, không suy diễn).</p>
 */
public final class AuditCurrentUserHolder {

    private static final AtomicReference<Supplier<Optional<UUID>>> SUPPLIER =
        new AtomicReference<>(Optional::empty);

    private AuditCurrentUserHolder() {
    }

    /**
     * Đăng ký cách resolve UUID người dùng hiện tại — gọi ĐÚNG MỘT lần lúc startup từ tầng
     * infrastructure ({@code AuditingConfig}). Gọi lại sẽ thay thế (lần đăng ký cuối thắng).
     *
     * @param supplier hàm lấy UUID người dùng hiện tại (trả rỗng nếu không xác thực)
     */
    public static void register(Supplier<Optional<UUID>> supplier) {
        SUPPLIER.set(supplier == null ? Optional::empty : supplier);
    }

    /**
     * @return UUID người dùng hiện tại, rỗng nếu chưa đăng ký supplier hoặc request không xác thực.
     */
    public static Optional<UUID> currentUserUuid() {
        return SUPPLIER.get().get();
    }
}
