package com.td.application.realtime;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.notifications.UserNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class UserNotificationInboxUseCaseTest {

    private final InMemoryUserNotificationRepository repository = new InMemoryUserNotificationRepository();
    private final CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
    private final ResolveIdentifierByLevelUseCase identifierResolver = Mockito.mock(ResolveIdentifierByLevelUseCase.class);
    private final DynamicDocumentFieldsService fieldsService = new DynamicDocumentFieldsService();
    private final UserNotificationInboxUseCase useCase =
        new UserNotificationInboxUseCase(repository, currentUserProvider, identifierResolver, fieldsService);

    @BeforeEach
    void setUp() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("VanThu01"));
        when(currentUserProvider.currentRoles()).thenReturn(Set.of());
        when(identifierResolver.resolveForCurrentUser()).thenReturn(Optional.of("H56.09"));
    }

    private UserNotification add(String recipient, String databaseType, String action, String docId) {
        UserNotification n = new UserNotification(recipient, RealtimeNotification.isUnitIdentifier(recipient),
            databaseType, action, docId, "{\"code\":\"C-" + action + "\",\"creator\":\"Nguyen Van A\"}");
        repository.saveAllInNewTransaction(List.of(n));
        return n;
    }

    @Test
    void list_onlyOwnNotifications_newestFirst_withDisplayFields() {
        add("vanthu01", "vbden", "chuyenxuly", "d1");
        add("nguoikhac", "vbden", "chuyenxuly", "d2");
        add("vanthu01", "duthao", "chuyenkyduyet", "d3");

        Map<String, Object> body = useCase.list(0, 20, false, null).getData();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).extracting(i -> i.get("docid")).containsExactly("d3", "d1");
        assertThat(items.get(0)).containsEntry("code", "C-chuyenkyduyet").containsEntry("read", false)
            .containsEntry("databasetype", "duthao").containsKey("notification_id");
        assertThat(body).containsEntry("unreadCount", 2L).containsEntry("total", 2L);
    }

    @Test
    void unitInbox_visibleOnlyToUnitClerks() {
        add("H56.09", "duthao", "chuyenphathanh", "d1");

        assertThat(useCase.list(0, 20, false, null).getData()).containsEntry("total", 0L);

        when(currentUserProvider.currentRoles()).thenReturn(Set.of("QCAPNHATVBDI"));
        assertThat(useCase.list(0, 20, false, null).getData()).containsEntry("total", 1L);
    }

    @Test
    void summary_groupsUnreadByModuleAndAction() {
        add("vanthu01", "vbden", "chuyenxuly", "d1");
        add("vanthu01", "vbden", "chuyenxuly", "d2");
        add("vanthu01", "duthao", "chuyenkyduyet", "d3");

        Map<String, Object> body = useCase.summary().getData();

        assertThat(body).containsEntry("unreadCount", 3L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) body.get("groups");
        assertThat(groups).extracting(g -> g.get("action") + "=" + g.get("count"))
            .containsExactlyInAnyOrder("chuyenxuly=2", "chuyenkyduyet=1");
        assertThat((List<?>) body.get("latest")).hasSize(3);
    }

    @Test
    void markRead_cannotTouchOtherUsersNotification() {
        UserNotification other = add("nguoikhac", "vbden", "chuyenxuly", "d1");
        UserNotification mine = add("vanthu01", "vbden", "chuyenxuly", "d2");

        assertThat(useCase.markRead(other.getId()).getData()).isZero();
        assertThat(useCase.markRead(mine.getId()).getData()).isEqualTo(1);
        assertThat(mine.isRead()).isTrue();
        assertThat(mine.getReadBy()).isEqualTo("VanThu01");
        assertThat(other.isRead()).isFalse();
    }

    @Test
    void markReadByDocument_andMarkAllReadPerModule() {
        add("vanthu01", "vbden", "chuyenxuly", "d1");
        add("vanthu01", "vbden", "thuhoi", "d1");
        add("vanthu01", "duthao", "chuyenkyduyet", "d2");
        add("vanthu01", "vbdi", "phathanhvanban", "d3");

        assertThat(useCase.markReadByDocument("d1").getData()).isEqualTo(2);
        assertThat(useCase.markAllRead("duthao").getData()).isEqualTo(1);
        assertThat(useCase.summary().getData()).containsEntry("unreadCount", 1L);
    }

    @Test
    void hide_removesFromList() {
        UserNotification mine = add("vanthu01", "vbden", "chuyenxuly", "d1");

        assertThat(useCase.hide(mine.getId()).getData()).isEqualTo(1);
        assertThat(useCase.list(0, 20, false, null).getData()).containsEntry("total", 0L);
        assertThat(useCase.hide(UUID.randomUUID()).getData()).isZero();
    }
}
