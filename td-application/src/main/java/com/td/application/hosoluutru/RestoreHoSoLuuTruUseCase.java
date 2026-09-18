package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — khôi phục hồ sơ lưu trữ từ thùng rác, đối xứng
 * {@link DeleteHoSoLuuTruUseCase} (đệ quy khôi phục cả văn bản con).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RestoreHoSoLuuTruUseCase {

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;

    public Result<List<UUID>> execute(List<UUID> hosoLuuTruIds) {
        if (hosoLuuTruIds == null || hosoLuuTruIds.isEmpty()) {
            return Result.failure("Danh sách hồ sơ lưu trữ không được để trống");
        }
        List<UUID> restored = new ArrayList<>();

        for (UUID id : hosoLuuTruIds) {
            hoSoLuuTruDocumentRepository.findById(id).ifPresent(document -> {
                if (!document.isDeleted()) {
                    restored.add(document.getId());
                    return;
                }
                for (var vanBan : hoSoLuuTruVanBanDocumentRepository.findDeletedByHosoId(document.getId())) {
                    vanBan.setDeletedOn(null);
                    vanBan.setDeletedBy(null);
                    hoSoLuuTruVanBanDocumentRepository.save(vanBan);
                }
                document.setDeletedOn(null);
                document.setDeletedBy(null);
                var saved = hoSoLuuTruDocumentRepository.save(document);
                restored.add(saved.getId());
            });
        }

        if (restored.isEmpty()) {
            return Result.failure("Không tìm thấy hồ sơ lưu trữ nào để khôi phục");
        }
        if (restored.size() < hosoLuuTruIds.size()) {
            log.warn("Khôi phục hồ sơ lưu trữ: chỉ {}/{} id tồn tại và được khôi phục", restored.size(), hosoLuuTruIds.size());
        }
        return Result.success(restored);
    }
}
