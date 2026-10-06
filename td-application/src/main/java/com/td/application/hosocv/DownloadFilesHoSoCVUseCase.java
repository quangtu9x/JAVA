package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.documentlinks.DocumentLinkService;
import com.td.application.documents.ListDocumentFilesUseCase;
import lombok.RequiredArgsConstructor;
import com.td.domain.documentlinks.DocumentLinkType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
 * minh cơ chế truyền file mới.
 *
 * <p><b>02/10/2026</b> (cutover nút xuất hồ sơ {@code ChiTietPage.jsx#ExportAllFile}): có
 * {@code hosocvId} thì server tự gom theo đúng thứ tự FE Domino — văn bản đi
 * ({@code document_out_attached_id}), văn bản đến ({@code document_in_attached_id}), rồi "văn bản
 * khác" ({@link HoSoVanBanKhacFinder}) — sau đó tới các văn bản client gửi kèm; trùng
 * {@code documentId} chỉ lấy 1 lần.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DownloadFilesHoSoCVUseCase {

    private static final String HOSOCV_DOCUMENT_TYPE = "hosocv";

    private final ListDocumentFilesUseCase listDocumentFilesUseCase;
    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DocumentLinkService documentLinkService;
    private final HoSoVanBanKhacFinder hoSoVanBanKhacFinder;

    public Result<List<HoSoFileGroupDto>> execute(DownloadFilesHoSoCVRequest request) {
        List<HoSoFileRefDto> refs = new ArrayList<>(collectHoSoRefs(request.getHosocvId()));
        if (request.getVanBanLienQuan() != null) {
            refs.addAll(request.getVanBanLienQuan());
        }

        List<HoSoFileGroupDto> groups = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (HoSoFileRefDto ref : refs) {
            if (ref.getDocumentId() == null || !seen.add(ref.getDocumentId())) {
                continue;
            }
            var files = listDocumentFilesUseCase.execute(ref.getDocumentId()).getItems();
            groups.add(new HoSoFileGroupDto(ref.getDocumentType(), ref.getDocumentId(), files));
        }
        return Result.success(groups);
    }

    private List<HoSoFileRefDto> collectHoSoRefs(UUID hosocvId) {
        if (hosocvId == null) {
            return List.of();
        }
        var hosocv = hoSoCVDocumentRepository.findById(hosocvId)
            .filter(document -> !document.isDeleted()
                && HOSOCV_DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType()));
        if (hosocv.isEmpty()) {
            return List.of();
        }
        // 06/10/2026: đọc bảng document_links (docs/architecture/document-links.md) thay field
        // document_out_attached_id/document_in_attached_id trên hồ sơ.
        List<HoSoFileRefDto> refs = new ArrayList<>();
        documentLinkService.findTargetIds(hosocvId,
                List.of(DocumentLinkType.HOSO_VBDI, DocumentLinkType.HOSO_VBDI_PHATHANH))
            .forEach(id -> refs.add(ref("vbdi", id)));
        documentLinkService.findTargetIds(hosocvId,
                List.of(DocumentLinkType.HOSO_VBDEN, DocumentLinkType.HOSO_VBDEN_XULY))
            .forEach(id -> refs.add(ref("vbden", id)));
        hoSoVanBanKhacFinder.findByHoSo(hosocvId)
            .forEach(vanBanKhac -> refs.add(ref(vanBanKhac.getDocumentType(), vanBanKhac.getId())));
        return refs;
    }

    private HoSoFileRefDto ref(String documentType, UUID documentId) {
        HoSoFileRefDto ref = new HoSoFileRefDto();
        ref.setDocumentType(documentType);
        ref.setDocumentId(documentId);
        return ref;
    }
}
