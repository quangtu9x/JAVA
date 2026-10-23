package com.td.application.duthao;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DraftProcessDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 09/10/2026 (yêu cầu người dùng "khi dự thảo đã trình ký duyệt thì chỉ hiển thị chức năng thu hồi
 * nếu người ký chưa thao tác"): đọc vết quá trình dự thảo (mới nhất trước) tới lần
 * {@code chuyenkyduyet} gần nhất để biết (1) ai đã trình ký và (2) người ký ({@code user_signer}) đã
 * ghi vết nào SAU lần trình đó chưa. Ký số (TDPdfViewer SIM/USB) và Gắn kèm tệp/ý kiến đều ghi vết
 * {@code gankemtep} do chính người ký tạo nên được tính là "đã thao tác" dù trạng thái vẫn là 1.
 */
@Service
@RequiredArgsConstructor
public class DuThaoSignSubmissionInspector {

    public static final String ACTION_CHUYEN_KY_DUYET = "chuyenkyduyet";
    private static final int MAX_PROCESS_RECORDS = 500;

    private final DraftProcessDocumentRepository draftProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public record Inspection(String submitterUsername, boolean signerActed) {
    }

    public Inspection inspect(UUID draftId, String signerUsername) {
        String signer = signerUsername == null ? "" : signerUsername.trim();
        String submitter = "";
        boolean signerActed = false;
        var records = draftProcessDocumentRepository
            .findByParentId(draftId.toString(), PageRequest.of(0, MAX_PROCESS_RECORDS))
            .getContent();
        for (DraftProcessDocument record : records) {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(record.getDynamicFieldsJson());
            String actor = text(fields.get(DraftProcessActorFields.USER_CREATOR_FIELD));
            if (actor.isEmpty()) {
                actor = text(fields.get(DraftProcessActorFields.CREATOR_FIELD));
            }
            if (ACTION_CHUYEN_KY_DUYET.equalsIgnoreCase(text(fields.get("action_type")))) {
                submitter = actor;
                break;
            }
            if (!signer.isEmpty() && signer.equalsIgnoreCase(actor)) {
                signerActed = true;
            }
        }
        return new Inspection(submitter, signerActed);
    }

    /**
     * 19/10/2026 ("Kết thúc" nhầm -> Thu hồi, chỉ người kết thúc): tài khoản người thao tác vết
     * {@code ketthuc} MỚI NHẤT (records sắp mới nhất trước). Dùng cho dự thảo kết thúc TRƯỚC khi có field
     * {@code user_ended}. Rỗng nếu không tìm thấy.
     */
    public String endedBy(UUID draftId) {
        var records = draftProcessDocumentRepository
            .findByParentId(draftId.toString(), PageRequest.of(0, MAX_PROCESS_RECORDS))
            .getContent();
        for (DraftProcessDocument record : records) {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(record.getDynamicFieldsJson());
            if ("ketthuc".equalsIgnoreCase(text(fields.get("action_type")))) {
                String actor = text(fields.get(DraftProcessActorFields.USER_CREATOR_FIELD));
                return actor.isEmpty() ? text(fields.get(DraftProcessActorFields.CREATOR_FIELD)) : actor;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
