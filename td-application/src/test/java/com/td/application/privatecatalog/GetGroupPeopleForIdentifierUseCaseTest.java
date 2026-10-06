package com.td.application.privatecatalog;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.privatecatalog.PrivateCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BL-116 (tiếp, 25/08/2026): {@link GetGroupPeopleForIdentifierUseCase} — tương đương DXL Domino
 * {@code nhom_nguoi_phathanh_by_identifier}, lọc theo field động {@code donvi_sudung_identifier}
 * (KHÁC cột thật {@code identifier}, xem javadoc lớp).
 */
@ExtendWith(MockitoExtension.class)
class GetGroupPeopleForIdentifierUseCaseTest {

    @Mock
    private PrivateCategoryRepository privateCategoryRepository;

    @Mock
    private com.td.application.sharedcore.AppUserRepository appUserRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetGroupPeopleForIdentifierUseCase newUseCase() {
        return new GetGroupPeopleForIdentifierUseCase(privateCategoryRepository, dynamicDocumentFieldsService,
            new ActiveGroupMembersFilter(appUserRepository));
    }

    @Test
    void execute_withBlankIdentifier_shouldReturnEmptyListWithoutQuerying() {
        var result = newUseCase().execute("  ");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_shouldMapFieldsAndSortBySortOrder() {
        PrivateCategory second = newCategory("Nhom B", 2, "{\"receiver_obj\":\"[{\\\"id\\\":\\\"u2\\\"}]\",\"donvi_sudung_identifier\":\"SO_A;SO_B\"}");
        PrivateCategory first = newCategory("Nhom A", 1, "{\"receiver_obj\":\"[{\\\"id\\\":\\\"u1\\\"}]\",\"donvi_sudung_identifier\":\"SO_A\"}");
        when(privateCategoryRepository.findByFormAndDonViSuDungIdentifier(
            GetGroupPeopleForIdentifierUseCase.GROUP_PEOPLE_FORM, "SO_A"))
            .thenReturn(List.of(second, first));

        var result = newUseCase().execute("SO_A");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> nodes = result.getData();
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0)).containsEntry("name", "Nhom A");
        assertThat(nodes.get(1)).containsEntry("name", "Nhom B");
        assertThat(nodes.get(0)).containsEntry("form", "group_people");
        assertThat(nodes.get(0)).containsEntry("receiver_obj", "[{\"id\":\"u1\"}]");
    }

    @Test
    void execute_shouldTrimIdentifierBeforeQuerying() {
        when(privateCategoryRepository.findByFormAndDonViSuDungIdentifier(
            GetGroupPeopleForIdentifierUseCase.GROUP_PEOPLE_FORM, "SO_A"))
            .thenReturn(List.of());

        var result = newUseCase().execute("  SO_A  ");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    private PrivateCategory newCategory(String name, int sortOrder, String dynamicFieldsJson) {
        PrivateCategory category = new PrivateCategory(
            "CODE_" + name, name, null, GetGroupPeopleForIdentifierUseCase.GROUP_PEOPLE_FORM,
            null, 0, name, sortOrder, null);
        category.replaceDynamicFields(dynamicFieldsJson);
        return category;
    }
}
