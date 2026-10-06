package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BL-220 (VBDi Core), yêu cầu người dùng (tách văn bản đi khỏi bảng dùng chung + bổ sung đường đọc
 * riêng): xem chi tiết văn bản đi với tất cả field (tĩnh + động), tương thích API legacy
 * {@code qlvb/get?databasetype=vbdi} — cùng kiến trúc {@code GetDuThaoDetailUseCase} đã làm cho cụm
 * dự thảo sau khi tách bảng.
 *
 * <p><b>Vì sao cần endpoint riêng</b>: TRƯỚC ĐÂY "xem chi tiết" dùng lại
 * {@code GET /api/v1/documents/{id}} chung ({@code GetDynamicDocumentUseCase}, BL-101) — không còn
 * đúng vì vbdi đã chuyển sang bảng vật lý ĐỘC LẬP {@code vbdi_documents} (xem {@link VbdiDocument}),
 * use case đọc bảng {@code documents} sẽ KHÔNG BAO GIỜ tìm thấy văn bản đi nữa. Viết lại TRỰC TIẾP
 * (đọc {@link VbdiDocumentRepository}), cùng công thức field {id, databasetype, status, content,
 * created_on, last_modified_on} + {@code dynamic_fields} — giữ đúng response shape cũ, FE không cần
 * đổi cách đọc.</p>
 *
 * <p>Cùng bugfix shape với {@code GetDuThaoDetailUseCase}/{@code GetVbdenDetailUseCase}: "trích yếu"
 * (field {@code subject}) được lưu vào CỘT TĨNH {@code title} của entity (không vào
 * {@code dynamic_fields}) — {@code putIfAbsent} (KHÔNG đè) để văn bản đã từng hiệu chỉnh và có sẵn
 * {@code subject} riêng trong dynamic_fields giữ đúng giá trị đã lưu.</p>
 *
 * <p><b>Bổ sung 28/08/2026 ("đã port đính kèm sang JAVA chưa" — người dùng hỏi sau khi
 * {@code uploadVbdiFile} đã port bước tải file SAU khi "Thêm mới" thành công)</b>: thêm field
 * {@code files} vào response — TRƯỚC ĐÓ hoàn toàn thiếu, khiến file vừa tải lên qua Java không bao
 * giờ hiển thị lại khi mở "Xem chi tiết". Đúng khuôn {@code GetDuThaoDetailUseCase#collectFiles}
 * (1 query {@code ListDocumentFilesUseCase} theo {@code document.getId()}, KHÔNG N+1) — shape
 * {@code {name,size,type,file_id}} khớp {@code Files/filesVBdi.jsx} đang đọc
 * ({@code resdata?.files}). <b>CHƯA port</b> (ngoài phạm vi câu hỏi): xem/tải/đổi tên/xoá/OCR tệp
 * (component {@code filesVBdi.jsx}) vẫn gọi Domino ({@code qlvb/vbden/getFile}/{@code qlvb/dococr}/
 * {@code qlvb/renamefile}/{@code qlvb/add?id=...}) — tệp tải lên qua Java (MinIO) sẽ KHÔNG xem/tải
 * được từ các API đó (cùng gap đã ghi nhận ở {@code GetDuThaoDetailUseCase}, chưa có endpoint
 * download/rename/delete tương ứng phía FE gọi tới).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetVbdiDetailUseCase {

    public static final String DOCUMENT_TYPE = SoanThaoVbdiUseCase.DOCUMENT_TYPE;

    private static final String SUBJECT_FIELD = "subject";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final com.td.application.documentlinks.RelatedDocumentSnapshots relatedDocumentSnapshots;

    public Result<Map<String, Object>> execute(GetVbdiDetailRequest request) {
        UUID id = parseUuid(request == null ? null : request.getId());
        if (id == null) {
            return Result.failure("Thiếu id văn bản đi cần xem");
        }

        VbdiDocument document = vbdiDocumentRepository.findById(id).orElse(null);
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
        // Dich NGUOC status noi bo (chu co nghia) ve ma Domino tho ("0"/"2") - bat buoc, xem javadoc
        // lop + VbdiLegacyStatusCodes (dung CHUNG voi chieu luu SoanThaoVbdiUseCase/HieuChinhVbdiUseCase).
        allFields.put("status", VbdiLegacyStatusCodes.toLegacyCode(document.getStatus()));
        allFields.put("content", document.getContent());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        allFields.putIfAbsent(SUBJECT_FIELD, document.getTitle());
        allFields.put("files", collectFiles(document.getId()));
        // 06/10/2026: documentin_obj / vanban_lienquan_obj dung lai tu bang document_links (giai doan 2 -
        // docs/architecture/document-links.md), ghi de gia tri cu con sot trong dynamic_fields.
        relatedDocumentSnapshots.decorate(allFields, document.getId(), com.td.application.documentlinks.RelatedDocumentSnapshots.SOURCE_VBDI);

        Map<String, Object> filtered = dynamicDocumentFieldsService.filterItems(allFields, request.getItems());
        return Result.success(filtered);
    }

    // Dung khuon GetDuThaoDetailUseCase#collectFiles: TOAN BO tep luu theo document_id = id van
    // ban di (endpoint chung /api/v1/documents/{id}/files), 1 query duy nhat.
    private List<Map<String, Object>> collectFiles(UUID documentId) {
        List<Map<String, Object>> files = new ArrayList<>();
        listDocumentFilesUseCase.execute(documentId).getItems()
            .forEach(fileDto -> files.add(toFileMap(fileDto)));
        return files;
    }

    // Khop dung shape {name,size,type} Domino ("files":[{"name":...,"size":...,"type":"docx"}]) ma
    // filesVBdi.jsx dang doc (resdata?.files) - them "file_id" de FE quyet dinh dung API tai/xem
    // nao (cung tien le GetDuThaoDetailUseCase#toFileMap).
    private Map<String, Object> toFileMap(FileDto fileDto) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", fileDto.getOriginalFileName());
        file.put("size", fileDto.getFileSize());
        file.put("type", extractExtension(fileDto.getOriginalFileName()));
        file.put("file_id", fileDto.getFileId());
        // Yeu cau nguoi dung 15/09/2026 (is_primary that - xem javadoc FileMetadataEntity#isPrimary).
        file.put("is_primary", Boolean.TRUE.equals(fileDto.getIsPrimary()));
        return file;
    }

    // Cung logic UploadFileUseCaseImpl#extractExtension/GetDuThaoDetailUseCase#extractExtension -
    // copy y het cho nhat quan (khac module, khong tai dung truc tiep duoc).
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