package com.td.application.sharedcore;

import lombok.Data;

/**
 * Request cho {@code POST /api/v1/danhmuc/publish}, tương thích payload legacy
 * {@code qlvb/danhmuc/publish} ({@code publish.java} thật) — CHỈ hỗ trợ {@code get="capbanhanh"}
 * (xem {@link GetCapBanHanhOptionsUseCase}), dùng bởi dropdown "Cấp ban hành" ở
 * {@code ChiTietPage.jsx} (module dự thảo). Các mode khác của Domino thật ({@code kythualenh},
 * {@code kyuyquyen}, {@code nguoiduyet}, {@code capbanhanhhdndtinh}) CHƯA port — controller trả 400
 * rõ ràng thay vì im lặng trả sai dữ liệu.
 *
 * <p>{@code username}: Domino thật KHÔNG dùng field này trong body — {@code publish.java} luôn lấy
 * username từ session đã xác thực ({@code connection.user_token.username}), bỏ qua giá trị FE gửi
 * lên (đúng nguyên tắc SEC-01 "không tin định danh do client gửi"). Vì vậy DTO này không khai báo
 * field {@code username} — Jackson mặc định bỏ qua field lạ, không cần strip thủ công.</p>
 */
@Data
public class PublishCatalogRequest {

    private String get;

    private String databasetype;
}
