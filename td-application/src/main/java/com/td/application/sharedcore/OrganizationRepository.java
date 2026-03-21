package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends IRepository<Organization> {

    boolean existsByIdentifierAndDeletedOnIsNull(String identifier);

    boolean existsByIdentifierAndIdNotAndDeletedOnIsNull(String identifier, UUID id);

    Optional<Organization> findByIdAndDeletedOnIsNull(UUID id);

    List<Organization> findByParentIdAndDeletedOnIsNull(UUID parentId);

    Page<Organization> search(SearchOrganizationsRequest request, Pageable pageable);
}
