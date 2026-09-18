package com.td.application.sharedcore;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class AppUserDto {
    private UUID id;
    private String keycloakSubject;
    private String username;
    private String fullName;
    private String email;
    private UUID organizationId;
    private UUID positionId;
    // Bug đã xác nhận (18/08/2026, xem docs/architecture/04-quy-tac-crud-api-shared-core.md): field
    // "boolean" đặt tên đã có sẵn tiền tố "is" khiến Lombok sinh getter isActive()/setter
    // setActive(boolean) (KHÔNG có setIsActive), rồi Jackson tự strip tiếp "is" khỏi getter khi suy
    // ra tên JSON → serialize thành "active" thay vì "isActive" mà FE đang đọc (dto.isActive luôn
    // undefined). @JsonProperty ép đúng tên key JSON, đã verify bằng ObjectMapper thật.
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;

    // BL-108
    private Integer level;
    private int sortOrder;
    private String phoneNumber;
    private String gender;
    private LocalDate birthdayDate;
    private String address;
    private String userIdNo;
    private String capLanhDao;
    private String identifier;
    @JsonProperty("isSim")
    private boolean isSim;
    private UUID parentUserId;

    // Gap #5 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026): badge
    // "có kiêm nhiệm" — TRUE nếu có ít nhất 1 AppUser khác trỏ parentUserId về bản ghi này, tính
    // ĐÚNG TOÀN CỤC qua query (khác best-effort trong-trang trước đây ở ItemsList.jsx). Cùng lý do
    // @JsonProperty tường minh như "isActive"/"isSim" ở trên (field không có tiền tố "is").
    @JsonProperty("hasKiemNhiem")
    private boolean hasKiemNhiem;

    // "Nhóm vai trò" (BL-111) — 1 user có thể thuộc NHIỀU nhóm (N:N, bổ sung 19/08/2026).
    private List<UUID> roleGroupIds;

    // Field tự định nghĩa từ FE (cột dynamic_fields JSONB) — BL-109 mở rộng cho AppUser.
    private Map<String, Object> dynamicFields;
}
