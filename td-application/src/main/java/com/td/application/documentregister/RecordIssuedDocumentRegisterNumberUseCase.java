package com.td.application.documentregister;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("Chọn sổ văn bản" ở {@code ChiTietModal.jsx} vbdi, port
 * {@code updateSoVanBan}, {@code ControllModal.jsx}): endpoint RIÊNG để ghi nhận 1 số vừa phát cho
 * 1 sổ văn bản — cần thiết vì "Thêm mới" văn bản đi (vbdi) HIỆN VẪN còn POST thẳng lên Domino
 * ({@code qlvb/add?databasetype=vbdi}, CHƯA cắt sang {@code SoanThaoVbdiUseCase}/BL-220), trong khi
 * "Sổ văn bản" (BL-116) đã chuyển hẳn sang Java ({@code document_registers}) — FE (dropdown "Chọn
 * sổ văn bản") giờ đọc/ghi sổ ở Postgres, KHÔNG còn ở Domino {@code tudien} nữa, nên bước "tăng số
 * sổ sau khi lưu" cũng PHẢI gọi Java thay vì {@code qlvb/add?databasetype=tudien&id=<UUID>} (Domino
 * không biết UUID Postgres này).
 *
 * <p>Khác {@code TiepNhanVbdenUseCase}/{@code SoanThaoVbdiUseCase} (tự gọi
 * {@code register.recordIssuedNumber(...)} NGAY TRONG luồng tạo document Java) — use case này phục
 * vụ đúng tình huống "tạo document ở nơi khác (Domino), chỉ cần ghi nhận số ở Java sau đó". Khi
 * "Thêm mới" vbdi cắt hẳn sang {@code SoanThaoVbdiUseCase} (đã tích hợp sẵn), endpoint này thành dư
 * thừa cho vbdi nhưng vẫn có ý nghĩa tổng quát (module khác có thể cần cùng kiểu bắc cầu).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RecordIssuedDocumentRegisterNumberUseCase {

    private final DocumentRegisterRepository documentRegisterRepository;

    public Result<UUID> execute(UUID id, RecordIssuedDocumentRegisterNumberRequest request) {
        try {
            var opt = documentRegisterRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy sổ văn bản với ID: " + id);
            }
            var register = opt.get();
            register.recordIssuedNumber(request.getNumber());
            var saved = documentRegisterRepository.save(register);
            return Result.success(saved.getId());
        } catch (IllegalStateException ex) {
            return Result.failure(ex.getMessage());
        } catch (Exception ex) {
            log.error("Ghi nhận số văn bản thất bại", ex);
            return Result.failure("Ghi nhận số văn bản thất bại: " + ex.getMessage());
        }
    }
}
