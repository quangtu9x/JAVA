package com.td.application.simpki;

import com.td.application.common.models.Result;

/** Tải PDF đã ký (hoặc chưa ký nếu còn READY) theo {@code simPkiFileId}. */
public interface DownloadSignedPdfUseCase {
    Result<byte[]> execute(String simPkiFileId);
}
