package com.td.application.vbdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
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
 * 09/09/2026 (yêu cầu người dùng "port qlvb/add?actionname=addlogseendocument...databasetype=vbdi
 * khi xem chi tiết văn bản đi" — phát hiện giữa chừng: {@code id} thực tế là UUID Postgres, còn cả
 * {@code qlvb/add} lẫn {@code qlvb/vbdi/seendocumentout} (Domino, {@code BE_THANHHOA}) đều chỉ đọc
 * được UNID Domino — 2 route đó KHÔNG BAO GIỜ hoạt động cho văn bản đi Java-native, phải build API
 * mới ở đây): port hợp nhất 2 đoạn Domino cùng phục vụ 1 sự kiện "mở xem chi tiết văn bản đi":
 * {@code TDDocument.java} (field {@code log_seen_document}/{@code userxemdebiet}, qua
 * {@code qlvb/add&actionname=addlogseendocument}) VÀ nhánh {@code key==""} của
 * {@code DocumentOutProcesses.java#Seen_document()} ({@code coordinator_hide_seen_rtext}/
 * {@code coordinator_hide_not_seen_rtext}, qua {@code qlvb/vbdi/seendocumentout}) — 2 API Domino
 * tách rời cho CÙNG 1 sự kiện, gộp lại 1 use case duy nhất.
 *
 * <p><b>Khác biệt CHỦ ĐÍCH với Domino gốc</b> (đã xác nhận qua đọc code, không phải suy đoán):
 * <ul>
 * <li>{@code log_seen_document}: Domino gốc là mảng JSON object {@code {name,username,id,unit,
 * unit_id,created}} (1 phần tử/người xem, dedup theo {@code id}). Ở đây rút gọn thành CHUỖI username
 * nối {@code ";"} — CÙNG khuôn với {@code coordinator_hide_seen_rtext}/{@code usercoordinater}/...
 * — bắt buộc phải theo khuôn này vì {@link com.td.application.viewquery.ViewQueryService
 * #queryVbdiBaoCaoChuaXem} (view "Báo cáo chưa xem") ĐÃ đọc field này qua
 * {@code VbdiDocumentRepository#findByAnyFieldContainsMember}/{@code excludeMemberFieldName} —
 * predicate SQL yêu cầu khuôn {@code ;user1;user2;} (xem
 * {@code PostgresVbdiDocumentRepository} dòng ~236-239); nếu ghi mảng JSON object thay vì chuỗi
 * {@code ";"}, view đó sẽ không bao giờ khớp lại được nữa.</li>
 * <li><b>09/09/2026 (2, người dùng phát hiện FE không hiển thị được "ngày giờ"/"đơn vị" người xem —
 * xem {@code LogSeenDocument.jsx})</b>: bổ sung field RIÊNG {@value #FIELD_LOG_SEEN_DOCUMENT_DETAIL}
 * (mảng JSON object {@code {username,name,unit,created,lastSeen}}, dedup theo {@code username}) để
 * khôi phục lại rich metadata đã mất ở trên MÀ KHÔNG đụng tới khuôn {@code ";"}-list của
 * {@code log_seen_document} (field đó vẫn giữ nguyên, chỉ phục vụ predicate SQL). {@code name}/
 * {@code unit} tra server-side qua {@link com.td.application.sharedcore.UserDisplayInfoService}
 * (KHÔNG nhận từ client — cùng nguyên tắc server-authoritative của {@code username}).</li>
 * <li><b>09/09/2026 (4, người dùng báo "chưa thấy thông tin đơn vị... cơ quan, đơn vị, phòng ban
 * nào")</b>: bản (2) từng chỉ lấy {@code UserOrganizationProfileDto#getUnitName()} — CHỈ có giá trị
 * khi tài khoản có tổ tiên khớp ĐÚNG form {@code "unit"}, rỗng với rất nhiều tài khoản khác (chỉ có
 * "agency"/"department") dù rõ ràng thuộc 1 tổ chức nào đó. {@link
 * com.td.application.sharedcore.UserDisplayInfoService} gộp CẢ 3 cấp cơ quan/đơn vị/phòng ban thành
 * 1 chuỗi hiển thị — xem javadoc lớp đó.</li>
 * <li><b>09/09/2026 (3, người dùng yêu cầu thêm "ngày giờ xem lần cuối")</b>: {@code created} = lần
 * xem ĐẦU TIÊN (immutable, không ghi đè các lần sau); {@code lastSeen} = CẬP NHẬT MỖI LẦN gọi (kể cả
 * khi đã từng xem trước đó) — khác {@code log_seen_document}/{@code coordinator_hide_seen_rtext}/...
 * (các field đó vẫn chỉ "có mặt hay không", không track lần cuối). {@code name}/{@code unit} cũng
 * được LÀM MỚI mỗi lần xem (phản ánh đơn vị/tên HIỆN TẠI, không còn giữ nguyên snapshot lần xem đầu
 * như bản 09/09/2026 (2)) — hợp lý hơn cho 1 field hiển thị "lần xem cuối", tránh hiển thị tên/đơn vị
 * cũ đã lỗi thời cạnh mốc thời gian mới nhất.</li>
 * <li>{@code coordinator_obj_log_rtext} (đánh dấu "Đã xem" cho từng người phối hợp — Domino gốc)
 * CỐ Ý KHÔNG port ở use case này (quyết định người dùng 09/09/2026) — chưa có tiền lệ đọc/ghi field
 * này ở bất kỳ đâu trong module vbdi Java (bảng {@code vbdi_send_log_documents} đã thay thế mục đích
 * theo dõi nơi nhận bằng kiến trúc append-only khác, key theo username thay vì id) — để tránh đoán
 * sai kiến trúc khi chưa có FE nào thực sự đọc field này từ Postgres.</li>
 * </ul></p>
 *
 * <p><b>Định danh SERVER-AUTHORITATIVE</b>: {@code username} lấy từ {@link CurrentUserProvider} —
 * KHÔNG nhận từ client (khác FE cũ tự tính currentUser rồi gửi nguyên object) — tránh giả mạo "ai đã
 * xem". Cùng nguyên tắc {@link AddVbdiUserActionLogUseCase}.</p>
 *
 * <p><b>Guard</b>: văn bản phải tồn tại, đúng {@code documentType="vbdi"}, chưa xoá mềm — cùng
 * nguyên tắc {@link GanVbdiDocumentUseCase}/{@link AddVbdiUserActionLogUseCase}. KHÔNG guard trạng
 * thái ISSUED (Domino gốc không yêu cầu — FE gọi ngay khi mở modal xem chi tiết, bất kể trạng
 * thái). Idempotent: gọi lại nhiều lần (mở lại modal) không tạo trùng lặp — mọi field đều rebuild từ
 * chuỗi {@code ";"} hiện có, chỉ thêm/loại nếu chưa đúng trạng thái mong muốn.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddVbdiSeenDocumentUseCase {

    private static final String FALLBACK_USERNAME = "system";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";

    static final String FIELD_LOG_SEEN_DOCUMENT = "log_seen_document";
    static final String FIELD_LOG_SEEN_DOCUMENT_DETAIL = "log_seen_document_detail";
    static final String FIELD_COORDINATOR_HIDE_SEEN = "coordinator_hide_seen_rtext";
    static final String FIELD_COORDINATOR_HIDE_NOT_SEEN = "coordinator_hide_not_seen_rtext";
    static final String FIELD_USERXEMDEBIET = "userxemdebiet";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final UserDisplayInfoService userDisplayInfoService;

    public Result<UUID> execute(AddVbdiSeenDocumentRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return Result.failure("Thiếu documentId cần đánh dấu đã xem");
        }
        var document = vbdiDocumentRepository.findById(request.getDocumentId()).orElse(null);
        if (document == null || document.isDeleted()
                || !SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String username = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        boolean alreadySeen = containsMember(fields.get(FIELD_LOG_SEEN_DOCUMENT), username);
        addMember(fields, FIELD_LOG_SEEN_DOCUMENT, username);
        addMember(fields, FIELD_COORDINATOR_HIDE_SEEN, username);
        removeMember(fields, FIELD_COORDINATOR_HIDE_NOT_SEEN, username);
        removeMember(fields, FIELD_USERXEMDEBIET, username);
        // 09/09/2026 (3): LUON goi (khac ban truoc chi goi khi !alreadySeen) - can cap nhat lastSeen
        // moi lan xem, ke ca da xem tu truoc.
        recordSeenDetail(fields, username);

        document.replaceDynamicFields(toJson(fields));
        var saved = vbdiDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(),
            Map.of("username", username, "alreadySeenBefore", alreadySeen),
            Map.of("username", username, FIELD_LOG_SEEN_DOCUMENT, fields.getOrDefault(FIELD_LOG_SEEN_DOCUMENT, "")));
        log.info("Ghi log xem van ban di {} boi {}", saved.getId(), username);
        return Result.success(saved.getId());
    }

    /** Ghi/cập nhật 1 phần tử {username,name,unit,created,lastSeen} trong
     * {@value #FIELD_LOG_SEEN_DOCUMENT_DETAIL} — dedup theo {@code username}: chưa có thì thêm mới
     * ({@code created}={@code lastSeen}=hiện tại); đã có thì CHỈ cập nhật {@code lastSeen}+
     * {@code name}/{@code unit} (làm mới theo hồ sơ hiện tại), giữ nguyên {@code created} gốc. */
    private void recordSeenDetail(Map<String, Object> fields, String username) {
        ArrayNode detailArr = readArray(fields, FIELD_LOG_SEEN_DOCUMENT_DETAIL);
        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        var displayInfo = userDisplayInfoService.resolve(username);
        String fullName = displayInfo.fullName();
        String unitName = displayInfo.unit();

        ObjectNode existing = null;
        for (JsonNode item : detailArr) {
            if (item.isObject() && username.equalsIgnoreCase(textOrNull(item, "username"))) {
                existing = (ObjectNode) item;
                break;
            }
        }
        if (existing != null) {
            existing.put("name", fullName);
            existing.put("unit", unitName);
            existing.put("lastSeen", now);
            if (!existing.has("created") || existing.get("created").isNull()) {
                existing.put("created", now);
            }
        } else {
            ObjectNode entry = OBJECT_MAPPER.createObjectNode();
            entry.put("username", username);
            entry.put("name", fullName);
            entry.put("unit", unitName);
            entry.put("created", now);
            entry.put("lastSeen", now);
            detailArr.add(entry);
        }
        fields.put(FIELD_LOG_SEEN_DOCUMENT_DETAIL, detailArr);
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    /** Đọc 1 field động dạng mảng JSON — chấp nhận cả 2 dạng: chuỗi JSON thô (field mới ghi lần đầu
     * qua {@link #toJson}) VÀ cấu trúc Java native (List/Map) do {@code parseDynamicFields} tự suy ra
     * kiểu khi đọc lại field đã lưu từ vòng trước (Jackson generic parse cả khối {@code dynamic_fields}). */
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

    private boolean containsMember(Object rawValue, String username) {
        return splitMembers(rawValue).stream().anyMatch(item -> item.equalsIgnoreCase(username));
    }

    private void addMember(Map<String, Object> fields, String fieldName, String username) {
        List<String> members = splitMembers(fields.get(fieldName));
        if (members.stream().noneMatch(item -> item.equalsIgnoreCase(username))) {
            members.add(username);
        }
        fields.put(fieldName, String.join(";", members));
    }

    private void removeMember(Map<String, Object> fields, String fieldName, String username) {
        List<String> members = splitMembers(fields.get(fieldName));
        if (members.isEmpty()) {
            return;
        }
        List<String> filtered = members.stream()
            .filter(item -> !item.equalsIgnoreCase(username))
            .collect(Collectors.toList());
        fields.put(fieldName, String.join(";", filtered));
    }

    /** Tách chuỗi {@code "a;b;c"} thành danh sách, bỏ khoảng trắng/phần tử rỗng — an toàn với null. */
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

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
