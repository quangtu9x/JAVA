package com.td.application.categories;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends IRepository<Category> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedOnIsNull(String code, UUID id);

    Optional<Category> findByIdAndDeletedOnIsNull(UUID id);

    Page<Category> search(SearchCategoriesRequest request, Pageable pageable);
}
