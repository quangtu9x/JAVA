package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("xem lại khi hiệu chỉnh và thao tác lưu đang gọi api domino, port
 * luôn"): "Hiệu chỉnh" (cập nhật văn bản đi đã có) — tương đương Ý NGHĨA với
 * {@code qlvb/add?id=<id>&databasetype=vbdi} (Domino, {@code ChiTietModal.jsx#onFinish} nhánh id có
 * sẵn) — trước bản vá này Java HOÀN TOÀN chưa có endpoint update cho vbdi (chỉ có
 * {@link SoanThaoVbdiUseCase}, tạo MỚI). Đây là gap THẬT SỰ nghiêm trọng: văn bản tạo qua Java
 * (UUID Postgres) không có bản ghi tương ứng bên Domino — gọi {@code qlvb/add?id=<uuid>} luôn thất
 * bại (Domino không nhận ra id) — nghĩa là MỌI văn bản tạo mới qua Java (từ Slice 1 follow-up "Lưu"
 * 28/08/2026) KHÔNG THỂ hiệu chỉnh được cho tới bản vá này.
 *
 * <p><b>Partial update (merge), KHÔNG replace toàn bộ</b> — đúng cách FE tự tính diff qua
 * {@code getChangedFieldDetails} trước khi gọi (chỉ gửi field ĐÃ ĐỔI): đọc {@code dynamic_fields}
 * hiện có, {@code putAll} field mới đè lên, ghi lại toàn bộ map — cùng khuôn
 * {@code UpdateDocumentUseCase} (bảng {@code documents} dùng chung, BL-101) và
 * {@code TrinhKyVbdiUseCase#updateDocumentStatus} (đã áp dụng cho chính bảng {@code vbdi_documents}
 * này).</p>
 *
 * <p><b>status</b>: dịch qua {@link VbdiLegacyStatusCodes} (mã Domino thô "0"/"2" ↔ chữ nội bộ) —
 * request KHÔNG gửi field này (null/rỗng) nghĩa là GIỮ NGUYÊN trạng thái hiện tại (khác
 * {@link SoanThaoVbdiUseCase}, nơi rỗng mặc định DRAFT vì đó là tạo mới). CỐ Ý CHO PHÉP đổi status
 * qua đường Hiệu chỉnh (không guard) — giữ ĐÚNG hành vi FE hiện tại
 * ({@code ChiTietModal.jsx#onFinish} tự set {@code status="2"} khi hiệu chỉnh 1 bản nháp, mô phỏng
 * luồng ban hành đơn giản hoá của BL-220, xem javadoc {@link SoanThaoVbdiUseCase}) — KHÔNG mở rộng
 * thêm rủi ro (vd guard chặn chuyển ISSUED→DRAFT) ngoài phạm vi câu hỏi lần này; nếu cần state
 * machine chặt chẽ hơn, đã có sẵn {@link TrinhKyVbdiUseCase}/{@link BanHanhVbdiUseCase} cho luồng
 * chính thức.</p>
 *
 * <p><b>CHƯA port trong lần này</b> (side-effect khác của {@code onFinish}, vẫn gọi Domino, ngoài
 * phạm vi câu hỏi — chỉ nhắm "thao tác lưu"): {@code kiemtrungvanban} (kiểm tra trùng số trước khi
 * lưu — gap đã ghi nhận ở Slice 1 follow-up (2)), ghi log chỉnh sửa qua
 * {@code getChangedFieldDetails} (form "log" Domino), {@code capnhatVBden} ("vào sổ phản hồi văn
 * bản đến"), cập nhật trạng thái dự thảo ({@code qlvb/vbdi/dabanhanhduthao}). {@code updateSoVanBan}
 * (tăng sổ văn bản) và "Gửi văn bản" (khi bấm "Lưu và Gửi") ĐÃ port từ trước, không cần đụng thêm.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class HieuChinhVbdiUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final AuthorAclFieldsService authorAclFieldsService;
    private final com.td.application.documentlinks.RelatedDocumentSnapshots relatedDocumentSnapshots;

    public Result<UUID> execute(HieuChinhVbdiRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id văn bản đi cần hiệu chỉnh");
        }

        var documentOptional = vbdiDocumentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        if (!SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        var before = buildSnapshot(document);

        // Merge (khong replace) dynamic_fields - dung buildDynamicFieldsJson de ep dung kieu du
        // lieu (date/number/richtext) cho phan diff FE gui, roi de len map hien co.
        Map<String, Object> existingFields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        // 06/10/2026 (bang document_links giai doan 2 - docs/architecture/document-links.md): documentin_obj /
        // vanban_lienquan_obj KHONG luu trong dynamic_fields nua - bo khoi payload.
        Map<String, String> incomingFields = new LinkedHashMap<>(request.getFields());
        relatedDocumentSnapshots.strip(incomingFields);
        String incomingJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
            incomingFields, request.getDateFields(), request.getNumberFields(), request.getRichtextFields());
        existingFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(incomingJson));
        authorAclFieldsService.applyOnUpdate(
            SoanThaoVbdiUseCase.DOCUMENT_TYPE, existingFields, incomingFields.keySet());
        document.replaceDynamicFields(toJson(existingFields));

        String title = (request.getSubject() == null || request.getSubject().isBlank())
            ? null : request.getSubject().trim();
        String status = VbdiLegacyStatusCodes.toInternalOrNull(request.getStatus());
        document.update(title, null, status, null, null, null, null);

        var saved = vbdiDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        log.info("Hieu chinh van ban di {}", saved.getId());
        return Result.success(saved.getId());
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
