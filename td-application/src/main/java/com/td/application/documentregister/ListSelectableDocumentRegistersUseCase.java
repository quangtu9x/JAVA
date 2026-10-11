package com.td.application.documentregister;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// BL-210/211 (Nhập mới VBDen, 19/08/2026): tương đương qlvb/danhmuc/DanhSachSo khi trả VỀ NHIỀU sổ
// (dropdown "Chọn sổ văn bản" — FE cho phép người dùng chọn 1 sổ KHÁC sổ mặc định, khác
// GetDefaultDocumentRegisterUseCase chỉ trả đúng 1 sổ mặc định). Mỗi sổ trả về có codeStartNumber
// đã tính sẵn (peekNextNumber) như GetDefaultDocumentRegisterUseCase.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ListSelectableDocumentRegistersUseCase {

    private final DocumentRegisterRepository documentRegisterRepository;
    private final com.td.application.sharedcore.UserDelegationResolver userDelegationResolver;

    public Result<List<DocumentRegisterDto>> execute(String identifier, String documentRegisterType) {
        try {
            if (identifier == null || identifier.isBlank()) {
                return Result.failure("identifier không được để trống");
            }
            if (documentRegisterType == null || documentRegisterType.isBlank()) {
                return Result.failure("documentRegisterType không được để trống");
            }
            var registers = documentRegisterRepository
                .findByIdentifierAndDocumentRegisterTypeAndIsLockedFalseAndDeletedOnIsNull(
                    identifier.trim(), documentRegisterType.trim());
            // 09/10/2026: ủy quyền đang hiệu lực của Lãnh đạo phân công mặc định từng sổ (1 truy vấn).
            var delegations = userDelegationResolver.activeByDelegator(registers.stream()
                .map(r -> r.getUserAssignerUsername()).filter(java.util.Objects::nonNull).toList());
            var dtos = registers.stream()
                .map(register -> {
                    DocumentRegisterDto dto = DocumentRegisterDtoMapper.mapWithComputedNextNumber(register);
                    dto.setDelegationObj(userDelegationResolver.activeObjOrEmpty(delegations, register.getUserAssignerUsername()));
                    return dto;
                })
                .toList();
            return Result.success(dtos);
        } catch (Exception ex) {
            log.error("Lấy danh sách sổ văn bản để chọn thất bại", ex);
            return Result.failure("Lấy danh sách sổ văn bản để chọn thất bại: " + ex.getMessage());
        }
    }
}
