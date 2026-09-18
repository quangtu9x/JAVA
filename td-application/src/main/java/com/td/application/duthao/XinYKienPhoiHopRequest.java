package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BL-230 (DuThao Core) — DoD (2): request cho {@code POST /api/v1/duthao/XinYKienPhoiHop}. Đúng
 * bằng chứng thật ({@code ChoYKienPhoiHop.java}, field {@code user_coordinator}/
 * {@code user_coordinated}): người dùng hiện tại trong danh sách chờ ({@code user_coordinator})
 * phản hồi ý kiến sẽ tự động được chuyển sang danh sách đã phản hồi ({@code user_coordinated}).
 * {@code nguoiPhoiHop} (thêm người vào danh sách chờ) không có servlet riêng evidenced (khởi tạo
 * danh sách chờ nằm ngoài phạm vi 5 file đã đọc) — hỗ trợ thêm ở đây vì cùng field
 * {@code user_coordinator} nên không cần API riêng, không suy diễn field mới.
 */
@Data
public class XinYKienPhoiHopRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Thêm người vào danh sách chờ ý kiến phối hợp (nếu có). */
    private List<String> nguoiPhoiHop = new ArrayList<>();

    /** Ý kiến phản hồi của người dùng hiện tại (nếu người dùng hiện tại đang phản hồi). */
    private String opinion;
}
