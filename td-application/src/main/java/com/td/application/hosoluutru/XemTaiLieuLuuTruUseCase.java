package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 30/10/2026: thay {@code qlvb/get?databasetype=hosoluutru} cho MỌI loại bản ghi của module (hồ sơ,
 * văn bản con, phiếu yêu cầu, danh mục dùng chung, vết LTLS) — Domino dùng chung 1 CSDL nên FE chỉ gửi
 * id; Java tách 5 bảng nên tra lần lượt. Trả field động + {@code form} + {@code files} (file_metadata,
 * cùng shape {name,size,type,file_id} như {@code XemVanBanKhacUseCase}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class XemTaiLieuLuuTruUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy tài liệu lưu trữ";

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;
    private final HoSoLuuTruPhieuYeuCauDocumentRepository hoSoLuuTruPhieuYeuCauDocumentRepository;
    private final HoSoLuuTruDanhMucDocumentRepository hoSoLuuTruDanhMucDocumentRepository;
    private final HoSoLuuTruProcessDocumentRepository hoSoLuuTruProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;

    public Result<Map<String, Object>> execute(GetHoSoLuuTruChiTietRequest request) {
        UUID id = parseUuid(request == null ? null : request.getId());
        if (id == null) {
            return Result.failure("Thiếu id tài liệu cần xem");
        }
        var vanBan = hoSoLuuTruVanBanDocumentRepository.findById(id).filter(d -> !d.isDeleted());
        if (vanBan.isPresent()) {
            var d = vanBan.get();
            return Result.success(toResponse(d.getId(), d.getTitle(), d.getDocumentType(), "vanban",
                d.getCreatedOn(), d.getDynamicFieldsJson()));
        }
        var phieu = hoSoLuuTruPhieuYeuCauDocumentRepository.findById(id).filter(d -> !d.isDeleted());
        if (phieu.isPresent()) {
            var d = phieu.get();
            return Result.success(toResponse(d.getId(), d.getTitle(), d.getDocumentType(), "phieuyeucau",
                d.getCreatedOn(), d.getDynamicFieldsJson()));
        }
        var danhMuc = hoSoLuuTruDanhMucDocumentRepository.findById(id).filter(d -> !d.isDeleted());
        if (danhMuc.isPresent()) {
            var d = danhMuc.get();
            Map<String, Object> data = toResponse(d.getId(), d.getTitle(), d.getDocumentType(), d.getDocumentType(),
                d.getCreatedOn(), d.getDynamicFieldsJson());
            data.putIfAbsent("name", d.getTitle());
            // form = documentType (kyhieuhoso/chatluong/loaihinhtailieu) — FE danhmuc đọc lại nameform.
            data.put("form", d.getDocumentType());
            return Result.success(data);
        }
        var hoso = hoSoLuuTruDocumentRepository.findById(id).filter(d -> !d.isDeleted());
        if (hoso.isPresent()) {
            var d = hoso.get();
            Map<String, Object> data = toResponse(d.getId(), d.getTitle(), d.getDocumentType(), d.getStatus(),
                d.getCreatedOn(), d.getDynamicFieldsJson());
            data.put("form", d.getStatus());
            return Result.success(data);
        }
        var process = hoSoLuuTruProcessDocumentRepository.findById(id).filter(d -> !d.isDeleted());
        if (process.isPresent()) {
            var d = process.get();
            return Result.success(toResponse(d.getId(), d.getTitle(), d.getDocumentType(), "quatrinhchuyenltls",
                d.getCreatedOn(), d.getDynamicFieldsJson()));
        }
        return Result.failure(NOT_FOUND_MESSAGE);
    }

    private Map<String, Object> toResponse(UUID id, String title, String documentType, String defaultForm,
                                           LocalDateTime createdOn, String dynamicFieldsJson) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("title", title);
        data.put("databasetype", documentType);
        data.put("form", defaultForm);
        data.put("created_on", createdOn);
        data.putAll(dynamicDocumentFieldsService.parseDynamicFields(dynamicFieldsJson));
        data.putIfAbsent("form", defaultForm);
        data.put("files", listFiles(id));
        return data;
    }

    public List<Map<String, Object>> listFiles(UUID documentId) {
        List<Map<String, Object>> files = new ArrayList<>();
        try {
            var page = listDocumentFilesUseCase.execute(documentId);
            if (page == null || page.getItems() == null) {
                return files;
            }
            for (FileDto file : page.getItems()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", file.getOriginalFileName());
                item.put("size", file.getFileSize());
                item.put("type", extensionOf(file.getOriginalFileName()));
                item.put("file_id", file.getFileId());
                files.add(item);
            }
        } catch (Exception ignored) {
            // Tệp là thông tin phụ — lỗi đọc tệp không làm hỏng việc xem tài liệu.
        }
        return files;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
