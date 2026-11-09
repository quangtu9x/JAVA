package com.td.application.vbcddh;

import com.td.application.common.models.Result;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 30/10/2026 (cutover FE vbcddh): thay {@code qlvb/get?databasetype=vbcddh|vbcddh_res} — chi tiết văn
 * bản CĐĐH / nhiệm vụ / bản ghi tiến độ. Bản ghi trong thùng rác ({@code form=*_Deleted}) vẫn mở được
 * (để "Phục hồi"); bản ghi đã xoá mềm thì không.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetVbcddhChiTietUseCase {

    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final VbcddhFieldMapper fieldMapper;

    public Result<Map<String, Object>> execute(String id, String items) {
        UUID documentId = VbcddhIds.parse(id);
        if (documentId == null) {
            return Result.failure("id văn bản chỉ đạo không hợp lệ");
        }
        VbcddhDocument document = vbcddhDocumentRepository.findById(documentId).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure("Không tìm thấy văn bản chỉ đạo/nhiệm vụ");
        }
        return Result.success(fieldMapper.toMap(document, items, true));
    }
}
