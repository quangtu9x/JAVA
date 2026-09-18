package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 11/09/2026 (yêu cầu người dùng "port vbdi/thungrac/thungrac sang JAVA, cutoverFE luôn"): port nút
 * "Khôi phục" ({@code VBDIButtonActionPage.jsx#handleRestore}, {@code case "recover"}) — Domino gốc
 * (xác nhận qua đọc trực tiếp {@code DocumentOutProcesses.java#Recover_document()}) chỉ làm 1 việc cốt
 * lõi: đảo ngược field {@code Form} từ {@code "DocumentOut_Deleted"} về lại {@code "DocumentOut"}
 * (cách Domino cài đặt soft-delete — đổi tên Form, KHÔNG có field boolean/timestamp riêng) + ghi 1
 * dòng lịch sử vào {@code recover_obj_rtext}. Ở đây (Java): đảo ngược ĐÚNG field
 * {@link VbdiDocument#isDeleted()} qua {@link com.td.domain.common.contracts.ISoftDelete#restore()}
 * (đối xứng {@link DeleteVbdiDocumentUseCase}, xem LUẬT "delete luôn soft-delete" — restore cũng vậy,
 * không có thao tác nào khác tương đương {@code recover_obj_rtext} — KHÔNG port lịch sử "phục hồi"
 * riêng, đủ dùng qua {@code DocumentLogService.logUpdate} chung như mọi use case khác).
 *
 * <p>Không tìm thấy văn bản / chưa từng bị xoá → coi như đã ở đúng trạng thái mong muốn, trả về THÀNH
 * CÔNG (idempotent, cùng quy ước {@link DeleteVbdiDocumentUseCase}) — riêng "không tìm thấy" vẫn phải
 * báo lỗi vì không có gì để khôi phục thật sự (khác trường hợp "đã ở trạng thái đích").</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RestoreVbdiDocumentUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi với ID: ";

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(UUID documentId) {
        try {
            var documentOptional = vbdiDocumentRepository.findById(documentId);
            if (documentOptional.isEmpty()) {
                return Result.failure(NOT_FOUND_MESSAGE + documentId);
            }

            var document = documentOptional.get();
            if (!document.isDeleted()) {
                // Chua tung bi xoa (hoac da khoi phuc tu truoc) - coi nhu thanh cong (idempotent).
                return Result.success(documentId);
            }

            var before = buildSnapshot(document);
            document.restore();
            var saved = vbdiDocumentRepository.save(document);
            documentCacheService.evict(saved.getId());
            documentCacheService.evictAllListCaches();
            documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Khoi phuc van ban di that bai", ex);
            return Result.failure("Khôi phục văn bản đi thất bại: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildSnapshot(VbdiDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
