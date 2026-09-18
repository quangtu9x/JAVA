package com.td.domain.common.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.td.domain.duthao.DuThaoDocument;

import jakarta.persistence.EntityListeners;

/**
 * Bugfix 26/08/2026 — lần 2 (người dùng build lại + test: created_by/last_modified_by vẫn NULL dù
 * đã wire Spring Data auditing): test HÀNH VI của listener thuần JPA
 * {@link AuditableEntityListener} — pure JVM (construct {@link DuThaoDocument} trực tiếp, gọi
 * callback tay), không cần DB/Testcontainers. Kèm 1 test cấu trúc khoá contract
 * {@code @EntityListeners(AuditableEntityListener.class)} trên {@link AuditableEntity}.
 *
 * <p>Bối cảnh kỹ thuật xem javadoc {@link AuditCurrentUserHolder}: cơ chế
 * {@code @CreatedBy}/{@code @LastModifiedBy} + {@code AuditingEntityListener} của spring-data-jpa
 * là no-op im lặng trong project này (3 persistence unit dựng tay, không có SpringBeanContainer nên
 * handler không bao giờ được tiêm).</p>
 */
class AuditableEntityListenerTest {

    private static final UUID CURRENT_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORIGINAL_CREATOR = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final AuditableEntityListener listener = new AuditableEntityListener();

    @AfterEach
    void resetHolder() {
        // tránh rò rỉ supplier giữa các test
        AuditCurrentUserHolder.register(Optional::empty);
    }

    private DuThaoDocument newDocument() {
        return new DuThaoDocument("Du thao test", null, null, null, null, null, null);
    }

    @Test
    void prePersist_shouldFillBothFields_whenUserPresent() {
        AuditCurrentUserHolder.register(() -> Optional.of(CURRENT_USER));
        var doc = newDocument();

        listener.touchForCreate(doc);

        assertThat(doc.getCreatedBy()).isEqualTo(CURRENT_USER);
        assertThat(doc.getLastModifiedBy()).isEqualTo(CURRENT_USER);
    }

    @Test
    void prePersist_shouldKeepExistingCreatedBy_whenBusinessCodeAlreadySetIt() {
        AuditCurrentUserHolder.register(() -> Optional.of(CURRENT_USER));
        var doc = newDocument();
        doc.setCreatedBy(ORIGINAL_CREATOR);

        listener.touchForCreate(doc);

        assertThat(doc.getCreatedBy()).isEqualTo(ORIGINAL_CREATOR);
        assertThat(doc.getLastModifiedBy()).isEqualTo(CURRENT_USER);
    }

    @Test
    void prePersist_shouldDoNothing_whenNoAuthenticatedUser() {
        // không đăng ký supplier gì — đúng kịch bản background job/cron: bỏ trống, không suy diễn
        var doc = newDocument();

        listener.touchForCreate(doc);

        assertThat(doc.getCreatedBy()).isNull();
        assertThat(doc.getLastModifiedBy()).isNull();
    }

    @Test
    void preUpdate_shouldRefreshOnlyLastModifiedBy_andKeepOriginalCreator() {
        AuditCurrentUserHolder.register(() -> Optional.of(CURRENT_USER));
        var doc = newDocument();
        doc.setCreatedBy(ORIGINAL_CREATOR);

        listener.touchForUpdate(doc);

        assertThat(doc.getCreatedBy()).isEqualTo(ORIGINAL_CREATOR);
        assertThat(doc.getLastModifiedBy()).isEqualTo(CURRENT_USER);
    }

    @Test
    void preUpdate_shouldDoNothing_whenNoAuthenticatedUser() {
        var doc = newDocument();

        listener.touchForUpdate(doc);

        assertThat(doc.getLastModifiedBy()).isNull();
    }

    @Test
    void callbacks_shouldIgnoreNonAuditableObjects_withoutThrowing() {
        AuditCurrentUserHolder.register(() -> Optional.of(CURRENT_USER));

        listener.touchForCreate(new Object());
        listener.touchForUpdate("khong-phai-entity");
    }

    @Test
    void auditableEntity_mustDeclarePlainJpaListener() {
        EntityListeners listeners = AuditableEntity.class.getAnnotation(EntityListeners.class);
        assertThat(listeners).as("AuditableEntity phải khai báo @EntityListeners").isNotNull();
        assertThat(listeners.value()).containsExactly(AuditableEntityListener.class);
    }
}
