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
 * Yêu cầu người dùng 28/08/2026 ("port luôn nhóm phát hành nhanh"): test
 * {@link GetGroupUnitForIdentifierUseCase} — tương đương 3 view Domino
 * nhom_CQN/CQLH/DVNB_phathanh_by_identifier, cùng Form {@code group_unit}, phân biệt bởi field
 * {@code type} (lọc trong bộ nhớ, xem javadoc lớp).
 */
@ExtendWith(MockitoExtension.class)
class GetGroupUnitForIdentifierUseCaseTest {

    @Mock
    private PrivateCategoryRepository privateCategoryRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetGroupUnitForIdentifierUseCase newUseCase() {
        return new GetGroupUnitForIdentifierUseCase(privateCategoryRepository, dynamicDocumentFieldsService);
    }

    private PrivateCategory newCategory(String name, int sortOrder, String dynamicFieldsJson) {
        PrivateCategory category = new PrivateCategory(
            "CODE_" + name, name, null, GetGroupUnitForIdentifierUseCase.GROUP_UNIT_FORM,
            null, 0, name, sortOrder, null);
        category.replaceDynamicFields(dynamicFieldsJson);
        return category;
    }

    @Test
    void execute_withBlankIdentifierOrType_shouldReturnEmptyListWithoutQuerying() {
        assertThat(newUseCase().execute("  ", GroupUnitType.EXTERNAL_UNIT).getData()).isEmpty();
        assertThat(newUseCase().execute("SO_A", "  ").getData()).isEmpty();
    }

    @Test
    void execute_shouldFilterByTypeAndSortBySortOrder() {
        PrivateCategory external = newCategory("Nhom CQN", 2,
            "{\"type\":\"" + GroupUnitType.EXTERNAL_UNIT + "\",\"receiver_obj\":\"[{\\\"id\\\":\\\"u2\\\"}]\","
                + "\"donvi_sudung_identifier\":\"SO_A\"}");
        PrivateCategory internal = newCategory("Nhom DVNB", 1,
            "{\"type\":\"" + GroupUnitType.INTERNAL_UNIT + "\",\"receiver_obj\":\"[{\\\"id\\\":\\\"u1\\\"}]\","
                + "\"donvi_sudung_identifier\":\"SO_A\"}");
        when(privateCategoryRepository.findByFormAndDonViSuDungIdentifier(
            GetGroupUnitForIdentifierUseCase.GROUP_UNIT_FORM, "SO_A"))
            .thenReturn(List.of(external, internal));

        var result = newUseCase().execute("SO_A", GroupUnitType.EXTERNAL_UNIT);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> nodes = result.getData();
        // Chi con 1 node dung type - "Nhom DVNB" (khac type) phai bi loai.
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0)).containsEntry("name", "Nhom CQN");
        assertThat(nodes.get(0)).containsEntry("form", "group_unit");
        assertThat(nodes.get(0)).containsEntry("receiver_obj", "[{\"id\":\"u2\"}]");
    }

    @Test
    void execute_withCategoryMissingTypeField_shouldExcludeIt() {
        PrivateCategory noType = newCategory("Nhom khong co type", 1,
            "{\"receiver_obj\":\"[]\",\"donvi_sudung_identifier\":\"SO_A\"}");
        when(privateCategoryRepository.findByFormAndDonViSuDungIdentifier(
            GetGroupUnitForIdentifierUseCase.GROUP_UNIT_FORM, "SO_A"))
            .thenReturn(List.of(noType));

        var result = newUseCase().execute("SO_A", GroupUnitType.EXTERNAL_UNIT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }
}
