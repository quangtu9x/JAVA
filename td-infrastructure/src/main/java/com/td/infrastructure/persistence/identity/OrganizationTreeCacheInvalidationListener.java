package com.td.infrastructure.persistence.identity;

import com.td.application.sharedcore.OrganizationTreeCache;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 15/10/2026: vô hiệu cache cây cơ cấu tổ chức ({@link OrganizationTreeCache}) khi {@link Organization}
 * hoặc {@link AppUser} thay đổi — gắn thẳng vào Hibernate (persistence unit "identity") thay vì gọi tay
 * ở từng use case (Create/Update/Delete Organization, Create/Update/Delete/Lock/Unlock/Transfer AppUser...)
 * để không sót đường ghi nào, kể cả code thêm về sau. Chỉ chạy SAU KHI commit thành công (post-commit),
 * nên lần dựng lại cây không đọc phải dữ liệu chưa commit. Xoá mềm/khoá đều là UPDATE nên cũng được bắt.
 *
 * <p>Lưu ý: KHÔNG bắt được câu lệnh JPQL/SQL cập nhật hàng loạt (bulk update) — hiện không có chỗ nào ghi
 * organizations/app_users kiểu đó; nếu thêm, phải gọi {@link OrganizationTreeCache#invalidate()} tay.</p>
 */
@Component
@Slf4j
public class OrganizationTreeCacheInvalidationListener
        implements PostCommitInsertEventListener, PostCommitUpdateEventListener, PostCommitDeleteEventListener {

    private final EntityManagerFactory identityEntityManagerFactory;
    private final OrganizationTreeCache organizationTreeCache;

    public OrganizationTreeCacheInvalidationListener(
            @Qualifier("identityEntityManagerFactory") EntityManagerFactory identityEntityManagerFactory,
            OrganizationTreeCache organizationTreeCache) {
        this.identityEntityManagerFactory = identityEntityManagerFactory;
        this.organizationTreeCache = organizationTreeCache;
    }

    @PostConstruct
    void register() {
        EventListenerRegistry registry = identityEntityManagerFactory
            .unwrap(SessionFactoryImplementor.class)
            .getServiceRegistry()
            .getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_COMMIT_INSERT, this);
        registry.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        registry.appendListeners(EventType.POST_COMMIT_DELETE, this);
    }

    // Dữ liệu tổ chức có thể đã bị đổi khi server tắt (Flyway migration, sửa tay trong DB) — xoá cache 1
    // lần lúc khởi động cho chắc.
    @EventListener(ApplicationReadyEvent.class)
    void invalidateOnStartup() {
        organizationTreeCache.invalidate();
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return isTreeEntity(persister.getMappedClass());
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        invalidateIfTreeEntity(event.getEntity());
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        invalidateIfTreeEntity(event.getEntity());
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        invalidateIfTreeEntity(event.getEntity());
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent event) {
        // rollback -> dữ liệu không đổi, giữ cache
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent event) {
        // rollback -> dữ liệu không đổi, giữ cache
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent event) {
        // rollback -> dữ liệu không đổi, giữ cache
    }

    private void invalidateIfTreeEntity(Object entity) {
        if (entity != null && isTreeEntity(entity.getClass())) {
            try {
                organizationTreeCache.invalidate();
            } catch (Exception ex) {
                log.warn("Organization tree cache invalidation failed: {}", ex.getMessage());
            }
        }
    }

    private static boolean isTreeEntity(Class<?> type) {
        return type != null && (Organization.class.isAssignableFrom(type) || AppUser.class.isAssignableFrom(type));
    }
}
