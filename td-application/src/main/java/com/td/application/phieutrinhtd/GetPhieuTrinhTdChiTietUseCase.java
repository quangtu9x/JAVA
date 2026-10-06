package com.td.application.phieutrinhtd;

import com.td.application.common.models.Result;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chi tiết 1 phiếu trình thẩm định, tương thích {@code qlvb/get?databasetype=phieutrinhtd&items=getall}
 * — kèm toàn bộ lịch sử "quatrinh" (nhật ký trình/chuyển trạng thái), xem
 * {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md} mục 6.2.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPhieuTrinhTdChiTietUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy phiếu trình";

    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final PhieuTrinhTdProcessDocumentRepository phieuTrinhTdProcessDocumentRepository;
    private final PhieuTrinhTdFieldMapper fieldMapper;

    public Result<Map<String, Object>> execute(GetPhieuTrinhTdChiTietRequest request) {
        if (request == null || request.getId() == null || request.getId().isBlank()) {
            return Result.failure("Thiếu id phiếu trình");
        }

        UUID id;
        try {
            id = UUID.fromString(request.getId());
        } catch (IllegalArgumentException ex) {
            return Result.failure("id phiếu trình không hợp lệ");
        }

        PhieuTrinhTdDocument document = phieuTrinhTdDocumentRepository.findById(id).orElse(null);
        // 06/10/2026: phieu trinh trong thung rac van mo duoc (de "Phuc hoi") - mapper dat form
        // "submission_dossier_Deleted" nhu Domino.
        if (document == null) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        // 06/10/2026 (cutover FE phieu trinh): kem danh sach tep Java (files) cua phieu trinh va tung dong
        // qua trinh - dung chung PhieuTrinhTdFieldMapper voi danh sach.
        Map<String, Object> result = new LinkedHashMap<>(fieldMapper.toDocumentMap(document, true));

        List<Map<String, Object>> quaTrinh = phieuTrinhTdProcessDocumentRepository.findByParentId(id).stream()
            .filter(process -> !process.isDeleted())
            .map(process -> fieldMapper.toProcessMap(process, true))
            .toList();
        result.put("quatrinh", quaTrinh);

        return Result.success(result);
    }

}
