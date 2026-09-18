package com.td.application.documentregister;

import com.td.domain.documentregister.DocumentRegister;

final class DocumentRegisterDtoMapper {

    private DocumentRegisterDtoMapper() {}

    static DocumentRegisterDto map(DocumentRegister e) {
        var dto = new DocumentRegisterDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setDocumentRegisterType(e.getDocumentRegisterType());
        dto.setYear(e.getYear());
        dto.setIdentifier(e.getIdentifier());
        dto.setAgency(e.getAgency());
        dto.setAgencyId(e.getAgencyId());
        dto.setDocumentType(e.getDocumentType());
        dto.setCodeNotation(e.getCodeNotation());
        dto.setUnitPublish(e.getUnitPublish());
        dto.setUnitPublishIdentifier(e.getUnitPublishIdentifier());
        dto.setUnitPublishId(e.getUnitPublishId());
        dto.setAssigner(e.getAssigner());
        dto.setUserAssignerId(e.getUserAssignerId());
        dto.setUserAssignerUsername(e.getUserAssignerUsername());
        dto.setAssignerPosition(e.getAssignerPosition());
        dto.setCodeStartNumber(e.getCodeStartNumber());
        dto.setAutoIncrement(e.isAutoIncrement());
        dto.setDefault(e.isDefault());
        dto.setCodeStartNumbers(e.getCodeStartNumbers());
        dto.setLocked(e.isLocked());
        dto.setCreatedOn(e.getCreatedOn());
        dto.setLastModifiedOn(e.getLastModifiedOn());
        return dto;
    }

    // BL-210/211 (19/08/2026): tương đương qlvb/danhmuc/DanhSachSo — codeStartNumber trả về là số
    // GỢI Ý đã tính sẵn (peekNextNumber, KHÔNG ghi nhận/thay đổi trạng thái sổ), chỉ có ý nghĩa khi
    // isAutoIncrement=true. Dùng chung cho GetDefaultDocumentRegisterUseCase (1 sổ) và
    // ListSelectableDocumentRegistersUseCase (nhiều sổ, dropdown "Chọn sổ văn bản" khi Nhập mới).
    static DocumentRegisterDto mapWithComputedNextNumber(DocumentRegister e) {
        var dto = map(e);
        if (e.isAutoIncrement()) {
            dto.setCodeStartNumber(e.peekNextNumber());
        }
        return dto;
    }
}
