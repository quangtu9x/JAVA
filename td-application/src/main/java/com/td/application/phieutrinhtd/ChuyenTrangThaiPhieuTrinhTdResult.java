package com.td.application.phieutrinhtd;

import java.util.UUID;

/**
 * 06/10/2026 (cutover FE phiếu trình): kết quả chuyển trạng thái / cho ý kiến - {@code processId} là dòng
 * quá trình vừa tạo, FE tải tệp đính kèm của bước lên đúng dòng đó ({@code api/v1/documents/{processId}/files}).
 */
public record ChuyenTrangThaiPhieuTrinhTdResult(UUID id, UUID processId) {
}
