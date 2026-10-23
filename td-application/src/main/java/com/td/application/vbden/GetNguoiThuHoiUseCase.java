package com.td.application.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbden.VbdenResDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 18/09/2026 (cutover {@code ModalThuHoiVanBan.jsx} — màn chi tiết văn bản đến, nhánh "Chọn đơn
 * vị/người để thu hồi"): tương đương {@code GetNguoiThuHoi.java} thật — trả về DANH SÁCH người/đơn
 * vị CÒN CÓ THỂ thu hồi (do CHÍNH người đang thao tác đã giao qua các lần chuyển xử lý, và HIỆN VẪN
 * chưa xử lý), nhóm theo 4 loại {@code chutri}/{@code phoihop}/{@code xemdebiet}/{@code theodoi} —
 * FE dùng danh sách này để hiển thị bảng chọn trước khi gọi {@link ThuHoiVanBanUseCase} với
 * {@code type="select"}.
 *
 * <p><b>Thuật toán ({@code getNguoiThuHoi()} thật)</b>: ĐỌC-CHỈ, không mutate gì (khác
 * {@link ThuHoiVanBanUseCase#revokeFromSnapshot}, vốn loại phần tử khỏi danh sách pending khi
 * thu hồi thật). Quét MỌI bản ghi {@code vbden_res} có {@code parentid} = văn bản này, lọc còn lại
 * bản ghi có {@code nguoi_thuc_hien} = người đang thao tác VÀ {@code action} thuộc
 * {@link ThuHoiVanBanUseCase#REVOCABLE_ACTIONS} (ĐÚNG tập hành động {@link ThuHoiVanBanUseCase}
 * dùng — đảm bảo danh sách hiển thị ở đây khớp CHÍNH XÁC những gì {@code type="all"} sẽ thu hồi).
 * Với MỖI bản ghi khớp, đọc 4 field snapshot {@code user_process_obj}/{@code user_coordinate_obj}/
 * {@code user_to_read_obj}/{@code user_track_obj} (qua {@link VbdenObjSnapshotFields#readJsonArray}
 * — an toàn bẫy field {@code "_obj"} bị parse thành List/Map lồng lúc ghi), giữ lại phần tử NÀO còn
 * nằm trong field HIỆN TẠI của văn bản cha ({@code user_process}/{@code user_coordinate}/
 * {@code user_to_read}/{@code user_track} — "còn chưa xử lý"), dedup theo username-hoặc-identifier
 * (đúng Domino {@code buildKey}).</p>
 *
 * <p><b>Khác {@code ThuHoiVanBanUseCase} về "theo dõi"</b>: field này ĐỌC SỐNG từ
 * {@code user_track} (không qua snapshot lịch sử — {@code user_track} được ghi bởi
 * {@link TheoDoiVanBanDenUseCase} qua luồng "Theo dõi"/"Bỏ theo dõi" hoàn toàn khác, CÓ dữ liệu
 * thật), nên nhóm {@code theodoi} có thể không rỗng dù {@link ThuHoiVanBanUseCase} (nhánh "all")
 * chưa từng đụng tới field này.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetNguoiThuHoiUseCase {

    private static final String DOCUMENT_TYPE = ThuHoiVanBanUseCase.DOCUMENT_TYPE;
    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String UNIT_PROCESS_FIELD = "unit_process";
    private static final String USER_COORDINATE_FIELD = "user_coordinate";
    private static final String UNIT_COORDINATE_FIELD = "unit_coordinate";
    private static final String USER_TO_READ_FIELD = "user_to_read";
    private static final String UNIT_TO_READ_FIELD = "unit_to_read";
    private static final String USER_TRACK_FIELD = ThuHoiVanBanUseCase.USER_TRACK_FIELD;
    private static final String NGUOI_THUC_HIEN_FIELD = "nguoi_thuc_hien";
    private static final String ACTION_FIELD = "action";
    private static final int MAX_HISTORY_RECORDS = 1000;
    private static final List<String> SNAPSHOT_FIELDS = List.of(ChuyenXuLyVbdenUseCase.USER_PROCESS_OBJ_FIELD_RAW,
        ChuyenXuLyVbdenUseCase.USER_COORDINATE_OBJ_FIELD_RAW, ChuyenXuLyVbdenUseCase.USER_TO_READ_OBJ_FIELD_RAW);
    static final String REASON_PROCESSED = "đã xử lý";
    static final String REASON_PROCESSING = "đã bắt đầu xử lý";
    static final String REASON_REVOKED = "bạn đã thu hồi";
    static final String REASON_FORWARDED = "đã chuyển tiếp cho người khác";

    private final DocumentRepository documentRepository;
    private final VbdenResDocumentRepository vbdenResDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final VbdenRevokeRecipientMatcher recipientMatcher;

    public Result<Map<String, List<Map<String, Object>>>> execute(GetNguoiThuHoiRequest request) {
        var parentOptional = documentRepository.findById(request.getId());
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var parent = parentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(parent.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (actingUsername == null || actingUsername.isBlank()) {
            return Result.failure("Khong xac dinh duoc nguoi dung hien tai");
        }

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        List<String> pendingUserProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        List<String> pendingUnitProcess = toStringList(fields.get(UNIT_PROCESS_FIELD));
        List<String> pendingUserCoordinate = toStringList(fields.get(USER_COORDINATE_FIELD));
        List<String> pendingUnitCoordinate = toStringList(fields.get(UNIT_COORDINATE_FIELD));
        List<String> pendingUserToRead = toStringList(fields.get(USER_TO_READ_FIELD));
        List<String> pendingUnitToRead = toStringList(fields.get(UNIT_TO_READ_FIELD));
        List<String> pendingUserTrack = toStringList(fields.get(USER_TRACK_FIELD));
        List<String> processedUsers = toStringList(fields.get("user_processed"));
        List<String> processedUnits = toStringList(fields.get("unit_processed"));
        List<String> processingUsers = toStringList(fields.get("user_processing"));
        Map<String, Map<String, Object>> notRevocable = new LinkedHashMap<>();
        Map<String, Set<String>> memberCache = recipientMatcher.newMemberCache();

        List<Map<String, Object>> chutri = new ArrayList<>();
        List<Map<String, Object>> phoihop = new ArrayList<>();
        List<Map<String, Object>> xemdebiet = new ArrayList<>();
        List<Map<String, Object>> theodoi = new ArrayList<>();
        Set<String> seenChutri = new LinkedHashSet<>();
        Set<String> seenPhoihop = new LinkedHashSet<>();
        Set<String> seenXemdebiet = new LinkedHashSet<>();
        Set<String> seenTheodoi = new LinkedHashSet<>();

        var historyPage = vbdenResDocumentRepository.findByParentId(
            request.getId().toString(), PageRequest.of(0, MAX_HISTORY_RECORDS));
        // 12/10/2026 (báo lỗi demo11 — "đã chuyển tiếp hoặc đã được thu hồi" gây nhầm): thời điểm gần nhất
        // CHÍNH người thao tác chuyển cho / thu hồi từng người — để nói rõ "bạn đã thu hồi" hay "đã chuyển tiếp".
        Map<String, java.time.LocalDateTime> lastTransferAt = new java.util.HashMap<>();
        Map<String, java.time.LocalDateTime> lastRevokeAt = new java.util.HashMap<>();
        for (VbdenResDocument historyRecord : historyPage.getContent()) {
            Map<String, Object> historyFields = dynamicDocumentFieldsService.parseDynamicFields(
                historyRecord.getDynamicFieldsJson());
            if (!actingUsername.equals(asString(historyFields.get(NGUOI_THUC_HIEN_FIELD)))) {
                continue;
            }
            String action = asString(historyFields.get(ACTION_FIELD));
            Map<String, java.time.LocalDateTime> target = ThuHoiVanBanUseCase.ACTION_THU_HOI.equals(action) ? lastRevokeAt
                : ThuHoiVanBanUseCase.REVOCABLE_ACTIONS.contains(action) ? lastTransferAt : null;
            if (target == null || historyRecord.getCreatedOn() == null) {
                continue;
            }
            for (String rawField : SNAPSHOT_FIELDS) {
                for (JsonNode node : VbdenObjSnapshotFields.readJsonArray(historyFields.get(rawField))) {
                    String key = recipientKey(node);
                    if (key != null) {
                        target.merge(key, historyRecord.getCreatedOn(), (a, b) -> a.isAfter(b) ? a : b);
                    }
                }
            }
        }
        Set<String> revokedByMe = new LinkedHashSet<>();
        lastRevokeAt.forEach((key, revokedAt) -> {
            java.time.LocalDateTime transferredAt = lastTransferAt.get(key);
            if (transferredAt == null || !revokedAt.isBefore(transferredAt)) {
                revokedByMe.add(key);
            }
        });

        for (VbdenResDocument historyRecord : historyPage.getContent()) {
            Map<String, Object> historyFields = dynamicDocumentFieldsService.parseDynamicFields(
                historyRecord.getDynamicFieldsJson());
            String recordCreator = asString(historyFields.get(NGUOI_THUC_HIEN_FIELD));
            String recordAction = asString(historyFields.get(ACTION_FIELD));
            if (!actingUsername.equals(recordCreator)
                    || !ThuHoiVanBanUseCase.REVOCABLE_ACTIONS.contains(recordAction)) {
                continue;
            }

            collectPending(historyFields.get(ChuyenXuLyVbdenUseCase.USER_PROCESS_OBJ_FIELD_RAW),
                pendingUserProcess, pendingUnitProcess, seenChutri, chutri, memberCache);
            for (String rawField : List.of(ChuyenXuLyVbdenUseCase.USER_PROCESS_OBJ_FIELD_RAW,
                    ChuyenXuLyVbdenUseCase.USER_COORDINATE_OBJ_FIELD_RAW, ChuyenXuLyVbdenUseCase.USER_TO_READ_OBJ_FIELD_RAW)) {
                collectNotRevocable(historyFields.get(rawField), processedUsers, processedUnits, processingUsers, revokedByMe, notRevocable);
            }
            collectPending(historyFields.get(ChuyenXuLyVbdenUseCase.USER_COORDINATE_OBJ_FIELD_RAW),
                pendingUserCoordinate, pendingUnitCoordinate, seenPhoihop, phoihop, memberCache);
            collectPending(historyFields.get(ChuyenXuLyVbdenUseCase.USER_TO_READ_OBJ_FIELD_RAW),
                pendingUserToRead, pendingUnitToRead, seenXemdebiet, xemdebiet, memberCache);
            collectPending(historyFields.get("user_track_obj"),
                pendingUserTrack, List.of(), seenTheodoi, theodoi, memberCache);
        }

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        result.put("chutri", chutri);
        result.put("phoihop", phoihop);
        result.put("xemdebiet", xemdebiet);
        result.put("theodoi", theodoi);
        // 11/10/2026: người/đơn vị ĐÃ nhận từ chính người thao tác nhưng KHÔNG còn thu hồi được (đã xử lý,
        // đã chuyển tiếp hoặc đã bị thu hồi) — FE dùng để giải thích khi danh sách thu hồi rỗng.
        Set<String> stillRevocable = new LinkedHashSet<>();
        stillRevocable.addAll(seenChutri);
        stillRevocable.addAll(seenPhoihop);
        stillRevocable.addAll(seenXemdebiet);
        result.put("khongthethuhoi", notRevocable.entrySet().stream()
            .filter(entry -> !stillRevocable.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList());
        return Result.success(result);
    }

    // Domino getJsonArray(): moi node {username|user_receiver, identifier, name} trong snapshot ma
    // HIEN VAN CON trong pendingUsers (theo username) HOAC pendingUnits (theo identifier) thi duoc
    // THEM vao out (dedup qua "seen") - KHONG loai khoi pendingUsers/pendingUnits (khac
    // ThuHoiVanBanUseCase#revokeFromSnapshot, use case NAY chi DOC, khong thu hoi that).
    private void collectPending(
            Object rawValue, List<String> pendingUsers, List<String> pendingUnits,
            Set<String> seen, List<Map<String, Object>> out, Map<String, Set<String>> memberCache) {
        JsonNode parsed = VbdenObjSnapshotFields.readJsonArray(rawValue);
        for (JsonNode node : parsed) {
            String username = VbdenRevokeRecipientMatcher.username(node);
            String identifier = node.path("identifier").asText("");
            String name = node.path("name").asText("");
            // 11/10/2026: don vi chi "con cho" khi con thanh vien dang giu van ban - xem VbdenRevokeRecipientMatcher.
            if (recipientMatcher.pendingHolders(node, pendingUsers, pendingUnits, memberCache).isEmpty()) {
                continue;
            }
            String key = !username.isBlank() ? "U:" + username : "I:" + identifier;
            if (!seen.add(key)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            if (!name.isBlank()) {
                entry.put("name", name);
            }
            if (!username.isBlank()) {
                entry.put("username", username);
            }
            if (!identifier.isBlank()) {
                entry.put("identifier", identifier);
            }
            out.add(entry);
        }
    }

    private void collectNotRevocable(
            Object rawValue, List<String> processedUsers, List<String> processedUnits, List<String> processingUsers,
            Set<String> revokedByMe, Map<String, Map<String, Object>> out) {
        for (JsonNode node : VbdenObjSnapshotFields.readJsonArray(rawValue)) {
            String username = node.hasNonNull("username") ? node.path("username").asText()
                : node.path("user_receiver").asText("");
            String identifier = node.path("identifier").asText("");
            if (username.isBlank() && identifier.isBlank()) {
                continue;
            }
            String key = !username.isBlank() ? "U:" + username : "I:" + identifier;
            boolean processed = (!username.isBlank() && processedUsers.contains(username))
                || (!identifier.isBlank() && processedUnits.contains(identifier));
            Map<String, Object> entry = new LinkedHashMap<>();
            String name = node.path("name").asText("");
            entry.put("name", name.isBlank() ? (!username.isBlank() ? username : identifier) : name);
            boolean processing = !username.isBlank() && processingUsers.contains(username);
            entry.put("ly_do", processed ? REASON_PROCESSED
                : processing ? REASON_PROCESSING
                : revokedByMe.contains(key) ? REASON_REVOKED : REASON_FORWARDED);
            out.merge(key, entry, (existing, incoming) -> reasonRank(incoming) > reasonRank(existing) ? incoming : existing);
        }
    }

    // Lý do cụ thể hơn thắng khi 1 người xuất hiện ở nhiều lần chuyển.
    private int reasonRank(Map<String, Object> entry) {
        Object reason = entry.get("ly_do");
        return REASON_PROCESSED.equals(reason) ? 3 : REASON_PROCESSING.equals(reason) ? 2
            : REASON_REVOKED.equals(reason) ? 1 : 0;
    }

    private static String recipientKey(JsonNode node) {
        String username = VbdenRevokeRecipientMatcher.username(node);
        String identifier = node.path("identifier").asText("");
        if (!username.isBlank()) {
            return "U:" + username;
        }
        return identifier.isBlank() ? null : "I:" + identifier;
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

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
