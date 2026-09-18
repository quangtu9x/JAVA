package com.td.application.privatecatalog;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.privatecatalog.PrivateCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrivateCategoryRepository extends IRepository<PrivateCategory> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedOnIsNull(String code, UUID id);

    Optional<PrivateCategory> findByIdAndDeletedOnIsNull(UUID id);

    Page<PrivateCategory> search(SearchPrivateCategoriesRequest request, Pageable pageable);

    /**
     * Cùng ngữ nghĩa DXL Domino {@code nhom_nguoi_phathanh_by_identifier} — lọc theo field động
     * {@code donvi_sudung_identifier} (danh sách nối {@code ";"}, KHÁC cột thật {@code identifier}
     * — xem javadoc {@code PrivateCategoryJpaRepository#findByFormAndDonViSuDungIdentifier}).
     */
    List<PrivateCategory> findByFormAndDonViSuDungIdentifier(String form, String identifier);
}
