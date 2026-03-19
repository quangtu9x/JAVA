package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncDocumentSearchByIdUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentSearchService documentSearchService;

    public Result<String> execute(UUID documentId) {
        try {
            if (documentId == null) {
                return Result.failure("Document ID không được để trống");
            }

            if (!documentSearchService.isEnabled()) {
                return Result.failure("Elasticsearch đang bị tắt bởi cấu hình app.elasticsearch.enabled=false");
            }

            if (!documentSearchService.isAvailable()) {
                return Result.failure("Elasticsearch chưa sẵn sàng hoặc index chưa được khởi tạo. Hãy chạy reindex trước");
            }

            var documentOptional = documentRepository.findById(documentId);
            if (documentOptional.isEmpty()) {
                documentSearchService.delete(documentId);
                return Result.success("Không tìm thấy document trong DB. Đã xóa bản ghi (nếu có) khỏi Elasticsearch");
            }

            var document = documentOptional.get();
            if (document.isDeleted()) {
                documentSearchService.delete(documentId);
                return Result.success("Document đang ở trạng thái deleted. Đã xóa khỏi Elasticsearch index");
            }

            documentSearchService.index(document);
            return Result.success("Đồng bộ document lên Elasticsearch thành công");
        } catch (Exception ex) {
            return Result.failure("Đồng bộ document lên Elasticsearch thất bại: " + ex.getMessage());
        }
    }
}
