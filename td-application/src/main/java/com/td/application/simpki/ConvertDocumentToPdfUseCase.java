package com.td.application.simpki;

import com.td.application.common.models.Result;

/** Chuyển đổi Word/Excel/PPT/... sang PDF qua TDSimPki (`/api/pdf/convert`) — trả thẳng bytes PDF. */
public interface ConvertDocumentToPdfUseCase {
    Result<byte[]> execute(byte[] fileBytes, String fileName);
}
