package com.td.application.chiasetep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.chiasetep.ChiaSeTepDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 30/10/2026 (port chiasetep): quyền theo dữ liệu (người tạo / người chỉnh sửa / người xem - Domino so khớp
 * user_creator, nguoichinhsuahide, nguoixemhide) + dựng Map trả FE đúng khuôn field Domino.
 */
@Component
@RequiredArgsConstructor
public class ChiaSeTepSupport {

    static final String EDITORS = "nguoichinhsuahide";
    static final String VIEWERS = "nguoixemhide";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> NODE_DATA_FIELDS = List.of(
        "cap", "creator", "user_creator", "nguoichinhsua", "nguoichinhsuahide", "nguoichinhsua_obj",
        "nguoixem", "nguoixemhide", "nguoixem_obj");

    private final ChiaSeTepDocumentRepository repository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public String username() {
        return currentUserProvider.currentUsername().map(String::trim).orElse("");
    }

    public Optional<AppUser> user(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return appUserRepository.findByUsernameAndDeletedOnIsNull(username);
    }

    public UUID currentUserId() {
        return user(username()).map(AppUser::getId).orElse(null);
    }

    public Map<String, Object> fields(ChiaSeTepDocument document) {
        return new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
    }

    public boolean isOwner(ChiaSeTepDocument document, String username) {
        return document.getUserCreator() != null && !username.isBlank()
            && document.getUserCreator().trim().equalsIgnoreCase(username);
    }

    public boolean canEditFolder(ChiaSeTepDocument folder, String username) {
        return folder != null && !folder.isDeleted() && folder.isFolder()
            && (isOwner(folder, username) || members(fields(folder).get(EDITORS)).contains(username.toLowerCase()));
    }

    public boolean canReadFolder(ChiaSeTepDocument folder, String username) {
        return canEditFolder(folder, username)
            || (folder != null && !folder.isDeleted() && folder.isFolder()
                && members(fields(folder).get(VIEWERS)).contains(username.toLowerCase()));
    }

    public Optional<ChiaSeTepDocument> folder(String id) {
        UUID uuid = parseUuid(id);
        if (uuid == null) {
            return Optional.empty();
        }
        return repository.findById(uuid).filter(d -> !d.isDeleted() && d.isFolder());
    }

    /** Tài liệu: người tạo, hoặc có quyền trên thư mục chứa. */
    public boolean canReadDocument(ChiaSeTepDocument document, String username) {
        if (document.isFolder()) {
            return canReadFolder(document, username);
        }
        return isOwner(document, username) || canReadFolder(folder(document.getParentId()).orElse(null), username);
    }

    public boolean canEditDocument(ChiaSeTepDocument document, String username) {
        if (document.isFolder()) {
            return canEditFolder(document, username);
        }
        return isOwner(document, username) || canEditFolder(folder(document.getParentId()).orElse(null), username);
    }

    /** Danh sách username (hạ thường) từ chuỗi "u1;u2". */
    public static List<String> members(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String part : String.valueOf(raw).split(";")) {
            String value = part.trim().toLowerCase();
            if (!value.isEmpty() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    /** Giữ nguyên hoa/thường như đã lưu (FE so khớp username trong *_obj). */
    public static List<String> rawMembers(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String part : String.valueOf(raw).split(";")) {
            String value = part.trim();
            if (!value.isEmpty() && result.stream().noneMatch(value::equalsIgnoreCase)) {
                result.add(value);
            }
        }
        return result;
    }

    /** Node jsTree đúng khuôn Domino DanhSachThuMuc#createNode (field *_obj trả CHUỖI JSON - FE JSON.parse). */
    public Map<String, Object> toNode(ChiaSeTepDocument folder) {
        Map<String, Object> fields = fields(folder);
        Map<String, Object> data = new LinkedHashMap<>();
        for (String key : NODE_DATA_FIELDS) {
            data.put(key, asJsonString(fields.get(key)));
        }
        data.put("user_creator", folder.getUserCreator() == null ? "" : folder.getUserCreator());
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", folder.getId().toString());
        node.put("text", folder.getTitle());
        node.put("form", folder.getForm());
        node.put("icon", "far fa-folder");
        node.put("data", data);
        node.put("children", new ArrayList<Map<String, Object>>());
        return node;
    }

    /** Map chi tiết / dòng danh sách: field động + id/form/parentid/user_creator tĩnh + tệp. */
    public Map<String, Object> toDocumentMap(ChiaSeTepDocument document, List<FileDto> files) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", document.getId());
        map.putAll(fields(document));
        map.put("id", document.getId());
        map.put("form", document.getForm());
        map.put("parentid", document.getParentId());
        map.put("user_creator", document.getUserCreator());
        map.put("databasetype", "chiasetep");
        map.put("created_on", document.getCreatedOn());
        map.put("files", toFileMaps(files));
        return map;
    }

    public static List<Map<String, Object>> toFileMaps(List<FileDto> files) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (files == null) {
            return result;
        }
        for (FileDto dto : files) {
            Map<String, Object> file = new LinkedHashMap<>();
            String name = dto.getOriginalFileName() != null ? dto.getOriginalFileName() : dto.getFileName();
            name = name == null ? "" : name;
            int dot = name.lastIndexOf('.');
            file.put("name", name);
            file.put("size", dto.getFileSize());
            file.put("type", dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase() : "");
            file.put("file_id", dto.getFileId());
            result.add(file);
        }
        return result;
    }

    public static String asJsonString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "";
        }
    }

    public static String toJson(Map<String, Object> fields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(fields);
        } catch (Exception ex) {
            return "{}";
        }
    }

    public static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
