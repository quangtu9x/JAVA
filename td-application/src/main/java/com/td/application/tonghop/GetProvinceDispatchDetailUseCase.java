package com.td.application.tonghop;

import com.td.application.common.models.Result;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Chi tiết 1 văn bản đi toàn tỉnh: thông tin văn bản + từng nơi nhận + dòng thời gian sự kiện. */
@Service
@RequiredArgsConstructor
public class GetProvinceDispatchDetailUseCase {

    private final ProvinceQueryRepository provinceQueryRepository;
    private final ProvinceScopePolicy provinceScopePolicy;

    public Result<Detail> execute(String tenantId, UUID documentId) {
        if (tenantId == null || tenantId.isBlank() || documentId == null) {
            return Result.failure("Thiếu tenantId/documentId");
        }
        ProvinceScope scope = provinceScopePolicy.current();
        if (!scope.isResolved()) {
            return Result.failure(SearchProvinceDocumentsUseCase.SCOPE_UNRESOLVED);
        }
        if (!scope.covers(tenantId)) {
            // Văn bản đơn vị khác: trả như không tìm thấy, không xác nhận sự tồn tại.
            return Result.failure("Không tìm thấy văn bản trong kho tổng hợp (có thể chưa đồng bộ)");
        }
        var document = provinceQueryRepository.findDocument(tenantId, documentId);
        if (document.isEmpty()) {
            return Result.failure("Không tìm thấy văn bản trong kho tổng hợp (có thể chưa đồng bộ)");
        }
        return Result.success(Detail.builder()
            .document(document.get())
            .recipients(provinceQueryRepository.dispatchRecipients(tenantId, documentId))
            .build());
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {
        private ProvinceDocumentItem document;
        private List<ProvinceDispatchRecipientItem> recipients;
    }
}
