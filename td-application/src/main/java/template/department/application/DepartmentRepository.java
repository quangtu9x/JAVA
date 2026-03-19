package template.department.application;

import com.td.application.common.interfaces.IRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import template.department.domain.Department;

import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends IRepository<Department> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedOnIsNull(String code, UUID id);

    Optional<Department> findByIdAndDeletedOnIsNull(UUID id);

    Page<Department> search(SearchDepartmentsRequest request, Pageable pageable);
}