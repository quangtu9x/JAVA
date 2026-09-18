package com.td.infrastructure.documents;

import com.td.application.vbdi.AddVbdiEditHistoryRequest;
import com.td.application.vbdi.AddVbdiEditHistoryUseCase;
import com.td.application.vbdi.VbdiDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("ghi log chỉnh sửa thì tạo ra bảng riêng tham khảo thêm như dự
 * thảo đã làm (lưu cả nhật ký thêm, xoá đính kèm)"): ghi 1 vết vào
 * {@code vbdi_edit_history_documents} MỖI KHI 1 tệp đính kèm được thêm/xoá cho văn bản đi — CHƯA
 * có tiền lệ ở duthao (đã khảo sát: {@code duthao_edit_history_documents} KHÔNG ghi sự kiện
 * thêm/xoá tệp thật, chỉ tình cờ ghi 1 trường hợp gỡ tên khỏi field phân loại text; cơ chế
 * "vết tệp" thật của Domino — {@code VetHieuChinhFile.jsx}, action=add/delete — CHƯA được port cho
 * BẤT KỲ module nào) — đây là phần MỞ RỘNG so với những gì dự thảo đã làm, đúng yêu cầu rõ ràng của
 * người dùng lần này.
 *
 * <p>Cùng khuôn best-effort với {@link VbdiFilesJsonbSync}: lỗi ghi vết KHÔNG được làm hỏng luồng
 * upload/xoá tệp chính (bọc try/catch, log warning) — {@code file_metadata} vẫn là nguồn dữ liệu
 * gốc, vết hiệu chỉnh chỉ là audit trail phụ trợ. Scoped CHỈ cho vbdi (như
 * {@link VbdiFilesJsonbSync}) — im lặng bỏ qua (no-op) nếu {@code documentId} không phải văn bản
 * đi.</p>
 *
 * <p>Tái dùng {@link AddVbdiEditHistoryUseCase} (không tự ghi thẳng repository) để đi qua đúng
 * guard nghiệp vụ (văn bản cha tồn tại, đúng document_type) và {@code DocumentLogService} audit
 * chung — nhất quán với cách {@code ChiTietModal.jsx} gọi ghi vết field text (cùng use case, khác
 * nguồn gọi).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class VbdiEditHistorySync {

    private static final String FORM_EDIT = "vbdi";
    private static final String FIELD_NAME = "tep_dinh_kem";

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final AddVbdiEditHistoryUseCase addVbdiEditHistoryUseCase;

    void logFileAdded(UUID documentId, String fileName) {
        logFileEvent(documentId, null, fileName);
    }

    void logFileRemoved(UUID documentId, String fileName) {
        logFileEvent(documentId, fileName, null);
    }

    private void logFileEvent(UUID documentId, String oldFileName, String newFileName) {
        if (documentId == null) {
            return;
        }
        try {
            // Scoped CHI cho vbdi - im lang bo qua (khong ghi vet) neu document khong phai vbdi,
            // cung nguyen tac VbdiFilesJsonbSync#syncIfVbdiDocument.
            if (vbdiDocumentRepository.findById(documentId).isEmpty()) {
                return;
            }

            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("oldvalue", oldFileName);
            diff.put("newvalue", newFileName);
            Map<String, Object> editObj = new LinkedHashMap<>();
            editObj.put(FIELD_NAME, diff);

            var request = new AddVbdiEditHistoryRequest();
            request.setParentId(documentId);
            request.setFormEdit(FORM_EDIT);
            request.setEditObj(editObj);
            addVbdiEditHistoryUseCase.execute(request);
        } catch (Exception ex) {
            log.warn("Ghi vet them/xoa tep dinh kem cho vbdi_documents {} that bai", documentId, ex);
        }
    }
}
