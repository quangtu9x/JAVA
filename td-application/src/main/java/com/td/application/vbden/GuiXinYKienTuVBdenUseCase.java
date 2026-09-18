package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.vbden.XinYKienDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 18/09/2026 (cutover "Gửi xin ý kiến từ vbden" — {@code VBDenButtonActionXuLy.jsx#handleXinYKien},
 * trước đó gọi Domino {@code qlvb/vbden/GuiXinYKienTuVBden} trực tiếp): tương đương
 * {@code GuiXinYKienTuVBden.java} thật — khác {@link GuiXinYKienUseCase} (nguồn = vbdi) ở chỗ nguồn
 * là CHÍNH văn bản đến ({@code idvbden}), và danh sách người nhận đến TỪ REQUEST (FE tự chọn qua
 * {@code YKienTVUBTable.jsx}), không đọc sẵn trên document nguồn.
 *
 * <p><b>Kiến trúc</b> (đọc trực tiếp {@code GuiXinYKienTuVBden.java} — hàm {@code tao_vbden} có guard
 * {@code if(copyToDoc==null)}): 1 DOCUMENT XIN Ý KIẾN DÙNG CHUNG cho TOÀN BỘ người nhận của 1 văn bản
 * vbden nguồn (không phải 1 doc/người) — giống hệt kiến trúc {@link GuiXinYKienUseCase}, chỉ khác khoá
 * dedup: {@code vbden_id} (= {@code idvbden}) thay vì {@code id_documentout} (= idvbdi).</p>
 *
 * <p><b>5 cờ {@code is_*_opinion}</b>: Domino gốc ({@code tao_vbden}, dòng ~232) HARDCODE
 * {@code is_tvnb_opinion="true"} khi TẠO MỚI, không đọc bất kỳ cờ nào FE gửi — port ĐÚNG hành vi
 * thật (xem javadoc {@link GuiXinYKienTuVBdenRequest}), không suy diễn thêm phân nhánh TVUB/TVNB/
 * BTV/BCH/TTĐU như nhánh vbdi.</p>
 *
 * <p><b>Whitelist field copy khi TẠO MỚI</b> (đúng {@code itemsToCopy} trong {@code tao_vbden}, COPY
 * VERBATIM — khác {@link GuiXinYKienUseCase} có đổi tên 1 số field):
 * {@code code, unit_in_charge, status, lanhdaochutri, document_type, subject, confidentiality,
 * priority, delivery_method, position, signer, signer_position, page_amount, sector,
 * promulgation_date, deadline_date, draftor}.</p>
 *
 * <p><b>Xử lý danh sách người nhận</b> (đúng {@code XuLyTVUB()}): mỗi node {@code {identifier,
 * username, xinykien}} trong {@code listTvubObj} — {@code xinykien=="1"} → thêm {@code username} vào
 * {@code user_process} (dedup) + set {@code username_send}/{@code time} CHỈ khi node CHƯA có (giữ
 * đúng "lần gửi đầu tiên"); khác {@code "1"} → loại khỏi {@code user_process}. Nếu request gửi
 * {@code listTvubObj} rỗng VÀ đã có document cũ → fallback đọc {@code list_tvub_obj} hiện có trên
 * document đó (đúng Domino dòng 90-91, trường hợp "gửi lại" không đổi danh sách).</p>
 *
 * <p><b>{@code content}/{@code deadline_date}</b>: nếu document đã có giá trị KHÁC giá trị mới, nối
 * lịch sử vào {@code contents}/{@code deadline_dates} (dạng {@code "cũ-->mới"}) trước khi ghi đè —
 * đúng Domino. {@code user_creator}/{@code creator} luôn set lại = người đang thao tác, cùng cơ chế
 * nối lịch sử vào {@code user_creators}/{@code creators} nếu khác người tạo trước đó.</p>
 *
 * <p><b>Ghi lại {@code xinykien_id} lên văn bản vbden NGUỒN</b> — ĐÚNG Domino
 * ({@code docvbden.replaceItemValue("xinykien_id",...)}) và THẬT SỰ cần thiết: {@code
 * YKienTVUBTable.jsx#fetchData} (dùng chung vbdi+vbden) đọc {@code resdata?.xinykien_id} để tải lại
 * trạng thái đã gửi — KHÔNG phải tuỳ chọn.</p>
 *
 * <p><b>KHÔNG port</b> (ghi rõ, nhất quán với đúng giới hạn {@link GuiXinYKienUseCase} đã chấp nhận
 * cho nhánh vbdi): copy file đính kèm sang {@code vbden_file} (Java dùng hạ tầng file hợp nhất theo
 * {@code document_id}, không cần bảng riêng theo doctype); {@code YKienTVUBTable.jsx}'s fetch lại
 * theo {@code xinykien_id} vẫn gọi {@code qlvb/get?databasetype=vbden} (Domino) — CÙNG giới hạn y hệt
 * đã chấp nhận cho nhánh {@code documentout_id} (vbdi) hiện có, không mở rộng phạm vi việc cutover
 * hành động "gửi" này sang luôn cả đường đọc lại của 1 component dùng chung.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GuiXinYKienTuVBdenUseCase {

    static final String DOCUMENT_TYPE = ThuHoiVanBanUseCase.DOCUMENT_TYPE;
    private static final String XINYKIEN_DOCUMENT_TYPE = ChoYKienXinYKienUseCase.DOCUMENT_TYPE;
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";
    private static final String VBDEN_ID_FIELD = "vbden_id";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String LIST_TVUB_OBJ_FIELD = "list_tvub_obj";
    private static final String LIST_EDIT_TVUB_OBJ_FIELD = "list_edit_tvub_obj";
    private static final String XINYKIEN_FLAG = "1";
    private static final DateTimeFormatter DD_MM_YYYY_HH_MM_SS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Set<String> COPY_FIELDS = Set.of(
        "code", "unit_in_charge", "status", "lanhdaochutri", "document_type", "subject",
        "confidentiality", "priority", "delivery_method", "position", "signer", "signer_position",
        "page_amount", "sector", "promulgation_date", "deadline_date", "draftor");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentRepository documentRepository;
    private final XinYKienDocumentRepository xinYKienDocumentRepository;
    private final AddXinYKienDynamicDocumentUseCase addXinYKienDynamicDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(GuiXinYKienTuVBdenRequest request) {
        var sourceOptional = documentRepository.findById(request.getIdvbden());
        if (sourceOptional.isEmpty() || sourceOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var source = sourceOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(source.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> sourceFields = dynamicDocumentFieldsService.parseDynamicFields(source.getDynamicFieldsJson());
        XinYKienDocument target = findExisting(request.getIdvbden()).orElse(null);
        boolean isNew = target == null;
        Map<String, Object> targetFields = isNew
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(target.getDynamicFieldsJson()));

        if (isNew) {
            copyWhitelistedFields(targetFields, sourceFields);
            targetFields.put(VBDEN_ID_FIELD, request.getIdvbden().toString());
            targetFields.put("status", "0");
            targetFields.put("document_internet", "1");
            targetFields.put("is_tvnb_opinion", "true");
            targetFields.put("received_date", LocalDateTime.now().format(DD_MM_YYYY_HH_MM_SS));
            targetFields.put("reply", "0");
        }

        // Bugfix: KHONG duoc asString(...) gia tri fallback truoc khi dua vao readJsonArray - field
        // "_obj" co the da duoc luu dang List/Map long (khong phai chuoi JSON), asString() se goi
        // Object#toString() (dung "=" khong phai ":") lam readJsonArray khong parse duoc gi (dung bay
        // da ghi trong memory "vbden-obj-field-json-vs-string-trap"). Giu NGUYEN kieu Object cho toi
        // luc dua vao readJsonArray (chap nhan CA String LAN List/Map).
        Object listTvubObjSource = request.getListTvubObj();
        if ((listTvubObjSource == null || ((String) listTvubObjSource).isBlank()) && !isNew) {
            listTvubObjSource = targetFields.get(LIST_TVUB_OBJ_FIELD);
        }
        ArrayNode recipients = (ArrayNode) VbdenObjSnapshotFields.readJsonArray(listTvubObjSource);

        List<String> userProcess = toStringList(targetFields.get(USER_PROCESS_FIELD));
        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        String nowStr = LocalDateTime.now().format(DD_MM_YYYY_HH_MM_SS);
        for (var node : recipients) {
            if (!(node instanceof ObjectNode objectNode)) {
                continue;
            }
            String username = objectNode.path("username").asText("");
            String selected = objectNode.path("xinykien").asText("");
            if (username.isBlank()) {
                continue;
            }
            if (XINYKIEN_FLAG.equals(selected)) {
                if (!userProcess.contains(username)) {
                    userProcess.add(username);
                }
                if (!objectNode.hasNonNull("username_send") || objectNode.path("username_send").asText("").isBlank()) {
                    objectNode.put("username_send", actingUsername == null ? "" : actingUsername);
                }
                if (!objectNode.hasNonNull("time") || objectNode.path("time").asText("").isBlank()) {
                    objectNode.put("time", nowStr);
                }
            } else {
                userProcess.remove(username);
            }
        }
        targetFields.put(USER_PROCESS_FIELD, userProcess);
        targetFields.put(LIST_TVUB_OBJ_FIELD, toJsonString(recipients));

        if (request.getListEditTvubObj() != null && !request.getListEditTvubObj().isBlank()) {
            targetFields.put(LIST_EDIT_TVUB_OBJ_FIELD, request.getListEditTvubObj());
        }

        appendHistoryIfChanged(targetFields, "content", "contents", request.getContent());
        appendHistoryIfChanged(targetFields, "deadline_date", "deadline_dates", request.getDeadlineDate());
        applyCreatorFields(targetFields, actingUsername);

        Result<UUID> saveResult = isNew
            ? createNew(source, targetFields)
            : updateExisting(target, targetFields);
        if (!saveResult.isSuccess()) {
            return saveResult;
        }

        writeBackXinYKienId(source, saveResult.getData());
        return saveResult;
    }

    private Optional<XinYKienDocument> findExisting(UUID idvbden) {
        Page<XinYKienDocument> page = xinYKienDocumentRepository.findByAnyFieldContains(
            XINYKIEN_DOCUMENT_TYPE, List.of(VBDEN_ID_FIELD), idvbden.toString(), PageRequest.of(0, 1));
        return page.getContent().stream().findFirst();
    }

    private void copyWhitelistedFields(Map<String, Object> target, Map<String, Object> source) {
        for (String field : COPY_FIELDS) {
            if (source.containsKey(field)) {
                target.put(field, source.get(field));
            }
        }
    }

    // Domino XuLyTVUB(): "content"/"deadline_date" khac gia tri cu -> noi lich su "cu-->moi" vao
    // field lich su tuong ung TRUOC khi ghi de.
    private void appendHistoryIfChanged(Map<String, Object> fields, String field, String historyField, String newValue) {
        if (newValue == null || newValue.isBlank()) {
            return;
        }
        String current = asString(fields.get(field));
        if (current != null && !current.isBlank() && !current.equals(newValue)) {
            String history = asString(fields.get(historyField));
            if (history == null || history.isBlank()) {
                history = current;
            }
            fields.put(historyField, history + "-->" + newValue);
        }
        fields.put(field, newValue);
    }

    // Domino XuLyTVUB(): user_creator/creator LUON ghi de = nguoi dang thao tac, noi lich su neu khac
    // nguoi tao truoc do.
    private void applyCreatorFields(Map<String, Object> fields, String actingUsername) {
        if (actingUsername == null || actingUsername.isBlank()) {
            return;
        }
        String previousUsername = asString(fields.get("user_creator"));
        if (previousUsername != null && !previousUsername.isBlank() && !previousUsername.equals(actingUsername)) {
            String histories = asString(fields.get("user_creators"));
            fields.put("user_creators", (histories == null || histories.isBlank() ? previousUsername : histories)
                + "-->" + actingUsername);
        }
        fields.put("user_creator", actingUsername);

        String actingFullName = appUserRepository.findByUsernameAndDeletedOnIsNull(actingUsername)
            .map(AppUser::getFullName).orElse(actingUsername);
        String previousCreator = asString(fields.get("creator"));
        if (previousCreator != null && !previousCreator.isBlank() && !previousCreator.equals(actingFullName)) {
            String creators = asString(fields.get("creators"));
            fields.put("creators", (creators == null || creators.isBlank() ? previousCreator : creators)
                + "-->" + actingFullName);
        }
        fields.put("creator", actingFullName);
    }

    private Result<UUID> createNew(BusinessDocument source, Map<String, Object> targetFields) {
        var createRequest = new AddDynamicDocumentRequest();
        createRequest.setTitle(source.getTitle());
        createRequest.setDocumentType(XINYKIEN_DOCUMENT_TYPE);
        createRequest.setFields(toStringFieldMap(targetFields));
        createRequest.setDateFields("");
        createRequest.setNumberFields("");
        createRequest.setRichtextFields("");
        return addXinYKienDynamicDocumentUseCase.execute(createRequest);
    }

    private Result<UUID> updateExisting(XinYKienDocument target, Map<String, Object> targetFields) {
        var before = buildSnapshot(target);
        target.replaceDynamicFields(toJson(targetFields));
        var saved = xinYKienDocumentRepository.save(target);
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        return Result.success(saved.getId());
    }

    private void writeBackXinYKienId(BusinessDocument source, UUID xinYKienId) {
        Map<String, Object> before = dynamicDocumentFieldsService.parseDynamicFields(source.getDynamicFieldsJson());
        Map<String, Object> sourceFields = new LinkedHashMap<>(before);
        sourceFields.put("xinykien_id", xinYKienId.toString());
        source.replaceDynamicFields(toJson(sourceFields));
        var savedSource = documentRepository.save(source);
        documentCacheService.evict(savedSource.getId());
        documentLogService.logUpdate(savedSource.getId(), before, sourceFields);
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

    private Map<String, String> toStringFieldMap(Map<String, Object> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            result.put(entry.getKey(), value instanceof List || value instanceof Map ? toJson(value) : value.toString());
        }
        return result;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Map<String, Object> buildSnapshot(XinYKienDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String toJsonString(ArrayNode value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
