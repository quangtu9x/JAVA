package com.td.application.tonghop;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc kho tổng hợp toàn tỉnh (bảng {@code province_*} trong control-plane). Chỉ đọc — dữ liệu do
 * {@link ProvinceSyncService} ghi.
 */
public interface ProvinceQueryRepository {

    ProvinceDocumentSearchResult searchDocuments(ProvinceDocumentSearchRequest request, LocalDate from, LocalDate to,
                                                 int offset, int limit);

    ProvinceStatisticsResult statistics(LocalDate from, LocalDate to);

    ProvinceDispatchPage searchDispatchDocuments(ProvinceDispatchSearchRequest request, LocalDate from, LocalDate to,
                                                 int lateArrivalMinutes, int lateAcceptHours, int offset, int limit);

    ProvinceDispatchSummary dispatchSummary(ProvinceDispatchSearchRequest request, LocalDate from, LocalDate to,
                                            int lateArrivalMinutes, int lateAcceptHours);

    List<ProvinceDispatchRecipientItem> dispatchRecipients(String tenantId, UUID vbdiId);

    Optional<ProvinceDocumentItem> findDocument(String tenantId, UUID documentId);

    record ProvinceDispatchPage(List<ProvinceDispatchDocumentItem> items, long totalItems) {
    }
}
