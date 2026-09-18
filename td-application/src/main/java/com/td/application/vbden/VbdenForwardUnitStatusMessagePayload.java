package com.td.application.vbden;

import lombok.Builder;
import lombok.Data;

/**
 * Payload envelope {@code vbden.forward_unit.status} — publish từ {@link ChuyenXuLyVbdenUseCase} MỖI
 * KHI có hành động xử lý ({@code phanphoi}/{@code phanxuly}/{@code chuyenxuly}/{@code chuyenphoihop})
 * diễn ra TRÊN 1 bản sao "văn bản đến" đã nhận qua {@code vbden.forward_unit} (nhận diện qua field
 * {@code origin_document_id}/{@code origin_tenant_id} trên bản sao, do
 * {@code VbdenForwardUnitTaskHandler#insertForwardedCopy} ghi lúc tạo bản sao — xem td-woker).
 *
 * <p>Bổ sung 08/09/2026 — yêu cầu người dùng: "muốn phản hồi trạng thái 'Đã đến' và các trạng thái
 * như chuyển xử lý... cho đơn vị 'Chuyển xử lý' vào quá trình xử lý". Đây là phần 2/2 (phần 1 — "Đã
 * đến" MỘT LẦN duy nhất lúc tạo bản sao — xem {@code VbdenForwardUnitReplyPayload}/
 * {@code VbdenForwardUnitReplyTaskHandler#insertArrivalHistoryRow}, td-woker): MỌI hành động xử lý
 * TIẾP THEO tại đơn vị đang giữ bản sao (không giới hạn riêng "chuyển xử lý", dù đó là ví dụ người
 * dùng nêu — bản sao có thể được phân công/phối hợp/chuyển tiếp nhiều lần, kể cả forward tiếp sang
 * tenant thứ 3) được echo NGƯỢC về "quá trình xử lý" ({@code vbden_res_documents}, xem
 * {@code VbdenForwardUnitStatusTaskHandler}, td-woker) của văn bản GỐC, để đơn vị đã "chuyển xử lý"
 * theo dõi được tiến trình xử lý tại đơn vị con mà không cần đăng nhập sang tenant khác.
 *
 * <p>KHÔNG share class/jar với {@code td-woker} — chỉ khớp NHAU về hình dạng JSON, xem
 * {@code VbdenForwardUnitStatusPayload} phía {@code td-woker}.
 */
@Data
@Builder
public class VbdenForwardUnitStatusMessagePayload {

    /** id văn bản đến GỐC (tenant khác) — dùng làm {@code parentid} khi ghi vào {@code vbden_res_documents}. */
    private String originDocumentId;

    /** Mã hành động thô ({@code ChuyenXuLyVbdenUseCase.ACTION_*}) + nhãn hiển thị tiếng Việt tương ứng. */
    private String action;
    private String actionLabel;

    /** Định danh + tên đơn vị đang giữ bản sao (đơn vị vừa thực hiện hành động này). */
    private String reportingIdentifier;
    private String reportingName;

    private String actingUsername;
    private String opinion;

    /** Ai được giao tiếp theo tại đơn vị này (nếu có) — hiển thị bổ sung, không bắt buộc. */
    private String newAssigneesLabel;
}
