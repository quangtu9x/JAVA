package com.td.application.vbdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.UserDisplayInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 14/09/2026 (yêu cầu người dùng "port chức năng ở route vbdi/xuly/nhandebiet như đã xem, chuyển xem
 * để biết, cutoverFE luôn") — port nút "Chuyển xem để biết" ({@code YkienModal.jsx},
 * {@code actionname="xemdebiet"}, gọi Domino {@code qlvb/vbdi/coordinatedtransfer?action=xemdebiet}).
 * Domino thật ({@code DocumentOutProcesses.java#HandleCoordinatedTransfer}) dùng CHUNG 1 cơ chế cho 3
 * action (xemdebiet/phoihop/chuyenxuly) — CHỈ port nhánh {@code action="xemdebiet"} theo đúng phạm vi
 * yêu cầu (route {@code vbdi/xuly/nhandebiet}); 2 nhánh còn lại (phối hợp/chuyển xử lý qua modal này)
 * ngoài phạm vi, giữ nguyên gọi Domino.
 *
 * <p><b>Field ghi lên văn bản đi</b> (đối chiếu {@code ProcessDocument()}, khớp field Java
 * {@link com.td.application.viewquery.ViewQueryService} đã dùng để lọc 2 tab "Chưa xem"/"Đã xem" —
 * xem {@code VIEW_VBDI_NHANDEBIET_UNSEEN}/{@code VIEW_VBDI_NHANDEBIET_SEEN}):
 * <ul>
 * <li>{@value #FIELD_XEMDEBIET} — tên hiển thị (";"-joined), cộng dồn, không xoá.</li>
 * <li>{@value #FIELD_COORDINATOR_HIDE_NOT_SEEN} — username (nhóm {@code coordinators})
 * HOẶC identifier (nhóm {@code internalUnits}) — field DUY NHẤT quyết định văn bản có hiện ở tab
 * "Chưa xem" của người/đơn vị đó hay không.</li>
 * <li>{@value #FIELD_COORDINATOR_HIDE} — username của nhóm {@code coordinators} (KHÔNG áp dụng cho
 * {@code internalUnits} — đúng Domino: {@code filed_UserName1} chỉ được set trong nhánh
 * {@code key.equalsIgnoreCase("coordinator")}).</li>
 * <li>{@value #FIELD_USERXEMDEBIET_SEND} — username người THỰC HIỆN hành động này (cộng dồn, đúng
 * Domino: chỉ thêm nếu chưa có).</li>
 * <li>{@value #FIELD_XEMDEBIET_OBJ} — mảng JSON snapshot đầy đủ từng người/đơn vị (đọc lại bởi
 * {@code QuaTrinhXuLyVBdi.jsx} tab "Quá trình xử lý", index "7") — MỖI phần tử được bổ sung
 * {@code usercreator}/{@code create_date}/{@code status}/{@code detail} (lịch sử 1 dòng) đúng
 * {@code create_Log()}/{@code create_Log_Detail()} Domino. Field này kết thúc {@code "_obj_rtext"} —
 * bị suy luận là {@link com.td.application.common.dynamicfields.FieldType#JSON} bởi
 * {@code FieldTypeInferrer} KHI ghi qua {@code AddDynamicDocumentUseCase}, nhưng use case này mutate
 * trực tiếp {@code dynamic_fields} đã parse sẵn (như {@link AddVbdiSeenDocumentUseCase}) nên KHÔNG đi
 * qua tầng suy luận kiểu đó — đọc lại vẫn phải tolerant CẢ chuỗi JSON thô LẪN cấu trúc Java-native đã
 * parse sẵn (xem {@link #readArray}, cùng bẫy dữ liệu đã ghi nhận ở vbden —
 * {@code vbden-obj-field-json-vs-string-trap}).</li>
 * </ul></p>
 *
 * <p><b>CỐ Ý KHÔNG port</b>: {@code createDocumentReaderAuthor} (ACL Domino — không có tương đương
 * Postgres, đúng tiền lệ mọi use case vbdi/vbden khác). Nội dung mặc định của {@code detail[0].content}
 * khi không nhập ý kiến ĐÃ SỬA lại cho đúng ngữ cảnh "xem để biết" (Domino gốc dùng chung 1 câu
 * "Chuyển phối hợp cho..." cho CẢ 3 action do code sao chép — xác nhận qua đọc trực tiếp
 * {@code create_Log_Detail()}, không phải suy đoán) — sửa vì use case này KHÔNG dùng chung hàm với
 * 2 action kia nên không có lý do giữ nguyên câu chữ sai ngữ cảnh.</p>
 *
 * <p>Guard: văn bản phải tồn tại, đúng {@code documentType="vbdi"}, chưa xoá mềm — cùng nguyên tắc
 * {@link AddVbdiSeenDocumentUseCase}. {@code username} người thực hiện lấy server-authoritative qua
 * {@link CurrentUserProvider} (KHÔNG nhận từ client).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChuyenXemDeBietVbdiUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String STATUS_DA_CHUYEN = "Đã chuyển";

    static final String FIELD_XEMDEBIET = "xemdebiet";
    static final String FIELD_XEMDEBIET_OBJ = "xemdebiet_obj_rtext";
    static final String FIELD_COORDINATOR_HIDE_NOT_SEEN = "coordinator_hide_not_seen_rtext";
    static final String FIELD_COORDINATOR_HIDE = "coordinator_hide_rtext";
    static final String FIELD_USERXEMDEBIET_SEND = "userxemdebiet_send";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong". FIELD_COORDINATOR_HIDE ("coordinator_
    // hide_rtext") nam san trong SET_AUTHOR_FIELDS co dinh nen duoc doc du khong khai bao rieng o day;
    // cac field con lai (xemdebiet/coordinator_hide_not_seen_rtext/userxemdebiet_send) khong khop quy
    // uoc "user_"/"hide" cua Domino that (DocumentOutProcesses.ProcessDocument luu qua doc.save(true)
    // truc tiep, KHONG qua CreateOrUpdateDocument()) - van goi applyOnUpdate de it nhat cap nhat
    // identifier nguoi dang thao tac + FIELD_COORDINATOR_HIDE, tranh sot theo quyet dinh nguoi dung
    // "port day du, tranh sot" o pham vi rong hon 1:1 Domino.
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final UserDisplayInfoService userDisplayInfoService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(ChuyenXemDeBietVbdiRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return Result.failure("Thieu documentId can chuyen xem de biet");
        }
        List<VbdiForwardReadRecipient> coordinators = nonNull(request.getCoordinators());
        List<VbdiForwardReadRecipient> internalUnits = nonNull(request.getInternalUnits());
        if (coordinators.isEmpty() && internalUnits.isEmpty()) {
            return Result.failure("Chua chon nguoi/don vi xem de biet");
        }
        var document = vbdiDocumentRepository.findById(request.getDocumentId()).orElse(null);
        if (document == null || document.isDeleted()
                || !SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String username = currentUserProvider.currentUsername().orElse("system");
        var actingInfo = userDisplayInfoService.resolve(username);
        String actingFullName = actingInfo.fullName() != null && !actingInfo.fullName().isBlank()
            ? actingInfo.fullName() : username;
        String opinion = request.getOpinion() == null ? "" : request.getOpinion().trim();
        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        List<String> displayNames = splitMembers(fields.get(FIELD_XEMDEBIET));
        List<String> hideNotSeen = splitMembers(fields.get(FIELD_COORDINATOR_HIDE_NOT_SEEN));
        List<String> hideAll = splitMembers(fields.get(FIELD_COORDINATOR_HIDE));
        List<String> sentBy = splitMembers(fields.get(FIELD_USERXEMDEBIET_SEND));
        addDistinct(sentBy, username);
        ArrayNode mergedObj = readArray(fields, FIELD_XEMDEBIET_OBJ);

        for (VbdiForwardReadRecipient item : coordinators) {
            processRecipient(item, item.getUsername(), true, displayNames, hideNotSeen, hideAll, mergedObj,
                actingFullName, now, opinion);
        }
        for (VbdiForwardReadRecipient item : internalUnits) {
            processRecipient(item, item.getIdentifier(), false, displayNames, hideNotSeen, hideAll, mergedObj,
                actingFullName, now, opinion);
        }

        fields.put(FIELD_XEMDEBIET, String.join(";", displayNames));
        fields.put(FIELD_COORDINATOR_HIDE_NOT_SEEN, String.join(";", hideNotSeen));
        // Dung Domino (filed_UserName1 chi duoc doc.replaceItemValue khi vUserCoordinater_2.size() >
        // 0) - CHI ghi khi co du lieu tu nhom "coordinator", tranh ghi de field thanh chuoi rong khi
        // request chi co internalUnits.
        if (!hideAll.isEmpty()) {
            fields.put(FIELD_COORDINATOR_HIDE, String.join(";", hideAll));
        }
        fields.put(FIELD_USERXEMDEBIET_SEND, String.join(";", sentBy));
        fields.put(FIELD_XEMDEBIET_OBJ, mergedObj);
        authorAclFieldsService.applyOnUpdate(SoanThaoVbdiUseCase.DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);

        var before = Map.of(FIELD_COORDINATOR_HIDE_NOT_SEEN, "");
        document.replaceDynamicFields(toJson(fields));
        var saved = vbdiDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before,
            Map.of(FIELD_COORDINATOR_HIDE_NOT_SEEN, fields.getOrDefault(FIELD_COORDINATOR_HIDE_NOT_SEEN, "")));
        log.info("Chuyen xem de biet van ban di {} boi {}", saved.getId(), username);
        return Result.success(saved.getId());
    }

    /**
     * Xử lý 1 phần tử — {@code memberKey} là {@code username} (nhóm cá nhân) hoặc {@code identifier}
     * (nhóm đơn vị nội bộ). Đúng Domino {@code ProcessDocument()}: tên hiển thị luôn được thêm (dedup
     * theo tên, không phân biệt cá nhân/đơn vị); {@code hideNotSeen} nhận username HOẶC identifier
     * tuỳ nhóm; {@code hideAll} ({@code coordinator_hide_rtext}) CHỈ nhận từ nhóm cá nhân.
     */
    private void processRecipient(
            VbdiForwardReadRecipient item, String memberKey, boolean isCoordinatorGroup,
            List<String> displayNames, List<String> hideNotSeen, List<String> hideAll, ArrayNode mergedObj,
            String actingFullName, String now, String opinion) {
        if (item == null) {
            return;
        }
        String name = item.getName() == null ? "" : item.getName();
        boolean isNewMember = memberKey != null && !memberKey.isBlank()
            && hideNotSeen.stream().noneMatch(existing -> existing.equalsIgnoreCase(memberKey));
        addDistinct(displayNames, name);
        if (memberKey != null && !memberKey.isBlank()) {
            addDistinct(hideNotSeen, memberKey);
            if (isCoordinatorGroup) {
                addDistinct(hideAll, memberKey);
            }
        }
        if (isNewMember) {
            mergedObj.add(buildSnapshotNode(item, actingFullName, now, opinion));
        }
    }

    private ObjectNode buildSnapshotNode(VbdiForwardReadRecipient item, String actingFullName, String now, String opinion) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("id", item.getId());
        node.put("name", item.getName());
        node.put("username", item.getUsername());
        node.put("identifier", item.getIdentifier());
        node.put("unit", item.getUnit());
        node.put("usercreator", actingFullName);
        node.put("create_date", now);
        node.put("status", STATUS_DA_CHUYEN);
        ArrayNode detail = OBJECT_MAPPER.createArrayNode();
        ObjectNode detailEntry = OBJECT_MAPPER.createObjectNode();
        detailEntry.put("usercreator", actingFullName);
        detailEntry.put("create_date", now);
        detailEntry.put("name", item.getName());
        detailEntry.put("status", STATUS_DA_CHUYEN);
        String defaultContent = actingFullName + " chuyen xem de biet cho " + item.getName()
            + (item.getUnit() != null && !item.getUnit().isBlank() ? " thuoc " + item.getUnit() : "")
            + " vao luc " + now;
        detailEntry.put("content", !opinion.isBlank() ? opinion : defaultContent);
        detail.add(detailEntry);
        node.set("detail", detail);
        return node;
    }

    /** Đọc 1 field động dạng mảng JSON — chấp nhận cả chuỗi JSON thô lẫn cấu trúc Java-native (List/
     * Map) do {@code parseDynamicFields} tự suy ra khi đọc lại field đã lưu từ vòng trước — cùng bẫy
     * đã ghi nhận ở {@code vbden-obj-field-json-vs-string-trap} (field kết thúc {@code "_obj"}/
     * {@code "_rtext"}). */
    private ArrayNode readArray(Map<String, Object> fields, String fieldName) {
        Object raw = fields.get(fieldName);
        if (raw == null) {
            return OBJECT_MAPPER.createArrayNode();
        }
        try {
            String text = raw instanceof String ? (String) raw : OBJECT_MAPPER.writeValueAsString(raw);
            if (text == null || text.isBlank()) {
                return OBJECT_MAPPER.createArrayNode();
            }
            JsonNode parsed = OBJECT_MAPPER.readTree(text);
            return parsed != null && parsed.isArray() ? (ArrayNode) parsed : OBJECT_MAPPER.createArrayNode();
        } catch (Exception ex) {
            return OBJECT_MAPPER.createArrayNode();
        }
    }

    private void addDistinct(List<String> list, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (list.stream().noneMatch(existing -> existing.equalsIgnoreCase(value))) {
            list.add(value);
        }
    }

    private List<String> splitMembers(Object rawValue) {
        String text = rawValue == null ? "" : String.valueOf(rawValue);
        if (text.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(text.split(";"))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
