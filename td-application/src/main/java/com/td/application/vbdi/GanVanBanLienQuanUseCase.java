package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 11/09/2026 (yêu cầu người dùng "modal Gán văn bản đến và Gán văn bản đi đang lỗi tương tự" — Java
 * CHƯA có, FE chưa cutover nên vẫn gọi Domino {@code qlvb/add?id={vbdiId}&databasetype=vbdi} lưu field
 * {@code vanban_lienquan_obj}): port hợp nhất 2 action {@code ganvanbanden}/{@code ganvanbandi} của
 * {@code GanVanBanModal.jsx} (cùng field, chỉ khác {@code database_type} FE tự gắn vào từng phần tử —
 * xem javadoc {@link GanVanBanLienQuanRequest}).
 *
 * <p><b>Xác nhận qua đọc TRỰC TIẾP Domino gốc</b> (khác {@code documentin_obj}/{@code ganduthaotraloi}
 * — đã port ở {@link TraLoiVanBanDenUseCase}): route {@code qlvb/add} luôn đổ vào
 * {@code TDDocument.java#CreateOrUpdateDocument()} — biến {@code actionname} CHỈ được rẽ nhánh đặc biệt
 * đúng 1 chỗ trong file này ({@code "addlogseendocument"}), KHÔNG có nhánh nào cho
 * {@code "ganvanbanden"}/{@code "ganvanbandi"}. Field {@code vanban_lienquan_obj} KHÔNG hề xuất hiện
 * trong {@code TDDocument.java} — nó được lưu HOÀN TOÀN GENERIC như mọi field text khác gửi lên từ
 * form, KHÔNG có write-back nào lên các văn bản được tham chiếu, KHÔNG đụng
 * {@code user_process}/{@code user_processing}, KHÔNG tạo bản ghi {@code document_process} con nào.</p>
 *
 * <p>Side-effect DUY NHẤT liên quan tới field này trong toàn bộ Domino xảy ra SAU NÀY, ở bước GỬI văn
 * bản đi ({@code SendDocument.java}, các hàm {@code copy_Document_Vbden_NoiBo}/
 * {@code copy_Document_Vbden_NoiBo_CAY}) — CHỈ copy FILE đính kèm từ văn bản liên quan sang kho file
 * của văn bản đi/bản sao nội bộ (qua {@code TDUlties.copyFileVanBanLienQuan}), và copy nguyên field
 * JSON này (không parse) sang bản ghi "DocumentIn" tạo ở đơn vị nhận khi gửi nội bộ
 * ({@code DraftToDocumentOut.java} cũng copy nguyên field này khi chuyển dự thảo thành văn bản đi).
 * CHƯA port phần "copy file khi gửi" này (ngoài phạm vi yêu cầu — thuộc luồng {@code SendDocument}, xem
 * {@link SendVbdiDocumentUseCase}, chưa có bằng chứng người dùng cần) — chỉ port đúng phần "lưu field
 * khi bấm Đồng ý ở modal", đúng yêu cầu.</p>
 *
 * <p><b>Không mở lại văn bản được tham chiếu</b>: khác {@link TraLoiVanBanDenUseCase} (phải mở từng văn
 * bản đến để chuyển {@code user_process}), ở đây KHÔNG cần — field chỉ là danh sách snapshot hiển thị
 * trên chính văn bản đi, {@code id} của văn bản liên quan có thể là UUID Postgres hoặc UNID Domino (32
 * hex), server không ép kiểu/không cần tồn tại thật (đúng Domino gốc — không xác thực lại văn bản được
 * tham chiếu khi lưu).</p>
 *
 * <p>Dedup theo {@code id} (server tự dedup lại, không tin FE đã lọc trùng đúng — cùng quy ước
 * {@link TraLoiVanBanDenUseCase#execute}) — gọi lại nhiều lần chỉ nối thêm phần tử MỚI, không nhân đôi.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GanVanBanLienQuanUseCase {

    private static final String RELATED_FIELD = "vanban_lienquan_obj";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(GanVanBanLienQuanRequest request) {
        if (request == null || request.getVbdiId() == null) {
            return Result.failure("Thieu van ban di can gan");
        }
        if (request.getRelatedDocuments() == null || request.getRelatedDocuments().isEmpty()) {
            return Result.failure("Chua chon van ban can gan");
        }

        var vbdiOptional = vbdiDocumentRepository.findById(request.getVbdiId());
        if (vbdiOptional.isEmpty() || !isUsableVbdiDocument(vbdiOptional.get())) {
            return Result.failure("Khong tim thay van ban di");
        }
        var vbdiDocument = vbdiOptional.get();

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(vbdiDocument.getDynamicFieldsJson()));
        List<Object> existingList = toObjectList(fields.get(RELATED_FIELD));
        Set<String> existingIds = new LinkedHashSet<>();
        for (Object item : existingList) {
            if (item instanceof Map<?, ?> map && map.get("id") != null) {
                existingIds.add(map.get("id").toString());
            }
        }

        var before = buildSnapshot(vbdiDocument);
        int added = 0;
        for (Map<String, Object> item : request.getRelatedDocuments()) {
            Object rawId = item == null ? null : item.get("id");
            if (rawId == null || rawId.toString().isBlank()) {
                continue;
            }
            String idText = rawId.toString().trim();
            if (existingIds.add(idText)) {
                Map<String, Object> entry = new LinkedHashMap<>(item);
                entry.put("id", idText);
                existingList.add(entry);
                added++;
            }
        }

        if (added == 0) {
            // Toan bo da co san (dedup het) - khong co gi de luu, tra ve thanh cong (dung Domino goc,
            // khong bao loi khi bam lai "Dong y" voi cung 1 van ban da gan).
            return Result.success(vbdiDocument.getId());
        }

        fields.put(RELATED_FIELD, existingList);
        vbdiDocument.replaceDynamicFields(toJson(fields));
        var saved = vbdiDocumentRepository.save(vbdiDocument);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        log.info("Gan {} van ban lien quan vao van ban di {}", added, saved.getId());
        return Result.success(saved.getId());
    }

    private boolean isUsableVbdiDocument(VbdiDocument document) {
        return !document.isDeleted()
            && SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType());
    }

    private Map<String, Object> buildSnapshot(VbdiDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private List<Object> toObjectList(Object value) {
        List<Object> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            result.addAll(list);
        }
        return result;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
