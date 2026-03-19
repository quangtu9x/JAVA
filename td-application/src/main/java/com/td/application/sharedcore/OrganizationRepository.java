package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends IRepository<Organization> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedOnIsNull(String code, UUID id);

    Optional<Organization> findByIdAndDeletedOnIsNull(UUID id);

    Page<Organization> search(SearchOrganizationsRequest request, Pageable pageable);
}
