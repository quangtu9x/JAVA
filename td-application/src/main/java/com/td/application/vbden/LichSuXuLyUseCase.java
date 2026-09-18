package com.td.application.vbden;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.vbden.VbdenResDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * N09 (01/09/2026 — "Lịch sử xử lý" cá nhân, xem {@code lichsuxuly/components/ItemsList.jsx}): tương
 * đương {@code LichSuXuLy.java} thật + view Domino {@code QTXL_by_username} (database
 * {@code vbden_res}) — danh sách MỌI bản ghi quá trình xử lý ({@code document_type="vbden_res"}) mà
 * NGƯỜI DÙNG ĐANG ĐĂNG NHẬP là người thực hiện, xuyên suốt MỌI văn bản đến (không giới hạn theo
 * {@code parentid} như {@code QTXL_by_parentid}/{@code QuaTrinhXuLy} — BL-210 DoD 4).
 *
 * <p><b>Bugfix tự phát hiện trước khi hoàn tất (01/09/2026)</b>: Domino thật khoá theo field
 * {@code user_creator}, nhưng grep lại TOÀN BỘ use case Java đang tạo bản ghi {@code vbden_res}
 * ({@link ChuyenXuLyVbdenUseCase}, {@link ChoYKienXinYKienUseCase}) xác nhận KHÔNG use case nào ghi
 * field {@code user_creator} cả — tất cả dùng tên field {@code nguoi_thuc_hien} (đổi tên có chủ đích
 * từ trước, không phải thiếu sót). Nếu query theo {@code user_creator} như Domino thật, tính năng này
 * sẽ LUÔN trả về RỖNG trên dữ liệu thật — đúng tình huống DT-002 đã cảnh báo (code đúng cú pháp,
 * nhưng field chưa từng được ghi). Đã sửa query theo ĐÚNG field Java thật sự ghi ({@code nguoi_thuc_hien}),
 * không theo tên Domino gốc.</p>
 *
 * <p><b>Khác 26 view {@code ViewQueryService} khác về cách xác định user</b>: các view khác đều
 * "trust client" (server dùng thẳng {@code request.getKeys()} FE gửi, không tự resolve) — RIÊNG view
 * này Domino thật lấy username từ TOKEN đăng nhập ({@code connection.user_token.username}), KHÔNG
 * nhận tham số username từ FE (đối chiếu {@code LichSuXuLy.java}: FE {@code fetchData()} không hề gửi
 * field username/keys nào) — port ĐÚNG bằng {@link CurrentUserProvider}, không suy đoán thêm tham số
 * FE chưa từng gửi.</p>
 *
 * <p><b>Ghép 2 document (merge, KHÔNG có ở bất kỳ view nào khác đã port)</b>: mỗi bản ghi quá trình
 * xử lý ({@code vbden_res}) chỉ chứa field hành động (action/opinion/creator/...) — Domino JOIN thêm
 * văn bản CHA ({@code parentid}, {@code document_type="vbden"}) để lấy field hiển thị
 * (code/subject/unit_in_charge/status/...) rồi GHÉP PHẲNG cả 2 vào 1 object (đối chiếu
 * {@code DocumentToObjectNode} (cha, whitelist {@link #PARENT_FIELD_WHITELIST} đúng
 * {@code filterItems} thật) rồi {@code DocumentToObjectNodeRes} (con, TOÀN BỘ field — {@code getall})
 * ghi ĐÈ lên trên). Field {@code id} lấy từ CHA (Domino: {@code DocumentToObjectNode} set {@code id}
 * = UNID văn bản cha TRƯỚC, hàm ghép con sau không set lại {@code id}) — khớp cách FE
 * (`ItemsList.jsx`) dùng {@code record.id} cho "chi tiết"/hành động, không phải id bản ghi xử lý.</p>
 *
 * <p><b>Bỏ qua bản ghi không JOIN được văn bản cha</b> (parentid rỗng/không hợp lệ/văn bản cha đã bị
 * xoá/không đúng {@code document_type="vbden"}) — Domino thật khi {@code docVBden==null} vẫn cố ghép
 * (silently produces 1 object thiếu field cha, do try/catch nuốt lỗi) nhưng đây là hành vi lỗi ngầm
 * không có giá trị hiển thị thật (thiếu code/subject thì dòng đó vô nghĩa với người dùng) — Java LỌC
 * BỎ hẳn thay vì trả 1 dòng cụt, an toàn hơn không đổi ý nghĩa nghiệp vụ. Hệ quả kỹ thuật đã biết:
 * {@code total}/{@code totalPages} phản ánh số bản ghi xử lý THÔ (trước lọc), có thể HƠI lệch với số
 * dòng thực trả về nếu có bản ghi mồ côi — chấp nhận được vì trường hợp này hiếm (dữ liệu thật luôn
 * có parentid hợp lệ), không đáng để đổi sang đếm lại sau lọc (tốn thêm 1 vòng query/trang).</p>
 *
 * <p><b>GIỚI HẠN ĐÃ BIẾT</b> (kỹ thuật, không phải nghiệp vụ — cùng tinh thần D.10): sắp xếp cố định
 * theo {@code created_on ASC} (cũ→mới, do dùng lại {@link DocumentRepository#findByFieldValue}, vốn
 * ORDER BY cố định cho toàn bộ view engine) — Domino thật categorize theo username rồi sắp trong mỗi
 * category, hướng sắp xếp thật của view {@code QTXL_by_username} chưa xác nhận qua DXL, không suy
 * đoán đảo ngược khi chưa có bằng chứng.</p>
 *
 * <p><b>CHƯA port</b>: {@code fetchData_Unit}/{@code QuaTrinhXuLyDonVi} (drill-down đơn vị, view/API
 * hoàn toàn khác — xem checklist mục Phần 2) và tìm kiếm nâng cao (`keyword`/`search` — FE hiện KHÔNG
 * gửi các field này cho màn "Lịch sử xử lý", không thêm khi chưa có bằng chứng cần).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LichSuXuLyUseCase {

    static final String PARENT_DOCUMENT_TYPE = "vbden";
    // Domino goc dung "user_creator" nhung Java (ChuyenXuLyVbdenUseCase/ChoYKienXinYKienUseCase) LUON
    // ghi "nguoi_thuc_hien" cho field nay - phai query dung ten Java thuc su ghi (xem javadoc lop).
    private static final String NGUOI_THUC_HIEN_FIELD = "nguoi_thuc_hien";
    private static final String PARENT_ID_FIELD = "parentid";
    private static final DateTimeFormatter CREATED_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Dung dung "filterItems" thuc cua LichSuXuLy.java (KHONG suy doan them field khac).
    private static final Set<String> PARENT_FIELD_WHITELIST = Set.of(
        "promulgation_date", "received_date", "deadline_date", "unit_in_charge", "document_type",
        "code", "subject", "status", "user_delivered", "user_process", "user_processed", "priority",
        "noinhanchutri", "lanhdaochutri");

    private final DocumentRepository documentRepository;
    private final VbdenResDocumentRepository vbdenResDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;
    private final CurrentUserProvider currentUserProvider;

    public PaginationResponse<Map<String, Object>> execute(LichSuXuLyRequest request) {
        Pageable pageable = buildPageable(request);
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }

        Page<VbdenResDocument> page = vbdenResDocumentRepository.findByFieldValue(
            NGUOI_THUC_HIEN_FIELD, username, pageable);

        List<Map<String, Object>> content = new ArrayList<>();
        for (VbdenResDocument processRecord : page.getContent()) {
            toMergedRow(processRecord).ifPresent(content::add);
        }
        return toPaginationResponse(content, page);
    }

    private java.util.Optional<Map<String, Object>> toMergedRow(VbdenResDocument processRecord) {
        Map<String, Object> resFields = dynamicDocumentFieldsService.parseDynamicFields(processRecord.getDynamicFieldsJson());
        String parentIdText = asString(resFields.get(PARENT_ID_FIELD));
        UUID parentId = parseUuid(parentIdText);
        if (parentId == null) {
            return java.util.Optional.empty();
        }

        var parentOptional = documentRepository.findById(parentId);
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()
                || !PARENT_DOCUMENT_TYPE.equalsIgnoreCase(parentOptional.get().getDocumentType())) {
            return java.util.Optional.empty();
        }
        var parent = parentOptional.get();

        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> parentFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        parentFields.forEach((key, value) -> {
            if (PARENT_FIELD_WHITELIST.contains(key)) {
                merged.put(key, value);
            }
        });
        merged.put("id", parent.getId().toString());
        merged.put("files", collectFiles(parent.getId()));

        // Con (getall) ghi DE len cha - dung tien le Domino DocumentToObjectNodeRes goi SAU.
        merged.putAll(resFields);
        merged.put("created", processRecord.getCreatedOn() == null ? null : processRecord.getCreatedOn().format(CREATED_FORMATTER));
        merged.put("res_files", collectFiles(processRecord.getId()));

        return java.util.Optional.of(merged);
    }

    // Dung khuon GetVbdenDetailUseCase#collectFiles.
    private List<Map<String, Object>> collectFiles(UUID documentId) {
        List<Map<String, Object>> files = new ArrayList<>();
        for (FileDto fileDto : listDocumentFilesUseCase.execute(documentId).getItems()) {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("name", fileDto.getOriginalFileName());
            file.put("size", fileDto.getFileSize());
            file.put("type", extractExtension(fileDto.getOriginalFileName()));
            file.put("file_id", fileDto.getFileId());
            files.add(file);
        }
        return files;
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 && dotIndex < fileName.length() - 1 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Pageable buildPageable(LichSuXuLyRequest request) {
        int pageNumber = Math.max(1, request.getPageNumber()) - 1;
        int pageSize = Math.min(Math.max(1, request.getPageSize()), 200);
        return PageRequest.of(pageNumber, pageSize);
    }

    private <T> PaginationResponse<T> toPaginationResponse(List<T> content, Page<?> page) {
        return new PaginationResponse<>(
            content, page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages(),
            page.isFirst(), page.isLast());
    }

    private <T> PaginationResponse<T> emptyPage(Pageable pageable) {
        return new PaginationResponse<>(List.of(), pageable.getPageNumber() + 1, pageable.getPageSize(), 0L, 0, true, true);
    }
}
