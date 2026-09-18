package com.td.application.documents;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.UserDisplayInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 09/09/2026 (yêu cầu người dùng "log xem tệp đính kèm cần thiết cho các module khác nữa, có thể
 * tạo ra 1 bảng log riêng để lưu, phân biệt được đính kèm này của module nào... tối ưu cả lưu trữ ở
 * db"): ghi 1 lượt XEM hoặc TẢI 1 tệp đính kèm — GENERIC cho MỌI module gọi {@code DocumentsController}
 * (đã generic sẵn cho mọi documentType, xem javadoc lớp đó) — thay cho thiết kế đầu (cùng ngày, đã
 * gỡ bỏ) chỉ ghi vào {@code vbdi_user_action_logs} (vbdi-only, model append-only gây phình bảng theo
 * lượt xem).
 *
 * <p><b>Chống phình bảng (UPSERT, xem javadoc {@link com.td.domain.documents.FileAccessLog})</b>:
 * mỗi lần gọi CHỈ tạo 1 dòng MỚI nếu đây là lần ĐẦU (tổ hợp module+documentId+fileId+username+action
 * chưa từng ghi) — các lần gọi sau CHỈ cập nhật {@code lastAccessedOn}/{@code accessCount} trên
 * ĐÚNG dòng đó, không tạo dòng mới.</p>
 *
 * <p><b>Tên tệp SERVER-AUTHORITATIVE</b>: tra {@code originalFileName} qua {@link GetFileUseCase}
 * theo {@code (documentId, fileId)} — KHÔNG nhận {@code fileName} từ client. KHÁC thiết kế đầu (đã
 * gỡ): tệp KHÔNG tìm thấy giờ CHẶN ghi log (trả lỗi) thay vì vẫn ghi với {@code fileName=null} — vì
 * đây là bảng generic dùng cho nhiều module, {@code GetFileUseCase} là NGUỒN DUY NHẤT xác nhận
 * {@code documentId}/{@code fileId} khớp nhau hợp lệ (không có guard "văn bản tồn tại" theo từng
 * module như use case vbdi-only trước đây) — không xác nhận được nghĩa là không có gì thật để ghi
 * log.</p>
 *
 * <p><b>Danh tính SERVER-AUTHORITATIVE</b>: {@code username} qua {@link CurrentUserProvider},
 * {@code fullname}/{@code unit} qua {@link UserDisplayInfoService} (KHÔNG nhận từ client) — khác
 * hẳn {@code AddVbdiUserActionLogUseCase} (chấp nhận {@code fullname}/{@code unit} từ client) vì đây
 * là thiết kế MỚI, có sẵn hạ tầng resolve server-side, không có lý do gì hạ thấp độ tin cậy.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RecordFileAccessUseCase {

    public static final String ACTION_VIEW = "view";
    public static final String ACTION_DOWNLOAD = "download";

    private static final String FALLBACK_USERNAME = "system";

    private final GetFileUseCase getFileUseCase;
    private final FileAccessLogRepository fileAccessLogRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserDisplayInfoService userDisplayInfoService;

    public Result<UUID> execute(RecordFileAccessRequest request) {
        if (request == null || request.getDocumentId() == null || request.getFileId() == null) {
            return Result.failure("Thiếu documentId/fileId cần ghi log");
        }
        if (request.getModule() == null || request.getModule().isBlank()) {
            return Result.failure("Thiếu module cần ghi log");
        }
        String action = normalizeAction(request.getAction());
        if (action == null) {
            return Result.failure("action không hợp lệ (chỉ nhận \"view\" hoặc \"download\")");
        }

        var fileRequest = DownloadFileRequest.builder()
            .documentId(request.getDocumentId())
            .fileId(request.getFileId())
            .build();
        var fileResult = getFileUseCase.execute(fileRequest);
        if (!fileResult.isSuccess() || fileResult.getData() == null) {
            return Result.failure("Không tìm thấy tệp đính kèm");
        }
        var file = fileResult.getData();
        String fileName = file.getOriginalFileName() != null && !file.getOriginalFileName().isBlank()
            ? file.getOriginalFileName() : file.getFileName();

        String username = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        var displayInfo = userDisplayInfoService.resolve(username);

        UUID id = fileAccessLogRepository.recordAccess(
            request.getModule().trim(), request.getDocumentId(), request.getFileId(), fileName,
            action, username, displayInfo.fullName(), displayInfo.unit());
        log.info("Ghi log {} tep {} cua {}#{} boi {}",
            action, request.getFileId(), request.getModule(), request.getDocumentId(), username);
        return Result.success(id);
    }

    private String normalizeAction(String rawAction) {
        if (rawAction == null) {
            return null;
        }
        String normalized = rawAction.trim().toLowerCase();
        return (ACTION_VIEW.equals(normalized) || ACTION_DOWNLOAD.equals(normalized)) ? normalized : null;
    }
}
