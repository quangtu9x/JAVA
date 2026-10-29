package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.realtime.UserNotificationInboxUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 27/10/2026 — hộp thư thông báo (chuông) của NGƯỜI ĐĂNG NHẬP: thông báo đã lưu ở DB đơn vị
 * ({@code user_notifications}) để người offline/mất kết nối xem lại. Không nhận username từ client — xem
 * {@link UserNotificationInboxUseCase}, {@code docs/architecture/realtime-socket-notifications.md}.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Thông báo", description = "Hộp thư thông báo của người đăng nhập")
public class UserNotificationsController extends BaseController {

    private final UserNotificationInboxUseCase inboxUseCase;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách thông báo của tôi (mới nhất trước) + số chưa đọc")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String databaseType) {
        return ok(inboxUseCase.list(page, size, unreadOnly, databaseType));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Số chưa đọc theo nhóm (phân hệ + hành động) + vài thông báo chưa đọc mới nhất")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary() {
        return ok(inboxUseCase.summary());
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu 1 thông báo đã đọc")
    public ResponseEntity<ApiResponse<Integer>> markRead(@PathVariable UUID id) {
        return ok(inboxUseCase.markRead(id));
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu tất cả đã đọc (tuỳ chọn chỉ 1 phân hệ: body {databaseType})")
    public ResponseEntity<ApiResponse<Integer>> markAllRead(@RequestBody(required = false) Map<String, String> body) {
        return ok(inboxUseCase.markAllRead(body == null ? null : body.get("databaseType")));
    }

    @PostMapping("/read-document")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu đã đọc mọi thông báo của 1 văn bản (khi mở văn bản) - body {documentId}")
    public ResponseEntity<ApiResponse<Integer>> markReadByDocument(@RequestBody Map<String, String> body) {
        return ok(inboxUseCase.markReadByDocument(body == null ? null : body.get("documentId")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ẩn 1 thông báo (xoá mềm)")
    public ResponseEntity<ApiResponse<Integer>> hide(@PathVariable UUID id) {
        return ok(inboxUseCase.hide(id));
    }
}
