package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu người dùng 14/09/2026 ("modal Câp nhật kết quả xử lý văn bản -> Đồng ý chưa cutoverFE (gọi
 * qlvb/vbden/CapNhatVanBanDen cũ)") — tương thích 1 PHẦN {@code qlvb/vbden/CapNhatVanBanDen?
 * action=capnhatykien} (nút "Cho ý kiến"/"Bổ sung ý kiến", {@code VBDenButtonActionXuLy.jsx}) — xem
 * javadoc {@link CapNhatYKienVbdenUseCase}.
 */
@Data
public class CapNhatYKienVbdenRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /** Ý kiến — FE ({@code ChoYKienModal.jsx}) gửi field {@code opinion}, không bắt buộc. */
    private String opinion;

    /** Tên hiển thị người thực hiện — chỉ để hiển thị. */
    private String creatorName;

    /** Chức vụ hiển thị — chỉ để hiển thị. */
    private String position;

    /**
     * Gắn văn bản trả lời (tuỳ chọn) — xem javadoc {@link VbdenReplyDocumentRef}. Domino gốc VẪN lưu
     * field này lên bản ghi quá trình xử lý dù switch-case {@code capnhatykien} là no-op hoàn toàn
     * (preamble {@code CreateOrUpdateDocument()} lưu MỌI field multipart gửi lên, không phân biệt
     * action) — port giữ đúng hành vi lưu-nhưng-không-xử-lý-tiếp này.
     */
    private List<VbdenReplyDocumentRef> resObj;
}
