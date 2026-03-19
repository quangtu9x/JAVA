package template.department.application;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import template.department.domain.Department;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CreateDepartmentUseCase {

    private final DepartmentRepository departmentRepository;
    private final DepartmentCacheService departmentCacheService;

    public Result<UUID> execute(CreateDepartmentRequest request) {
        try {
            String code = normalizeCode(request.getCode());
            String name = TextNormalizer.normalizeAndSanitize(request.getName());
            String description = TextNormalizer.normalizeAndSanitize(request.getDescription());

            if (departmentRepository.existsByCodeAndDeletedOnIsNull(code)) {
                return Result.failure("Mã phòng ban '" + code + "' đã tồn tại");
            }

            int level = 0;
            String fullPath = name;
            UUID parentId = request.getParentId();

            if (parentId != null) {
                var parentOpt = departmentRepository.findByIdAndDeletedOnIsNull(parentId);
                if (parentOpt.isEmpty()) {
                    return Result.failure("Không tìm thấy phòng ban cha với ID: " + parentId);
                }
                var parent = parentOpt.get();
                if (!parent.isActive()) {
                    return Result.failure("Phòng ban cha đang bị vô hiệu hóa, không thể tạo phòng ban con");
                }
                level = parent.getLevel() + 1;
                fullPath = parent.getFullPath() + " / " + name;
            }

            var department = new Department(code, name, description, parentId, level, fullPath, request.getSortOrder());
            var saved = departmentRepository.save(department);
            departmentCacheService.evictAllListCaches();

            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Tạo phòng ban thất bại: " + ex.getMessage());
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        return TextNormalizer.normalize(code).toUpperCase().replaceAll("\\s+", "_");
    }
}