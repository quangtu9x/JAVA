package template.department.application;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDepartmentUseCase {

    private final DepartmentRepository departmentRepository;
    private final DepartmentCacheService departmentCacheService;

    public Result<DepartmentDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID phòng ban không được để trống");
            }

            var cached = departmentCacheService.get(id);
            if (cached != null) {
                return Result.success(cached);
            }

            var opt = departmentRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy phòng ban với ID: " + id);
            }

            var dto = DepartmentDtoMapper.map(opt.get());
            departmentCacheService.put(id, dto);
            return Result.success(dto);
        } catch (Exception ex) {
            return Result.failure("Lấy phòng ban thất bại: " + ex.getMessage());
        }
    }
}