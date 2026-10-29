package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.realtime.RealtimeNotification;
import com.td.application.realtime.RealtimeNotifier;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-230 (DuThao Core) — Slice 1 ("create/save core + get detail", tài liệu phân tích 04 mục 13) —
 * hiệu chỉnh 1 dự thảo ĐÃ TỒN TẠI, tương thích 1 PHẦN {@code qlvb/add?databasetype=duthao&id=...}
 * (khác {@link TaoDuThaoUseCase}, chỉ xử lý tạo mới hoàn toàn).
 *
 * <p><b>Phạm vi đã port</b> (R1, cùng tinh thần {@code LuuVanBanDenUseCase} bên vbden nhưng đơn
 * giản hơn — {@code duthao} không có kiến trúc "sổ văn bản" 2-document):</p>
 * <ul>
 *   <li>Merge {@code request.getFields()} (kiểu hoá qua {@code dateFields}/{@code numberFields}/
 *   {@code richtextFields}, cùng cơ chế BL-101) vào {@code dynamic_fields} HIỆN CÓ của dự thảo
 *   (merge, không replace toàn bộ — field không có trong request giữ nguyên giá trị cũ).</li>
 *   <li>Đồng bộ lại cột tĩnh {@code title} nếu request có gửi {@code subject} — tương tự bugfix đã
 *   áp dụng ở {@code LuuVanBanDenUseCase#mergeFieldsIntoDocument} (lý do: {@link TaoDuThaoUseCase}
 *   lưu {@code subject} vào {@code title}, không vào {@code dynamic_fields} — nếu chỉ merge
 *   {@code subject} vào {@code dynamic_fields} mà không đồng bộ lại {@code title}, 2 giá trị sẽ lệch
 *   nhau sau khi hiệu chỉnh).</li>
 * </ul>
 *
 * <p><b>CHƯA port</b> (ngoài phạm vi Slice 1 — thuộc Cụm B/C/D/E của tài liệu phân tích 04, để dành
 * cho các slice sau): tạo/cập nhật {@code draft_process} (quá trình dự thảo, side-effect theo
 * {@code action} — Cụm B), tính lại {@code status}/{@code status_pre}/{@code last_process_id},
 * đồng bộ {@code vbden} liên kết ({@code capnhatVBden} phía FE), gắn/bỏ gắn {@code vbdi}/{@code
 * vbden}/{@code hosocv} (Cụm E). FE vẫn tự gọi các luồng đó riêng như hiện tại sau khi lưu field lõi
 * qua use case này.</p>
 *
 * <p><b>Bugfix 24/08/2026</b> (cùng nguyên nhân/tài liệu tham chiếu đã ghi ở
 * {@link TaoDuThaoUseCase} — 3 view {@code GetView} theo {@code user_creator} luôn rỗng): backfill
 * LUÔN ở đây cho dự thảo ĐÃ TỒN TẠI tạo TRƯỚC bugfix (hoặc tạo qua đường khác vẫn thiếu field) —
 * nếu {@code user_creator} hiện đang rỗng/vắng mặt sau khi merge, gán bằng username người đang lưu.
 *
 * <p><b>Cập nhật 26/08/2026 (đồng bộ quyết định "SERVER-AUTHORITATIVE" ở {@link TaoDuThaoUseCase}
 * — user phải lưu đúng tài khoản tạo)</b>: ngoài backfill-if-blank, giờ LOẠI {@code user_creator}
 * khỏi fields client gửi TRƯỚC khi merge — client không được phép đổi danh tính người tạo của bản
 * ghi đã có (kể cả chính người tạo mở lại). Giá trị hiện có luôn được giữ nguyên; chỉ bản ghi HỎNG
 * (rỗng/vắng) mới được gán bằng người đang lưu. Không dùng {@code CurrentUserProvider} để GHI ĐÈ
 * {@code user_creator} có sẵn (khác người tạo thật sự khi người khác mở sửa) vì đó là hành vi SAI —
 * ai sửa cuối cùng đã có cột audit {@code last_modified_by} ghi nhận.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LuuDuThaoUseCase {

    private static final String DOCUMENT_TYPE = TaoDuThaoUseCase.DOCUMENT_TYPE;
    private static final String SUBJECT_FIELD = "subject";
    private static final String USER_CREATOR_FIELD = TaoDuThaoUseCase.USER_CREATOR_FIELD;
    private static final String FALLBACK_USERNAME = "system";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản dự thảo";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AuthorAclFieldsService authorAclFieldsService;
    private final com.td.application.documentlinks.RelatedDocumentSnapshots relatedDocumentSnapshots;
    // 19/10/2026: ủy quyền ở Người nhận phối hợp -> người ủy quyền vào user_coordinated_movement (đã xác minh).
    private final DuThaoCoordinatorDelegations coordinatorDelegations;
    private final RealtimeNotifier realtimeNotifier;

    public Result<UUID> execute(LuuDuThaoRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id văn bản dự thảo cần cập nhật");
        }
        DuThaoDocument document = duThaoDocumentRepository.findById(request.getId()).orElse(null);
        if (document == null || !DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, String> fields = request.getFields() == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getFields());
        // 26/08/2026 (SERVER-AUTHORITATIVE, xem javadoc lớp): user_creator là danh tính NGƯỜI TẠO —
        // loại khỏi incoming TRƯỚC merge để client không thể ghi đè giá trị hiện có; bản ghi HỎNG
        // (trống) sẽ được backfillUserCreatorIfBlank gán bằng người đang lưu bên dưới.
        fields.remove(USER_CREATOR_FIELD);
        // 06/10/2026 (bang document_links giai doan 2 - docs/architecture/document-links.md): documentin_obj /
        // vanban_lienquan_obj KHONG luu trong dynamic_fields nua - bo khoi payload (FE tung gui lai ca
        // mang 1 phan tu moi lan luu du thao tao tu van ban den, ghi de mat cac van ban tra loi gan sau).
        relatedDocumentSnapshots.strip(fields);

        String coordinatorsBefore = coordinators(document);
        mergeFieldsIntoDocument(document, fields, request);
        duThaoDocumentRepository.save(document);
        notifyNewCoordinators(document, coordinatorsBefore);

        return Result.success(document.getId());
    }

    // TDDocument.java dong 666 (DraftInitNotification#DraftingCoordinatorInit): Lưu dự thảo có thêm người phối
    // hợp -> báo người MỚI (Domino action "chuyenduthao4", FE không có nhãn -> "chuyenphoihop").
    private void notifyNewCoordinators(DuThaoDocument document, String coordinatorsBefore) {
        java.util.Set<String> before = new java.util.HashSet<>();
        for (String user : coordinatorsBefore.split(";")) {
            before.add(user.trim().toLowerCase(java.util.Locale.ROOT));
        }
        java.util.List<String> added = new java.util.ArrayList<>();
        for (String user : coordinators(document).split(";")) {
            if (!user.isBlank() && !before.contains(user.trim().toLowerCase(java.util.Locale.ROOT))) {
                added.add(user.trim());
            }
        }
        if (added.isEmpty()) {
            return;
        }
        realtimeNotifier.notify(RealtimeNotification.of(DOCUMENT_TYPE, "chuyenphoihop", document.getId(), document.getTitle())
            .to(added)
            .exclude(currentUserProvider.currentUsername().orElse("")));
    }

    private String coordinators(DuThaoDocument document) {
        Object value = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()).get("user_coordinator");
        return value == null ? "" : String.valueOf(value);
    }

    // Merge (KHONG replace toan bo) field moi vao dynamic_fields hien co - field khong nam trong
    // request.getFields() giu nguyen gia tri cu (khac AddDynamicDocumentUseCase - luon tao document
    // MOI hoan toan nen khong can merge). Cung dong bo lai "title" neu request co gui "subject" -
    // xem javadoc lop.
    private void mergeFieldsIntoDocument(DuThaoDocument document, Map<String, String> fields, LuuDuThaoRequest request) {
        Map<String, Object> existing = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        Map<String, Object> incoming = dynamicDocumentFieldsService.parseDynamicFields(
            dynamicDocumentFieldsService.buildDynamicFieldsJson(
                fields, request.getDateFields(), request.getNumberFields(), request.getRichtextFields()));
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(incoming);
        backfillUserCreatorIfBlank(merged);
        if (fields.containsKey(DuThaoCoordinatorDelegations.COORDINATOR_OBJ_FIELD)) {
            coordinatorDelegations.apply(merged);
        }
        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, merged, fields.keySet());
        document.replaceDynamicFields(toJson(merged));

        String subject = fields.get(SUBJECT_FIELD);
        if (subject != null && !subject.isBlank()) {
            document.update(subject, null, null, null, null, null, null);
        }
    }

    // Bugfix 24/08/2026 - xem javadoc lop: backfill "user_creator" cho du thao HONG (tao truoc
    // bugfix TaoDuThaoUseCase, hoac thieu vi ly do khac) - CHI khi dang rong/vang mat, KHONG ghi de
    // gia tri da co (nguoi mo sua co the KHAC nguoi tao that su).
    private void backfillUserCreatorIfBlank(Map<String, Object> merged) {
        Object existingValue = merged.get(USER_CREATOR_FIELD);
        boolean isBlank = existingValue == null || String.valueOf(existingValue).isBlank();
        if (isBlank) {
            merged.put(USER_CREATOR_FIELD, currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME));
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
