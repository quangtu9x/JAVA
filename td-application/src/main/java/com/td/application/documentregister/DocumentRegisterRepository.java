package com.td.application.documentregister;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.documentregister.DocumentRegister;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRegisterRepository extends IRepository<DocumentRegister> {

    Optional<DocumentRegister> findByIdAndDeletedOnIsNull(UUID id);

    List<DocumentRegister> findAllByIdInAndDeletedOnIsNull(List<UUID> ids);

    boolean existsByIdentifierAndDocumentRegisterTypeAndNameAndDeletedOnIsNull(
        String identifier, String documentRegisterType, String name);

    boolean existsByIdentifierAndDocumentRegisterTypeAndNameAndIdNotAndDeletedOnIsNull(
        String identifier, String documentRegisterType, String name, UUID id);

    boolean existsByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
        String identifier, String documentRegisterType);

    Optional<DocumentRegister> findByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
        String identifier, String documentRegisterType);

    List<DocumentRegister> findByIdentifierAndDocumentRegisterTypeAndIsLockedFalseAndDeletedOnIsNull(
        String identifier, String documentRegisterType);

    boolean existsByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndIdNotAndDeletedOnIsNull(
        String identifier, String documentRegisterType, UUID id);

    Page<DocumentRegister> search(SearchDocumentRegistersRequest request, Pageable pageable);
}
