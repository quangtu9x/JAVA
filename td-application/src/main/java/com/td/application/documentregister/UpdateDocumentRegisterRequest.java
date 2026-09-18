package com.td.application.documentregister;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDocumentRegisterRequest {

    @Size(max = 300, message = "Tên sổ tối đa 300 ký tự")
    private String name;

    private String documentRegisterType;
    private Integer year;
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
