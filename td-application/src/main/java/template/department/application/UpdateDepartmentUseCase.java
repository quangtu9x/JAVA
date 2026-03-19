package template.department.application;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UpdateDepartmentUseCase {

    private final DepartmentRepository departmentRepository;
    private final DepartmentCacheService departmentCacheService;

    public Result<UUID> execute(UUID id, UpdateDepartmentRequest request) {
        try {
            var opt = departmentRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy phòng ban với ID: " + id);
            }
            var department = opt.get();

            String code = request.getCode() != null
                ? TextNormalizer.normalize(request.getCode()).toUpperCase().replaceAll("\\s+", "_")
                : null;
            String name = TextNormalizer.normalizeAndSanitize(request.getName());
            String description = TextNormalizer.normalizeAndSanitize(request.getDescription());

            if (code != null && !code.equals(department.getCode())) {
                if (departmentRepository.existsByCodeAndIdNotAndDeletedOnIsNull(code, id)) {
                    return Result.failure("Mã phòng ban '" + code + "' đã được sử dụng");
                }
            }

            UUID effectiveParentId = request.isUpdateParent()
                ? request.getParentId()
                : department.getParentId();

            if (effectiveParentId != null && effectiveParentId.equals(id)) {
                return Result.failure("Phòng ban không thể là cha của chính nó");
            }

            String effectiveName = name != null ? name : department.getName();
            int newLevel = 0;
            String newFullPath = effectiveName;

            if (effectiveParentId != null) {
                var parentOpt = departmentRepository.findByIdAndDeletedOnIsNull(effectiveParentId);
                if (parentOpt.isEmpty()) {
                    return Result.failure("Không tìm thấy phòng ban cha với ID: " + effectiveParentId);
                }
                var parent = parentOpt.get();
                newLevel = parent.getLevel() + 1;
                newFullPath = parent.getFullPath() + " / " + effectiveName;
            }

            department.update(
                code,
                name,
                description,
                effectiveParentId,
                newLevel,
                newFullPath,
                request.getSortOrder(),
                request.getIsActive()
            );

            var saved = departmentRepository.save(department);
            departmentCacheService.evict(saved.getId());
            departmentCacheService.evictAllListCaches();

            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Cập nhật phòng ban thất bại: " + ex.getMessage());
        }
    }
}