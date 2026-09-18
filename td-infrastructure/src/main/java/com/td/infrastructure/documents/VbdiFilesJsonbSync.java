package com.td.infrastructure.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.vbdi.VbdiDocument;
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
 * Yêu cầu người dùng 28/08/2026 ("tương lai sẽ chuyển JSONB sang Elasticsearch để tìm kiếm, muốn
 * lưu cả files vào JSONB để khi tìm kiếm kết quả chỉ hiển thị, không phải gọi ngược vào database để
 * lấy danh sách"): sau mỗi lần tải lên/xoá tệp, ĐỒNG BỘ LẠI toàn bộ danh sách tệp hiện có của 1
 * văn bản đi vào {@code vbdi_documents.dynamic_fields.files} — {@code file_metadata} (bảng riêng)
 * vẫn là NGUỒN SỰ THẬT (không đổi), {@code dynamic_fields.files} chỉ là BẢN SAO đã tính sẵn để mọi
 * nơi đọc {@code dynamic_fields} nguyên khối (list/search qua {@code ViewQueryService
 * #toFilteredDocumentFields}, và sau này Elasticsearch index nguyên {@code dynamic_fields}) có
 * ngay danh sách tệp mà KHÔNG cần join/gọi lại {@code file_metadata}.
 *
 * <p>Ghi ĐÈ TOÀN BỘ mảng {@code files} (không patch từng phần tử) — đơn giản, luôn đúng theo
 * {@code file_metadata} tại thời điểm gọi, chấp nhận 1 lượt query {@code findAllByDocumentId} mỗi
 * lần tải lên/xoá (số tệp/1 văn bản luôn nhỏ). CHỈ áp dụng cho {@code vbdi} (phạm vi yêu cầu lần
 * này) — {@code documentId} không khớp bản ghi nào trong {@code vbdi_documents} thì bỏ qua im lặng
 * (tệp thuộc module khác, chưa mở rộng đồng bộ tương tự).</p>
 *
 * <p>KHÔNG dùng {@code ListDocumentFilesUseCase}/{@code FileDto} (shape dùng cho API công khai,
 * nhiều field không cần) — đọc thẳng {@link FileMetadataEntity} qua {@link FileMetadataJpaRepository}
 * đã có sẵn ở {@code UploadFileUseCaseImpl}/{@code DeleteFileUseCaseImpl}, tránh thêm tầng gọi.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class VbdiFilesJsonbSync {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final FileMetadataJpaRepository fileMetadataRepo;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    void syncIfVbdiDocument(UUID documentId) {
        if (documentId == null) {
            return;
        }
        try {
            VbdiDocument document = vbdiDocumentRepository.findById(documentId).orElse(null);
            if (document == null) {
                return;
            }
            List<FileMetadataEntity> entities = fileMetadataRepo.findAllByDocumentId(documentId);
            List<Map<String, Object>> files = entities.stream().map(this::toFileMap).toList();

            Map<String, Object> fields = new LinkedHashMap<>(
                dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
            fields.put("files", files);
            document.replaceDynamicFields(toJson(fields));
            vbdiDocumentRepository.save(document);
        } catch (Exception ex) {
            // Khong lam vo luong upload/xoa tep chi vi dong bo JSONB that bai (best-effort cache) -
            // ghi log de phat hien, du lieu file_metadata (nguon su that) van dung.
            log.warn("Dong bo dynamic_fields.files cho vbdi_documents {} that bai", documentId, ex);
        }
    }

    // Cung shape {name,size,type,file_id} da dung o GetVbdiDetailUseCase#toFileMap (khop
    // Files/filesVBdi.jsx dang doc resdata?.files) - giu NHAT QUAN 1 shape duy nhat cho "files" du
    // doc tu dynamic_fields (list/search) hay tu ListDocumentFilesUseCase (xem chi tiet).
    private Map<String, Object> toFileMap(FileMetadataEntity entity) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", entity.getOriginalFilename());
        file.put("size", entity.getFileSize());
        file.put("type", entity.getFileExtension());
        file.put("file_id", entity.getId());
        // Yeu cau nguoi dung 15/09/2026 (is_primary that - xem javadoc FileMetadataEntity#isPrimary):
        // giu dung shape voi toFileMap o GetVbdiDetailUseCase/ListDocumentFilesUseCaseImpl.
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
