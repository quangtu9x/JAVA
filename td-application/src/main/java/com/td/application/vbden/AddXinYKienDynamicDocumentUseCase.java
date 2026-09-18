package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbden.XinYKienDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * N08 ("cho ý kiến" TVUB/TVNB/BCH/BTV/TTĐU), yêu cầu người dùng 31/08/2026 ("tạo bảng riêng"): tương
 * đương {@link com.td.application.documents.AddDynamicDocumentUseCase} (BL-101) nhưng lưu vào bảng
 * vật lý ĐỘC LẬP {@code vbden_xinykien_documents} (xem {@link XinYKienDocument}) thay vì
 * {@code documents} dùng chung — đúng tiền lệ {@link com.td.application.duthao.AddDuThaoDynamicDocumentUseCase}/
 * {@code AddVbdiDynamicDocumentUseCase}. Dùng chung cho CẢ 2 document_type của bảng này:
 * {@code vbden_xinykien} (document chính, {@link GuiXinYKienUseCase}) VÀ {@code vbden_xinykien_res}
 * (lịch sử "cho ý kiến", {@link ChoYKienXinYKienUseCase}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddXinYKienDynamicDocumentUseCase {

    private static final String DEFAULT_TITLE = "Văn bản xin ý kiến";
    private static final String DEFAULT_STATUS = "0";

    private final XinYKienDocumentRepository xinYKienDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(AddDynamicDocumentRequest request) {
        try {
            String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? DEFAULT_TITLE
                : request.getTitle().trim();
            String status = (request.getStatus() == null || request.getStatus().isBlank())
                ? DEFAULT_STATUS
                : request.getStatus().trim();

            authorAclFieldsService.applyOnCreate(request.getDocumentType(), request.getFields());

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new XinYKienDocument(title, request.getDocumentType(), status, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = xinYKienDocumentRepository.save(document);
            documentCacheService.evictAllListCaches();
            documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo văn bản xin ý kiến (dynamic) thất bại", ex);
            return Result.failure("Tạo văn bản xin ý kiến thất bại: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildSnapshot(XinYKienDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
