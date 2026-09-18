package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-230 (DuThao Core) — DoD (1): tạo dự thảo từ văn bản đến/đi. Không có servlet "tạo dự thảo"
 * riêng trong 5 file Domino thật đã đọc ({@code InitDraft.java} chỉ đếm view cho dashboard,
 * {@code DraftObject.java} chỉ là DTO field shape) — việc tạo dự thảo đi qua cơ chế
 * {@code CreateOrUpdateDocument()} tổng quát (base class {@code TDDocument}, tương đương
 * {@code qlvb/add}), đúng tinh thần đã xác nhận nhiều lần trong các backlog trước (BL-210/BL-220:
 * "tạo văn bản" không cần code riêng khi bằng chứng cho thấy cơ chế tạo là generic). Uỷ quyền cho
 * {@link AddDuThaoDynamicDocumentUseCase} với {@code documentType="duthao"}, trạng thái khởi tạo
 * {@link DuThaoStatus#DRAFTING} (tương đương Domino {@code status="0"}).
 *
 * <p><b>Cập nhật 24/08/2026 (yêu cầu người dùng "tạo bảng riêng, để độc lập dữ liệu")</b>: chuyển
 * từ {@code AddDynamicDocumentUseCase} (bảng {@code documents} dùng chung) sang
 * {@link AddDuThaoDynamicDocumentUseCase} (bảng vật lý ĐỘC LẬP {@code duthao_documents}) — xem
 * javadoc {@code com.td.domain.duthao.DuThaoDocument}.</p>
 *
 * <p><b>Lịch sử quy tắc {@code user_creator}</b>:</p>
 * <ul>
 *   <li><b>24/08/2026</b>: tự stamp username qua {@link CurrentUserProvider} khi field trống/vắng
 *       mặt (putIfAbsent — không đè giá trị client gửi) để sửa lỗi 3 view GetView theo
 *       user_creator luôn rỗng.</li>
 *   <li><b>26/08/2026 — ĐẢO CHIỀU theo yêu cầu người dùng</b> ("fix sai rồi... hồi lại như cũ lưu
 *       đúng thông tin tài khoản tạo ở đây"; kịch bản kiểm tra: đăng nhập demo1 nhưng client gửi
 *       user_creator=skhcn thì KHÔNG được lưu skhcn): quy tắc mới là SERVER-AUTHORITATIVE — LUÔN
 *       GHI ĐÈ {@code user_creator} bằng username người đang đăng nhập, bất kể form gửi gì. Lý do:
 *       đây là danh tính NGƯỜI TẠO, làm khoá các view cá nhân
 *       ({@code ViewQueryService#VIEW_DUTHAO_USER_CREATOR}) — chấp nhận giá trị client tức cho phép
 *       mượn danh làm lệch queue. "Ai soạn hộ" vẫn truy vết được qua cột audit {@code created_by}
 *       (điền bởi {@code AuditableEntityListener}, độc lập với dynamic_fields). Đường Luu tương
 *       ứng chặn client ghi đè user_creator của bản ghi đã có — xem {@link LuuDuThaoUseCase}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TaoDuThaoUseCase {

    public static final String DOCUMENT_TYPE = "duthao";
    static final String USER_CREATOR_FIELD = "user_creator";
    private static final String FALLBACK_USERNAME = "system";

    private final AddDuThaoDynamicDocumentUseCase addDuThaoDynamicDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;

    public Result<UUID> execute(TaoDuThaoRequest request) {
        var addRequest = new AddDynamicDocumentRequest();
        addRequest.setTitle(request.getSubject());
        addRequest.setDocumentType(DOCUMENT_TYPE);
        addRequest.setStatus(request.getStatus() == null || request.getStatus().isBlank()
            ? DuThaoStatus.DRAFTING
            : request.getStatus());
        addRequest.setFields(stampUserCreator(request.getFields()));
        addRequest.setDateFields(request.getDateFields());
        addRequest.setNumberFields(request.getNumberFields());
        addRequest.setRichtextFields(request.getRichtextFields());
        return addDuThaoDynamicDocumentUseCase.execute(addRequest);
    }

    // 26/08/2026 (đảo chiều): SERVER-AUTHORITATIVE — LUÔN GHI ĐÈ user_creator bằng username người
    // đang đăng nhập (fallback "system" nếu không xác thực), bỏ qua hoàn toàn giá trị client gửi.
    // Xem javadoc lớp "Lịch sử quy tắc user_creator".
    private Map<String, String> stampUserCreator(Map<String, String> fields) {
        Map<String, String> result = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
        result.put(USER_CREATOR_FIELD, currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME));
        return result;
    }
}
