package com.td.infrastructure.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.documents.BusinessDocument;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bổ sung 04/09/2026 (yêu cầu người dùng: "Tệp đính kèm: khi gắn tệp đính kèm lưu luôn thông tin
 * files vào dynamic_fields để không phải đọc lại DB để map, nếu không có mới đọc lại (văn bản đi đã
 * làm như vậy)"): PORT Y HỆT {@link VbdiFilesJsonbSync} (28/08/2026, cùng lý do — chuẩn bị cho
 * Elasticsearch index nguyên {@code dynamic_fields}, list/search không cần join lại
 * {@code file_metadata}) sang {@code vbden}.
 *
 * <p><b>Khác {@link VbdiFilesJsonbSync} ĐÚNG 1 điểm kiến trúc</b>: {@code vbdi} có bảng vật lý RIÊNG
 * ({@code vbdi_documents}/{@link com.td.application.vbdi.VbdiDocumentRepository}) nên "tìm thấy qua
 * repository đó" ĐÃ ĐỦ để biết đây là 1 văn bản đi — không cần so tên. {@code vbden} KHÔNG có bảng
 * riêng (dùng chung {@code documents}/{@link DocumentRepository} với NHIỀU document_type khác —
 * {@code vbden_res}, {@code vbden_so}, {@code vbden_xinykien}, {@code vbcddh}...) nên PHẢI kiểm tra
 * thêm {@code documentType.equalsIgnoreCase("vbden")} tường minh trước khi ghi, tránh đồng bộ nhầm
 * field {@code files} vào các document_type khác đi qua CÙNG bảng.</p>
 *
 * <p>Ghi ĐÈ TOÀN BỘ mảng {@code files} (không patch từng phần tử), cùng shape
 * {@code {name,size,type,file_id}} đã dùng ở {@code GetVbdenDetailUseCase#collectFiles}/
 * {@code GetVbdiDetailUseCase#toFileMap} — giữ NHẤT QUÁN 1 shape cho "files" dù đọc từ
 * {@code dynamic_fields} (list/search) hay từ {@code ListDocumentFilesUseCase} (xem chi tiết, vẫn
 * đọc DB trực tiếp — {@code dynamic_fields.files} chỉ là BẢN SAO đã tính sẵn, {@code file_metadata}
 * vẫn là nguồn sự thật, KHÔNG đổi).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class VbdenFilesJsonbSync {

    private static final String VBDEN_DOCUMENT_TYPE = "vbden";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentRepository documentRepository;
    private final FileMetadataJpaRepository fileMetadataRepo;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    void syncIfVbdenDocument(UUID documentId) {
        if (documentId == null) {
            return;
        }
        try {
            BusinessDocument document = documentRepository.findById(documentId).orElse(null);
            if (document == null || !VBDEN_DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
                return;
            }
            List<FileMetadataEntity> entities = fileMetadataRepo.findAllByDocumentId(documentId);
            List<Map<String, Object>> files = entities.stream().map(this::toFileMap).toList();

            Map<String, Object> fields = new LinkedHashMap<>(
                dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
            fields.put("files", files);
            document.replaceDynamicFields(toJson(fields));
            documentRepository.save(document);
        } catch (Exception ex) {
            // Khong lam vo luong upload/xoa tep chi vi dong bo JSONB that bai (best-effort cache) -
            // ghi log de phat hien, du lieu file_metadata (nguon su that) van dung.
            log.warn("Dong bo dynamic_fields.files cho vbden document {} that bai", documentId, ex);
        }
    }

    private Map<String, Object> toFileMap(FileMetadataEntity entity) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", entity.getOriginalFilename());
        file.put("size", entity.getFileSize());
        file.put("type", entity.getFileExtension());
        file.put("file_id", entity.getId());
        // Yeu cau nguoi dung 15/09/2026 (is_primary that - xem javadoc FileMetadataEntity#isPrimary):
        // giu dung shape voi toFileMap o GetVbdenDetailUseCase/ListDocumentFilesUseCaseImpl.
        file.put("is_primary", Boolean.TRUE.equals(entity.getIsPrimary()));
        return file;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
