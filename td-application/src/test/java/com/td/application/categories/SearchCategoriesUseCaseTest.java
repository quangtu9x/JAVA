package com.td.application.categories;

import com.td.application.documents.DynamicDocumentFieldsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 24/09/2026 (yêu cầu người dùng): danh mục dùng chung ưu tiên "Thứ tự hiển thị" tăng dần, sau đó Tên
// A-Z, cuối cùng Mã (ổn định phân trang) — cùng quy ước SearchAppUsersUseCase.
@ExtendWith(MockitoExtension.class)
class SearchCategoriesUseCaseTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;

    @Test
    void execute_defaultSort_isSortOrderThenNameThenCode() {
        when(categoryRepository.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        new SearchCategoriesUseCase(categoryRepository, dynamicDocumentFieldsService).execute(new SearchCategoriesRequest());

        assertThat(capturedSort()).containsExactly(
            Sort.Order.asc("sortOrder"), Sort.Order.asc("name"), Sort.Order.asc("code"));
    }

    @Test
    void execute_explicitSortBy_keepsItPrimaryAndAppendsRemainingDefaults() {
        when(categoryRepository.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        var request = new SearchCategoriesRequest();
        request.setSortBy("name");
        request.setSortDirection("desc");

        new SearchCategoriesUseCase(categoryRepository, dynamicDocumentFieldsService).execute(request);

        assertThat(capturedSort()).containsExactly(
            Sort.Order.desc("name"), Sort.Order.asc("sortOrder"), Sort.Order.asc("code"));
    }

    @Test
    void createRequest_defaultSortOrder_is99() {
        assertThat(new CreateCategoryRequest().getSortOrder()).isEqualTo(CreateCategoryRequest.DEFAULT_SORT_ORDER).isEqualTo(99);
    }

    private List<Sort.Order> capturedSort() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(categoryRepository).search(any(), captor.capture());
        return captor.getValue().getSort().toList();
    }
}
