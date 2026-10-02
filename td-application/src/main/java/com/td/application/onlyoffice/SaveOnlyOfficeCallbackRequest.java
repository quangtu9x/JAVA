package com.td.application.onlyoffice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.util.UUID;

/**
 * Lưu bản OnlyOffice vừa sửa (status 2/6 ở callback — xem {@code SaveOnlyOfficeCallbackUseCase}) của tệp
 * gốc {@code documentId}/{@code fileId}.
 *
 * <p><b>Đổi hành vi 25/09/2026</b> (yêu cầu người dùng: "tôi muốn lưu vết và file mới, không lưu đè vào
 * file cũ, có thể tham khảo chức năng sửa file cũ (bte)"): trước đây GHI ĐÈ đúng tệp gốc (giữ nguyên
 * fileId). Nay lưu thành TỆP MỚI (dòng file_metadata mới, {@code source_file_id} = tệp gốc), tệp gốc giữ
 * nguyên — cùng tinh thần luồng "Sửa tệp" BTE ({@code tdeditorJavaFile.js}). {@code editSessionKey} (=
 * {@code document.key} của phiên soạn thảo) để các lần callback sau trong CÙNG phiên ghi vào đúng tệp mới
 * đó thay vì tạo trùng; {@code username} (người mở phiên) dùng đặt tên tệp mới giống BTE.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveOnlyOfficeCallbackRequest {

    private UUID documentId;
    private UUID fileId;
    private InputStream content;
    private long contentLength;
    private String editSessionKey;
    private String username;
}
