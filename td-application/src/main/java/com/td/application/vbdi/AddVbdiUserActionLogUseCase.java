package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.domain.vbdi.VbdiUserActionLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 30/08/2026 (yêu cầu người dùng "port qlvb/vbdi?databasetype=vbdi&action=getloguseraction sang
 * java"): port Domino {@code action=loguseraction}
 * ({@code vn.com.tandan.xsp.services.vbdi.LogUserAction.java#setLogUserAction}) — ghi "Lịch sử thao
 * tác văn bản" (khối {@code LogUserAction.jsx}, tiêu đề "Lịch sử thao tác văn bản:").
 *
 * <p>Đọc lại phía FE ({@code ControllModal.jsx#logUserAction}): payload gửi kèm {@code Form:
 * "user_action"}, {@code timestamp} (chuỗi định dạng sẵn), {@code ip_address}, {@code browser_info}
 * — CẢ 4 field này Domino gốc CŨNG KHÔNG đọc/lưu (đã xác nhận qua đọc trực tiếp
 * {@code setLogUserAction()}: chỉ đọc {@code document_id/username/fullname/unit/identifier/
 * action_type/timestamp}, và ngay cả {@code timestamp} đọc được cũng chỉ dùng làm GIÁ TRỊ item
 * Domino, không lưu {@code ip_address}/{@code browser_info} ở đâu cả) — dead data phía Domino, nên
 * KHÔNG cần khai báo lại các field đó ở {@link AddVbdiUserActionLogRequest}. {@code timestamp} ở
 * đây dùng LUÔN cột audit chuẩn {@code created_on} (server-side, chính xác hơn giờ máy client Domino
 * đang nhận nguyên văn).</p>
 *
 * <p><b>Khác biệt CHỦ ĐÍCH với Domino gốc</b> (xem thêm javadoc {@link VbdiUserActionLog}): Domino
 * upsert 1 document/{@code (document_id,username)} rồi PIVOT {@code action_type} thành field — ở
 * đây LUÔN ghi 1 dòng MỚI mỗi lần gọi (không upsert/không dedup theo timestamp trùng — với cột
 * {@code created_on} có độ chính xác mili-giây, việc 2 lần gọi trùng khớp tuyệt đối gần như không
 * xảy ra, khác Domino chỉ có độ chính xác giây nên cần {@code containsValue} để tránh trùng hiển
 * thị).</p>
 *
 * <p><b>Guard</b>: văn bản đi cha ({@code parentId}) phải tồn tại ({@code document_type='vbdi'},
 * chưa xoá mềm) — cùng nguyên tắc {@link AddVbdiSendLogUseCase}. KHÔNG guard trạng thái ISSUED
 * (Domino gốc không yêu cầu — hành động này log CẢ những thao tác trước khi ban hành, vd
 * "sendOnSave"/"Lưu và gửi"). <b>username</b> SERVER-AUTHORITATIVE qua {@link CurrentUserProvider}
 * (client không đặt được, tránh giả mạo "ai đã thao tác") — {@code fullname}/{@code unit}/
 * {@code identifier} CHẤP NHẬN từ client (field trình bày thuần tuý, đúng mức tin cậy Domino gốc).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddVbdiUserActionLogUseCase {

    private static final String FALLBACK_USERNAME = "system";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";

    private final VbdiUserActionLogRepository vbdiUserActionLogRepository;
    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(AddVbdiUserActionLogRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return Result.failure("Thiếu id văn bản đi cần ghi vết thao tác");
        }
        var parent = vbdiDocumentRepository.findById(request.getDocumentId()).orElse(null);
        if (parent == null || !SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(parent.getDocumentType())
                || parent.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (request.getActionType() == null || request.getActionType().isBlank()) {
            return Result.failure("Thiếu loại thao tác cần ghi vết");
        }

        String username = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        var entity = new VbdiUserActionLog(
            request.getDocumentId(), username, request.getFullname(), request.getUnit(),
            request.getIdentifier(), request.getActionType().trim());

        var saved = vbdiUserActionLogRepository.save(entity);
        documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
        return Result.success(saved.getId());
    }

    private Map<String, Object> buildSnapshot(VbdiUserActionLog log) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("parentId", log.getParentId());
        snapshot.put("username", log.getUsername());
        snapshot.put("actionType", log.getActionType());
        return snapshot;
    }
}
