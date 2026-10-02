package com.td.application.hosocv;

import com.td.application.documents.VbdenOrgViewFilter;
import com.td.domain.hosocv.HoSoCVDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 02/10/2026: danh sách "văn bản khác" (xem {@link LuuVanBanKhacUseCase}) của 1 hồ sơ, mới tạo
 * trước — cùng điều kiện view {@code vwOther_Related_Document_Document_Profiles_ID} của
 * {@code ViewQueryService}. Dùng chung cho {@link GetHoSoChiTietUseCase} (van_ban_lien_quan) và
 * {@link DownloadFilesHoSoCVUseCase} (tải toàn bộ tệp hồ sơ).
 */
@Component
@RequiredArgsConstructor
public class HoSoVanBanKhacFinder {

    // 1 hồ sơ thực tế chỉ vài chục văn bản khác - giới hạn an toàn cho 1 lần đọc không phân trang.
    private static final int MAX_ITEMS = 500;

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;

    public List<HoSoCVDocument> findByHoSo(UUID hosocvId) {
        if (hosocvId == null) {
            return List.of();
        }
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of(LuuVanBanKhacUseCase.FIELD_DOCUMENT_PROFILES_ID))
            .orgValue(hosocvId.toString().toLowerCase(Locale.ROOT))
            .newestCreatedFirst(true)
            .build();
        return hoSoCVDocumentRepository
            .findByVbdenOrgView(LuuVanBanKhacUseCase.DOCUMENT_TYPE, filter, PageRequest.of(0, MAX_ITEMS))
            .getContent();
    }
}
