package com.td.application.simpki;

import com.td.application.common.models.Result;

import java.util.List;

/**
 * Định vị vị trí chữ ký trong PDF qua TDSimPki ({@code POST /api/pdf/locate-signatures}, stateless) —
 * dùng cho tính năng TỰ ĐẶT CHỮ KÝ theo tên người ký ở modal Ký SIM (TDPdfViewer). Không khớp gì →
 * {@code Result.success(List.of())} (không phải lỗi — caller chỉ đang "thử tìm").
 */
public interface LocateSimPkiSignaturesUseCase {
    Result<List<SimPkiSignatureLocation>> execute(LocateSimPkiSignaturesRequest request);
}
