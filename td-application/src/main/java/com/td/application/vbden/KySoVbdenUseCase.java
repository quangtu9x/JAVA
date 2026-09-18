package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.UploadFileRequest;
import com.td.application.documents.UploadFileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Yêu cầu người dùng 15/09/2026 ("port ký số cho các module khác như vbdi, vbden ... giữ file gốc
 * trước khi ký, file ký lưu mới"): tương đương {@code KySoVbdiUseCase} (vbdi) cho vbden — tái dùng
 * {@link UploadFileUseCase} (BL-006 family) để lưu file đã ký, đánh dấu {@code isPrimary=true} (xem
 * {@code UploadFileUseCaseImpl#demoteExistingPrimary} — tệp CŨ tự động bị demote, KHÔNG bị xoá, vẫn
 * còn nguyên trong danh sách tệp của văn bản đến).
 *
 * <p>Khác {@code KySoVbdiUseCase} 1 điểm: KHÔNG có field {@code dakyso}/{@code dakycoquan} tương ứng
 * nào trên văn bản đến (không có gate nghiệp vụ nào ở vbden đọc field này — khác vbdi có
 * {@code VBDIButtonAction.jsx} cảnh báo "chưa ký số cơ quan" trước khi gửi) — CHỈ lưu tệp, không
 * mutate field nào khác trên văn bản đến cha, đúng đề bài "thay thế file, giữ file gốc". Vết lịch sử
 * ("đã ký số") ghi RIÊNG qua {@link GanKemTepVbdenUseCase} (FE gọi tiếp sau khi upload thành công),
 * không gộp vào use case này — cùng tách bạch "lưu file" / "ghi vết" đã áp dụng cho duthao/vbdi.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class KySoVbdenUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";

    private final DocumentRepository documentRepository;
    private final UploadFileUseCase uploadFileUseCase;

    public Result<UUID> execute(KySoVbdenRequest request) {
        var documentOptional = documentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (!DOCUMENT_TYPE.equalsIgnoreCase(documentOptional.get().getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        var uploadRequest = UploadFileRequest.builder()
            .documentId(request.getId())
            .fileContent(request.getFileContent())
            .fileName(request.getFileName())
            .mimeType(request.getMimeType())
            .fileSize(request.getFileSize())
            .isPrimary(true)
            .description("Tệp đã ký số")
            .build();

        return uploadFileUseCase.execute(uploadRequest);
    }
}
