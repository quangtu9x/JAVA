package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.domain.vbden.VbdenUserActionLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 18/09/2026 (yêu cầu người dùng "thêm vết audit của văn bản đến, tham khảo văn bản đi đã làm và có
 * hiển thị khi xem chi tiết"): writer DUY NHẤT của bảng vật lý ĐỘC LẬP {@code vbden_user_action_logs}
 * — mirror {@code com.td.application.vbdi.AddVbdiUserActionLogUseCase} — ghi "Lịch sử thao tác văn
 * bản" (khối {@code LogUserAction.jsx} phía vbden, nhúng trong Chi tiết văn bản đến).
 *
 * <p>Guard: văn bản đến cha phải tồn tại trong bảng {@code documents} chung
 * ({@code document_type='vbden'}, chưa xoá mềm — dùng {@link DocumentRepository} generic, ĐÚNG như
 * {@link AddVbdenEditHistoryUseCase} đang guard, KHÁC vbdi có bảng/entity riêng). KHÔNG guard trạng
 * thái cụ thể (giống vbdi — hành động này log CẢ những thao tác trước khi vào sổ/xử lý xong).</p>
 *
 * <p><b>Danh tính người thao tác SERVER-AUTHORITATIVE</b>: {@code username} = username đăng nhập
 * qua {@link CurrentUserProvider} (fallback "system"), client KHÔNG tự đặt được —
 * {@code fullname}/{@code unit}/{@code identifier} CHẤP NHẬN từ client (field trình bày thuần tuý,
 * đúng mức tin cậy đã áp dụng ở vbdi).</p>
 *
 * <p>LUÔN ghi 1 dòng MỚI mỗi lần gọi (append-only, không upsert/không dedup) — mọi lần ghi đều
 * "đăng ký" thêm vào audit chung BL-103 qua {@link DocumentLogService#logCreate}, cùng khuôn
 * {@code AddVbdiUserActionLogUseCase}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddVbdenUserActionLogUseCase {

    static final String DOCUMENT_TYPE = TiepNhanVbdenUseCase.DOCUMENT_TYPE;

    private static final String FALLBACK_USERNAME = "system";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";

    private final VbdenUserActionLogRepository vbdenUserActionLogRepository;
    private final DocumentRepository documentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(AddVbdenUserActionLogRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return Result.failure("Thiếu id văn bản đến cần ghi vết thao tác");
        }
        var document = documentRepository.findById(request.getDocumentId()).orElse(null);
        if (document == null || !DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())
                || document.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (request.getActionType() == null || request.getActionType().isBlank()) {
            return Result.failure("Thiếu loại thao tác cần ghi vết");
        }

        String username = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        var entity = new VbdenUserActionLog(
            request.getDocumentId(), username, request.getFullname(), request.getUnit(),
            request.getIdentifier(), request.getActionType().trim());

        var saved = vbdenUserActionLogRepository.save(entity);
        documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
        return Result.success(saved.getId());
    }

    private Map<String, Object> buildSnapshot(VbdenUserActionLog log) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("parentId", log.getParentId());
        snapshot.put("username", log.getUsername());
        snapshot.put("actionType", log.getActionType());
        return snapshot;
    }
}
