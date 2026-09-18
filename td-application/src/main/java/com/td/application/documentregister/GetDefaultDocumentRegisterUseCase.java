package com.td.application.documentregister;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// BL-210/211 (Nhập mới VBDen): tương đương qlvb/danhmuc/DanhSachSo (Domino thật) — lấy đúng 1 sổ
// MẶC ĐỊNH của 1 đơn vị (identifier) + 1 loại sổ ("Văn bản đến"/"Văn bản đi") để tự điền form "Nhập
// mới". Khác Domino ở chỗ KHÔNG nhận danh sách "keys" tuỳ ý rồi trả cả danh sách — chỉ trả đúng 1 sổ
// mặc định (đã có sẵn ràng buộc "chỉ 1 sổ mặc định/loại sổ/đơn vị" từ BL-116), FE không cần tự chọn
// phần tử đầu tiên trong mảng kết quả như bản Domino cũ.
//
// `codeStartNumber` trả về là số ĐÃ TÍNH SẴN (peekNextNumber — không ghi nhận/thay đổi trạng thái sổ),
// CHỈ có ý nghĩa gợi ý khi `autoIncrement=true`; khi `autoIncrement=false` (sổ không tự động đánh số,
// mirror `tangsotudong="0"` phía Domino) FE phải yêu cầu người dùng nhập số thủ công, bỏ qua giá trị
// gợi ý này.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GetDefaultDocumentRegisterUseCase {

    private final DocumentRegisterRepository documentRegisterRepository;

    public Result<DocumentRegisterDto> execute(String identifier, String documentRegisterType) {
        try {
            if (identifier == null || identifier.isBlank()) {
                return Result.failure("identifier không được để trống");
            }
            if (documentRegisterType == null || documentRegisterType.isBlank()) {
                return Result.failure("documentRegisterType không được để trống");
            }
            var opt = documentRegisterRepository.findByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
                identifier.trim(), documentRegisterType.trim());
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy sổ văn bản mặc định cho đơn vị này");
            }
            var register = opt.get();
            if (register.isLocked()) {
                return Result.failure("Sổ văn bản '" + register.getName() + "' đã bị khoá");
            }
            return Result.success(DocumentRegisterDtoMapper.mapWithComputedNextNumber(register));
        } catch (Exception ex) {
            log.error("Lấy sổ văn bản mặc định thất bại", ex);
            return Result.failure("Lấy sổ văn bản mặc định thất bại: " + ex.getMessage());
        }
    }
}
