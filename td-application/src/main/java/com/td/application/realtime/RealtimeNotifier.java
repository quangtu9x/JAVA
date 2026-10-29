package com.td.application.realtime;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.tonghop.TenantScopedExecutor;
import com.td.domain.notifications.UserNotification;
import com.td.domain.sharedcore.AppUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gửi thông báo từ use case — thay {@code TDInit.sendSocketNotSave} Domino.
 *
 * <p>Use case gọi {@link #notify} ngay sau khi lưu. Việc gửi chạy SAU KHI COMMIT (rollback thì không báo nhầm)
 * và trên luồng NỀN ({@link RealtimeTaskDispatcher}) - request nghiệp vụ không chờ ghi hộp thư/HTTP:</p>
 * <ol>
 *   <li>Lưu hộp thư {@code user_notifications} vào DB ĐƠN VỊ CỦA NGƯỜI NHẬN ({@link RecipientTenantResolver};
 *   không xác định được -> DB người gửi), mỗi tenant 1 transaction riêng - nguồn sự thật: người offline/mất
 *   kết nối kéo lại khi đăng nhập ({@link UserNotificationInboxUseCase}).</li>
 *   <li>Đẩy socket ("chuông cửa") kèm {@code notification_id} của từng người nhận.</li>
 * </ol>
 * <p>Tên người thao tác ({@code creator}) và tenant người gửi được chụp lại trên luồng request. Mọi lỗi đều bị
 * nuốt + log — không bao giờ làm hỏng nghiệp vụ.</p>
 */
@Component
@Slf4j
public class RealtimeNotifier {

    private final RealtimeNotificationGateway gateway;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentTenantProvider currentTenantProvider;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final RecipientTenantResolver recipientTenantResolver;
    private final RealtimeTaskDispatcher dispatcher;

    public RealtimeNotifier(RealtimeNotificationGateway gateway, CurrentUserProvider currentUserProvider,
                            AppUserRepository appUserRepository, UserNotificationRepository userNotificationRepository,
                            DynamicDocumentFieldsService dynamicDocumentFieldsService,
                            CurrentTenantProvider currentTenantProvider, TenantScopedExecutor tenantScopedExecutor,
                            RecipientTenantResolver recipientTenantResolver,
                            RealtimeTaskDispatcher dispatcher) {
        this.gateway = gateway;
        this.currentUserProvider = currentUserProvider;
        this.appUserRepository = appUserRepository;
        this.userNotificationRepository = userNotificationRepository;
        this.dynamicDocumentFieldsService = dynamicDocumentFieldsService;
        this.currentTenantProvider = currentTenantProvider;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.recipientTenantResolver = recipientTenantResolver;
        this.dispatcher = dispatcher;
    }

    public void notify(RealtimeNotification notification) {
        try {
            if (notification == null || notification.getRecipients().isEmpty()) {
                return;
            }
            // Chụp ngữ cảnh của luồng request (luồng nền không có SecurityContext/TenantContext).
            String actingUsername = currentUserProvider.currentUsername().orElse("");
            String originTenant = currentTenantProvider.currentTenantId().orElse(null);
            Runnable task = () -> deliver(notification, originTenant, actingUsername);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatch(task, notification);
                    }
                });
            } else {
                dispatch(task, notification);
            }
        } catch (RuntimeException ex) {
            log.warn("Không gửi được thông báo {}/{}: {}", notification == null ? null
                : notification.getDatabaseType(), notification == null ? null : notification.getAction(),
                ex.getMessage());
        }
    }

    private void dispatch(Runnable task, RealtimeNotification notification) {
        try {
            dispatcher.dispatch(task);
        } catch (RuntimeException ex) {
            log.warn("Không xếp hàng được thông báo {}/{}: {}", notification.getDatabaseType(),
                notification.getAction(), ex.getMessage());
        }
    }

    private void deliver(RealtimeNotification notification, String originTenant, String actingUsername) {
        try {
            if (notification.getCreator() == null || notification.getCreator().isBlank()) {
                notification.creator(displayName(actingUsername));
            }
            notification.createdOn(LocalDateTime.now().toString());
            persistQuietly(notification, originTenant);
            gateway.publish(notification);
        } catch (RuntimeException ex) {
            log.warn("Không gửi được thông báo {}/{}: {}", notification.getDatabaseType(),
                notification.getAction(), ex.getMessage());
        }
    }

    private void persistQuietly(RealtimeNotification notification, String originTenant) {
        String fieldsJson;
        try {
            fieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(displayFields(notification), "", "", "");
        } catch (RuntimeException ex) {
            log.warn("Không dựng được nội dung thông báo: {}", ex.getMessage());
            return;
        }
        Map<String, List<UserNotification>> rowsByTenant = new LinkedHashMap<>();
        for (String recipient : notification.getRecipients()) {
            String tenant = recipientTenantResolver.resolve(recipient)
                .filter(tenantScopedExecutor::isKnownTenant)
                .orElse(originTenant);
            rowsByTenant.computeIfAbsent(tenant == null ? "" : tenant, k -> new ArrayList<>())
                .add(new UserNotification(recipient, RealtimeNotification.isUnitIdentifier(recipient),
                    notification.getDatabaseType(), notification.getAction(), notification.getDocId(), fieldsJson));
        }
        rowsByTenant.forEach((tenant, rows) -> {
            try {
                List<UserNotification> saved = tenant.isEmpty()
                    ? userNotificationRepository.saveAllInNewTransaction(rows)
                    : tenantScopedExecutor.runInTenant(tenant, () -> userNotificationRepository.saveAllInNewTransaction(rows));
                saved.forEach(row -> notification.putNotificationId(row.getRecipient(), row.getId()));
            } catch (RuntimeException ex) {
                // Không lưu được (vd DB đơn vị lỗi) vẫn đẩy socket - người đang online vẫn nhận được.
                log.warn("Không lưu được hộp thư thông báo {}/{} ở tenant '{}': {}", notification.getDatabaseType(),
                    notification.getAction(), tenant, ex.getMessage());
            }
        });
    }

    private static Map<String, String> displayFields(RealtimeNotification n) {
        Map<String, String> fields = new LinkedHashMap<>();
        putIfPresent(fields, "code", n.getCode());
        putIfPresent(fields, "creator", n.getCreator());
        putIfPresent(fields, "position", n.getPosition());
        putIfPresent(fields, "opinion", n.getOpinion());
        putIfPresent(fields, "deadline", n.getDeadline());
        putIfPresent(fields, "message", n.getMessage());
        return fields;
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }

    private String displayName(String username) {
        if (username == null || username.isBlank()) {
            return "";
        }
        try {
            return appUserRepository.findByUsernameAndDeletedOnIsNull(username)
                .map(AppUser::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(username);
        } catch (RuntimeException ex) {
            return username;
        }
    }
}
