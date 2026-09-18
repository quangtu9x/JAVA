package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.SoanThaoVbdiUseCase;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.vbden.XinYKienDocument;
import com.td.domain.vbdi.VbdiDocument;
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
 * N08 (31/08/2026 — "cho ý kiến" TVUB/TVNB/BCH/BTV/TTĐU): tương đương {@code GuiXinYKien.java}/
 * {@code GuiXinYKienTuVBden.java} phía Domino — khi phát hành/xử lý văn bản đi (vbdi) cần xin ý kiến
 * TVUB/TVNB, tự tạo (hoặc cập nhật, nếu đã gửi trước đó) 1 "văn bản đến giả"
 * ({@code document_type="vbden_xinykien"}, bảng riêng {@code vbden_xinykien_documents} — xem javadoc
 * {@link XinYKienDocument}) để mỗi người trong danh sách xử lý như 1 văn bản đến bình thường qua 14
 * view {@code ViewQueryService} đã port.
 *
 * <p><b>Nguồn danh sách người nhận</b>: xác nhận qua đọc trực tiếp FE thật
 * ({@code ChiTietModal.jsx#handleGuiXinYKien}/{@code handleGuiXinYKienTVNB}) — payload gửi lên CHỈ
 * có {@code idvbdi} (+{@code noibo} cho biến thể TVNB), KHÔNG có danh sách người nhận. Server tự đọc
 * field {@code list_tvub_obj} ({@code noibo=false}) hoặc {@code list_tvnb_obj} ({@code noibo=true})
 * — mảng JSON đã có sẵn TRÊN CHÍNH document vbdi (được ghi khi người dùng chọn thành viên TVUB/TVNB
 * ở màn soạn thảo vbdi, qua hạ tầng Dynamic Data Engine chung — field kết thúc {@code _obj} tự suy
 * luận kiểu JSON, không cần code riêng để LƯU field này, chỉ cần ĐỌC LẠI ở đây). Mỗi phần tử dạng
 * {@code {identifier, xinykien, username, ...}} — {@code xinykien="1"} nghĩa là THÊM người này vào
 * {@code user_process} (đang chờ cho ý kiến), khác "1" nghĩa là BỎ (đã huỷ chọn).
 *
 * <p><b>Dedup theo nguồn</b>: Domino dùng {@code copyToDoc.setUniversalID(docvbdi.getUniversalID())}
 * — ép ID trùng để "gửi lại" luôn cập nhật ĐÚNG 1 document xin ý kiến duy nhất cho 1 văn bản đi.
 * Java dùng UUID sinh tự động (không gán tay được) nên dedup qua field động {@code id_documentout}
 * (đã copy verbatim từ Domino, xem {@code data_xinykien_mau.txt}) = {@code idvbdi} — tìm bằng
 * {@code xinYKienDocumentRepository.findByAnyFieldContains(..., List.of("id_documentout"), ...)}
 * trước khi quyết định tạo mới hay cập nhật.
 *
 * <p><b>Field copy từ vbdi</b> (whitelist ĐÚNG {@code GuiXinYKien.java}, dòng
 * {@code itemsToCopy}): document_type, subject, confidentiality, priority, delivery_method,
 * position, signer, signer_position, page_amount, sector, creator, promulgation_date, deadline_date,
 * draftor — CỘNG {@code unit_in_charge}/{@code unit_in_charge_identifier} (từ
 * {@code unit_publish}/{@code unit_publish_identifier} của vbdi — ĐỔI TÊN, không copy verbatim),
 * {@code unit_draftor} (từ {@code unit_in_charge} của vbdi), {@code code} (từ {@code code_full} của
 * vbdi). Field CHỈ set khi TẠO MỚI (không ghi đè lần gửi lại sau): {@code status="0"},
 * {@code document_internet="1"}, {@code received_date=hiện tại}, {@code reply="0"},
 * {@code id_documentout=idvbdi}.
 *
 * <p><b>CHƯA port</b> (ghi rõ, không suy đoán): tạo/cập nhật {@code list_tvub_obj} NGƯỢC LẠI trên
 * chính document vbdi (đánh dấu ai đã có văn bản xin ý kiến, kèm {@code idvbden}/{@code username_send}/
 * {@code time}) — Domino {@code XuLyTVUB()} làm việc này; đọc lại {@code GuiXinYKien.java} xác nhận
 * bước này CHỈ ảnh hưởng hiển thị phía vbdi (theo dõi đã gửi ai chưa), không ảnh hưởng việc người
 * nhận có thấy văn bản trong {@code chochoykien} hay không (điều kiện view chỉ dựa vào
 * {@code user_process} trên CHÍNH document xinykien, đã port đủ) — để dành slice sau nếu cần màn
 * theo dõi phía vbdi.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GuiXinYKienUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String XINYKIEN_DOCUMENT_TYPE = ChoYKienXinYKienUseCase.DOCUMENT_TYPE;
    private static final String ID_DOCUMENTOUT_FIELD = "id_documentout";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String LIST_TVUB_OBJ_FIELD = "list_tvub_obj";
    private static final String LIST_TVNB_OBJ_FIELD = "list_tvnb_obj";
    private static final String OPINION_FLAG_TVUB = "is_tvub_opinion";
    private static final String OPINION_FLAG_TVNB = "is_tvnb_opinion";
    private static final String XINYKIEN_FLAG = "1";
    private static final DateTimeFormatter DD_MM_YYYY_HH_MM_SS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Set<String> COPY_FIELDS = Set.of(
        "document_type", "subject", "confidentiality", "priority", "delivery_method", "position",
        "signer", "signer_position", "page_amount", "sector", "creator", "promulgation_date",
        "deadline_date", "draftor");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final XinYKienDocumentRepository xinYKienDocumentRepository;
    private final AddXinYKienDynamicDocumentUseCase addXinYKienDynamicDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(GuiXinYKienRequest request) {
        var sourceOptional = vbdiDocumentRepository.findById(request.getIdvbdi());
        if (sourceOptional.isEmpty() || sourceOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var source = sourceOptional.get();
        if (!SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(source.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> sourceFields = dynamicDocumentFieldsService.parseDynamicFields(source.getDynamicFieldsJson());
        String listFieldName = request.isNoibo() ? LIST_TVNB_OBJ_FIELD : LIST_TVUB_OBJ_FIELD;
        String opinionFlagField = request.isNoibo() ? OPINION_FLAG_TVNB : OPINION_FLAG_TVUB;
        List<Map<String, Object>> recipients = parseRecipients(sourceFields.get(listFieldName));
        if (recipients.isEmpty()) {
            return Result.failure("Chưa chọn thành viên cần xin ý kiến");
        }

        XinYKienDocument target = findExisting(request.getIdvbdi()).orElse(null);
        boolean isNew = target == null;
        Map<String, Object> targetFields = isNew
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(target.getDynamicFieldsJson()));

        copyWhitelistedFields(targetFields, sourceFields);
        targetFields.put("unit_in_charge_identifier", asString(sourceFields.get("unit_publish_identifier")));
        targetFields.put("unit_in_charge", asString(sourceFields.get("unit_publish")));
        targetFields.put("unit_draftor", asString(sourceFields.get("unit_in_charge")));
        targetFields.put("code", asString(sourceFields.get("code_full")));
        targetFields.put(opinionFlagField, "true");

        List<String> userProcess = toStringList(targetFields.get(USER_PROCESS_FIELD));
        for (Map<String, Object> recipient : recipients) {
            String username = asString(recipient.get("username"));
            if (username == null || username.isBlank()) {
                continue;
            }
            boolean selected = XINYKIEN_FLAG.equals(asString(recipient.get("xinykien")));
            if (selected) {
                if (!userProcess.contains(username)) {
                    userProcess.add(username);
                }
            } else {
                userProcess.remove(username);
            }
        }
        targetFields.put(USER_PROCESS_FIELD, userProcess);

        if (isNew) {
            String now = LocalDateTime.now().format(DD_MM_YYYY_HH_MM_SS);
            targetFields.put("document_internet", "1");
            targetFields.put("received_date", now);
            targetFields.put("reply", "0");
            targetFields.put(ID_DOCUMENTOUT_FIELD, request.getIdvbdi().toString());

            var createRequest = new AddDynamicDocumentRequest();
            createRequest.setTitle(source.getTitle());
            createRequest.setDocumentType(XINYKIEN_DOCUMENT_TYPE);
            createRequest.setFields(toStringFieldMap(targetFields));
            createRequest.setDateFields("");
            createRequest.setNumberFields("");
            createRequest.setRichtextFields("");
            return addXinYKienDynamicDocumentUseCase.execute(createRequest);
        }

        var before = buildSnapshot(target);
        target.replaceDynamicFields(toJson(targetFields));
        var saved = xinYKienDocumentRepository.save(target);
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        return Result.success(saved.getId());
    }

    private Optional<XinYKienDocument> findExisting(UUID idvbdi) {
        Page<XinYKienDocument> page = xinYKienDocumentRepository.findByAnyFieldContains(
            XINYKIEN_DOCUMENT_TYPE, List.of(ID_DOCUMENTOUT_FIELD), idvbdi.toString(), PageRequest.of(0, 1));
        return page.getContent().stream().findFirst();
    }

    private void copyWhitelistedFields(Map<String, Object> target, Map<String, Object> source) {
        for (String field : COPY_FIELDS) {
            if (source.containsKey(field)) {
                target.put(field, source.get(field));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRecipients(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                List<Map<String, Object>> parsed = OBJECT_MAPPER.readValue(
                    text, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() { });
                result.addAll(parsed);
            } catch (Exception ex) {
                log.warn("Khong parse duoc {}: {}", value, ex.getMessage());
            }
        }
        return result;
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
}
