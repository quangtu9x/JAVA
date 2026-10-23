package com.td.web.config;

import com.td.application.common.models.ApiResponse;
import com.td.infrastructure.errortracking.ErrorTrackingService;
import com.td.infrastructure.security.TdTokenAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Bo xu ly exception tap trung cho toan bo API dung {@link ApiResponse} envelope: 1 noi duy nhat
 * quyet dinh HTTP status + noi dung response cho tung loai loi, de controller/service khong phai
 * tu try-catch de tra loi rieng le (xem BL/rà soát chuẩn hóa exception handling).
 *
 * <p><b>Luu y ve Spring Security</b>: loi xay ra o filter chain (truoc khi vao DispatcherServlet -
 * vi du khong co token/token sai) KHONG di qua {@code @RestControllerAdvice} duoc; nhung truong hop
 * do da co {@code ApiResponseAuthenticationEntryPoint} (401) va {@code ApiResponseAccessDeniedHandler}
 * (403) trong module {@code td-infrastructure} xu ly rieng, dung chung format {@code ApiResponse}
 * qua {@code SecurityResponseWriter}. Handler {@link #handleAccessDenied} va {@link #handleAuthentication}
 * o day chi bat truong hop {@link AccessDeniedException}/{@link AuthenticationException} thoat ra
 * SAU khi da vao DispatcherServlet (vi du {@code @PreAuthorize} tren phuong thuc controller that bai
 * trong luc goi handler) - neu khong co 2 handler nay, {@link #handleGeneral} se nuot mat thanh loi
 * 500 chung chung thay vi 401/403 dung nghia nhu thiet ke bao mat cua he thong.</p>
 *
 * <p><b>Error Tracking</b>: mỗi handler ở đây sinh 1 Error ID (qua {@link ErrorTrackingService}) và
 * trả về trong {@code ApiResponse.errorId} để client/support tra cứu ngược context đầy đủ (user,
 * request, stack trace...) — xem {@code ErrorTrackingController}. {@code errorTrackingService} được
 * autowire "tùy chọn" ({@code required = false}) — nếu vắng mặt (ví dụ trong các
 * {@code @WebMvcTest} slice hẹp không nạp bean này), handler vẫn hoạt động bình thường, chỉ không
 * sinh Error ID (field bị lược khỏi JSON do {@code @JsonInclude(NON_NULL)} trên {@code ApiResponse}) —
 * đảm bảo tính năng phụ trợ này không bao giờ là nguyên nhân làm hỏng response chính.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String VALIDATION_ERROR_MESSAGE = "Du lieu khong hop le";
    private static final String ACCESS_DENIED_MESSAGE = "Khong co quyen thuc hien thao tac nay";
    private static final String UNAUTHENTICATED_MESSAGE = "Chua xac thuc hoac token khong hop le";

    @Setter
    @Autowired(required = false)
    private ErrorTrackingService errorTrackingService;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = VALIDATION_ERROR_MESSAGE;
        }

        log.warn("Validation failed: {}", message);
        String errorId = track(request, HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message, errorId));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations()
            .stream()
            .map(violation -> violation.getMessage())
            .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = VALIDATION_ERROR_MESSAGE;
        }

        log.warn("Constraint violation: {}", message);
        String errorId = track(request, HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message, errorId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body: {}", ex.getMessage());
        String errorId = track(request, HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Noi dung yeu cau khong hop le", errorId));
    }

    /**
     * Thieu tham so bat buoc (vd. {@code @RequestParam} khong co gia tri va khong co default).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing request parameter: {}", ex.getMessage());
        String errorId = track(request, HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Thieu tham so bat buoc: " + ex.getParameterName(), errorId));
    }

    /**
     * Sai kieu du lieu tham so (vd. truyen chuoi khong phai UUID/so cho {@code @PathVariable}).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        String errorId = track(request, HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Gia tri tham so '" + ex.getName() + "' khong hop le", errorId));
    }

    /**
     * Gom ve day cach xu ly {@code IllegalArgumentException} truoc day tung duoc tu try-catch rieng
     * le o nhieu controller (ViewQueryController, VbdenController, VbdiController,
     * LegacyDanhMucController, HoSoCVController) khi {@code ViewQueryService} nem ra do viewname/tham
     * so khong hop le - cung mot cach xu ly (400 + {@code ex.getMessage()}), nay chi con 1 noi.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid argument: {}", ex.getMessage());
        String errorId = track(request, HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), errorId));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoSuchElement(NoSuchElementException ex, HttpServletRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Khong tim thay du lieu";
        log.warn("Resource not found: {}", message);
        String errorId = track(request, HttpStatus.NOT_FOUND, ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), message, errorId));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        String errorId = track(request, HttpStatus.CONFLICT, ex);
        // 19/10/2026: quá độ dài cột (VARCHAR) -> câu dễ hiểu nêu số ký tự tối đa (ApiResponse#friendlyMessage).
        String cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : null;
        String friendly = ApiResponse.friendlyMessage(cause);
        String message = friendly != null && !friendly.equals(cause)
            ? friendly
            : "Du lieu vi pham rang buoc, vui long kiem tra lai";
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(HttpStatus.CONFLICT.value(), message, errorId));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not supported: {}", ex.getMessage());
        String errorId = track(request, HttpStatus.METHOD_NOT_ALLOWED, ex);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ApiResponse.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "Phuong thuc HTTP khong duoc ho tro", errorId));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Media type not supported: {}", ex.getMessage());
        String errorId = track(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(ApiResponse.error(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Dinh dang noi dung khong duoc ho tro", errorId));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        String errorId = track(request, HttpStatus.PAYLOAD_TOO_LARGE, ex);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ApiResponse.error(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Tep tin vuot qua kich thuoc cho phep", errorId));
    }

    /**
     * {@code @PreAuthorize}/{@code hasAnyRole(...)} that bai trong luc goi handler controller - xem
     * javadoc class de biet vi sao can handler rieng nay thay vi de {@link #handleGeneral} nuot mat.
     * Dung chung message voi {@code ApiResponseAccessDeniedHandler} de nhat quan du loi phat sinh o
     * filter chain hay o day.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        String errorId = track(request, HttpStatus.FORBIDDEN, ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), ACCESS_DENIED_MESSAGE, errorId));
    }

    /**
     * Phong khi {@link AuthenticationException} thoat ra tu controller/service thay vi filter chain
     * (truong hop thong thuong da duoc {@code ApiResponseAuthenticationEntryPoint} xu ly truoc do).
     * Giu nguyen message goc cho {@link TdTokenAuthenticationException} (vd. "Sai token") vi FE dua
     * vao chuoi nay de tu dong refresh token ngam.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        String message = ex instanceof TdTokenAuthenticationException ? ex.getMessage() : UNAUTHENTICATED_MESSAGE;
        log.warn("Authentication failed: {}", message);
        String errorId = track(request, HttpStatus.UNAUTHORIZED, ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), message, errorId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex, HttpServletRequest request) {
        String errorId = track(request, HttpStatus.INTERNAL_SERVER_ERROR, ex);
        log.error("Unexpected error [{}]", errorId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(500, "Co loi xay ra, vui long thu lai sau", errorId));
    }

    private String formatFieldError(FieldError error) {
        return error.getDefaultMessage() == null
            ? VALIDATION_ERROR_MESSAGE
            : error.getDefaultMessage();
    }

    /**
     * Sinh Error ID + luu context qua {@link ErrorTrackingService} nếu bean có mặt trong context
     * (xem javadoc class) — không bao giờ throw, tối đa chỉ trả {@code null}.
     */
    private String track(HttpServletRequest request, HttpStatus status, Exception ex) {
        if (errorTrackingService == null) {
            return null;
        }
        return errorTrackingService.captureError(request, status, ex);
    }
}
