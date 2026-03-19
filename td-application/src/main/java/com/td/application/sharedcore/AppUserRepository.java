package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends IRepository<AppUser> {

    boolean existsByUsernameAndDeletedOnIsNull(String username);

    boolean existsByUsernameAndIdNotAndDeletedOnIsNull(String username, UUID id);

    boolean existsByKeycloakSubjectAndDeletedOnIsNull(String keycloakSubject);

    Optional<AppUser> findByIdAndDeletedOnIsNull(UUID id);

    Optional<AppUser> findByKeycloakSubjectAndDeletedOnIsNull(String keycloakSubject);

    Page<AppUser> search(SearchAppUsersRequest request, Pageable pageable);
}
