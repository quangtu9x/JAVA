package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Bổ sung 07/09/2026 (2, yêu cầu người dùng: "STATUS_02 'Từ chối tiếp nhận' ... khi vào sổ có gọi
 * qlvb/vbdi?...action=ReplyDocumentOut ... kiểm tra và port luôn cả 2") — request cho
 * {@code POST /api/v1/vbden/TuChoiTiepNhan}, tương thích {@code ReplyDocumentOut.java} thật
 * (package {@code vn.com.tandan.xsp.services.vbdi}, gọi qua {@code qlvb/vbdi?databasetype=vbden
 * &action=ReplyDocumentOut}) — CHỈ 2 nhánh {@code type != "vaoso"} (từ chối/trả lại), xem javadoc
 * {@link TuChoiTiepNhanVbdenUseCase}.
 */
@Data
public class TuChoiTiepNhanVbdenRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Lý do từ chối/trả lại — FE gửi field {@code opinion} (tương thích {@code ReplyDocumentOut}). */
    private String opinion;

    /**
     * {@code "tralai"} = "Trả lại" (Domino: {@code status_code="tralai"}, KHÔNG thuộc 6 mã chuẩn
     * {@code StatusCode.java}); mọi giá trị khác (kể cả rỗng, FE hiện gửi {@code "no"}) = "Từ chối
     * tiếp nhận" (STATUS_02). Xem javadoc {@link TuChoiTiepNhanVbdenUseCase} về lý do "tralai"
     * KHÔNG publish broker trạng thái.
     */
    private String type;
}
