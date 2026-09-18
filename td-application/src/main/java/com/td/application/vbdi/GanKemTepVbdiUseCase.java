package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 15/09/2026 ("ký số lưu vết tương tự như BTE, giữ file gốc trước khi ký, file
 * ký lưu mới ... dùng chung cho toàn bộ các module khác sử dụng ký số"): tương đương
 * {@code GanKemTepDuThaoUseCase} (duthao) nhưng cho vbdi — ghi 1 vết {@code vbdi_res} (document con,
 * document_type="vbdi_res", CÙNG bảng vật lý {@code vbdi_documents} — xem {@link VbdiDocument},
 * KHÁC duthao có bảng {@code draft_process_documents} riêng) mang ý kiến, KHÔNG đụng bất kỳ field
 * nào trên văn bản đi cha (khác hẳn {@link TrinhKyVbdiUseCase} — vết đó ĐI KÈM chuyển trạng thái
 * PENDING_SIGNATURE; đây CHỈ là 1 ghi chú, đúng tinh thần {@code CapNhatYKienVbdenUseCase} bên
 * vbden — action không mutate field nghiệp vụ nào).
 *
 * <p>Dùng lại NGUYÊN {@link AddVbdiDynamicDocumentUseCase} (writer chung của MỌI document con vbdi,
 * đã dùng bởi {@link TrinhKyVbdiUseCase}) — không viết writer mới, không thêm bảng vật lý mới.</p>
 *
 * <p><b>Ngữ cảnh gọi</b>: FE gọi endpoint này SAU KHI đã tải tệp đã ký lên qua
 * {@code POST /api/v1/vbdi/KySo} (tệp cũ giữ nguyên, tệp mới đánh dấu {@code isPrimary=true} — xem
 * {@code KySoVbdiUseCase}) — vết ở đây CHỈ mang tính ghi chú lịch sử, không lặp lại việc lưu tệp.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GanKemTepVbdiUseCase {

    /** document_type của MỌI document con vbdi (nhiệm vụ ký duyệt, ghi chú...) — xem TrinhKyVbdiUseCase. */
    public static final String CHILD_DOCUMENT_TYPE = "vbdi_res";
    /** action_type cố định cho vết "ghi chú/đính kèm ý kiến" — cùng tên duthao dùng, không mutate gì thêm. */
    public static final String ACTION_GAN_KEM_TEP = "gankemtep";

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String FALLBACK_USERNAME = "system";
    private static final String DEFAULT_TITLE = "Đính kèm ý kiến văn bản đi";

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final AddVbdiDynamicDocumentUseCase addVbdiDynamicDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;

    public Result<UUID> execute(GanKemTepVbdiRequest request) {
        var documentOptional = vbdiDocumentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (!KySoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(documentOptional.get().getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);

        var processRequest = new AddDynamicDocumentRequest();
        processRequest.setTitle(DEFAULT_TITLE);
        processRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("parentid", request.getId().toString());
        fields.put("action_type", ACTION_GAN_KEM_TEP);
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            fields.put("opinion", request.getOpinion());
        }
        fields.put("creator", actingUsername);
        processRequest.setFields(fields);

        return addVbdiDynamicDocumentUseCase.execute(processRequest);
    }
}
