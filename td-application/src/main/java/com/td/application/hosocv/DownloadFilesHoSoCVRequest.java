package com.td.application.hosocv;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
}
