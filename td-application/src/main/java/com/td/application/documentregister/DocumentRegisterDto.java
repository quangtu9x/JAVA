package com.td.application.documentregister;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DocumentRegisterDto {
    private UUID id;
    private String name;
    private String documentRegisterType;
    private int year;
    private String identifier;
    private String agency;
    private String agencyId;
    private String documentType;
    private String codeNotation;
    private String unitPublish;
    private String unitPublishIdentifier;
    private String unitPublishId;
    private String assigner;
    // Bổ sung 04/09/2026 (BL-116 tiếp — xem javadoc DocumentRegister field userAssignerId/
    // userAssignerUsername/assignerPosition).
    private UUID userAssignerId;
    private String userAssignerUsername;
    private String assignerPosition;
    private int codeStartNumber;
    // Bug Lombok+Jackson đã xác nhận (docs/architecture/04-quy-tac-crud-api-shared-core.md): field
    // boolean "isXxx" bị Jackson serialize thành "xxx" nếu không ép bằng @JsonProperty.
    @JsonProperty("isAutoIncrement")
    private boolean autoIncrement;
    @JsonProperty("isDefault")
    private boolean isDefault;
    private String codeStartNumbers;
    @JsonProperty("isLocked")
    private boolean isLocked;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
    /**
     * 09/10/2026 (port Ủy quyền): ủy quyền đang hiệu lực của "Lãnh đạo phân công" mặc định của sổ
     * ({@code userAssignerUsername}) — dạng delegation_obj Domino, "" nếu không có. Thay Domino
     * UyQuyen#capnhat_so_vanban (chép delegation_obj vào bản ghi sổ). Chỉ điền ở API sổ mặc định/sổ để chọn.
     */
    private Object delegationObj = "";
}
