package com.td.application.tonghop.national;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Huỷ lệnh quản trị còn {@code PENDING} (docs/22 §4 "CANCEL: chỉ request PENDING"). Lệnh đã được gateway nhận
 * (PROCESSING) không huỷ được — cập nhật có điều kiện nên không tranh chấp với bộ thực thi.
 */
@Slf4j
@Service
public class CancelNationalExchangeOperationUseCase {

    private final NationalExchangeAccessPolicy accessPolicy;
    private final GetNationalExchangeDetailUseCase detailUseCase;
    private final ObjectProvider<NationalExchangeCommandRepository> commandProvider;
    private final CurrentUserProvider currentUserProvider;

    public CancelNationalExchangeOperationUseCase(NationalExchangeAccessPolicy accessPolicy,
                                                  GetNationalExchangeDetailUseCase detailUseCase,
                                                  ObjectProvider<NationalExchangeCommandRepository> commandProvider,
                                                  CurrentUserProvider currentUserProvider) {
        this.accessPolicy = accessPolicy;
        this.detailUseCase = detailUseCase;
        this.commandProvider = commandProvider;
        this.currentUserProvider = currentUserProvider;
    }

    public Result<NationalExchangeDetail.Operation> execute(String requestId) {
        NationalExchangeAccess access = accessPolicy.current();
        if (!access.isCanView() || !(access.isCanReplay() || access.isCanResend())) {
            return Result.failure("Bạn không có quyền huỷ yêu cầu quản trị");
        }
        NationalExchangeCommandRepository commands = commandProvider.getIfAvailable();
        if (commands == null) {
            return Result.failure(SearchNationalExchangesUseCase.NOT_CONFIGURED);
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
            return Result.failure("Thiếu mã yêu cầu");
        }
        try {
            var operation = commands.find(requestId);
            if (operation.isEmpty()) {
                return Result.failure("Không tìm thấy yêu cầu");
            }
            String exchangeId = operation.get().getExchangeId();
            // Kiểm phạm vi qua chi tiết gói (gói đơn vị khác → không tìm thấy).
            if (exchangeId == null || !detailUseCase.execute(exchangeId).isSuccess()) {
                return Result.failure("Không tìm thấy yêu cầu");
            }
            String username = currentUserProvider.currentUsername().orElse("unknown");
            if (!commands.cancel(requestId, exchangeId, username, username)) {
                return Result.failure("Yêu cầu không còn ở trạng thái chờ — không huỷ được");
            }
            return commands.find(requestId).<Result<NationalExchangeDetail.Operation>>map(Result::success)
                .orElseGet(() -> Result.failure("Không tìm thấy yêu cầu"));
        } catch (RuntimeException e) {
            log.warn("Huỷ lệnh {} lỗi: {}", requestId, e.getMessage(), e);
            return Result.failure(SearchNationalExchangesUseCase.UNAVAILABLE);
        }
    }
}
