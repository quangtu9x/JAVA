package template.department.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import template.department.application.DepartmentRepository;
import template.department.application.SearchDepartmentsRequest;
import template.department.domain.Department;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresDepartmentRepository implements DepartmentRepository {

    private final DepartmentJpaRepository jpa;

    @Override
    public Optional<Department> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<Department, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<Department> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public <S extends Department> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(Department entity) {
        jpa.delete(entity);
    }

    @Override
    public boolean existsByCodeAndDeletedOnIsNull(String code) {
        return jpa.existsByCodeAndDeletedOnIsNull(code);
    }

    @Override
    public boolean existsByCodeAndIdNotAndDeletedOnIsNull(String code, UUID id) {
        return jpa.existsByCodeAndIdNotAndDeletedOnIsNull(code, id);
    }

    @Override
    public Page<Department> search(SearchDepartmentsRequest request, Pageable pageable) {
        Specification<Department> spec = Specification
            .where(notDeleted())
            .and(keywordFilter(request))
            .and(parentFilter(request))
            .and(levelFilter(request))
            .and(activeFilter(request));
        return jpa.findAll(spec, pageable);
    }

    private static Specification<Department> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<Department> keywordFilter(SearchDepartmentsRequest request) {
        if (request.getKeyword() == null || request.getKeyword().isBlank()) {
            return null;
        }
        String keyword = "%" + request.getKeyword().trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("name")), keyword),
            cb.like(cb.lower(root.get("code")), keyword),
            cb.like(cb.lower(root.get("description")), keyword),
            cb.like(cb.lower(root.get("fullPath")), keyword)
        );
    }

    private static Specification<Department> parentFilter(SearchDepartmentsRequest request) {
        if (request.getParentId() == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("parentId"), request.getParentId());
    }

    private static Specification<Department> levelFilter(SearchDepartmentsRequest request) {
        if (request.getLevel() == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("level"), request.getLevel());
    }

    private static Specification<Department> activeFilter(SearchDepartmentsRequest request) {
        if (request.getIsActive() == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("isActive"), request.getIsActive());
    }
}