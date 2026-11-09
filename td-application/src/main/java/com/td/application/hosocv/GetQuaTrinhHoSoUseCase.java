package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.UserFullNameResolver;
import com.td.domain.hosocv.HoSoCVProcessDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 30/10/2026: panel "Quá trình duyệt" / "Thông tin chuyển lưu trữ" ở chi tiết hồ sơ
 * (QuaTrinhDuyetItemsList/QuaTrinhChuyenLuuTruItemsList.jsx) — thay Domino {@code qlvb/view/query}
 * (hosocv_res, view vwQuaTrinhDuyet_/vwQuaTrinhChuyenLuuTru_Document_Profiles_ID). Đọc
 * {@code hosocv_process_documents} rồi đổi về đúng field FE đang đọc:
 * {@code create_date, creator, receiver, action_type, subject}. Mới nhất trước.
 * <ul>
 *   <li>{@code loai=duyet}: trinhduyet → "2" (thu hồi → "0"), duyet → "1"/"3".</li>
 *   <li>{@code loai=chuyenluutru}: chuyenluutru → action_type đã ghi (tiếp nhận/từ chối), mặc định "2".</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetQuaTrinhHoSoUseCase {

    public static final String LOAI_DUYET = "duyet";
    public static final String LOAI_CHUYEN_LUU_TRU = "chuyenluutru";

    private final HoSoCVProcessDocumentRepository hoSoCVProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final UserFullNameResolver userFullNameResolver;

    public Result<List<Map<String, Object>>> execute(UUID hosocvId, String loai) {
        if (hosocvId == null) {
            return Result.success(List.of());
        }
        boolean isChuyenLuuTru = LOAI_CHUYEN_LUU_TRU.equalsIgnoreCase(loai == null ? "" : loai.trim());
        Map<String, String> nameCache = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (HoSoCVProcessDocument process : hoSoCVProcessDocumentRepository.findByHoSoCVId(hosocvId)) {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(process.getDynamicFieldsJson());
            String processType = text(fields, "process_type");
            String actionType = isChuyenLuuTru ? chuyenLuuTruActionType(processType, fields) : duyetActionType(processType, fields);
            if (actionType == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", process.getId());
            item.put("create_date", process.getCreatedOn());
            item.put("creator", displayName(text(fields, "creator"), nameCache));
            String receiverUsername = text(fields, "receiver_username");
            item.put("receiver", receiverUsername.isBlank() ? text(fields, "receiver") : displayName(receiverUsername, nameCache));
            item.put("action_type", actionType);
            item.put("subject", firstNonBlank(text(fields, "subject"), text(fields, "opinion"), text(fields, "note")));
            item.put("process_type", processType);
            result.add(item);
        }
        Collections.reverse(result); // repository trả cũ → mới; panel hiển thị mới nhất trước như Domino
        return Result.success(result);
    }

    private String duyetActionType(String processType, Map<String, Object> fields) {
        if ("trinhduyet".equals(processType)) {
            return "thuhoi".equals(text(fields, "action")) ? "0" : "2";
        }
        if ("duyet".equals(processType)) {
            return "khongduyet".equals(text(fields, "action")) ? "3" : "1";
        }
        return null;
    }

    private String chuyenLuuTruActionType(String processType, Map<String, Object> fields) {
        if (!"chuyenluutru".equals(processType)) {
            return null;
        }
        String actionType = text(fields, "action_type");
        return actionType.isBlank() ? "2" : actionType;
    }

    private String displayName(String username, Map<String, String> cache) {
        if (username == null || username.isBlank()) {
            return "";
        }
        return cache.computeIfAbsent(username, key -> {
            String fullName = userFullNameResolver.fullNameOrNull(key);
            return fullName != null ? fullName : key;
        });
    }

    private static String text(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
