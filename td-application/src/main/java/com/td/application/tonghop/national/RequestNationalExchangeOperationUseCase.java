package com.td.application.tonghop.national;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tạo lệnh quản trị trên 1 gói Trục quốc gia (docs/22 §4, docs/24 §4.3): ghi {@code PENDING} vào hàng đợi lệnh của
 * TD Gateway, trả ngay ({@code 202}) — bộ thực thi của gateway chạy bất đồng bộ. KHÔNG gọi Trục/SDK trong request.
 *
 * <ul>
 *   <li>Quyền: {@code RESEND} cần {@code canResend} (chỉ phạm vi toàn tỉnh); {@code RECEIVE_AGAIN}/{@code REPLAY_INTERNAL}/
 *       {@code REPROCESS_STATUS} cần {@code canReplay}.</li>
 *   <li>Phạm vi: gói phải thuộc đơn vị (nếu không toàn tỉnh); phát lại ở phạm vi đơn vị bị ép chỉ tenant mình.</li>
 *   <li>Điều kiện trạng thái kiểm sớm ở đây (gateway kiểm lại khi chạy) để người dùng nhận lỗi ngay.</li>
 *   <li>Lý do bắt buộc; người yêu cầu lấy từ token; idempotency theo key (trùng key → trả lệnh cũ); chặn lệnh cùng loại
 *       đang chờ trên cùng gói.</li>
 * </ul>
 */
@Slf4j
@Service
public class RequestNationalExchangeOperationUseCase {

    public static final Set<String> TYPES = Set.of("RESEND", "RECEIVE_AGAIN", "REPLAY_INTERNAL", "REPROCESS_STATUS");
    static final int MIN_REASON = 10;
    static final int MAX_REASON = 1000;
    static final Set<String> RECEIVE_AGAIN_STATUSES = Set.of("FAILED", "FAILED_RETRYABLE", "QUARANTINED");
    static final Set<String> RESEND_STATUSES = Set.of("FAILED", "BUILT_ONLY");
    static final Set<String> REPLAY_STATUSES = Set.of("RECEIVED", "AWAITING_ACK", "DONE_PENDING");

    private final NationalExchangeAccessPolicy accessPolicy;
    private final GetNationalExchangeDetailUseCase detailUseCase;
    private final ObjectProvider<NationalExchangeCommandRepository> commandProvider;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectProvider<GetUserOrganizationProfileUseCase> profileProvider;
    private final ObjectMapper objectMapper;

    public RequestNationalExchangeOperationUseCase(NationalExchangeAccessPolicy accessPolicy,
                                                   GetNationalExchangeDetailUseCase detailUseCase,
                                                   ObjectProvider<NationalExchangeCommandRepository> commandProvider,
                                                   CurrentUserProvider currentUserProvider,
                                                   ObjectProvider<GetUserOrganizationProfileUseCase> profileProvider,
                                                   ObjectMapper objectMapper) {
        this.accessPolicy = accessPolicy;
        this.detailUseCase = detailUseCase;
        this.commandProvider = commandProvider;
        this.currentUserProvider = currentUserProvider;
        this.profileProvider = profileProvider;
        this.objectMapper = objectMapper;
    }

    public Result<NationalExchangeDetail.Operation> execute(String exchangeId, NationalExchangeOperationRequest request,
                                                            String idempotencyKey) {
        NationalExchangeAccess access = accessPolicy.current();
        if (!access.isCanView()) {
            return Result.failure(SearchNationalExchangesUseCase.FORBIDDEN);
        }
        if (!access.isOperationsEnabled()) {
            return Result.failure("Chưa bật thao tác quản trị (NATIONAL_EXCHANGE_OPERATIONS_ENABLED) — gateway cần bật bộ thực thi");
        }
        NationalExchangeCommandRepository commands = commandProvider.getIfAvailable();
        if (commands == null) {
            return Result.failure(SearchNationalExchangesUseCase.NOT_CONFIGURED);
        }
        String type = request == null || request.getOperationType() == null ? ""
            : request.getOperationType().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            return Result.failure("Loại thao tác không hợp lệ");
        }
        boolean allowed = "RESEND".equals(type) ? access.isCanResend() : access.isCanReplay();
        if (!allowed) {
            return Result.failure("RESEND".equals(type)
                ? "Gửi lại ra Trục chỉ dành cho quản trị toàn tỉnh có quyền NATIONAL_EXCHANGE_RESEND"
                : "Bạn không có quyền NATIONAL_EXCHANGE_REPLAY");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.length() < MIN_REASON || reason.length() > MAX_REASON) {
            return Result.failure("Lý do bắt buộc, từ " + MIN_REASON + " đến " + MAX_REASON + " ký tự");
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > 200) {
            return Result.failure("Idempotency-Key quá dài");
        }

