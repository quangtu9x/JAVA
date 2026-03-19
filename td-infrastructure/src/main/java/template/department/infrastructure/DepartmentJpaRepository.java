package template.department.infrastructure;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import template.department.domain.Department;

import java.util.Optional;
import java.util.UUID;

import com.td.infrastructure.persistence.repository.BaseRepository;

public interface DepartmentJpaRepository extends BaseRepository<Department> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN TRUE ELSE FALSE END FROM Department d WHERE d.code = :code AND d.id <> :id AND d.deletedOn IS NULL")
    boolean existsByCodeAndIdNotAndDeletedOnIsNull(@Param("code") String code, @Param("id") UUID id);

    @Query("SELECT d FROM Department d WHERE d.id = :id AND d.deletedOn IS NULL")
    Optional<Department> findByIdAndDeletedOnIsNull(@Param("id") UUID id);
}