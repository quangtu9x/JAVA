package com.td.application.hosocv;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BL-240 (HoSoCV Core) — DoD (4): request cho {@code POST /api/v1/hosocv/GetAllFile}. Tương ứng
 * body thật {@code {idHoSo, formDataVBLQ}} gửi lên {@code qlvb/getallfilehosocv}
 * ({@code AttachmentController.doGetAllFileHSCV} → {@code TDAttachment.GetAttachmentByHSCV()}) —
 * {@code idHoSo} không thực sự được dùng trong logic thật (chỉ {@code formDataVBLQ} — danh sách
 * văn bản liên quan — được xử lý), nên KHÔNG mang sang đây (tránh field không dùng).
 */
@Data
public class DownloadFilesHoSoCVRequest {

    private List<HoSoFileRefDto> vanBanLienQuan = new ArrayList<>();

    /**
     * 02/10/2026 (xuất hồ sơ "Tải toàn bộ tệp" — ChiTietPage.jsx#ExportAllFile): có id hồ sơ thì
     * server TỰ gom văn bản đi/đến đã gắn + "văn bản khác" của hồ sơ (không phụ thuộc trang danh
     * sách FE đang hiển thị), gộp với {@link #vanBanLienQuan} nếu có.
     */
    private UUID hosocvId;
}
