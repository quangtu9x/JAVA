package com.td.application.vbden;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 14/09/2026 ("modal Trả lại văn bản -> chọn và đồng ý -> đang gọi api cũ
 * qlvb/vbden/TraLaiVanBan?databasetype=vbden -> port và cutoverFE") — tương thích payload legacy
 * {@code qlvb/vbden/TraLaiVanBan} (multipart form: {@code {parentid, user_process, name_process,
 * opinion}}, xem {@code ModalTraLaiVanBan.jsx}) — xem javadoc {@link TraLaiVanBanUseCase}.
 *
 * <p>{@code userProcess}/{@code nameProcess} — tên field GIỮ theo Domino gốc dù dễ nhầm ("user_process"
 * ở ĐÂY nghĩa là NGƯỜI ĐƯỢC TRẢ LẠI, chọn từ dropdown "Chọn người trả lại" — kết quả
 * {@code GetNguoiTraLaiUseCase} — KHÔNG phải người đang xử lý như field cùng tên
 * {@code user_process} trên chính văn bản).</p>
 */
@Data
public class TraLaiVanBanRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    @NotBlank(message = "userProcess khong duoc de trong")
    private String userProcess;

    /** Tên hiển thị người được trả lại — chỉ để hiển thị/ghi snapshot, không dùng để xác định quyền. */
    private String nameProcess;

    /** Nội dung trả lại — FE ({@code ModalTraLaiVanBan.jsx}) bắt buộc nhập trước khi cho bấm "Đồng ý". */
    private String opinion;
}
