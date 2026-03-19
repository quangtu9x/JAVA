package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.UserDataScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserDataScopeRepository extends IRepository<UserDataScope> {

    Optional<UserDataScope> findByIdAndDeletedOnIsNull(UUID id);

    Page<UserDataScope> search(SearchUserDataScopesRequest request, Pageable pageable);
}
