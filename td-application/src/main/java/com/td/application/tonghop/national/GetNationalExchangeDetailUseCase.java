package com.td.application.tonghop.national;

import com.td.application.common.models.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Chi tiết 1 gói trao đổi qua Trục quốc gia: nơi nhận, dòng thời gian, lần thao tác, yêu cầu quản trị. Áp phạm vi
 * tenant + che dữ liệu kỹ thuật theo {@link NationalExchangeAccessPolicy}.
 */
@Slf4j
@Service
public class GetNationalExchangeDetailUseCase {

    static final String NOT_FOUND = "Không tìm thấy gói trao đổi";

    private final ObjectProvider<NationalExchangeQueryRepository> repositoryProvider;
    private final NationalExchangeAccessPolicy accessPolicy;

    public GetNationalExchangeDetailUseCase(ObjectProvider<NationalExchangeQueryRepository> repositoryProvider,
                                            NationalExchangeAccessPolicy accessPolicy) {
        this.repositoryProvider = repositoryProvider;
        this.accessPolicy = accessPolicy;
    }

    public Result<NationalExchangeDetail> execute(String exchangeId) {
        // exchange_id = "OUT:<unitId>" | "IN:<transportDocumentId>" (VARCHAR 320 — tdgateway V8).
        if (exchangeId == null || exchangeId.isBlank() || exchangeId.length() > 320) {
            return Result.failure("Thiếu mã gói trao đổi");
        }
        NationalExchangeAccess access = accessPolicy.current();
        if (!access.isCanView()) {
            return Result.failure(SearchNationalExchangesUseCase.FORBIDDEN);
        }
        NationalExchangeQueryRepository repo = repositoryProvider.getIfAvailable();
        if (repo == null) {
            return Result.failure(SearchNationalExchangesUseCase.NOT_CONFIGURED);
        }
        Optional<NationalExchangeDetail> loaded;
        try {
            loaded = repo.findDetail(exchangeId);
        } catch (RuntimeException ex) {
            log.warn("Doc chi tiet goi {} tu TD Gateway that bai: {}", exchangeId, ex.getMessage(), ex);
            return Result.failure(SearchNationalExchangesUseCase.UNAVAILABLE);
        }
        if (loaded.isEmpty()) {
            return Result.failure(NOT_FOUND);
        }
        NationalExchangeDetail detail = loaded.get();
        if (!access.isProvince()) {
            // Gói của đơn vị khác trả như "không tìm thấy" — không xác nhận sự tồn tại.
            if (!NationalExchangeMasking.belongsTo(detail, access.getTenantId())) {
                return Result.failure(NOT_FOUND);
            }
            NationalExchangeMasking.restrictToTenant(detail, access.getTenantId());
        }
        if (!access.isCanAudit()) {
            NationalExchangeMasking.maskDetail(detail);
        }
        return Result.success(detail);
    }
}
