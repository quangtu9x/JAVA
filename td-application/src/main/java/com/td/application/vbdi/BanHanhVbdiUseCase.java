package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.numbering.DocumentNumberingService;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BL-220 (VBDi Core) — DoD (3): ban hành — sinh số đi chính thức + cập nhật trạng thái. Yêu cầu văn
 * bản đã qua bước trình ký ({@link VbdiStatus#PENDING_SIGNATURE}), đúng thứ tự nghiệp vụ evidenced
 * ("Soạn thảo → Trình ký → Ban hành", tiêu chí kiểm thử BL-220).
 *
 * <p><b>Số đi</b>: {@code document_number} được sinh lại (ghi đè) tại đây qua
 * {@link DocumentNumberingService} trực tiếp — bất kể giá trị tạm đã được
 * {@link SoanThaoVbdiUseCase} sinh sẵn lúc soạn thảo (do BL-104 cấu hình {@code vbdi} active từ
 * trước) — đảm bảo số đi CHÍNH THỨC luôn phản ánh đúng thời điểm ban hành, khớp DoD (3) dù có tốn 1
 * lượt counter dư ở bước soạn thảo (giới hạn đã ghi nhận, không sửa BL-104 vì ngoài phạm vi BL-220).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BanHanhVbdiUseCase {

    public static final String DOCUMENT_TYPE = "vbdi";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String NOT_PENDING_MESSAGE = "Văn bản chưa trình ký, không thể ban hành";
    private static final String DOCUMENT_NUMBER_FIELD = "document_number";
    private static final String PROMULGATION_DATE_FIELD = "promulgation_date";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentNumberingService documentNumberingService;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;

    public Result<String> execute(BanHanhVbdiRequest request) {
        var documentOptional = vbdiDocumentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (!VbdiStatus.PENDING_SIGNATURE.equals(document.getStatus())) {
            return Result.failure(NOT_PENDING_MESSAGE);
        }

        var before = buildSnapshot(document);
        Map<String, Object> fields = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        String documentNumber = documentNumberingService.generateNumber(DOCUMENT_TYPE).orElse(null);
        if (documentNumber != null) {
            fields.put(DOCUMENT_NUMBER_FIELD, documentNumber);
        }
        String promulgationDate = (request.getPromulgationDate() == null || request.getPromulgationDate().isBlank())
            ? LocalDate.now().toString()
            : request.getPromulgationDate();
        fields.put(PROMULGATION_DATE_FIELD, promulgationDate);

        document.replaceDynamicFields(toJson(fields));
        document.update(null, null, VbdiStatus.ISSUED, null, null, null, null);
        var saved = vbdiDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        return Result.success(documentNumber);
    }

    private Map<String, Object> buildSnapshot(VbdiDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
