package com.td.application.documents;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bugfix 31/08/2026 ("ở danh sách chưa thấy hiển thị tệp đính kèm" — màn "Vào sổ văn bản đến",
 * {@code GetSoVanBanDenUseCase}): batch tương đương {@link ListDocumentFilesUseCase} (1 document)
 * nhưng cho NHIỀU document 1 lúc — 1 trang danh sách có thể tới 200 dòng, gọi
 * {@code ListDocumentFilesUseCase} riêng cho từng dòng sẽ tạo N+1 query. Document không có tệp nào
 * đơn giản KHÔNG xuất hiện trong map trả về (không throw, không entry rỗng).
 */
public interface ListFilesForDocumentsUseCase {
    Map<UUID, List<FileDto>> execute(List<UUID> documentIds);
}
