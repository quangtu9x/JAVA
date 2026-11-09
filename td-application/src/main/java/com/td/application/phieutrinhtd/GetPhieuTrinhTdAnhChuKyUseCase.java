package com.td.application.phieutrinhtd;

import com.td.application.common.models.Result;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserSignatureImagesUseCase;
import com.td.application.sharedcore.UserSignatureImageDto;
import com.td.application.sharedcore.UserSignatureImagesDto;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * 30/10/2026: ảnh chữ ký chính (anhkyso) của 1 người liên quan trong khối chữ ký phiếu trình
 * (ChiTietPage.jsx#setDataNguoiLienQuan) - thay Domino {@code qlvb/get?databasetype=qlvb items=anhkyso}
 * + {@code qlvb/getfile}. FE chỉ có username (id trong *_obj là id giả lập), nên tra AppUser theo
 * username rồi dùng lại {@link GetUserSignatureImagesUseCase}. Trả metadata (documentId/fileId) - FE tải
 * ảnh qua api/v1/identity-documents/{documentId}/files/{fileId}/download như các nơi khác.
 *
 * <p>Chỉ trả ảnh của người CÓ TÊN trong phiếu trình (người tạo/người thông qua/lãnh đạo duyệt/phối
 * hợp...) - tránh dùng endpoint dò ảnh chữ ký của người bất kỳ. {@code data = null} = chưa gắn ảnh.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPhieuTrinhTdAnhChuKyUseCase {

    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final AppUserRepository appUserRepository;
    private final GetUserSignatureImagesUseCase getUserSignatureImagesUseCase;

    public Result<UserSignatureImageDto> execute(GetPhieuTrinhTdAnhChuKyRequest request) {
        if (request == null || isBlank(request.getId()) || isBlank(request.getUsername())) {
            return Result.failure("Thiếu id phiếu trình hoặc tên đăng nhập");
        }
        UUID id;
        try {
            id = UUID.fromString(request.getId().trim());
        } catch (IllegalArgumentException e) {
            return Result.failure("Id phiếu trình không hợp lệ");
        }
        PhieuTrinhTdDocument document = phieuTrinhTdDocumentRepository.findById(id).orElse(null);
        if (document == null) {
            return Result.failure("Không tìm thấy phiếu trình");
        }
        String username = request.getUsername().trim();
        if (!isParticipant(document.getDynamicFieldsJson(), username)) {
            return Result.failure("Người dùng không thuộc phiếu trình");
        }
        var user = appUserRepository.findByUsernameAndDeletedOnIsNull(username).orElse(null);
        if (user == null) {
            return Result.success(null);
        }
        Result<UserSignatureImagesDto> images = getUserSignatureImagesUseCase.execute(user.getId());
        if (!images.isSuccess() || images.getData() == null) {
            return Result.success(null);
        }
        return Result.success(images.getData().getOfficial());
    }

    // Username xuất hiện trong field động dưới dạng giá trị JSON ("u"), chuỗi ";u;" (user_receivers...)
    // hoặc JSON lồng dạng chuỗi (\"u\" - created_by_user_info, *_obj cũ).
    static boolean isParticipant(String dynamicFieldsJson, String username) {
        if (dynamicFieldsJson == null || username == null || username.isBlank()) {
            return false;
        }
        String hay = dynamicFieldsJson.toLowerCase(Locale.ROOT);
        String u = username.toLowerCase(Locale.ROOT);
        return hay.contains("\"" + u + "\"")
            || hay.contains(";" + u + ";")
            || hay.contains("\\\"" + u + "\\\"");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
