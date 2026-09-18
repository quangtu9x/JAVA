package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương ứng ở
 * button của văn bản đi"): "Văn thư không ban hành" — đối chiếu trực tiếp
 * {@code vn.com.tandan.xsp.services.vbdi.XuLyDuThao#ChuyenTrangThaiDuThaoVanThuXoa()} (dòng
 * 66-118) — văn thư từ chối, KHÔNG ban hành văn bản đi; đánh dấu dự thảo cha ĐÃ HOÀN TẤT theo
 * hướng bị từ chối (khác {@link DuThaoDaBanHanhUseCase}, cùng đích {@code status} nhưng khác field
 * động đánh dấu kết quả — xem javadoc {@link DuThaoStatus#DOCUMENT_OUT_FINALIZED}).
 *
 * <p><b>Mutation ĐÚNG theo Domino thật</b> (dòng 84-85): field động {@code xoavanbandi} = chuỗi cố
 * định {@code "Văn thư không ban hành "} + thời điểm hiện tại ({@code dd/MM/yyyy HH:mm:ss}) — VÀ
 * cột tĩnh {@code status} → {@link DuThaoStatus#DOCUMENT_OUT_FINALIZED} ("8"). Domino KHÔNG nhận lý
 * do/opinion từ request — luôn ghi đúng chuỗi cố định này, giữ nguyên hành vi.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DuThaoVanThuKhongBanHanhUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản dự thảo";
    private static final String XOA_VAN_BAN_DI_FIELD = "xoavanbandi";
    private static final String REASON_PREFIX = "Văn thư không ban hành ";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;

    public Result<UUID> execute(DuThaoVanThuKhongBanHanhRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id dự thảo cần cập nhật");
        }
        var draftOptional = duThaoDocumentRepository.findById(request.getId());
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var draft = draftOptional.get();
        if (!ChuyenThanhVanBanDiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(draft.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        var before = buildSnapshot(draft);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson()));
        fields.put(XOA_VAN_BAN_DI_FIELD, REASON_PREFIX + TIMESTAMP_FORMATTER.format(LocalDateTime.now()));

        draft.replaceDynamicFields(toJson(fields));
        draft.update(null, null, DuThaoStatus.DOCUMENT_OUT_FINALIZED, null, null, null, null);
        var saved = duThaoDocumentRepository.save(draft);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        log.info("Van thu khong ban hanh - danh dau du thao {} hoan tat", saved.getId());
        return Result.success(saved.getId());
    }

    private Map<String, Object> buildSnapshot(DuThaoDocument document) {
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
