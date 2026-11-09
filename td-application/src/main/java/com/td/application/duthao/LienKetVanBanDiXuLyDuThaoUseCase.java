package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.UserDisplayInfoService;
import com.td.application.vbdi.SoanThaoVbdiUseCase;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.vbdi.VbdiDocument;
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
 * 30/10/2026: thay lời gọi Domino {@code qlvb/add?id={vbdiId}} ở {@code duthao/toanbo/ChiTietPage.jsx}
 * (nhánh "Tạm thêm để đủ UC QB" — dự thảo tạo từ màn xử lý văn bản đi, prop {@code idlink}). Sau khi
 * lưu dự thảo, ghi lên văn bản đi: {@code draft_id}, {@code draft_identifier_id} (= tên dự thảo), gỡ
 * người dùng hiện tại khỏi {@code chuyenxuly}/{@code userchuyenxuly} (hàng đợi
 * {@code VIEW_VBDI_XULY}) và thêm vào {@code userdachuyenxuly}.
 *
 * <p>Người dùng lấy phía server. Chỉ chấp nhận khi dự thảo thật sự tạo từ văn bản đi đó
 * ({@code document_vbdi_idlink} trên dự thảo = {@code vbdiId}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LienKetVanBanDiXuLyDuThaoUseCase {

    private static final String FALLBACK_USERNAME = "system";
    private static final String IDLINK_FIELD = "document_vbdi_idlink";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final UserDisplayInfoService userDisplayInfoService;

    public Result<UUID> execute(LienKetVanBanDiXuLyDuThaoRequest request) {
        if (request == null || request.getDraftId() == null || request.getVbdiId() == null) {
            return Result.failure("Thiếu dự thảo hoặc văn bản đi");
        }
        var draft = duThaoDocumentRepository.findById(request.getDraftId()).orElse(null);
        if (draft == null || draft.isDeleted()
                || !TaoDuThaoUseCase.DOCUMENT_TYPE.equalsIgnoreCase(draft.getDocumentType())) {
            return Result.failure("Không tìm thấy văn bản dự thảo");
        }
        Map<String, Object> draftFields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        String idlink = textIgnoreCase(draftFields, IDLINK_FIELD);
        if (!request.getVbdiId().toString().equalsIgnoreCase(idlink.trim())) {
            return Result.failure("Dự thảo không được tạo từ văn bản đi này");
        }

        VbdiDocument vbdi = vbdiDocumentRepository.findById(request.getVbdiId()).orElse(null);
        if (vbdi == null || vbdi.isDeleted()
                || !SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(vbdi.getDocumentType())) {
            return Result.failure("Không tìm thấy văn bản đi");
        }

        String username = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        String fullName = userDisplayInfoService.resolve(username).fullName();

        Map<String, Object> before = buildSnapshot(vbdi);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson()));
        fields.put("draft_id", draft.getId().toString());
        String draftName = textIgnoreCase(draftFields, "name");
        if (!draftName.isBlank()) {
            fields.put("draft_identifier_id", draftName);
        }
        fields.put("chuyenxuly", removeMember(text(fields.get("chuyenxuly")), fullName));
        fields.put("userchuyenxuly", removeMember(text(fields.get("userchuyenxuly")), username));
        fields.put("userdachuyenxuly", addMember(text(fields.get("userdachuyenxuly")), username));
        vbdi.replaceDynamicFields(toJson(fields));

        var saved = vbdiDocumentRepository.save(vbdi);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        log.info("Lien ket du thao {} voi van ban di xu ly {} boi {}", draft.getId(), saved.getId(), username);
        return Result.success(saved.getId());
    }

    private String textIgnoreCase(Map<String, Object> fields, String key) {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return text(entry.getValue());
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> members(String raw) {
        List<String> result = new ArrayList<>();
        for (String part : raw.split(";")) {
            if (!part.trim().isEmpty()) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private String removeMember(String raw, String member) {
        if (member == null || member.isBlank()) {
            return raw;
        }
        List<String> kept = new ArrayList<>();
        for (String item : members(raw)) {
            if (!item.equalsIgnoreCase(member.trim())) {
                kept.add(item);
            }
        }
        return String.join(";", kept);
    }

    private String addMember(String raw, String member) {
        List<String> items = members(raw);
        if (items.stream().noneMatch(item -> item.equalsIgnoreCase(member))) {
            items.add(member);
        }
        return String.join(";", items);
    }

    private Map<String, Object> buildSnapshot(VbdiDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
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
