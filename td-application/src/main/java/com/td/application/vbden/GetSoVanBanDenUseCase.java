package com.td.application.vbden;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListFilesForDocumentsUseCase;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * BL-210/211 (VBDen — Sổ văn bản đến) — Slice 9: tương thích {@code qlvb/vbden/GetSoVanBanDen}
 * (Domino thật) — màn "Vào sổ văn bản đến" (route {@code vbden/vaosovanban}). Đọc trực tiếp từ
 * {@code docs/views_domino/view_vanbanden_so.dxl}.
 *
 * <p><b>Kiến trúc 2-document (đúng Domino, sau đối chiếu lại — xem javadoc
 * {@link TiepNhanVbdenUseCase})</b>: query trên {@code documentType="vbden_so"} (bản ghi "sổ" riêng,
 * tương đương database {@code vbden_so.nsf}) — KHÔNG cần {@code requireNonEmptyFieldName} vì MỌI
 * document {@code vbden_so} theo định nghĩa đã "vào sổ". Field lọc/sort ({@code identifier_creator},
 * {@code document_number}, {@code received_date}) đọc từ CHÍNH document {@code vbden_so}. Các field
 * hiển thị khác (subject, code, status, unit_in_charge, deadline_date, promulgation_date, priority...)
 * KHÔNG lưu trên {@code vbden_so} — đọc LIVE từ văn bản gốc qua {@code parentid} tại
 * {@link #joinWithParentDocument} (tương đương {@code dbVBden.getDocumentByUNID(parentid)} trong
 * {@code GetSoVanBanDen.java} thật) — tránh nhân bản toàn bộ metadata (nguy cơ lệch dữ liệu nếu văn
 * bản gốc bị sửa sau khi vào sổ).</p>
 *
 * <p><b>6 viewname hỗ trợ</b> (dxl view_vanbanden_so.dxl):
 * <ul>
 *   <li>{@code sovanban} (dòng 12-36): khoá {@code identifier_creator}, sort {@code document_number}
 *   giảm dần (số nguyên).</li>
 *   <li>{@code sovanban_sx_ngayden} (dòng 297-325): giống trên nhưng sort chính {@code received_date}
 *   (snapshot trên chính {@code vbden_so}, xem {@code TiepNhanVbdenUseCase}) giảm dần, sort phụ
 *   {@code document_number} giảm dần.</li>
 *   <li>{@code homqua}/{@code trongngay}/{@code trongthang}/{@code trongtuan} (dòng 119-217): lọc
 *   theo {@code @Date(@Created)} — với kiến trúc 2-document, đây CHÍNH XÁC là thời điểm bản ghi
 *   {@code vbden_so} được TẠO (= lúc vào sổ), không phải ngày tạo văn bản gốc — khớp đúng ngữ nghĩa
 *   Domino (view sống trong {@code vbden_so.nsf}). Domino tự SINH LẠI formula mỗi ngày với ngày
 *   hard-code; Java tính động bằng {@code LocalDate.now()} tại thời điểm query.</li>
 * </ul></p>
 *
 * <p><b>Cải tiến có chủ đích so với Domino</b>: Domino gốc ({@code GetSoVanBanDen.java} dòng 82-85)
 * chỉ đổi sang lọc theo {@code iddanhmucso} khi {@code viewname=="sovanban"} — nếu người dùng vừa
 * chọn 1 sổ cụ thể VỪA đổi sắp xếp theo ngày đến (viewname đổi thành {@code "sovanban_sx_ngayden"}),
 * bản Domino gốc SẼ BỎ QUA lọc theo sổ (không kiểm tra lại — nhiều khả năng là thiếu sót gốc, không
 * phải chủ đích). Bản Java áp dụng {@code iddanhmucso} bất kể viewname nào trong 6 view trên — nhất
 * quán hơn, không phải lỗi hồi quy vì không có UI nào trước đây dựa vào hành vi bỏ-qua này.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GetSoVanBanDenUseCase {

    private static final String REGISTER_ENTRY_DOCUMENT_TYPE = TiepNhanVbdenUseCase.REGISTER_ENTRY_DOCUMENT_TYPE;
    private static final String PARENT_DOCUMENT_TYPE = TiepNhanVbdenUseCase.DOCUMENT_TYPE;
    private static final String DOCUMENT_REGISTER_ID_FIELD = "document_register_id";
    private static final String DOCUMENT_NUMBER_FIELD = "document_number";
    private static final String RECEIVED_DATE_FIELD = "received_date";
    private static final String PARENT_ID_FIELD = "parentid";
    private static final String FILES_FIELD = "files";
    private static final List<String> ORG_FIELDS = List.of("identifier_creator");

    private static final String VIEW_SOVANBAN = "sovanban";
    private static final String VIEW_SOVANBAN_SX_NGAYDEN = "sovanban_sx_ngayden";
    private static final String VIEW_HOMQUA = "homqua";
    private static final String VIEW_TRONGNGAY = "trongngay";
    private static final String VIEW_TRONGTHANG = "trongthang";
    private static final String VIEW_TRONGTUAN = "trongtuan";

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final ListFilesForDocumentsUseCase listFilesForDocumentsUseCase;

    public PaginationResponse<Map<String, Object>> execute(GetSoVanBanDenRequest request) {
        String identifier = resolveCurrentIdentifier();
        Pageable pageable = buildPageable(request);
        if (identifier == null) {
            return emptyPage(pageable);
        }

        String viewname = normalize(request.getViewname());
        VbdenOrgViewFilter filter = buildFilter(viewname, request);
        filter.setOrgValue(identifier);

        Page<BusinessDocument> page = documentRepository.findByVbdenOrgView(REGISTER_ENTRY_DOCUMENT_TYPE, filter, pageable);
        // Bugfix 31/08/2026 ("o danh sach chua thay hien thi tep dinh kem"): cot "File" (ListIconFiles)
        // doc "record.files" - truoc day KHONG duoc dua vao day. Chi truy van khi FE thuc su can den
        // (getall hoac co liet ke "files" trong items) - GOM 1 lan cho CA TRANG (khong phai tung dong)
        // de tranh N+1, xem javadoc ListFilesForDocumentsUseCase.
        Map<UUID, List<Map<String, Object>>> filesByParentId = wantsField(request.getItems(), FILES_FIELD)
            ? collectFilesByParentId(page.getContent())
            : Map.of();
        List<Map<String, Object>> content = page.getContent().stream()
            .map(entry -> joinWithParentDocument(entry, request.getItems(), filesByParentId))
            .toList();
        return toPaginationResponse(content, page);
    }

    private boolean wantsField(String items, String field) {
        if (items == null || items.isBlank() || "getall".equalsIgnoreCase(items.trim())) {
            return true;
        }
        for (String part : items.split(";")) {
            if (field.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    // File dinh kem thuoc ve VAN BAN GOC (parentid tren ban ghi "so"), khong phai id ban ghi "so" -
    // cung luu y da ghi o GetVbdenDetailUseCase. Gom TAT CA parentId cua ca trang thanh 1 danh sach
    // roi goi 1 lan duy nhat (khong phai tung dong).
    private Map<UUID, List<Map<String, Object>>> collectFilesByParentId(List<BusinessDocument> entries) {
        List<UUID> parentIds = entries.stream()
            .map(this::resolveParentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<FileDto>> filesByDocId = listFilesForDocumentsUseCase.execute(parentIds);
        Map<UUID, List<Map<String, Object>>> result = new LinkedHashMap<>();
        filesByDocId.forEach((docId, fileDtos) ->
            result.put(docId, fileDtos.stream().map(this::toFileMap).toList()));
        return result;
    }

    private UUID resolveParentId(BusinessDocument entry) {
        Map<String, Object> entryFields = dynamicDocumentFieldsService.parseDynamicFields(entry.getDynamicFieldsJson());
        return parseUuid(asString(entryFields.get(PARENT_ID_FIELD)));
    }

    // Khop dung shape {name,size,type,file_id} ma Files/filesVBden.jsx (qua ListIconFiles) dang doc
    // (record.files) - cung tien le GetVbdenDetailUseCase#toFileMap.
    private Map<String, Object> toFileMap(FileDto fileDto) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", fileDto.getOriginalFileName());
        file.put("size", fileDto.getFileSize());
        file.put("type", extractExtension(fileDto.getOriginalFileName()));
        file.put("file_id", fileDto.getFileId());
        return file;
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private VbdenOrgViewFilter buildFilter(String viewname, GetSoVanBanDenRequest request) {
        VbdenOrgViewFilter.VbdenOrgViewFilterBuilder builder = VbdenOrgViewFilter.builder()
            .orgFieldNames(ORG_FIELDS)
            .keyword(request.getKeyword())
            .keywordColumns(request.getColumns())
            // Bo sung 08/09/2026 (nguoi dung yeu cau "lay lai tan goc" cho van ban da vao so): Domino
            // that (view "sovanban" that, dxl "SELECT form=\"DocumentIn\"") loai tru ban ghi da thu
            // hoi/xoa GIAN TIEP qua doi "Form" sang co hau to "Deleted" (xem view sovanban_bithuhoi
            // "isrevoke=\"1\"" - view RIENG chi de tra cuu lich su, khong phai view chinh) - Java
            // KHONG co field "Form" mutable tuong duong nen phai loai truc tiep qua "reply" (VbdiRevoke
            // TaskHandler/ActionRevokeDocument dat "1" khi lay lai) - neu khong, danh dau xong van
            // hien thi nguyen trong danh sach "So van ban den", vo nghia.
            .excludeFieldValues(Map.of("reply", List.of("1")));

        LocalDate today = LocalDate.now();
        switch (viewname) {
            case VIEW_SOVANBAN -> applySort(builder, DOCUMENT_NUMBER_FIELD, "integer", request.getSort());
            case VIEW_SOVANBAN_SX_NGAYDEN -> {
                builder.primarySortField(RECEIVED_DATE_FIELD).primarySortType("date")
                    .secondarySortField(DOCUMENT_NUMBER_FIELD).secondarySortType("integer");
                applySortDirection(builder, request.getSort());
            }
            case VIEW_HOMQUA -> {
                LocalDate yesterday = today.minusDays(1);
                builder.minCreatedOn(yesterday).maxCreatedOn(yesterday);
            }
            case VIEW_TRONGNGAY -> builder.minCreatedOn(today).maxCreatedOn(today);
            case VIEW_TRONGTHANG -> builder.minCreatedOn(today.withDayOfMonth(1))
                .maxCreatedOn(today.withDayOfMonth(today.lengthOfMonth()));
            case VIEW_TRONGTUAN -> builder.minCreatedOn(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                .maxCreatedOn(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            default -> throw new IllegalArgumentException("Khong ho tro viewname: " + request.getViewname());
        }

        // Loc theo 1 so cu the (iddanhmucso) - ap dung cho CA 6 viewname, xem javadoc lop ve khac
        // biet co chu dich so voi Domino goc (chi ap dung khi viewname=="sovanban").
        if (request.getIddanhmucso() != null && !request.getIddanhmucso().isBlank()) {
            builder.includeFieldValues(Map.of(DOCUMENT_REGISTER_ID_FIELD, List.of(request.getIddanhmucso().trim())));
        }
        return builder.build();
    }

    // dxl 12-36 (sovanban): sort chinh document_number, dao chieu theo request.sort.
    private void applySort(VbdenOrgViewFilter.VbdenOrgViewFilterBuilder builder, String field, String type, String sort) {
        builder.primarySortField(field).primarySortType(type);
        applySortDirection(builder, sort);
    }

    private void applySortDirection(VbdenOrgViewFilter.VbdenOrgViewFilterBuilder builder, String sort) {
        builder.sortDescending(sort == null || !sort.endsWith("_up"));
    }

    // Bugfix 21/08/2026: identifier_creator duoc gan luc "tiep nhan" theo DocumentRegister chon
    // bang getIdentifier_by_level(currentUser) (FE) - PHAI resolve server-side theo DUNG thuat toan
    // do (ResolveIdentifierByLevelUseCase), KHONG dung AppUser.getIdentifier() (field phang, khac
    // gia tri thuc te da luu tren du lieu voi user co cay to chuc nhieu cap).
    private String resolveCurrentIdentifier() {
        return resolveIdentifierByLevelUseCase.resolveForCurrentUser().orElse(null);
    }

    private String normalize(String viewname) {
        return viewname == null ? VIEW_SOVANBAN : viewname.trim().toLowerCase();
    }

    // Tuong duong GetSoVanBanDen.java thuc: doc field cua ban ghi "so" (id, document_number,
    // document_register*, identifier_creator) + doc LIVE field hien thi con lai tu van ban goc qua
    // parentid. Neu van ban goc khong con (bi xoa cung) - tra ve field cua rieng ban ghi so, khong
    // throw (giu trai nghiem danh sach khong vo vi 1 dong loi).
    private Map<String, Object> joinWithParentDocument(
            BusinessDocument entry, String items, Map<UUID, List<Map<String, Object>>> filesByParentId) {
        Map<String, Object> entryFields = dynamicDocumentFieldsService.parseDynamicFields(entry.getDynamicFieldsJson());

        Map<String, Object> merged = new LinkedHashMap<>();
        String parentIdValue = asString(entryFields.get(PARENT_ID_FIELD));
        UUID parentId = parseUuid(parentIdValue);
        BusinessDocument parent = parentId == null ? null : documentRepository.findById(parentId).orElse(null);
        if (parent != null) {
            merged.put("title", parent.getTitle());
            merged.put("status", parent.getStatus());
            merged.put("created_on", parent.getCreatedOn());
            merged.put("last_modified_on", parent.getLastModifiedOn());
            merged.putAll(dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));
            // Bugfix 22/08/2026 (cung dang GetVbdenDetailUseCase - "chua thay hien thi trich yeu"):
            // van ban tao qua TiepNhanVbdenUseCase chi co "title" (cot entity), khong co "subject"
            // trong dynamic_fields. putIfAbsent - KHONG de len "subject" da duoc hieu chinh rieng.
            merged.putIfAbsent("subject", parent.getTitle());
            if (wantsField(items, FILES_FIELD)) {
                merged.put(FILES_FIELD, filesByParentId.getOrDefault(parentId, List.of()));
            }
        } else {
            log.debug("Ban ghi so {} co parentid={} nhung khong tim thay van ban goc (co the da bi xoa)",
                entry.getId(), parentIdValue);
        }
        // Field cua CHINH ban ghi so ("id" o day la id ban ghi so, khong phai van ban goc - dung
        // ngu nghia GetSoVanBanDen.java thuc: "id (UNID document sổ)") de duoc uu tien/ghi de.
        merged.putAll(entryFields);
        merged.put("id", entry.getId());
        // Bugfix 24/08/2026 (dong bo GetVbdenDetailUseCase - cung nguyen nhan): doi key sang
        // "databasetype", tranh de len field nghiep vu "document_type" ("Loai van ban") thuc.
        merged.put("databasetype", PARENT_DOCUMENT_TYPE);

        return dynamicDocumentFieldsService.filterItems(merged, items);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private <T> PaginationResponse<T> toPaginationResponse(List<T> content, Page<?> page) {
        return new PaginationResponse<>(
            content, page.getNumber() + 1, page.getSize(), page.getTotalElements(),
            page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private <T> PaginationResponse<T> emptyPage(Pageable pageable) {
        return new PaginationResponse<>(List.of(), pageable.getPageNumber() + 1, pageable.getPageSize(), 0L, 0, true, true);
    }

    // Cung quy uoc voi ViewQueryService.buildPageable.
    private Pageable buildPageable(GetSoVanBanDenRequest request) {
        int pageNumber = Math.max(1, request.getPageNumber()) - 1;
        int pageSize = Math.min(Math.max(1, request.getPageSize()), 200);
        return PageRequest.of(pageNumber, pageSize);
    }
}
