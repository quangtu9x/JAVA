package com.td.application.phieutrinhtd;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tạo mới 1 phiếu trình thẩm định (form Domino {@code submission_dossier}), tương đương
 * {@code qlvb/add?databasetype=phieutrinhtd} (không truyền {@code id}) — đúng tiền lệ
 * {@code AddVbcddhDynamicDocumentUseCase} (BL-250). Mặc định {@code status="0"} (Đang soạn thảo — xem
 * {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md} mục 2.1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddPhieuTrinhTdDynamicDocumentUseCase {

    private static final String DEFAULT_TITLE = "Phiếu trình";

    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(AddDynamicDocumentRequest request) {
        try {
            String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? DEFAULT_TITLE
                : request.getTitle().trim();

            authorAclFieldsService.applyOnCreate(request.getDocumentType(), request.getFields());

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new PhieuTrinhTdDocument(title, PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = phieuTrinhTdDocumentRepository.save(document);
            documentCacheService.evictAllListCaches();
            documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo phiếu trình thẩm định thất bại", ex);
            return Result.failure("Tạo phiếu trình thẩm định thất bại: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildSnapshot(PhieuTrinhTdDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
