package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.documents.ListDocumentFilesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * BL-240 (HoSoCV Core) — DoD (4): download file từ hồ sơ. Đúng cơ chế thật
 * ({@code TDAttachment.GetAttachmentByHSCV()}, gọi qua {@code qlvb/getallfilehosocv} —
 * KHÔNG phải route {@code qlvb/hosocv/downloadfile} của {@code HoSoCVController} thật, vốn là 1
 * stub rỗng ({@code DownloadFileHSCV.DownloadFileAllHSCV()} chỉ lấy document theo UNID rồi không
 * làm gì thêm) — nên phải bám theo route/servlet thật sự có logic, không phải route trùng tên gây
 * nhầm lẫn): với mỗi văn bản liên quan (dự thảo/văn bản đến/văn bản đi/văn bản phát hành/phiếu
 * trình — FE tự gom {@code {databasetype, id}} từ các field {@code *_attached_id} của hồ sơ, xem
 * {@link GanKemVanBanUseCase}), trả về TOÀN BỘ danh sách file đính kèm.
 *
 * <p>Khác biệt so với Domino thật: trả về metadata file ({@link com.td.application.documents.FileDto})
 * thay vì nội dung base64 nhúng trực tiếp trong response — do kiến trúc lưu trữ MinIO (đã dùng
 * xuyên suốt dự án, xem {@code UploadFileUseCase}/{@code ListDocumentFilesUseCase}) thay vì NSF
 * attachment nhúng trong document; tải nội dung file thật đi qua endpoint chung đã có sẵn
 * {@code GET /api/v1/documents/{documentId}/files/{fileId}/download} cho từng file, không phát
 * minh cơ chế truyền file mới. Không quét theo {@code idHoSo} (không dùng trong logic thật, xem
 * Javadoc {@link DownloadFilesHoSoCVRequest}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DownloadFilesHoSoCVUseCase {

    private final ListDocumentFilesUseCase listDocumentFilesUseCase;

    public Result<List<HoSoFileGroupDto>> execute(DownloadFilesHoSoCVRequest request) {
        List<HoSoFileRefDto> refs = request.getVanBanLienQuan() == null ? List.of() : request.getVanBanLienQuan();
        List<HoSoFileGroupDto> groups = new ArrayList<>();
        for (HoSoFileRefDto ref : refs) {
            if (ref.getDocumentId() == null) {
                continue;
            }
            var files = listDocumentFilesUseCase.execute(ref.getDocumentId()).getItems();
            groups.add(new HoSoFileGroupDto(ref.getDocumentType(), ref.getDocumentId(), files));
        }
        return Result.success(groups);
    }
}
