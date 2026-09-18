package com.td.application.phieutrinhtd;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Khôi phục phiếu trình thẩm định từ thùng rác, đối xứng {@link DeletePhieuTrinhTdUseCase}. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RestorePhieuTrinhTdUseCase {

    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;

    public Result<List<UUID>> execute(List<UUID> phieuTrinhIds) {
        if (phieuTrinhIds == null || phieuTrinhIds.isEmpty()) {
            return Result.failure("Danh sách phiếu trình không được để trống");
        }
        List<UUID> restored = new ArrayList<>();

        for (UUID id : phieuTrinhIds) {
            phieuTrinhTdDocumentRepository.findById(id).ifPresent(document -> {
                if (!document.isDeleted()) {
                    restored.add(document.getId());
                    return;
                }
                document.setDeletedOn(null);
                document.setDeletedBy(null);
                var saved = phieuTrinhTdDocumentRepository.save(document);
                restored.add(saved.getId());
            });
        }

        if (restored.isEmpty()) {
            return Result.failure("Không tìm thấy phiếu trình nào để khôi phục");
        }
        if (restored.size() < phieuTrinhIds.size()) {
            log.warn("Khôi phục phiếu trình: chỉ {}/{} id tồn tại và được khôi phục", restored.size(), phieuTrinhIds.size());
        }
        return Result.success(restored);
    }
}
