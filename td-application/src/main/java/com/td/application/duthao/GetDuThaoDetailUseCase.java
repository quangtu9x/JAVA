package com.td.application.duthao;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BL-230 (DuThao Core) — Slice 1 ("create/save core + get detail", tài liệu phân tích 04 mục 13):
 * xem chi tiết dự thảo với tất cả field (tĩnh + động), tương thích API legacy
 * {@code qlvb/get?databasetype=duthao} dùng bởi {@code ChiTietPage.jsx#fetchData}.
 *
 * <p><b>Cập nhật 24/08/2026 (yêu cầu người dùng "tạo bảng riêng, để độc lập dữ liệu")</b>: TRƯỚC
 * ĐÂY uỷ quyền cho {@code GetDynamicDocumentUseCase} (BL-101, hạ tầng chung {@code documents}/
 * {@code dynamic_fields}) — không còn đúng vì {@code duthao} đã chuyển sang bảng vật lý ĐỘC LẬP
 * {@code duthao_documents} (xem {@link DuThaoDocument}), {@code GetDynamicDocumentUseCase} sẽ KHÔNG
 * BAO GIỜ tìm thấy document loại này nữa. Viết lại TRỰC TIẾP (đọc {@link DuThaoDocumentRepository}),
 * cùng công thức field {id, databasetype, status, content, created_on, last_modified_on} +
 * {@code dynamic_fields} như {@code GetDynamicDocumentUseCase} — giữ đúng response shape cũ, FE
 * không cần đổi cách đọc.</p>
 *
 * <p>Bugfix tương tự {@code GetVbdenDetailUseCase#executePlainDocument}: {@link TaoDuThaoUseCase}
 * lưu "trích yếu" (field {@code subject} người dùng nhập) vào CỘT TĨNH {@code title} của entity
 * (không vào {@code dynamic_fields} — {@code DuThaoController#buildTaoDuThaoRequest} loại
 * {@code subject}/{@code title} khỏi fields động trước khi ghi) — dự thảo MỚI TẠO qua Java sẽ không
 * có {@code subject} trong {@code dynamic_fields}, chỉ có {@code title}. {@code putIfAbsent} (KHÔNG
 * đè) để dự thảo đã từng được HIỆU CHỈNH qua {@code Luu()} (đã có {@code subject} riêng trong
 * {@code dynamic_fields}, có thể đã lệch khỏi {@code title} gốc) giữ đúng giá trị đã lưu.</p>
 *
 * <p>CHƯA port (ngoài phạm vi Slice 1, xem tài liệu phân tích 04 mục 12): đánh dấu "đã xem"
 * ({@code log_read_obj}) — chưa thấy view nào đã port dựa vào field này, khác {@code vbden}
 * ({@code user_to_read}/{@code user_to_readed}) đã có view lọc theo 2 field đó.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDuThaoDetailUseCase {

    public static final String DOCUMENT_TYPE = TaoDuThaoUseCase.DOCUMENT_TYPE;

    private static final String SUBJECT_FIELD = "subject";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản dự thảo";

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final com.td.application.documentlinks.RelatedDocumentSnapshots relatedDocumentSnapshots;

    public Result<Map<String, Object>> execute(GetDuThaoDetailRequest request) {
        UUID id = parseUuid(request == null ? null : request.getId());
        if (id == null) {
            return Result.failure("Thiếu id văn bản dự thảo cần xem");
        }

        DuThaoDocument document = duThaoDocumentRepository.findById(id).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("content", document.getContent());
        // Bugfix 26/08/2026 (người dùng: "created_on và last_modified_by chưa thấy lưu thông tin"):
        // expose đủ 4 cột audit — trước đây chỉ có created_on/last_modified_on nên không thể nhìn
        // thấy ai tạo/sửa. Giá trị createdBy/lastModifiedBy được Spring Data JPA auditing điền tự
        // động (xem AuditableEntity). Key thêm mới — FE cũ bỏ qua key lạ, không phá shape cũ.
        allFields.put("created_by", document.getCreatedBy());
        allFields.put("last_modified_by", document.getLastModifiedBy());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        allFields.putIfAbsent(SUBJECT_FIELD, document.getTitle());
        allFields.put("files", collectFiles(document.getId()));
        // 06/10/2026: documentin_obj / vanban_lienquan_obj dung lai tu bang document_links (giai doan 2 -
        // docs/architecture/document-links.md), ghi de gia tri cu con sot trong dynamic_fields.
        relatedDocumentSnapshots.decorate(allFields, document.getId(), com.td.application.documentlinks.RelatedDocumentSnapshots.SOURCE_DUTHAO);

        Map<String, Object> filtered = dynamicDocumentFieldsService.filterItems(allFields, request.getItems());
        return Result.success(filtered);
    }

    // Bugfix 27/08/2026 — LẦN 2, đảo lại quyết định lúc sáng cùng ngày (yêu cầu người dùng "quản lý
    // tệp đính kèm theo id cha, draft_process vẫn thể hiện đúng lần gắn kèm"): "files" của CHÍNH
    // document dự thảo là TOÀN BỘ tệp lưu theo document_id = id dự thảo (KHÔNG còn tổng hợp qua
    // nhiều vết draft_process con — đó là thiết kế TRƯỚC ĐÓ trong cùng ngày, đã bị đảo lại). Khớp
    // đúng cách MỌI module khác dùng chung endpoint /api/v1/documents/{id}/files — 1 query duy
    // nhất, không N+1 qua danh sách vết xử lý. Xem ViewQueryService#collectProcessFiles để biết
    // cách LỌC LẠI theo từng lần xử lý cho tab "Quá trình xử lý" (dùng cột process_id riêng, không
    // ảnh hưởng câu query ở đây).
    private List<Map<String, Object>> collectFiles(UUID draftId) {
        List<Map<String, Object>> files = new ArrayList<>();
        PaginationResponse<FileDto> draftFiles = listDocumentFilesUseCase.execute(draftId);
        draftFiles.getItems().forEach(fileDto -> files.add(toFileMap(fileDto)));
        return files;
    }

    // Khop dung shape {name, size, type} cua sample Domino ("files": [{"name":...,"size":...,
    // "type":"docx"}]) - FE (Files/index.jsx, iconlistduthao.jsx) doc THANG 3 key nay, khong phai
    // shape FileDto goc (file_id/original_file_name/...).
    //
    // Bugfix (bao loi nguoi dung "thao tac vao tep dinh kem thi chua xem duoc"): FE (mofile/
    // getPDFBlob/convertFastToPdf) van goi API Domino cu (qlvb/vbden/getFile - doc theo TEN file
    // trong 1 document Domino) de xem/tai tep - tep tai qua Java (MinIO, endpoint that GET
    // /api/v1/documents/{documentId}/files/{fileId}/download) khong ton tai ben Domino nen click
    // vao khong xem duoc. Them "file_id" (FE tu quyet dinh dung API nao dua vao co field nay hay
    // khong, xem ListIconFiles) - CHI la them 1 key, khong doi 3 key cu nen khong pha shape cac noi
    // da doc truoc gio.
    private Map<String, Object> toFileMap(FileDto fileDto) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", fileDto.getOriginalFileName());
        file.put("size", fileDto.getFileSize());
        file.put("type", extractExtension(fileDto.getOriginalFileName()));
        file.put("file_id", fileDto.getFileId());
        return file;
    }

    // Cung logic UploadFileUseCaseImpl#extractExtension (infrastructure) - khong tai dung duoc truc
    // tiep (khac module), copy y het cho nhat quan.
    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
