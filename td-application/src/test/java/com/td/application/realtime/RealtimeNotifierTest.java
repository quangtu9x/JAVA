package com.td.application.realtime;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.tonghop.TenantScopedExecutor;
import com.td.domain.notifications.UserNotification;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RealtimeNotifierTest {

    private final List<RealtimeNotification> sent = new ArrayList<>();
    private final CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
    private final AppUserRepository appUserRepository = Mockito.mock(AppUserRepository.class);
    private final DynamicDocumentFieldsService fieldsService = new DynamicDocumentFieldsService();
    private final RecipientTenantResolver tenantResolver = Mockito.mock(RecipientTenantResolver.class);
    private final CurrentTenantProvider originTenant = () -> Optional.of("H05");

    /** Hộp thư giả theo tenant: ghi vào tenant đang "chạy" (do RecordingTenantExecutor đặt). */
    private final Map<String, InMemoryUserNotificationRepository> inboxByTenant = new HashMap<>();
    private final ThreadLocal<String> runningTenant = new ThreadLocal<>();
    private final UserNotificationRepository inbox = new InMemoryUserNotificationRepository() {
        @Override
        public List<UserNotification> saveAllInNewTransaction(List<UserNotification> notifications) {
            String tenant = runningTenant.get() == null ? "(none)" : runningTenant.get();
            return inboxByTenant.computeIfAbsent(tenant, k -> new InMemoryUserNotificationRepository())
                .saveAllInNewTransaction(notifications);
        }
    };
    private final Set<String> knownTenants = new java.util.HashSet<>(Set.of("H05", "H05_01"));
    private final TenantScopedExecutor tenantExecutor = new TenantScopedExecutor() {
        @Override
        public boolean isKnownTenant(String tenantId) {
            return knownTenants.contains(tenantId);
        }

        @Override
        public <T> T runInTenant(String tenantId, Supplier<T> action) {
            runningTenant.set(tenantId);
            try {
                return action.get();
            } finally {
                runningTenant.remove();
            }
        }
    };
    private final List<Runnable> queued = new ArrayList<>();

    private RealtimeNotifier notifier(RealtimeNotificationGateway gateway, UserNotificationRepository repository,
                                      RealtimeTaskDispatcher dispatcher) {
        return new RealtimeNotifier(gateway, currentUserProvider, appUserRepository, repository, fieldsService,
            originTenant, tenantExecutor, tenantResolver, dispatcher);
    }

    private final RealtimeNotifier notifier = notifier(sent::add, inbox, Runnable::run);

    private List<UserNotification> rows(String tenant) {
        InMemoryUserNotificationRepository repository = inboxByTenant.get(tenant);
        return repository == null ? List.of() : repository.rows;
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void recipients_areSplitTrimmedDeduplicatedAndExcludeActor() {
        RealtimeNotification n = RealtimeNotification.of("vbden", "chuyenxuly", UUID.randomUUID(), "12/UBND")
            .to(";a; b ;;", "a,c")
            .toValues(List.of("d", "b"), null)
            .exclude("C");

        assertThat(n.getRecipients()).containsExactly("a", "b", "d");
    }

    @Test
    void unitIdentifier_matchesOrgCodesOnly() {
        assertThat(RealtimeNotification.isUnitIdentifier("H05")).isTrue();
        assertThat(RealtimeNotification.isUnitIdentifier("H56.07.01")).isTrue();
        assertThat(RealtimeNotification.isUnitIdentifier("h56.07")).isFalse();
        assertThat(RealtimeNotification.isUnitIdentifier("nguyenvana")).isFalse();
        assertThat(RealtimeNotification.isUnitIdentifier("H56abc")).isFalse();
    }

    @Test
    void notify_fillsCreatorWithFullNameOfCurrentUser() {
        AppUser user = Mockito.mock(AppUser.class);
        when(user.getFullName()).thenReturn("Nguyễn Văn A");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvana")).thenReturn(Optional.of(user));
        when(tenantResolver.resolve(any())).thenReturn(Optional.empty());

        notifier.notify(RealtimeNotification.of("duthao", "chuyenkyduyet", UUID.randomUUID(), "Dự thảo").to("lanhdao"));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getCreator()).isEqualTo("Nguyễn Văn A");
    }

    @Test
    void notify_persistsOneInboxRowPerRecipient_andPushCarriesItsId() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        when(tenantResolver.resolve(any())).thenReturn(Optional.empty());
        UUID docId = UUID.randomUUID();

        notifier.notify(RealtimeNotification.of("vbden", "chuyenxuly", docId, "125/UBND")
            .to("truongphong;H56.09").opinion("Xử lý gấp").deadline("30/10/2026"));

        List<UserNotification> saved = rows("H05");
        assertThat(saved).hasSize(2);
        UserNotification user = saved.get(0);
        UserNotification unit = saved.get(1);
        assertThat(user.getRecipientType()).isEqualTo(UserNotification.RECIPIENT_USER);
        assertThat(unit.getRecipientType()).isEqualTo(UserNotification.RECIPIENT_UNIT);
        assertThat(user.getDocumentId()).isEqualTo(docId.toString());
        assertThat(fieldsService.parseDynamicFields(user.getDynamicFieldsJson()))
            .containsEntry("code", "125/UBND").containsEntry("opinion", "Xử lý gấp")
            .containsEntry("deadline", "30/10/2026");

        RealtimeNotification pushed = sent.get(0);
        assertThat(pushed.getNotificationId("truongphong")).isEqualTo(user.getId());
        assertThat(pushed.getNotificationId("H56.09")).isEqualTo(unit.getId());
        assertThat(pushed.getCreatedOn()).isNotBlank();
    }

    @Test
    void notify_recipientInOtherTenant_isStoredInThatTenantsInbox() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        when(tenantResolver.resolve("vanthu.skhcn")).thenReturn(Optional.of("H05_01"));
        when(tenantResolver.resolve("H05.01.05")).thenReturn(Optional.of("H05_01"));
        when(tenantResolver.resolve("nguoitinh")).thenReturn(Optional.empty());
        when(tenantResolver.resolve("donvi.tenantkhongbiet")).thenReturn(Optional.of("H99"));

        notifier.notify(RealtimeNotification.of("vbdi", "phathanhvanban", UUID.randomUUID(), "12/UBND")
            .to("vanthu.skhcn;H05.01.05;nguoitinh;donvi.tenantkhongbiet"));

        assertThat(rows("H05_01")).extracting(UserNotification::getRecipient)
            .containsExactly("vanthu.skhcn", "H05.01.05");
        // Không xác định / tenant không tồn tại -> tenant người gửi (như trước).
        assertThat(rows("H05")).extracting(UserNotification::getRecipient)
            .containsExactly("nguoitinh", "donvi.tenantkhongbiet");
        assertThat(sent.get(0).getNotificationId("vanthu.skhcn")).isNotNull();
    }

    @Test
    void notify_runsOnDispatcher_notOnCallerThread() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(tenantResolver.resolve(any())).thenReturn(Optional.empty());
        RealtimeNotifier queuedNotifier = notifier(sent::add, inbox, queued::add);

        queuedNotifier.notify(RealtimeNotification.of("vbden", "tralai", UUID.randomUUID(), "x").to("u1"));
        assertThat(sent).isEmpty();
        assertThat(rows("H05")).isEmpty();

        // Luồng nền không có SecurityContext: username đã được chụp lúc gọi notify.
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        queued.forEach(Runnable::run);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getCreator()).isEqualTo("nguyenvana");
        assertThat(rows("H05")).hasSize(1);
    }

    @Test
    void notify_inboxFailure_stillPushes() {
        UserNotificationRepository failingInbox = Mockito.mock(UserNotificationRepository.class);
        when(failingInbox.saveAllInNewTransaction(any())).thenThrow(new IllegalStateException("db down"));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        when(tenantResolver.resolve(any())).thenReturn(Optional.empty());

        notifier(sent::add, failingInbox, Runnable::run)
            .notify(RealtimeNotification.of("vbden", "tralai", UUID.randomUUID(), "x").to("u1"));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getNotificationId("u1")).isNull();
    }

    @Test
    void notify_withoutRecipients_sendsNothing() {
        notifier.notify(RealtimeNotification.of("duthao", "thuhoi", UUID.randomUUID(), "x").to("", null));
        assertThat(sent).isEmpty();
        assertThat(inboxByTenant).isEmpty();
    }

    @Test
    void notify_insideTransaction_waitsForCommit() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        when(tenantResolver.resolve(any())).thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();

        notifier.notify(RealtimeNotification.of("vbden", "tralai", UUID.randomUUID(), "x").to("u1"));
        assertThat(sent).isEmpty();
        assertThat(rows("H05")).isEmpty();

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        assertThat(sent).hasSize(1);
        assertThat(rows("H05")).hasSize(1);
    }

    @Test
    void notify_gatewayFailure_isSwallowed() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        when(tenantResolver.resolve(any())).thenReturn(Optional.empty());

        notifier(n -> {
            throw new IllegalStateException("socket down");
        }, inbox, Runnable::run).notify(RealtimeNotification.of("vbden", "xoa", UUID.randomUUID(), "x").to("u1"));

        assertThat(rows("H05")).hasSize(1);
    }
}
