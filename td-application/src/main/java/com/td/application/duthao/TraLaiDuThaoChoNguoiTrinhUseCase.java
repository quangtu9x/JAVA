package com.td.application.duthao;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Yêu cầu người dùng 01/10/2026: nút "Trả lại văn bản → Người trình" ở VBĐi. FE trước đây gọi
 * {@code qlvb/vbdi/tralainguoitrinhky} — endpoint KHÔNG tồn tại cả ở Domino nên chưa từng chạy được.
 * Người trình = người thực hiện vết {@code chuyenkyduyet} gần nhất (xem
 * {@link TraLaiDuThaoTarget#NGUOI_TRINH}); còn lại cùng logic {@link TraLaiDuThaoSupport}.
 */
@Service
@RequiredArgsConstructor
public class TraLaiDuThaoChoNguoiTrinhUseCase {

    private final TraLaiDuThaoSupport support;

    @Transactional
    public Result<UUID> execute(TraLaiDuThaoRequest request) {
        return support.execute(request, TraLaiDuThaoTarget.NGUOI_TRINH);
    }
}
