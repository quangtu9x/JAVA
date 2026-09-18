package com.td.application.vbden;

import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 14/09/2026 ("làm luôn chức năng Gắn văn bản trả lời ở modal này luôn" — tab
 * "Sổ văn bản đi"/"Văn bản đi liên quan" trong {@code ChoYKienModal.jsx}, state {@code feedback} —
 * xem {@code ItemsActiveListVBdiTraLoi.jsx}): 1 văn bản đi (đã Java-native, {@code id} = UUID
 * Postgres) được chọn làm "văn bản trả lời" khi cập nhật/kết thúc xử lý văn bản đến — tương đương 1
 * phần tử mảng {@code res_obj} phía Domino ({@code {id, code, promulgation_date, unit_publish,
 * action:"ganvbdi"}}, xem {@code CapNhatVanBanDen.java#processResObj}).
 *
 * <p>Chỉ {@code code} thực sự được đọc lại (hiển thị ở tab "Quá trình xử lý",
 * {@code Ketquaxuly.jsx#build_res_obj}); {@code promulgationDate} dùng làm mốc {@code ngaybanhanh}
 * thay {@code @Today} khi tính lại {@code status} (xem javadoc
 * {@link CapNhatKetQuaXuLyUseCase}/{@link KetThucXuLyUseCase}); {@code unitPublish}/{@code id} chỉ
 * lưu để đối chiếu, không dùng để tính toán gì thêm.</p>
 */
@Data
public class VbdenReplyDocumentRef {

    private UUID id;

    private String code;

    /** Định dạng {@code dd/MM/yyyy}, giống {@code deadline_date} — rỗng/không parse được thì bỏ qua. */
    private String promulgationDate;

    private String unitPublish;
}
