package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 14/09/2026 (đối xứng {@link XoaVanBanDenUseCase}, nút "Phục hồi văn bản" —
 * {@code VBDenButtonAction.jsx#handleRestore} — vốn đã gọi endpoint {@code qlvb/vbden/PhucHoiVanBan}
 * từ trước, chỉ là backend Java CHƯA tồn tại nên vẫn rơi vào Domino cũ; cần port cùng lúc với "Xóa"
 * vì 2 thao tác đối xứng — nếu chỉ port "Xóa" mà không port "Phục hồi", văn bản bị xoá qua đường Java
 * mới (chỉ đổi cột {@code deleted_on} ở Postgres) sẽ KHÔNG THỂ khôi phục được nữa qua nút "Phục hồi"
 * cũ (nút đó đọc/ghi NSF Domino, không còn thấy gì để phục hồi)) — đối chiếu {@code PhucHoiVanBan.java}
 * thật (package {@code vn.com.tandan.xsp.services.vbden}).
 *
 * <p><b>Khác biệt CÓ CHỦ ĐÍCH với {@code PhucHoiVanBan.java} thật</b>: Domino gốc gate quyền phục hồi
 * văn bản nội dung bằng {@code user_creator == username} (so username PHẲNG) — KHÁC hẳn
 * {@code XoaVanBanDen.java} dùng {@code identifier_creator} (so theo identifier tổ chức qua
 * {@code get_Identifier_user_by_level}). Đây là 2 field/thuật toán khác nhau trên CÙNG 1 khái niệm
 * "người/đơn vị sở hữu" — gần như chắc chắn là thiếu nhất quán của Domino gốc (2 servlet viết ở 2
 * thời điểm khác nhau), không phải chủ đích nghiệp vụ. Port này dùng THỐNG NHẤT
 * {@code identifier_creator} + {@link ResolveIdentifierByLevelUseCase} cho CẢ xoá lẫn phục hồi — vừa
 * nhất quán trong toàn bộ module vbden (mọi use case khác đều theo identifier tổ chức, không theo
 * username phẳng), vừa khớp với FE ({@code VBDenButtonAction.jsx#btnPhucHoi} đã tự gate hiển thị nút
 * theo {@code resdata.identifier_creator === currentUser.identifier}, không phải username).</p>
 *
 * <p>Không có "còn ai khác đang giữ" cần kiểm tra khi phục hồi (khác {@link XoaVanBanDenUseCase} có
 * {@code countso<=1}) — đúng Domino thật, phục hồi văn bản nội dung là vô điều kiện khi đã qua được
 * gate quyền. Bản ghi "sổ" ({@code idso}) LUÔN được thử phục hồi, không điều kiện gì thêm (đúng
 * Domino, cùng bất đối xứng như bên xoá).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PhucHoiVanBanDenUseCase {

    private static final String DOCUMENT_TYPE = TiepNhanVbdenUseCase.DOCUMENT_TYPE;
    private static final String REGISTER_ENTRY_DOCUMENT_TYPE = TiepNhanVbdenUseCase.REGISTER_ENTRY_DOCUMENT_TYPE;
    private static final String IDENTIFIER_CREATOR_FIELD = "identifier_creator";

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(PhucHoiVanBanDenRequest request) {
        if (request == null || request.getIdvbden() == null) {
            return Result.failure("Thiếu id văn bản đến");
        }
        try {
            restoreParentIfOwned(request.getIdvbden());
            if (request.getIdso() != null) {
                restoreRegisterEntry(request.getIdso());
            }
            return Result.success(request.getIdvbden());
        } catch (Exception ex) {
            log.error("Khoi phuc van ban den that bai", ex);
            return Result.failure("Khôi phục văn bản đến thất bại: " + ex.getMessage());
        }
    }

    private void restoreParentIfOwned(UUID idvbden) {
        var documentOptional = documentRepository.findById(idvbden);
        if (documentOptional.isEmpty()) {
            return;
        }
        var document = documentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType()) || !document.isDeleted()) {
            return;
        }

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        String identifierCreator = asString(fields.get(IDENTIFIER_CREATOR_FIELD));
        String currentIdentifier = resolveIdentifierByLevelUseCase.resolveForCurrentUser().orElse(null);
        if (identifierCreator == null || identifierCreator.isBlank() || !identifierCreator.equals(currentIdentifier)) {
            return;
        }

        var before = buildSnapshot(document);
        document.restore();
        var saved = documentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private void restoreRegisterEntry(UUID idso) {
        var entryOptional = documentRepository.findById(idso);
        if (entryOptional.isEmpty()) {
            return;
        }
        var entry = entryOptional.get();
        if (!REGISTER_ENTRY_DOCUMENT_TYPE.equalsIgnoreCase(entry.getDocumentType()) || !entry.isDeleted()) {
            return;
        }
        var before = buildSnapshot(entry);
        entry.restore();
        var saved = documentRepository.save(entry);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private Map<String, Object> buildSnapshot(BusinessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
