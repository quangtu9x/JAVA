package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-240 (HoSoCV Core) — DoD (1): tạo hồ sơ công việc. Không có servlet tạo hồ sơ riêng evidenced
 * ({@code HoSoCVController.java} chỉ xử lý gắn văn bản/download file/token LGSP) — đúng tinh thần
 * đã áp dụng nhiều lần (BL-210/BL-220/BL-230/BL-250: "tạo văn bản/hồ sơ" đi qua cơ chế
 * {@code CreateOrUpdateDocument()} tổng quát khi không có servlet riêng).
 *
 * <p><b>Mở rộng BL-240, yêu cầu người dùng ("tạo bảng mới ... như các module đã thực hiện")</b>:
 * đổi từ {@link com.td.application.documents.AddDynamicDocumentUseCase} (bảng {@code documents}
 * dùng chung) sang {@link AddHoSoCVDynamicDocumentUseCase} (bảng vật lý ĐỘC LẬP
 * {@code hosocv_documents}, xem javadoc {@code HoSoCVDocument}) — đúng tiền lệ
 * {@code TaoDuThaoUseCase} sau khi {@code duthao} tách bảng riêng. Trạng thái khởi tạo
 * {@link HoSoCVStatus#DANG_XU_LY} (Domino {@code status="0"}).
 *
 * <p><b>Bugfix (phát hiện khi mở rộng vòng đời/view — chưa có ghi nhận trong BL-240 gốc)</b>:
 * {@code TDDocument.CreateOrUpdateDocument()} thật LUÔN gán {@code user_creator = username hiện
 * tại} cho document mới (`docs/analysis/08-...md` mục 5.1 bước 4) — bản Java trước đây KHÔNG làm
 * việc này (chỉ pass-through {@code fields} từ FE), khiến {@code user_creator} luôn rỗng trừ khi FE
 * tự gửi đúng field này (không đảm bảo, và không server-authoritative — 1 client có thể mạo danh
 * người tạo khác). Sửa: stamp {@code user_creator} bằng {@link CurrentUserProvider} SAU KHI copy
 * field FE gửi (ghi đè, đúng nguyên tắc SERVER-AUTHORITATIVE đã dùng cho {@code TaoDuThaoUseCase}).
 * Đây cũng là field khoá của view {@code vwUser_All} (xem {@code ViewQueryService}) — không có field
 * này, view "toàn bộ hồ sơ của cá nhân" sẽ luôn rỗng.
 */
@Service
@RequiredArgsConstructor
public class TaoHoSoCVUseCase {

    public static final String DOCUMENT_TYPE = "hosocv";
    static final String USER_CREATOR_FIELD = "user_creator";
    private static final String FALLBACK_USERNAME = "system";

    private final AddHoSoCVDynamicDocumentUseCase addHoSoCVDynamicDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;

    public Result<UUID> execute(TaoHoSoCVRequest request) {
        var addRequest = new AddDynamicDocumentRequest();
        addRequest.setTitle(request.getTitle());
        addRequest.setDocumentType(DOCUMENT_TYPE);
        addRequest.setStatus(request.getStatus() == null || request.getStatus().isBlank()
            ? HoSoCVStatus.DANG_XU_LY
            : request.getStatus());
        addRequest.setFields(stampUserCreator(request.getFields()));
        addRequest.setDateFields(request.getDateFields());
        addRequest.setNumberFields(request.getNumberFields());
        addRequest.setRichtextFields(request.getRichtextFields());
        return addHoSoCVDynamicDocumentUseCase.execute(addRequest);
    }

    // SERVER-AUTHORITATIVE (dung tien le TaoDuThaoUseCase): LUON GHI DE user_creator bang username
    // nguoi dang dang nhap (fallback "system" neu khong xac thuc duoc), bo qua hoan toan gia tri
    // client gui - xem javadoc lop.
    private Map<String, String> stampUserCreator(Map<String, String> fields) {
        Map<String, String> result = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
        result.put(USER_CREATOR_FIELD, currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME));
        return result;
    }
}
