package com.td.application.dbserver;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-117 (bổ sung 19/08/2026): "vô hiệu hoá" (khác xoá) — server tạm thời không nên chọn nữa (vd.
// đang bảo trì) nhưng vẫn muốn giữ lại lịch sử/không muốn nhập lại. FE ẩn khỏi dropdown chọn lúc
// khởi tạo đơn vị nhưng vẫn hiện trong danh sách quản lý (khác hẳn xoá mềm — xem DeleteDbServerUseCase).
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("controlPlaneTransactionManager")
public class DeactivateDbServerUseCase {

    private final DbServerRepository dbServerRepository;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = dbServerRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy máy chủ CSDL với ID: " + id);
            }
            var entity = opt.get();
            entity.deactivate();
            var saved = dbServerRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Vô hiệu hoá máy chủ CSDL thất bại", ex);
            return Result.failure("Vô hiệu hoá máy chủ CSDL thất bại: " + ex.getMessage());
        }
    }
}
