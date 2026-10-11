package com.td.application.privatecatalog;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.privatecatalog.PrivateCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 09/10/2026 — port nhom_{TVUB,...}_by_identifier (xin ý kiến thành viên). Stub tay (không Mockito). */
class GetGroupPeopleByTypeUseCaseTest {

    private final List<Object[]> calls = new ArrayList<>();
    private List<PrivateCategory> groups = List.of();

    private final GetGroupPeopleByTypeUseCase useCase = new GetGroupPeopleByTypeUseCase(
        stub(PrivateCategoryRepository.class, (name, args) -> {
            if (name.equals("findByFormAndTypeAndIdentifier")) {
                calls.add(args);
                return groups;
            }
            return null;
        }),
        new DynamicDocumentFieldsService(),
        new ActiveGroupMembersFilter(stub(AppUserRepository.class, (name, args) ->
            name.equals("findActiveUsernamesIn") ? activeOnly((Collection<?>) args[0]) : null),
            com.td.application.sharedcore.UserDelegationTestSupport.emptyResolver()));

    @Test
    void mapsViewnameToGroupTypeAndDropsLockedMembers() {
        PrivateCategory group = new PrivateCategory("TVUB", "Nhóm TVUB", null, "group_people", null, 0, "/", 99, "H56");
        group.replaceDynamicFields("{\"type\":\"Thành viên Ủy ban\",\"receiver_obj\":"
            + "[{\"id\":\"1\",\"username\":\"tv1\",\"name\":\"TV 1\"},{\"id\":\"2\",\"username\":\"khoa\",\"name\":\"Bi khoa\"}]}");
        groups = List.of(group);

        var result = useCase.execute("nhom_TVUB_by_identifier", "H56.01");

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0)).containsExactly("group_people", "Thành viên Ủy ban", "H56.01");
        Map<String, Object> row = result.getData().get(0);
        assertThat(row).containsEntry("name", "Nhóm TVUB").containsEntry("type", "Thành viên Ủy ban");
        assertThat((List<?>) row.get("receiver_obj")).hasSize(1);
    }

    @Test
    void supportsAllFiveOpinionViewsOnly() {
        for (String view : List.of("nhom_TVUB_by_identifier", "nhom_TVNB_by_identifier", "nhom_BTV_by_identifier",
                "nhom_BCH_by_identifier", "nhom_TTDU_by_identifier")) {
            assertThat(GetGroupPeopleByTypeUseCase.supportsView(view)).isTrue();
        }
        assertThat(GetGroupPeopleByTypeUseCase.supportsView("vwcocautochuc")).isFalse();
        assertThat(useCase.execute("nhom_TVUB_by_identifier", " ").getData()).isEmpty();
        assertThat(calls).isEmpty();
    }

    private static Set<String> activeOnly(Collection<?> usernames) {
        Set<String> active = new java.util.LinkedHashSet<>();
        usernames.forEach(u -> {
            if (!"khoa".equals(u)) {
                active.add(String.valueOf(u));
            }
        });
        return active;
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, java.util.function.BiFunction<String, Object[], Object> answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> answer.apply(method.getName(), args));
    }
}
