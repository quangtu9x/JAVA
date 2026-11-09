package com.td.application.vanbanphapquy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListFilesForDocumentsUseCase;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.OrganizationAgencyResolver;
import com.td.application.sharedcore.OrganizationRepository;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.vanbanphapquy.VbpqDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 30/10/2026 (port module "Văn bản pháp quy" - Domino qlvb/view/query|search|get|add|delete +
 * qlvb/chiasetep/xoatailieu ?databasetype=vanbanphapquy). Đọc: mọi người đã đăng nhập. Ghi: giống FE
 * getQuyenThaoTac_SoSanhMaDinhDanhCoQuan - có vai trò quản trị/cập nhật VB đến/đi VÀ văn bản thuộc cơ quan
 * của chính người dùng (cơ quan resolve phía server, không tin FE). Xoá = xoá mềm.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VanBanPhapQuyUseCase {

    private static final Set<String> EDITOR_ROLES = Set.of("CVQUANTRI", "CVQUANTRIDV", "QCAPNHATVBDEN",
        "QCAPNHATVBDENDV", "QCAPNHATVBDI", "QCAPNHATVBDIDV");
    private static final Set<String> RESERVED = Set.of("id", "form", "user_creator", "files", "databasetype",
        "coquan_id", "coquan_identifier", "date_fields", "number_fields", "richtext_fields");
    private static final List<String> SEARCH_FIELDS = List.of("sokyhieu", "coquanbanhanh", "nguoiky", "trichyeu",
        "ghichu", "chucvu");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbpqDocumentRepository repository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ListFilesForDocumentsUseCase listFilesForDocumentsUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final OrganizationAgencyResolver organizationAgencyResolver;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public Result<PaginationResponse<Map<String, Object>>> getView(VanBanPhapQuyRequests.GetView request) {
        int pageNumber = Math.max(1, request.getPageNumber());
        int pageSize = Math.min(Math.max(1, request.getPageSize()), 1000);
        List<String> keys = request.getKeys() == null ? List.of() : request.getKeys();
        String coquan = keys.isEmpty() ? null : blankToNull(keys.get(0));
        String menuKey = keys.size() < 2 ? null : blankToNull(keys.get(1));
        if ("all".equalsIgnoreCase(menuKey)) {
            menuKey = null;
        }
        String viewname = request.getViewname() == null ? "" : request.getViewname();
        String year = null;
        Map<String, String> equalsFields = new LinkedHashMap<>();
        if (menuKey != null) {
            switch (viewname) {
                case "vwVanBan_By_Nam" -> year = menuKey;
                case "vwVanBan_By_LinhVuc" -> equalsFields.put("linhvuc", menuKey);
                case "vwVanBan_By_LoaiVanBan" -> equalsFields.put("loaivanban", menuKey);
                default -> { }
            }
        }
        Map<String, String> containsFields = new LinkedHashMap<>();
        String keyword = request.getKeyword();
        if (request.getSearch() != null) {
            for (String field : SEARCH_FIELDS) {
                putText(containsFields, field, request.getSearch().get(field));
            }
            putText(equalsFields, "loaivanban", request.getSearch().get("loaivanban"));
            putText(equalsFields, "linhvuc", request.getSearch().get("linhvuc"));
            if ((keyword == null || keyword.isBlank()) && request.getSearch().get("keyword") instanceof String text) {
                keyword = text;
            }
        }
        Page<VbpqDocument> page = repository.search(coquan, year, equalsFields, containsFields, keyword,
            PageRequest.of(pageNumber - 1, pageSize));
        List<UUID> ids = page.getContent().stream().map(VbpqDocument::getId).toList();
        Map<UUID, List<FileDto>> files = ids.isEmpty() ? Map.of() : listFilesForDocumentsUseCase.execute(ids);
        List<Map<String, Object>> items = page.getContent().stream()
            .map(document -> toMap(document, files.getOrDefault(document.getId(), List.of()))).toList();
        return Result.success(new PaginationResponse<>(items, pageNumber, pageSize, page.getTotalElements(),
            page.getTotalPages(), page.isFirst(), page.isLast()));
    }

    @Transactional(readOnly = true)
    public Result<Map<String, Object>> detail(VanBanPhapQuyRequests.Id request) {
        var document = request == null || request.getId() == null ? null
            : repository.findById(request.getId()).filter(d -> !d.isDeleted()).orElse(null);
        if (document == null) {
            return Result.failure("Không tìm thấy văn bản");
        }
        Map<UUID, List<FileDto>> files = listFilesForDocumentsUseCase.execute(List.of(document.getId()));
        return Result.success(toMap(document, files.getOrDefault(document.getId(), List.of())));
    }

    public Result<UUID> save(VanBanPhapQuyRequests.Save request) {
        if (!hasEditorRole()) {
            return Result.failure("Bạn không có quyền cập nhật văn bản pháp quy");
        }
        Map<String, Object> incoming = parseIncoming(request.getFields());
        UUID agencyId = organizationAgencyResolver.resolveCurrentUserAgencyId().orElse(null);
        if (request.getId() != null) {
            var document = repository.findById(request.getId()).filter(d -> !d.isDeleted()).orElse(null);
            if (document == null) {
                return Result.failure("Không tìm thấy văn bản");
            }
            if (!canEdit(document, agencyId)) {
                return Result.failure("Văn bản không thuộc cơ quan của bạn");
            }
            Map<String, Object> fields = new LinkedHashMap<>(
                dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
            fields.putAll(incoming);
            document.replaceDynamicFields(toJson(fields));
            document.rename(titleOf(fields, document.getTitle()));
            return Result.success(repository.save(document).getId());
        }
        String username = currentUserProvider.currentUsername().orElse("");
        String identifier = agencyId == null ? text(request.getFields() == null ? null
            : request.getFields().get("coquan_identifier"))
            : organizationRepository.findByIdAndDeletedOnIsNull(agencyId).map(o -> o.getIdentifier()).orElse("");
        incoming.put("user_creator", username);
        incoming.put("coquan_identifier", identifier);
        if (agencyId != null) {
            incoming.put("coquan_id", agencyId.toString());
        }
        var document = new VbpqDocument(titleOf(incoming, ""), agencyId, identifier, username);
        document.replaceDynamicFields(toJson(incoming));
        return Result.success(repository.save(document).getId());
    }

    public Result<List<UUID>> delete(List<UUID> ids) {
        if (!hasEditorRole()) {
            return Result.failure("Bạn không có quyền xoá văn bản pháp quy");
        }
        UUID agencyId = organizationAgencyResolver.resolveCurrentUserAgencyId().orElse(null);
        UUID deletedBy = currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull).map(AppUser::getId).orElse(null);
        List<UUID> deleted = new ArrayList<>();
        for (UUID id : ids == null ? List.<UUID>of() : ids) {
            var document = repository.findById(id).orElse(null);
            if (document == null) {
                continue;
            }
            if (!document.isDeleted()) {
                if (!canEdit(document, agencyId)) {
                    return Result.failure("Văn bản không thuộc cơ quan của bạn");
                }
                document.markAsDeleted(deletedBy);
                repository.save(document);
            }
            deleted.add(id);
        }
        return deleted.isEmpty() ? Result.failure("Không tìm thấy văn bản để xoá") : Result.success(deleted);
    }

    private boolean hasEditorRole() {
        Set<String> roles = currentUserProvider.currentRoles();
        return roles.contains("ADMIN") || roles.stream().anyMatch(EDITOR_ROLES::contains);
    }

    private boolean canEdit(VbpqDocument document, UUID agencyId) {
        if (currentUserProvider.currentRoles().contains("ADMIN")) {
            return true;
        }
        if (document.getCoquanId() != null) {
            return document.getCoquanId().equals(agencyId);
        }
        String username = currentUserProvider.currentUsername().orElse("");
        return document.getUserCreator() != null && document.getUserCreator().equalsIgnoreCase(username);
    }

    private Map<String, Object> toMap(VbpqDocument document, List<FileDto> files) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", document.getId());
        map.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        map.put("id", document.getId());
        map.put("form", "vanbanphapquy");
        map.put("identifier", document.getCoquanIdentifier());
        map.put("coquan_identifier", document.getCoquanIdentifier());
        map.put("user_creator", document.getUserCreator());
        map.put("databasetype", "vanbanphapquy");
        map.put("created_on", document.getCreatedOn());
        map.put("files", toFileMaps(files));
        return map;
    }

    // Khuôn {name,size,type,file_id} như ViewQueryService#toFileMap (Files/index.jsx, iconlist đọc).
    private static List<Map<String, Object>> toFileMaps(List<FileDto> files) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (FileDto dto : files == null ? List.<FileDto>of() : files) {
            String name = dto.getOriginalFileName() != null ? dto.getOriginalFileName() : dto.getFileName();
            name = name == null ? "" : name;
            int dot = name.lastIndexOf('.');
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("name", name);
            file.put("size", dto.getFileSize());
            file.put("type", dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase() : "");
            file.put("file_id", dto.getFileId());
            result.add(file);
        }
        return result;
    }

    private Map<String, Object> parseIncoming(Map<String, String> raw) {
        Map<String, String> clean = new LinkedHashMap<>();
        if (raw != null) {
            raw.forEach((key, value) -> {
                if (key != null && !RESERVED.contains(key.trim().toLowerCase())) {
                    clean.put(key, value == null ? "" : value);
                }
            });
        }
        return new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(
            dynamicDocumentFieldsService.buildDynamicFieldsJson(clean, null, null, null)));
    }

    private static String titleOf(Map<String, Object> fields, String fallback) {
        String title = text(fields.get("trichyeu"));
        if (title.isEmpty()) {
            title = text(fields.get("sokyhieu"));
        }
        return AddDynamicDocumentRequest.labelTitle(title.isEmpty() ? (fallback == null ? "" : fallback) : title);
    }

    private static void putText(Map<String, String> target, String key, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            target.put(key, text.trim());
        } else if (value instanceof Map<?, ?> map && map.get("value") instanceof String text && !text.isBlank()) {
            target.put(key, text.trim());
        }
    }

    private static String toJson(Map<String, Object> fields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(fields);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? null : value.trim();
    }
}
