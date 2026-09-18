package com.td.application.documents;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (2): 1 chữ ký số tìm thấy trong file PDF, đúng cấu trúc
 * thật ({@code TDAttachmentSignVerify.getContent()}: {@code name}/{@code time}/{@code status}/
 * {@code timespan}).
 *
 * <p><b>Giới hạn đã ghi nhận (đúng "Rủi ro" của BL-221 — "Tích hợp LGSP/CA tỉnh")</b>: chỉ xác
 * thực TOÀN VẸN dữ liệu (chữ ký có khớp đúng nội dung + khoá công khai trên certificate hay
 * không) — KHÔNG xác thực certificate người ký có phải do CA tỉnh/LGSP cấp và còn hiệu lực hay
 * không (cần tích hợp CA trust store thật, ngoài phạm vi BL-221 — cần làm việc với bên thứ ba).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChuKySoDto {

    /** Tên người ký (CN trên certificate) — tương đương {@code Name_of_the_signer} thật. */
    @JsonProperty("name")
    private String name;

    /** Thời điểm ký (theo field {@code /M} trong signature dictionary PDF). */
    @JsonProperty("time")
    private String time;

    /** Kết quả toàn vẹn dữ liệu: {@code true} nếu chữ ký khớp đúng nội dung đã ký. */
    @JsonProperty("integrity_valid")
    private boolean integrityValid;

    /** {@code true} nếu chữ ký bao phủ toàn bộ tài liệu (không có nội dung thêm sau khi ký). */
    @JsonProperty("covers_whole_document")
    private boolean coversWholeDocument;
}
