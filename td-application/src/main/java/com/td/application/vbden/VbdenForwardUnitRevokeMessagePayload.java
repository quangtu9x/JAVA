package com.td.application.vbden;

import lombok.Builder;
import lombok.Data;

/**
 * Payload envelope {@code vbden.forward_unit.revoke} — publish từ {@link PublishForwardUnitRevokeUseCase}
 * khi "Lấy lại VB" ({@link LayLaiVanBanUseCase}) hoặc "Thu hồi" ({@link ThuHoiVanBanUseCase}) tác động
 * lên 1 văn bản đến ĐÃ TỪNG được "chuyển xử lý" sang đơn vị khác tenant ({@code forwarded_units}, xem
 * javadoc {@code ChuyenXuLyVbdenUseCase#publishForwardUnitIfCrossTenant} — giai đoạn 2 đã ghi nhận
 * gap ở đây, nay port).
 *
 * <p>Chạy TẠI TENANT ĐÍCH (đơn vị con đã nhận bản sao) — xem {@code VbdenForwardUnitRevokePayload}/
 * {@code VbdenForwardUnitRevokeTaskHandler} phía {@code td-woker}.
 *
 * <p>KHÔNG share class/jar với {@code td-woker} — chỉ khớp NHAU về hình dạng JSON.
 */
@Data
@Builder
public class VbdenForwardUnitRevokeMessagePayload {

    /** id bản sao TẠI TENANT ĐÍCH ({@code forwarded_units[].documentId}) cần bị đánh dấu "đã lấy lại/thu hồi". */
    private String targetDocumentId;

    /** id văn bản GỐC (tenant hiện tại) — chỉ để log/audit, KHÔNG dùng để truy vấn tại tenant đích. */
    private String originDocumentId;

    private String actingUsername;
    private String opinion;

    /** {@code LayLaiVanBanUseCase.ACTION_LAY_LAI_VAN_BAN} ("laylaivanban") hoặc
     * {@code ThuHoiVanBanUseCase.ACTION_THU_HOI} ("thuhoi") — dùng để render nhãn audit phía tenant đích. */
    private String reason;
}
