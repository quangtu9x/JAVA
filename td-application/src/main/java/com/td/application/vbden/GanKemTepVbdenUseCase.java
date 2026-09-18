package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 15/09/2026 ("ký số lưu vết tương tự như BTE, giữ file gốc trước khi ký, file
 * ký lưu mới ... dùng chung cho toàn bộ các module khác sử dụng ký số"): tương đương
 * {@code GanKemTepDuThaoUseCase} (duthao)/{@code GanKemTepVbdiUseCase} (vbdi) nhưng cho vbden — ghi
 * 1 vết {@code vbden_res} mang ý kiến, KHÔNG mutate field nghiệp vụ nào trên văn bản đến cha, KHÔNG
 * tính lại status — CÙNG tinh thần {@link CapNhatYKienVbdenUseCase} (action {@code capnhatykien}
 * cũng chỉ ghi 1 dòng {@code vbden_res} thuần tuý, không side-effect gì khác — xem javadoc lớp đó,
 * đây chính là bằng chứng "ghi 1 dòng opinion-only vào vbden_res" là pattern AN TOÀN đã có tiền lệ
 * thật, không phải suy đoán).
 *
 * <p>Dùng lại NGUYÊN {@link AddVbdenResDocumentUseCase} (writer chung của {@code vbden_res}) — không
 * viết writer mới, không thêm bảng vật lý mới.</p>
 *
 * <p><b>Ngữ cảnh gọi</b>: FE gọi endpoint này SAU KHI đã tải tệp đã ký lên qua
 * {@code POST /api/v1/vbden/KySo} (tệp cũ giữ nguyên, tệp mới đánh dấu {@code isPrimary=true} — xem
 * {@code KySoVbdenUseCase}) — vết ở đây CHỈ mang tính ghi chú lịch sử.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GanKemTepVbdenUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    /** action_type cố định cho vết "ghi chú/đính kèm ý kiến" — cùng tên duthao/vbdi dùng. */
    public static final String ACTION_GAN_KEM_TEP = "gankemtep";

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";
    private static final String FALLBACK_USERNAME = "system";
    private static final String DEFAULT_TITLE = "Đính kèm ý kiến văn bản đến";
    private static final String CHILD_DOCUMENT_TYPE = "vbden_res";

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;

    public Result<UUID> execute(GanKemTepVbdenRequest request) {
        var documentOptional = documentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (!DOCUMENT_TYPE.equalsIgnoreCase(documentOptional.get().getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);

        var processRequest = new AddDynamicDocumentRequest();
        processRequest.setTitle(DEFAULT_TITLE);
        processRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("parentid", request.getId().toString());
        fields.put("action", ACTION_GAN_KEM_TEP);
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            fields.put("opinion", request.getOpinion());
        }
        fields.put("creator", actingUsername);
        processRequest.setFields(fields);

        return addVbdenResDocumentUseCase.execute(processRequest);
    }
}
