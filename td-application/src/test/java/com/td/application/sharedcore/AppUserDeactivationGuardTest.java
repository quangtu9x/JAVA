package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserDeactivationGuardTest {

    private final List<Organization> organizations = new ArrayList<>();
    private final List<AppUser> kiemNhiem = new ArrayList<>();

    private final AppUserDeactivationGuard guard = new AppUserDeactivationGuard(
        stub(AppUserRepository.class, (name, args) ->
            name.equals("findByParentUserIdAndDeletedOnIsNull") ? kiemNhiem : null),
        stub(OrganizationRepository.class, (name, args) ->
            name.equals("findByIdAndDeletedOnIsNull")
                ? organizations.stream().filter(o -> o.getId().equals(args[0])).findFirst()
                : Optional.empty()));

    @Test
    void representativeOfOwnOrganization_blocksLockAndDelete() {
        var org = organization("Phòng Công nghệ", null);
        var user = user("demo1", org.getId(), true);
        organizations.add(organization("Phòng Công nghệ", user.getId().toString(), org));

        assertThat(guard.checkLock(user, List.of(user.getId())))
            .contains("người đại diện nhận văn bản").contains("Phòng Công nghệ");
        assertThat(guard.checkDelete(user)).contains("người đại diện nhận văn bản");
    }

    @Test
    void representative_blocksTransferToAnotherOrganization_butNotSameOrganization() {
        var org = organization("Phòng Công nghệ", null);
        var user = user("demo1", org.getId(), true);
        organizations.add(organization("Phòng Công nghệ", user.getId().toString(), org));

        assertThat(guard.checkTransfer(user, UUID.randomUUID()))
            .contains("chuyển đơn vị").contains("người đại diện nhận văn bản");
        assertThat(guard.checkTransfer(user, org.getId())).isNull();
        assertThat(guard.checkTransfer(user, null)).isNull();
    }

    @Test
    void representativeOfAnotherOrganization_isIgnored() {
        var ownOrg = organization("Phòng A", null);
        var user = user("demo1", ownOrg.getId(), true);
        organizations.add(ownOrg);
        organizations.add(organization("Sở B", user.getId().toString()));

        assertThat(guard.checkLock(user, List.of(user.getId()))).isNull();
        assertThat(guard.checkDelete(user)).isNull();
    }

    @Test
    void mainAccountWithActiveKiemNhiem_blocksLock_unlessLockedTogetherOrAlreadyLocked() {
        var user = user("main", null, true);
        var child = user("main_kn", null, true);
        kiemNhiem.add(child);

        assertThat(guard.checkLock(user, List.of(user.getId()))).contains("kiêm nhiệm").contains("main_kn");
        assertThat(guard.checkLock(user, List.of(user.getId(), child.getId()))).isNull();

        kiemNhiem.set(0, user("main_kn", null, false));
        assertThat(guard.checkLock(user, List.of(user.getId()))).isNull();
        // Xoá vẫn chặn khi còn tài khoản kiêm nhiệm, kể cả đang khoá.
        assertThat(guard.checkDelete(user)).contains("kiêm nhiệm");
    }

    private static AppUser user(String username, UUID organizationId, boolean active) {
        return new AppUser("kc-" + username, username, "Tên " + username, null, organizationId, null, active);
    }

    private static Organization organization(String name, String receiverId) {
        return new Organization("ID-" + name, name, null, null, "unit", 3, "/", 99, 0,
            receiverId, null, null, null, null, null, null, null, null);
    }

    // Cùng id với 'template' để mô phỏng tổ chức trực thuộc của người dùng đã có người đại diện.
    private static Organization organization(String name, String receiverId, Organization template) {
        var org = organization(name, receiverId);
        try {
            var field = org.getClass().getSuperclass();
            while (field != null) {
                try {
                    var idField = field.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(org, template.getId());
                    return org;
                } catch (NoSuchFieldException ignored) {
                    field = field.getSuperclass();
                }
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
        throw new IllegalStateException("Không tìm thấy field id");
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, java.util.function.BiFunction<String, Object[], Object> answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> answer.apply(method.getName(), args));
    }
}