        // Chi tiết đã áp phạm vi: gói đơn vị khác → "Không tìm thấy".
        Result<NationalExchangeDetail> detailResult = detailUseCase.execute(exchangeId);
        if (!detailResult.isSuccess()) {
            return Result.failure(detailResult.getError());
        }
        NationalExchangeDetail detail = detailResult.getData();
        String precondition = precondition(type, detail.getExchange());
        if (precondition != null) {
            return Result.failure(precondition);
        }

        try {
            if (key != null) {
                var existing = commands.findByIdempotencyKey(key);
                if (existing.isPresent()) {
                    return Result.success(existing.get());
                }
            }
            if (commands.hasActive(exchangeId, type)) {
                return Result.failure("Gói này đã có yêu cầu cùng loại đang chờ/đang xử lý");
            }
            Map<String, Object> parameters = new LinkedHashMap<>();
            String targetTenant = null;
            if ("REPLAY_INTERNAL".equals(type)) {
                Result<List<String>> tenants = replayTenants(access, detail, request.getTenantIds());
                if (!tenants.isSuccess()) {
                    return Result.failure(tenants.getError());
                }
                parameters.put("tenantIds", tenants.getData());
                parameters.put("agencyCodes", clean(request.getAgencyCodes()));
                targetTenant = tenants.getData().size() == 1 ? tenants.getData().get(0) : null;
            }
            String username = currentUserProvider.currentUsername().orElse("unknown");
            String requestId = UUID.randomUUID().toString();
            var created = commands.create(new NationalExchangeCommandRepository.NewOperation(requestId, type, exchangeId,
                targetTenant != null ? targetTenant : access.getTenantId(), null, reason, username, fullName(username),
                key != null ? key : "td-web:" + requestId, objectMapper.writeValueAsString(parameters)));
            return Result.success(created);
        } catch (Exception e) {
            log.warn("Không tạo được lệnh quản trị {} cho {}: {}", type, exchangeId, e.getMessage(), e);
            return Result.failure(SearchNationalExchangesUseCase.UNAVAILABLE);
        }
    }

    /** Điều kiện trạng thái (gateway kiểm lại khi thực thi). Null = hợp lệ. */
    static String precondition(String type, NationalExchangeItem x) {
        boolean inbound = NationalExchangeStates.DIRECTION_INBOUND.equals(x.getDirection());
        return switch (type) {
            // BUILT_ONLY = dựng ở chế độ thử, chưa từng gửi lên Trục — gửi được khi gateway đã bật kết nối thật
            // (gateway tự từ chối nếu vẫn tắt/vẫn chỉ dựng gói).
            case "RESEND" -> !inbound && "edoc".equals(x.getMessageType()) && RESEND_STATUSES.contains(x.getStatus())
                ? null : "Chỉ gửi lại được gói văn bản đi gửi lỗi hoặc mới dựng gói (chưa gửi)";
            case "RECEIVE_AGAIN" -> inbound && RECEIVE_AGAIN_STATUSES.contains(x.getStatus()) ? null
                : "Chỉ nhận lại được gói đến nhận lỗi/cách ly — gói đã nhận dùng 'Phát lại nội tỉnh'";
            case "REPLAY_INTERNAL" -> inbound && "edoc".equals(x.getMessageType()) && REPLAY_STATUSES.contains(x.getStatus())
                ? null : "Chỉ phát lại được gói văn bản đến đã lưu thành công";
            case "REPROCESS_STATUS" -> inbound && "status".equals(x.getMessageType()) && "QUARANTINED".equals(x.getStatus())
                ? null : "Chỉ xử lý lại được gói trạng thái đang cách ly (không khớp văn bản)";
            default -> "Loại thao tác không hợp lệ";
        };
    }

    /** Phạm vi đơn vị: chỉ tenant mình. Toàn tỉnh: tenant yêu cầu phải là tenant nhận của gói (docs/22 §4 quy tắc 6). */
    static Result<List<String>> replayTenants(NationalExchangeAccess access, NationalExchangeDetail detail,
                                              List<String> requested) {
        Set<String> recipientTenants = detail.getRecipients().stream()
            .map(NationalExchangeDetail.Recipient::getTenantId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (!access.isProvince()) {
            return Result.success(List.of(access.getTenantId()));
        }
        List<String> wanted = clean(requested);
        if (!recipientTenants.containsAll(wanted)) {
            return Result.failure("Đơn vị chọn không thuộc danh sách nơi nhận của gói");
        }
        return Result.success(wanted);
    }

    private static List<String> clean(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct()
            .limit(200).toList();
    }

    private String fullName(String username) {
        try {
            var profiles = profileProvider.getIfAvailable();
            var profile = profiles == null ? null : profiles.execute(username);
            return profile == null || profile.getFullName() == null ? username : profile.getFullName();
        } catch (RuntimeException e) {
            return username;
        }
    }
}
