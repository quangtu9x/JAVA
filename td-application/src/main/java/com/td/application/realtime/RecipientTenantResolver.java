package com.td.application.realtime;

import com.td.application.sharedcore.OrganizationRepository;
import com.td.application.sharedcore.OrganizationTenantLookup;
import com.td.application.tenant.ResolveUserTenantUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant (DB đơn vị) chứa hộp thư của 1 người nhận: mã đơn vị -> {@link OrganizationTenantLookup} (tổ tiên có
 * {@code linked_tenant_id}); username -> {@link ResolveUserTenantUseCase} (cùng cách {@code TenantResolver} chọn DB
 * lúc người đó đăng nhập). Rỗng = không xác định -> ghi ở tenant người gửi như cũ.
 *
 * <p>Cache trong bộ nhớ 10 phút: 1 lần phát hành có thể tới hàng trăm đơn vị, mỗi lần tra là vài truy vấn
 * identity (người dùng/tổ chức/tenant) - đơn vị/người dùng hiếm khi đổi tenant.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipientTenantResolver {

    private static final long TTL_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_ENTRIES = 20_000;

    private record Entry(Optional<String> tenantId, long expiresAt) {
    }

    private final OrganizationRepository organizationRepository;
    private final ResolveUserTenantUseCase resolveUserTenantUseCase;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public Optional<String> resolve(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return Optional.empty();
        }
        String key = recipient.trim().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return cached.tenantId();
        }
        Optional<String> tenantId = lookup(recipient.trim());
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new Entry(tenantId, now + TTL_MILLIS));
        return tenantId;
    }

    private Optional<String> lookup(String recipient) {
        try {
            return RealtimeNotification.isUnitIdentifier(recipient)
                ? OrganizationTenantLookup.resolveLinkedTenantId(organizationRepository, recipient)
                : resolveUserTenantUseCase.execute(recipient);
        } catch (RuntimeException ex) {
            log.debug("Không xác định được tenant của người nhận {}: {}", recipient, ex.getMessage());
            return Optional.empty();
        }
    }
}
