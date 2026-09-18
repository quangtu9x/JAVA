package com.td.application.hosocv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BL-240 (HoSoCV Core) — DoD (2): request cho {@code POST /api/v1/hosocv/GanVanBan}. Đúng bằng
 * chứng thật ({@code GanKemVanBan.XuLyGanVaoHoSoCV_Chung()}): {@code hosocvId} (hồ sơ đích),
 * {@code vanBanIds} (danh sách văn bản/dự thảo gắn vào — {@code vanbanids} thật), {@code actionName}
 * (loại gắn kèm: {@code gankemduthao}/{@code ganvanbandi}/{@code ganvanbanphathanh}/
 * {@code ganvanbanden}/{@code ganvanbandenxuly}/{@code gankemphieutrinh} — xem
 * {@link GanKemVanBanUseCase}), {@code action} ({@code "remove"} để gỡ, còn lại (kể cả rỗng) là
 * gắn thêm — đúng {@code if(action.equals("remove")) {...} else {...}} thật).
 *
 * <p>Khác biệt so với Domino thật: KHÔNG cần tham số {@code databasetype} (Domino cần để mở đúng
 * NSF database vật lý của văn bản) vì mọi document (bất kể loại) đều nằm chung 1 bảng
 * {@code documents}, tra cứu trực tiếp theo {@code vanBanId} là đủ.
 */
@Data
public class GanKemVanBanRequest {

    @NotNull(message = "hosocvId khong duoc de trong")
    private UUID hosocvId;

    private List<UUID> vanBanIds = new ArrayList<>();

    @NotBlank(message = "actionName khong duoc de trong")
    private String actionName;

    private String action;
}
