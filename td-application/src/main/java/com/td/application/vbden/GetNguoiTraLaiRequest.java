package com.td.application.vbden;

import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 14/09/2026 ("ở chi tiết văn bản đến, chức năng Trả lại ... đang gọi
 * qlvb/vbden/GetNguoiTraLai?databasetype=vbden -> port sang java") — tương thích payload legacy
 * {@code qlvb/vbden/GetNguoiTraLai} ({@code {id}}, id văn bản đến cha) — xem javadoc
 * {@link GetNguoiTraLaiUseCase}.
 *
 * <p>KHÔNG {@code @NotNull}: Domino gốc coi {@code id} rỗng là no-op hợp lệ (trả danh sách rỗng,
 * KHÔNG lỗi — xem javadoc use case), không phải lỗi validate.</p>
 */
@Data
public class GetNguoiTraLaiRequest {

    private UUID id;
}
