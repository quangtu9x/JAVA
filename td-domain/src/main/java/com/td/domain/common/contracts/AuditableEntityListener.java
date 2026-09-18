package com.td.domain.common.contracts;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Listener audit thuần JPA cho mọi entity kế thừa {@link AuditableEntity} — tự điền
 * {@code createdBy}/{@code lastModifiedBy} từ {@link AuditCurrentUserHolder}.
 *
 * <p><b>Vì sao KHÔNG dùng {@code AuditingEntityListener} của spring-data-jpa</b> (bugfix 26/08/2026
 * — người dùng báo cáo 2 cột này luôn NULL dù đã wire annotation chuẩn): listener đó cần được tiêm
 * {@code AuditingHandler} qua {@code SpringBeanContainer} của Hibernate; project dùng 3 persistence
 * unit dựng tay trong {@code MultiPersistenceUnitJpaConfig} nên Hibernate instantiate listener bằng
 * reflection thường → không tiêm gì cả → mọi callback thành no-op IM LẶNG. Listener này chỉ dùng
 * jakarta.persistence + JDK, không có điểm chết về DI, hoạt động giống hệt trên cả 3 persistence
 * unit. Cột thời gian {@code createdOn}/{@code lastModifiedOn} vẫn do {@code @PrePersist}/
 * {@code @PreUpdate} trên chính entity quản (đã đúng từ trước).</p>
 */
public class AuditableEntityListener {

    // INSERT: điền createdBy (chỉ khi đang trống — không đè giá trị code nghiệp vụ đã gán chủ đích,
    // vd UploadCategoryFileUseCaseImpl tự setCreatedBy trước save) + lastModifiedBy.
    @PrePersist
    public void touchForCreate(Object entity) {
        if (!(entity instanceof IAuditableEntity auditable)) {
            return;
        }
        AuditCurrentUserHolder.currentUserUuid().ifPresent(currentUser -> {
            if (auditable.getCreatedBy() == null) {
                auditable.setCreatedBy(currentUser);
            }
            auditable.setLastModifiedBy(currentUser);
        });
    }

    // UPDATE: chỉ refresh lastModifiedBy — createdBy giữ nguyên giá trị gốc (ai tạo thì là người đó).
    @PreUpdate
    public void touchForUpdate(Object entity) {
        if (!(entity instanceof IAuditableEntity auditable)) {
            return;
        }
        AuditCurrentUserHolder.currentUserUuid().ifPresent(auditable::setLastModifiedBy);
    }
}
