package com.td.application.privatecatalog;

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

// 25/09/2026 (quy ước "Thứ tự hiển thị"): Danh mục riêng ưu tiên thứ tự tăng dần, sau đó Tên A-Z, cuối
// cùng Mã — cùng SearchCategoriesUseCase/SearchAppUsersUseCase.
@ExtendWith(MockitoExtension.class)
class SearchPrivateCategoriesUseCaseTest {

    @Mock
    private PrivateCategoryRepository privateCategoryRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;

    @Test
    void execute_defaultSort_isSortOrderThenNameThenCode() {
        when(privateCategoryRepository.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        new SearchPrivateCategoriesUseCase(privateCategoryRepository, dynamicDocumentFieldsService, null)
            .execute(new SearchPrivateCategoriesRequest());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(privateCategoryRepository).search(any(), captor.capture());
        assertThat(captor.getValue().getSort().toList()).containsExactly(
            Sort.Order.asc("sortOrder"), Sort.Order.asc("name"), Sort.Order.asc("code"));
    }

    @Test
    void createRequest_defaultSortOrder_is99() {
        assertThat(new CreatePrivateCategoryRequest().getSortOrder())
            .isEqualTo(CreatePrivateCategoryRequest.DEFAULT_SORT_ORDER).isEqualTo(99);
    }
}
