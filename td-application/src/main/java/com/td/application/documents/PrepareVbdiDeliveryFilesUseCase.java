package com.td.application.documents;

import com.td.application.common.models.Result;

import java.util.List;

/**
 * Bổ sung 07/09/2026 (yêu cầu người dùng, xem javadoc {@link VbdiDeliveryFileRef}): copy TOÀN BỘ
 * tệp đính kèm của 1 văn bản đi sang vị trí lưu trữ RIÊNG, DÙNG CHUNG cho MỌI nơi nhận của 1 lượt
 * "gửi văn bản" — gọi ĐÚNG 1 LẦN cho cả lượt gửi (KHÔNG gọi lại cho từng nơi nhận, xem
 * {@code SendVbdiDocumentUseCase#execute}), tránh nhân bản N lần theo N nơi nhận.
 *
 * <p>Best-effort ở CẤP TỪNG TỆP: 1 tệp lỗi khi copy (vd MinIO tạm thời lỗi) chỉ bị BỎ QUA (không có
 * trong danh sách trả về, ghi log lỗi), KHÔNG làm hỏng các tệp khác/toàn bộ lượt gửi — văn bản đến
 * vẫn được tạo với các tệp copy THÀNH CÔNG, thiếu tệp còn hơn KHÔNG gửi được văn bản. Rỗng (văn bản
 * đi không có tệp nào, hoặc TẤT CẢ đều lỗi) trả về danh sách rỗng, KHÔNG phải lỗi.</p>
 */
public interface PrepareVbdiDeliveryFilesUseCase {
    Result<List<VbdiDeliveryFileRef>> execute(PrepareVbdiDeliveryFilesRequest request);
}
