package com.td.application.tonghop.national;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Tải gói eDXML (docs/22 §7 quyền {@code NATIONAL_EXCHANGE_DOWNLOAD}): kiểm quyền + phạm vi ở td-web, rồi proxy endpoint
 * nội bộ của gateway (gateway ghi audit người tải). Không trả đường dẫn vật lý.
 */
@Slf4j
@Service
public class DownloadNationalExchangePackageUseCase {

    private final NationalExchangeAccessPolicy accessPolicy;
    private final GetNationalExchangeDetailUseCase detailUseCase;
    private final ObjectProvider<NationalExchangePackageGateway> gatewayProvider;
    private final CurrentUserProvider currentUserProvider;

    public DownloadNationalExchangePackageUseCase(NationalExchangeAccessPolicy accessPolicy,
                                                  GetNationalExchangeDetailUseCase detailUseCase,
                                                  ObjectProvider<NationalExchangePackageGateway> gatewayProvider,
                                                  CurrentUserProvider currentUserProvider) {
        this.accessPolicy = accessPolicy;
        this.detailUseCase = detailUseCase;
        this.gatewayProvider = gatewayProvider;
        this.currentUserProvider = currentUserProvider;
    }

    public Result<NationalExchangePackageGateway.PackageStream> execute(String exchangeId) {
        NationalExchangeAccess access = accessPolicy.current();
        if (!access.isCanView() || !access.isCanDownload()) {
            return Result.failure("Bạn không có quyền tải gói (NATIONAL_EXCHANGE_DOWNLOAD)");
        }
        NationalExchangePackageGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null || !access.isDownloadEnabled()) {
            return Result.failure("Chưa cấu hình kết nối tải gói của TD Gateway (NATIONAL_EXCHANGE_GATEWAY_URL)");
        }
        Result<NationalExchangeDetail> detail = detailUseCase.execute(exchangeId);
        if (!detail.isSuccess()) {
            return Result.failure(detail.getError());
        }
        String username = currentUserProvider.currentUsername().orElse("unknown");
        try {
            return gateway.download(exchangeId, username, username)
                .<Result<NationalExchangePackageGateway.PackageStream>>map(Result::success)
                .orElseGet(() -> Result.failure("Gateway không còn tệp gói này"));
        } catch (Exception e) {
            log.warn("Tải gói {} từ gateway lỗi: {}", exchangeId, e.getMessage(), e);
            return Result.failure("Không tải được gói từ TD Gateway, vui lòng thử lại sau");
        }
    }
}
