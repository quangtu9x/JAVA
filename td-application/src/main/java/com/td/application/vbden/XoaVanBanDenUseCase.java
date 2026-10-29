package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.realtime.RealtimeNotification;
import com.td.application.realtime.RealtimeNotifier;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 14/09/2026 ("ở chi tiết văn bản đến, chức năng Xóa đang gọi API cũ ... kiểm
 * tra nếu chưa có thì port sang JAVA và cutoverFE" — network thấy gọi {@code qlvb/vbden/XoaVanBanDen}
 * + {@code qlvb/vbden/CapNhatVanBanDen}) — đối chiếu trực tiếp {@code XoaVanBanDen.java} thật
 * (package {@code vn.com.tandan.xsp.services.vbden}), nút "Xóa" ở {@code VBDenButtonActionVT.jsx}
 * ({@code handleButton("delete", ...)}), gọi kèm {@code capnhatVBden(..., "xoavanbanden", ...)} —
 * nhánh {@code action="xoavanbanden"} của {@code CapNhatVanBanDen.java} thật KHÔNG có xử lý riêng
 * (rơi vào {@code default} rỗng) — chỉ tạo 1 dòng lịch sử/audit-log chung (giống MỌI action khác đi
 * qua {@code CapNhatVanBanDen}) qua {@code CreateOrUpdateDocument()} — không cần port riêng, đã có
 * hạ tầng tương đương ({@link DocumentLogService#logDelete}).
 *
 * <p><b>Kiến trúc 2-document (đúng Domino, xem javadoc {@link TiepNhanVbdenUseCase}/
 * {@link GetSoVanBanDenUseCase})</b>: {@code idvbden} = văn bản NỘI DUNG dùng chung
 * ({@code documentType="vbden"}), {@code idso} = bản ghi "sổ" riêng của 1 lần "vào sổ"
 * ({@code documentType="vbden_so"}, field {@code parentid} trỏ về {@code idvbden}) — 1 nội dung có
 * thể được nhiều đơn vị "vào sổ" (nhiều bản ghi {@code vbden_so} cùng trỏ 1 {@code idvbden}).</p>
 *
 * <p><b>Đối chiếu {@code XoaVanBanDen.java} thật (từng dòng)</b>:</p>
 * <ul>
 *   <li>Chỉ soft-delete văn bản NỘI DUNG ({@code idvbden}) khi (a) {@code identifier_creator} của
 *   nó KHỚP identifier hiện tại (resolve qua {@link ResolveIdentifierByLevelUseCase}, đúng thuật
 *   toán {@code get_Identifier_user_by_level} — KHÔNG dùng field phẳng), VÀ (b) đây là bản ghi
 *   {@code vbden_so} DUY NHẤT (hoặc không còn cái nào khác) của CÙNG identifier đó còn trỏ tới nội
 *   dung này ({@code countso <= 1} — đếm qua view {@code sovanban_by_idvbden} thật, Java dùng
 *   {@link DocumentRepository#findByParentId} lọc thêm theo {@code identifier_creator}) — tránh xoá
 *   nội dung còn đang được đơn vị SỞ HỮU dùng ở 1 sổ khác.</li>
 *   <li>LUÔN thử soft-delete bản ghi "sổ" ({@code idso}) — KHÔNG điều kiện gì (không cần khớp
 *   identifier, không cần văn bản nội dung có xoá được hay không) — mỗi đơn vị nhận có thể tự xoá
 *   dòng "sổ" của riêng mình khỏi danh sách bất kể có phải chủ sở hữu nội dung hay không.</li>
 *   <li><b>Ghi chú hành vi Domino gốc (đã đối chiếu kỹ, KHÔNG phải suy đoán)</b>: {@code doService()}
 *   thật LUÔN gọi {@code msg.setStatus(true)} ngay sau {@code processRequest()} trả về không lỗi
 *   (dòng 30-32) — ĐÈ LÊN mọi {@code msg.setStatus(false)} đã set bên trong (vd. nhánh "không có
 *   quyền xoá"). Nghĩa là response CHỈ báo lỗi khi có exception thật sự (vd. UNID sai định dạng) —
 *   không có quyền/không tìm thấy văn bản vẫn trả {@code status=true}. Port giữ ĐÚNG hành vi này:
 *   {@link #execute} chỉ trả {@link Result#failure} khi thiếu {@code idvbden} hoặc có exception,
 *   mọi trường hợp "bỏ qua" (không có quyền, không tìm thấy, đã xoá trước đó) đều coi là thành công
 *   (idempotent) — KHÔNG lộ ra field phụ kiểu {@code "khong co quyen xoa"} (không FE nào đọc field
 *   đó, {@code VBDenButtonActionVT.jsx} chỉ check {@code res?.status}).</li>
 * </ul>
 *
 * <p>Không port {@code delete_date}/{@code delete_people}/{@code delete_people_fullname} riêng
 * (Domino, đổi tên {@code Form} sang hậu tố {@code _Deleted}) — dùng ĐÚNG cơ chế soft-delete chung
 * {@link com.td.domain.common.contracts.ISoftDelete#markAsDeleted} (cột {@code deleted_on}/
 * {@code deleted_by}), cùng quy ước {@code DeleteVbdiDocumentUseCase} (BL-220) — không tạo field
 * động riêng chỉ để mô phỏng lại cách Domino đổi tên Form.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class XoaVanBanDenUseCase {

    private static final String DOCUMENT_TYPE = TiepNhanVbdenUseCase.DOCUMENT_TYPE;
    private static final String REGISTER_ENTRY_DOCUMENT_TYPE = TiepNhanVbdenUseCase.REGISTER_ENTRY_DOCUMENT_TYPE;
    private static final String IDENTIFIER_CREATOR_FIELD = "identifier_creator";
    // Gioi han thuc te so ban ghi "so" cua 1 noi dung (so don vi nhan) - khong co van ban nao thuc
    // te vuot qua nguong nay, tranh phai phan trang khi dem.
    private static final int MAX_REGISTER_ENTRIES_PER_PARENT = 500;

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final RealtimeNotifier realtimeNotifier;

    public Result<UUID> execute(XoaVanBanDenRequest request) {
        if (request == null || request.getIdvbden() == null) {
            return Result.failure("Thiếu id văn bản đến");
        }
        try {
            UUID deletedBy = resolveCurrentUserId();
            deleteParentIfOwnedAndLast(request.getIdvbden(), deletedBy);
            if (request.getIdso() != null) {
                deleteRegisterEntry(request.getIdso(), deletedBy);
            }
            return Result.success(request.getIdvbden());
        } catch (Exception ex) {
            log.error("Xoa van ban den that bai", ex);
            return Result.failure("Xóa văn bản đến thất bại: " + ex.getMessage());
        }
    }

    // XoaVanBanDen.java that (dong 84-134): chi xoa noi dung dung chung khi dung nguoi so huu
    // (identifier_creator) VA day la ban ghi so CUOI CUNG cua don vi do.
    private void deleteParentIfOwnedAndLast(UUID idvbden, UUID deletedBy) {
        var documentOptional = documentRepository.findById(idvbden);
        if (documentOptional.isEmpty()) {
            return;
        }
        var document = documentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType()) || document.isDeleted()) {
            return;
        }

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        String identifierCreator = asString(fields.get(IDENTIFIER_CREATOR_FIELD));
        String currentIdentifier = resolveIdentifierByLevelUseCase.resolveForCurrentUser().orElse(null);
        if (identifierCreator == null || identifierCreator.isBlank() || !identifierCreator.equals(currentIdentifier)) {
            return;
        }
        if (countRegisterEntriesForOwner(idvbden, identifierCreator) > 1) {
            return;
        }

        var before = buildSnapshot(document);
        document.markAsDeleted(deletedBy);
        var saved = documentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logDelete(saved.getId(), before);
        // XoaVanBanDen.java dong 84-130: báo danh sách author của văn bản.
        realtimeNotifier.notify(RealtimeNotification.of(DOCUMENT_TYPE, "xoa", saved.getId(),
                java.util.Objects.toString(fields.get("code"), null))
            .toValues(fields.get("author"))
            .exclude(currentUserProvider.currentUsername().orElse("")));
    }

    private long countRegisterEntriesForOwner(UUID idvbden, String identifierCreator) {
        var page = documentRepository.findByParentId(
            REGISTER_ENTRY_DOCUMENT_TYPE, idvbden.toString(), PageRequest.of(0, MAX_REGISTER_ENTRIES_PER_PARENT));
        return page.getContent().stream()
            .filter(entry -> identifierCreator.equals(asString(
                dynamicDocumentFieldsService.parseDynamicFields(entry.getDynamicFieldsJson()).get(IDENTIFIER_CREATOR_FIELD))))
            .count();
    }

    // XoaVanBanDen.java that (dong 143-162): luon thu xoa ban ghi "so" rieng, khong dieu kien gi them.
    private void deleteRegisterEntry(UUID idso, UUID deletedBy) {
        var entryOptional = documentRepository.findById(idso);
        if (entryOptional.isEmpty()) {
            return;
        }
        var entry = entryOptional.get();
        if (!REGISTER_ENTRY_DOCUMENT_TYPE.equalsIgnoreCase(entry.getDocumentType()) || entry.isDeleted()) {
            return;
        }
        var before = buildSnapshot(entry);
        entry.markAsDeleted(deletedBy);
        var saved = documentRepository.save(entry);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logDelete(saved.getId(), before);
    }

    // Ghi dung nguoi xoa that (BL-103 audit) - null khi khong xac dinh duoc user, cung quy uoc
    // DeleteVbdiDocumentUseCase.
    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
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
