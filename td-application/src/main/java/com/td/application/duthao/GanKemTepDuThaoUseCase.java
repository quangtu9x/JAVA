package com.td.application.duthao;

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
 * DT-007 (tiếp, 26/08/2026) — port nhánh "xử lý đính kèm" của {@code ChiTietPage.jsx#onFinish}
 * (đối chiếu trực tiếp đoạn code FE: khi Lưu dự thảo mà người dùng có gõ ý kiến ({@code opinion})
 * và/hoặc chọn thêm tệp mới, Domino gốc gọi {@code qlvb/add?databasetype=duthao&parentid=...} với
 * {@code Form=draft_process}, {@code action_type=gankemtep} — ghi 1 vết xử lý MANG Ý KIẾN + (qua
 * multipart) các tệp đính kèm). TÁCH BIỆT với việc lưu field lõi ({@link TaoDuThaoUseCase}/
 * {@link LuuDuThaoUseCase}) và đính kèm tệp vào CHÍNH dự thảo — đây là 1 vết audit RIÊNG trên
 * {@code draft_process_documents} (đọc lại qua {@code vwGroup_process_by_draft_id}, DT-007 trước).
 *
 * <p><b>Khác biệt CÓ CHỦ ĐÍCH so với Domino gốc</b>: FE gửi {@code creator=currentUser.name} (TÊN
 * hiển thị) trong request — SERVER ở đây tự gán {@code creator} = username người đang đăng nhập
 * (SERVER-AUTHORITATIVE, cùng nguyên tắc {@code TaoDuThaoUseCase#user_creator}/
 * {@code XuLyDuThaoUseCase#actingUsername}), KHÔNG tin giá trị client gửi (nếu có) — đồng thời nhất
 * quán với MỌI vết {@code draft_process} khác (đều lưu username, không lưu tên hiển thị).</p>
 *
 * <p><b>KHÔNG xử lý file trong use case này</b>: khác Domino (1 request multipart chung), Java tách
 * làm 2 bước — {@code execute()} chỉ tạo vết {@code draft_process} và trả về id của nó; FE gọi tiếp
 * {@code POST /api/v1/documents/{id}/files} (endpoint chung, không ràng buộc bảng cụ thể — chỉ cần
 * 1 UUID hợp lệ làm khoá lưu trữ) với id vừa nhận để đính kèm từng tệp — cùng khuôn 2 bước đã dùng
 * cho {@code TaoDuThaoUseCase}/{@code uploadDuThaoFile} ở Slice 1.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GanKemTepDuThaoUseCase {

    /** action_type cố định — đúng giá trị Domino gốc FE gửi cho nhánh "xử lý đính kèm" này. */
    public static final String ACTION_GAN_KEM_TEP = "gankemtep";

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy dự thảo";
    private static final String DEFAULT_TITLE = "Đính kèm ý kiến/tệp dự thảo";
    private static final String PARENT_ID_FIELD = "parentid";
    private static final String ACTION_FIELD = "action_type";
    private static final String OPINION_FIELD = "opinion";
    private static final String CREATOR_FIELD = "creator";
    private static final String FALLBACK_USERNAME = "system";

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;

    public Result<UUID> execute(GanKemTepDuThaoRequest request) {
        var draftOptional = duThaoDocumentRepository.findById(request.getId());
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);

        var processRequest = new AddDynamicDocumentRequest();
        processRequest.setTitle(DEFAULT_TITLE);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(PARENT_ID_FIELD, request.getId().toString());
        fields.put(ACTION_FIELD, ACTION_GAN_KEM_TEP);
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            fields.put(OPINION_FIELD, request.getOpinion());
        }
        fields.put(CREATOR_FIELD, actingUsername);
        processRequest.setFields(fields);

        return addDraftProcessDocumentUseCase.execute(processRequest);
    }
}
