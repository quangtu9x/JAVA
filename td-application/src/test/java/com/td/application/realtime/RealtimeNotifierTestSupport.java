package com.td.application.realtime;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.tonghop.TenantScopedExecutor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link RealtimeNotifier} cho test dựng use case bằng constructor — không gọi mạng, hộp thư trong bộ nhớ, chạy
 * ĐỒNG BỘ (dispatcher = chạy ngay), 1 tenant duy nhất.
 */
public final class RealtimeNotifierTestSupport {

    private RealtimeNotifierTestSupport() {
    }

    public static RealtimeNotifier noop() {
        return recording(new java.util.ArrayList<>());
    }

    /** Ghi lại mọi thông báo đã đẩy vào {@code sink} (ngoài transaction nên gửi ngay). */
    public static RealtimeNotifier recording(List<RealtimeNotification> sink) {
        // Lớp con thay vì mock: stub Mockito trong helper bị coi là "thừa" ở test strict không phát sinh thông báo.
        RecipientTenantResolver noTenant = new RecipientTenantResolver(null, null) {
            @Override
            public Optional<String> resolve(String recipient) {
                return Optional.empty();
            }
        };
        CurrentTenantProvider tenant = Optional::empty;
        return new RealtimeNotifier(sink::add, Mockito.mock(CurrentUserProvider.class),
            Mockito.mock(AppUserRepository.class), new InMemoryUserNotificationRepository(),
            new DynamicDocumentFieldsService(), tenant, inlineTenantExecutor(), noTenant, Runnable::run);
    }

    /** Tenant executor giả: mọi tenant đều "biết", chạy tại chỗ. */
    public static TenantScopedExecutor inlineTenantExecutor() {
        return new TenantScopedExecutor() {
            @Override
            public boolean isKnownTenant(String tenantId) {
                return true;
            }

            @Override
            public <T> T runInTenant(String tenantId, Supplier<T> action) {
                return action.get();
            }
        };
    }
}
