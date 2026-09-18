package com.td.application.documents;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;

import java.util.UUID;

/**
 * Gap "đính kèm" 28/08/2026 ("port luôn gap còn lại đi"): đổi tên tệp cho tệp tải lên qua Java/
 * MinIO (có {@code file_id}) — trước đó FE (vd {@code Files/filesVBdi.jsx}) chỉ gọi được Domino
 * {@code qlvb/renamefile}, không đổi tên được tệp thuộc về document đã tách sang Postgres/Java
 * (Domino không có bản ghi tương ứng theo {@code id}).
 */
public interface RenameFileUseCase extends UseCase<RenameFileRequest, Result<UUID>> {
}
