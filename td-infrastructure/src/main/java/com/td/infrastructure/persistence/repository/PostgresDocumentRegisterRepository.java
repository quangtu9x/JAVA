package com.td.infrastructure.persistence.repository;

import com.td.application.documentregister.DocumentRegisterRepository;
import com.td.application.documentregister.SearchDocumentRegistersRequest;
import com.td.domain.documentregister.DocumentRegister;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresDocumentRegisterRepository implements DocumentRegisterRepository {

    private final DocumentRegisterJpaRepository jpa;

    @Override
    public Optional<DocumentRegister> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<DocumentRegister, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<DocumentRegister> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public List<DocumentRegister> findAllByIdInAndDeletedOnIsNull(List<UUID> ids) {
        return jpa.findAllByIdInAndDeletedOnIsNull(ids);
    }

    @Override
    public <S extends DocumentRegister> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(DocumentRegister entity) {
        jpa.delete(entity);
    }

    @Override
    public boolean existsByIdentifierAndDocumentRegisterTypeAndNameAndDeletedOnIsNull(
            String identifier, String documentRegisterType, String name) {
        return jpa.existsByIdentifierAndDocumentRegisterTypeAndNameAndDeletedOnIsNull(
            identifier, documentRegisterType, name);
    }

    @Override
    public boolean existsByIdentifierAndDocumentRegisterTypeAndNameAndIdNotAndDeletedOnIsNull(
            String identifier, String documentRegisterType, String name, UUID id) {
        return jpa.existsByIdentifierAndDocumentRegisterTypeAndNameAndIdNotAndDeletedOnIsNull(
            identifier, documentRegisterType, name, id);
    }

    @Override
    public boolean existsByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
            String identifier, String documentRegisterType) {
        return jpa.existsByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
            identifier, documentRegisterType);
    }

    @Override
    public Optional<DocumentRegister> findByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
            String identifier, String documentRegisterType) {
        return jpa.findByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
            identifier, documentRegisterType);
    }

    @Override
    public List<DocumentRegister> findByIdentifierAndDocumentRegisterTypeAndIsLockedFalseAndDeletedOnIsNull(
            String identifier, String documentRegisterType) {
        return jpa.findByIdentifierAndDocumentRegisterTypeAndIsLockedFalseAndDeletedOnIsNull(
            identifier, documentRegisterType);
    }

    @Override
    public boolean existsByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndIdNotAndDeletedOnIsNull(
            String identifier, String documentRegisterType, UUID id) {
        return jpa.existsByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndIdNotAndDeletedOnIsNull(
            identifier, documentRegisterType, id);
    }

    @Override
    public Page<DocumentRegister> search(SearchDocumentRegistersRequest request, Pageable pageable) {
        Specification<DocumentRegister> spec = Specification
            .where(notDeleted())
            .and(keywordFilter(request))
            .and(identifierFilter(request))
            .and(typeFilter(request))
            .and(yearFilter(request))
            .and(lockedFilter(request));
        return jpa.findAll(spec, pageable);
    }

    private static Specification<DocumentRegister> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<DocumentRegister> keywordFilter(SearchDocumentRegistersRequest req) {
        if (req.getKeyword() == null || req.getKeyword().isBlank()) return null;
        String kw = "%" + req.getKeyword().trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
            cb.like(cb.lower(root.get("name")),         kw),
            cb.like(cb.lower(root.get("documentType")),  kw),
            cb.like(cb.lower(root.get("codeNotation")),  kw),
            cb.like(cb.lower(root.get("agency")),        kw)
        );
    }

    private static Specification<DocumentRegister> identifierFilter(SearchDocumentRegistersRequest req) {
        if (req.getIdentifier() == null || req.getIdentifier().isBlank()) return null;
        return (root, q, cb) -> cb.equal(root.get("identifier"), req.getIdentifier());
    }

    private static Specification<DocumentRegister> typeFilter(SearchDocumentRegistersRequest req) {
        if (req.getDocumentRegisterType() == null || req.getDocumentRegisterType().isBlank()) return null;
        return (root, q, cb) -> cb.equal(root.get("documentRegisterType"), req.getDocumentRegisterType());
    }

    private static Specification<DocumentRegister> yearFilter(SearchDocumentRegistersRequest req) {
        if (req.getYear() == null) return null;
        return (root, q, cb) -> cb.equal(root.get("year"), req.getYear());
    }

    private static Specification<DocumentRegister> lockedFilter(SearchDocumentRegistersRequest req) {
        if (req.getIsLocked() == null) return null;
        return (root, q, cb) -> cb.equal(root.get("isLocked"), req.getIsLocked());
    }
}
