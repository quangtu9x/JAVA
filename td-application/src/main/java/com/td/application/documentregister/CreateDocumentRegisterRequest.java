package com.td.application.documentregister;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDocumentRegisterRequest {

    @NotBlank(message = "Tên sổ không được để trống")
    @Size(max = 300, message = "Tên sổ tối đa 300 ký tự")
    private String name;

    @NotBlank(message = "Loại sổ không được để trống")
    private String documentRegisterType; // "Văn bản đi" | "Văn bản đến"

    @NotNull(message = "Năm sử dụng không được để trống")
    private Integer year;

    // Mã định danh đơn vị/phòng ban (currentUser.identifier bên FE) — tách sổ giữa các phòng/ban.
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
    private java.util.UUID userAssignerId;
    private String userAssignerUsername;
    private String assignerPosition;

    private Integer codeStartNumber;
    private Boolean autoIncrement;
    private Boolean isDefault;
    private String codeStartNumbers;
}
