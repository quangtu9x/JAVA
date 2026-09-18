package com.td.application.duthao;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương ứng ở
 * button của văn bản đi"): "Trả lại lãnh đạo" — đối chiếu trực tiếp
 * {@code vn.com.tandan.xsp.services.vbdi.XuLyDuThao#TraLaiDuThaoChoLanhDaoKyDuyet()}. Logic thật
 * nằm ở {@link TraLaiDuThaoSupport} (dùng chung với {@link TraLaiDuThaoChoNguoiSoanThaoUseCase}) —
 * xem javadoc lớp đó.
 */
@Service
@RequiredArgsConstructor
public class TraLaiDuThaoChoLanhDaoUseCase {

    private final TraLaiDuThaoSupport support;

    @Transactional
    public Result<UUID> execute(TraLaiDuThaoRequest request) {
        return support.execute(request, TraLaiDuThaoTarget.LANH_DAO);
    }
}
