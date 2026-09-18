package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bổ sung 07/09/2026 (4, yêu cầu người dùng: "port luôn chức năng 'Không vào sổ'") — tương thích
 * {@code CapNhatVanBanDen.java} thật, hàm {@code khongvaoso()} (dispatch cho 3 action
 * {@code khongvaoso}/{@code tralai}/{@code tuchoi} — CHỈ port đúng {@code khongvaoso}, xem phân biệt
 * dưới):
 * <pre>
 * public void khongvaoso() {
 *     String identifier = TDDocumentUlties.getRequestAsString(req, "identifier", false);
 *     TDInit.setViewError(identifier, "captrengiao;quamangnoibo;lienthong", "vbden");
 *     TDDocumentUlties.removeToFieldListStringHideVBden(docParentDocument, "contact_unit_identifier", identifier);
 *     TDDocumentUlties.removeToFieldListStringHideVBden(docParentDocument, "unit_process", identifier);
 *     TDDocumentUlties.removeToFieldListStringHideVBden(docParentDocument, "unit_coordinate", identifier);
 *     TDDocumentUlties.removeToFieldListStringHideVBden(docParentDocument, "unit_to_read", identifier);
 *     TDDocumentUlties.addToFieldListStringHideVBden(docParentDocument, "unit_discard", identifier);
 *     TDDocumentUlties.addToFieldListStringHideVBden(docParentDocument, "user_discard", username);
 *     docParentDocument.save();
 * }
 * </pre>
 *
 * <p><b>Phân biệt với {@link TuChoiTiepNhanVbdenUseCase} (bugfix 07/09/2026 (3) — 2 chức năng bị
 * NHẦM LẪN/GỘP CHUNG ở lần port đầu tiên, người dùng mô tả lại rõ ràng để tách đúng)</b>: FE THẬT có
 * ÍT NHẤT 2 màn hình dùng {@code action=khongvaoso} theo 2 CÁCH KHÁC HẲN NHAU:</p>
 * <ul>
 *   <li><b>{@code ItemsListTuChoi.jsx}/{@code ItemsList.jsx} (chovaoso, nút "Từ chối"/"Trả lại")</b>:
 *   gọi {@code ReplyDocumentOut} (đơn vị `vbdi`, publish {@code response_status}) TRƯỚC, rồi mới gọi
 *   {@code CapNhatVanBanDen?action=khongvaoso} — 2 endpoint Domino RIÊNG cho CÙNG 1 nút bấm. Đây là
 *   {@link TuChoiTiepNhanVbdenUseCase} (ĐÃ port, gộp thành 1 giao dịch, CÓ publish broker
 *   STATUS_02/"tralai").</li>
 *   <li><b>Đây (use case này)</b>: các màn hình KHÁC (vào sổ hàng loạt/đơn lẻ chọn nút "Không vào
 *   sổ" riêng, KHÔNG phải "Từ chối"/"Trả lại") gọi THẲNG {@code CapNhatVanBanDen?action=khongvaoso}
 *   — KHÔNG kèm {@code ReplyDocumentOut}, KHÔNG publish broker trạng thái nào cho đơn vị gửi. Các
 *   điểm gọi FE: {@code chovaoso/VaoSoNhieu.jsx}, {@code chovaoso/ItemsList.jsx} (hàm KHÁC
 *   {@code tu_choi_tra_lai_nhieu_van_ban}), {@code chovaosothudientu/ItemsList.jsx},
 *   {@code denquamang/ItemsList.jsx}, {@code Components/ChiTietSoVanBanDenDayDu.jsx}/
 *   {@code ChiTietSoVanBanDenRutGon.jsx}.</li>
 * </ul>
 *
 * <p><b>Field bị loại KHÁC {@link TuChoiTiepNhanVbdenUseCase}</b>: Domino thật loại
 * {@code identifier} khỏi 4 field ({@code contact_unit_identifier}/{@code unit_process}/
 * {@code unit_coordinate}/{@code unit_to_read}) — nhiều hơn nhánh "Từ chối" (chỉ loại
 * {@code unit_process}) — ĐÚNG cùng 4 field mà {@link LuuVanBanDenUseCase#loaiKhoiDanhSachChoVaoSo}
 * dùng cho chiều NGƯỢC LẠI (vào sổ → đưa vào {@code *_registered}; ở đây, không vào sổ → đưa vào
 * {@code unit_discard}).</p>
 *
 * <p><b>KHÔNG đặt {@code is_document_refuse}/{@code nghiepvu}</b> (khác {@link TuChoiTiepNhanVbdenUseCase}) —
 * đối chiếu trực tiếp mã nguồn xác nhận {@code khongvaoso()} thật KHÔNG ghi 2 field đó. {@code opinion}
 * FE luôn thu thập (bắt buộc nhập lý do) nhưng Domino thật KHÔNG đọc/lưu ở đâu trong hàm này — giữ
 * đúng, không tự bịa nơi lưu.</p>
 *
 * <p><b>{@code identifier}</b>: Domino đọc THẲNG từ request (client tự truyền) — cổng vào ĐÂY resolve
 * SERVER-SIDE qua {@link ResolveIdentifierByLevelUseCase} (đúng nguyên tắc "danh tính người thực hiện
 * SERVER-AUTHORITATIVE" đã áp dụng xuyên suốt dự án, xem {@code AddVbdiSendLogUseCase}) thay vì tin
 * client — an toàn hơn, không đổi hành vi nghiệp vụ quan sát được.</p>
 *
 * <p><b>CHƯA port</b>: {@code TDInit.setViewError(...)} — cơ chế làm mới cache badge/counter Redis
 * của Domino, không có khái niệm tương đương (R11, đã ghi nhận nhiều nơi khác trong dự án).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KhongVaoSoVbdenUseCase {

    private static final String DOCUMENT_TYPE = "vbden";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";

    private static final String CONTACT_UNIT_IDENTIFIER_FIELD = "contact_unit_identifier";
    private static final String UNIT_PROCESS_FIELD = "unit_process";
    private static final String UNIT_COORDINATE_FIELD = "unit_coordinate";
    private static final String UNIT_TO_READ_FIELD = "unit_to_read";
    private static final String UNIT_DISCARD_FIELD = "unit_discard";
    private static final String USER_DISCARD_FIELD = "user_discard";
    // Dung DUNG 4 field Domino that loai bo - KHONG hon KHONG kem (khac TuChoiTiepNhanVbdenUseCase
    // chi loai unit_process).
    private static final String[] FIELDS_TO_REMOVE_FROM = {
        CONTACT_UNIT_IDENTIFIER_FIELD, UNIT_PROCESS_FIELD, UNIT_COORDINATE_FIELD, UNIT_TO_READ_FIELD
    };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;

    public Result<UUID> execute(KhongVaoSoVbdenRequest request) {
        if (request.getId() == null) {
            return Result.failure("Thiếu id văn bản đến");
        }
        var documentOptional = documentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (actingUsername == null || actingUsername.isBlank()) {
            return Result.failure("Không xác định được người dùng hiện tại");
        }
        String identifier = resolveIdentifierByLevelUseCase.resolveForCurrentUser().orElse(null);

        var before = buildSnapshot(document);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        if (identifier != null && !identifier.isBlank()) {
            for (String fieldName : FIELDS_TO_REMOVE_FROM) {
                List<String> values = toStringList(fields.get(fieldName));
                if (values.remove(identifier)) {
                    fields.put(fieldName, values);
                }
            }
            addOne(fields, UNIT_DISCARD_FIELD, identifier);
        }
        addOne(fields, USER_DISCARD_FIELD, actingUsername);

        document.replaceDynamicFields(toJson(fields));
        var saved = documentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        return Result.success(saved.getId());
    }

    private Map<String, Object> buildSnapshot(BusinessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        } else if (value != null) {
            for (String part : value.toString().split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private void addOne(Map<String, Object> fields, String fieldName, String value) {
        List<String> list = toStringList(fields.get(fieldName));
        if (!list.contains(value)) {
            list.add(value);
        }
        fields.put(fieldName, list);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
