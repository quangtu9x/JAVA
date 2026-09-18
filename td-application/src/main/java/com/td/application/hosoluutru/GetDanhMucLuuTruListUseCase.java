package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port module "Hồ sơ lưu trữ" — danh sách 1 loại danh mục dùng chung ({@code kyhieuhoso}/
 * {@code chatluong}/{@code loaihinhtailieu}), tương đương view Domino {@code danh_muc_chung}
 * (categorized theo {@code nameform}) — xem {@code docs/analysis/16-...md} mục 6.5.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDanhMucLuuTruListUseCase {

    private final HoSoLuuTruDanhMucDocumentRepository hoSoLuuTruDanhMucDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<List<Map<String, Object>>> execute(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return Result.failure("Thiếu documentType (nameform)");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var document : hoSoLuuTruDanhMucDocumentRepository.findByDocumentType(documentType.trim().toLowerCase())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", document.getId());
            item.put("name", document.getTitle());
            item.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
            result.add(item);
        }
        return Result.success(result);
    }
}
