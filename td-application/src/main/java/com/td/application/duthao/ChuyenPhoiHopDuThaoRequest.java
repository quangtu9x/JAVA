package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 19/10/2026 — port Domino {@code qlvb/vanbanduthao/ChuyenPhoiHopDuThao} (nút "Chuyển phối hợp" ở danh sách
 * "Phối hợp dự thảo", {@code duthao/chogopy/components/ItemsList.jsx}). Cùng payload FE gửi Domino.
 */
@Data
public class ChuyenPhoiHopDuThaoRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Tên người được chuyển phối hợp, nối "; " (Domino: {@code coordinator}). */
    private String coordinator;

    /** Tài khoản người được chuyển phối hợp, nối ";" (Domino: {@code user_coordinator}). */
    private String userCoordinator;

    /**
     * Mảng người được chọn (Domino: {@code coordinator_obj}) — chuỗi JSON hoặc mảng; phần tử có thể kèm
     * {@code delegated_from_username} (ủy quyền, xem {@link DuThaoCoordinatorDelegations}).
     */
    private Object coordinatorObj;
}
