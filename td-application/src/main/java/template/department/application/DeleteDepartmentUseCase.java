package template.department.application;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteDepartmentUseCase {

    private final DepartmentRepository departmentRepository;
    private final DepartmentCacheService departmentCacheService;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = departmentRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy phòng ban với ID: " + id);
            }

            var department = opt.get();
            department.markAsDeleted(UUID.randomUUID());
            var saved = departmentRepository.save(department);

            departmentCacheService.evict(saved.getId());
            departmentCacheService.evictAllListCaches();

            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Xóa phòng ban thất bại: " + ex.getMessage());
        }
    }
}