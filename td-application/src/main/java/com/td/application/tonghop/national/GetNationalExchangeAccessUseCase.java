package com.td.application.tonghop.national;

import com.td.application.common.models.Result;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Quyền + phạm vi của người dùng hiện tại trên màn Trục quốc gia — FE dùng để ẩn/hiện, server vẫn tự kiểm tra. */
@Service
public class GetNationalExchangeAccessUseCase {

    private final NationalExchangeAccessPolicy accessPolicy;
    private final ObjectProvider<NationalExchangeQueryRepository> repositoryProvider;

    public GetNationalExchangeAccessUseCase(NationalExchangeAccessPolicy accessPolicy,
                                            ObjectProvider<NationalExchangeQueryRepository> repositoryProvider) {
        this.accessPolicy = accessPolicy;
        this.repositoryProvider = repositoryProvider;
    }

    public Result<NationalExchangeAccess> execute() {
        NationalExchangeAccess access = accessPolicy.current();
        if (access.isCanView() && access.getTenantId() != null) {
            NationalExchangeQueryRepository repo = repositoryProvider.getIfAvailable();
            if (repo != null) {
                try {
                    access.setTenantName(repo.tenantDisplayName(access.getTenantId()));
                } catch (RuntimeException ignored) {
                    // Tên đơn vị chỉ để hiển thị.
                }
            }
        }
        return Result.success(access);
    }
}
