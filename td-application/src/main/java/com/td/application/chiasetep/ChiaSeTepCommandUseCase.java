package com.td.application.chiasetep;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.CopyDocumentFilesRequest;
import com.td.application.documents.CopyDocumentFilesUseCase;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.chiasetep.ChiaSeTepDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 30/10/2026 (port chiasetep): phần GHI - thêm/sửa thư mục & tài liệu (qlvb/add), xoá thư mục kèm con
 * (xoathumuc), xoá tài liệu (xoatailieu), sao chép-dán thư mục (danthumuc), bỏ chia sẻ theo người
 * (bochiasethumuc), gắn văn bản (ganvanban). Xoá = xoá mềm. Quyền kiểm tra phía server theo dữ liệu.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChiaSeTepCommandUseCase {

    private static final String MODULE = "chiasetep";
    private static final Set<String> RESERVED = Set.of(
        "id", "form", "parentid", "parent_id", "user_creator", "files", "databasetype", "date_fields",
        "number_fields", "richtext_fields", "apdungcho");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ChiaSeTepDocumentRepository repository;
    private final ChiaSeTepSupport support;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CopyDocumentFilesUseCase copyDocumentFilesUseCase;

    public Result<UUID> save(ChiaSeTepRequests.Save request) {
        String me = support.username();
        if (me.isBlank()) {
            return Result.failure("Không xác định được người dùng");
        }
        Map<String, Object> incoming = parseIncoming(request.getFields());
        if (request.getId() != null) {
            var document = repository.findById(request.getId()).filter(d -> !d.isDeleted()).orElse(null);
            if (document == null) {
                return Result.failure("Không tìm thấy dữ liệu");
            }
            if (!support.canEditDocument(document, me)) {
                return Result.failure("Bạn không có quyền cập nhật dữ liệu này");
            }
            Map<String, Object> fields = support.fields(document);
            fields.putAll(incoming);
            document.replaceDynamicFields(ChiaSeTepSupport.toJson(fields));
            document.rename(titleOf(document.getForm(), fields, document.getTitle()));
            return Result.success(repository.save(document).getId());
        }
        String form = ChiaSeTepDocument.FORM_TAI_LIEU.equalsIgnoreCase(trim(request.getForm()))
            ? ChiaSeTepDocument.FORM_TAI_LIEU : ChiaSeTepDocument.FORM_THU_MUC;
        String parentId = trim(request.getParentId());
        if (ChiaSeTepDocument.FORM_THU_MUC.equals(form) && (parentId.isEmpty() || ChiaSeTepDocument.ROOT_PARENT.equals(parentId))) {
            incoming.put("cap", "1");
            return create(form, ChiaSeTepDocument.ROOT_PARENT, incoming, me);
        }
        var parent = support.folder(parentId).orElse(null);
        if (parent == null) {
            return Result.failure("Không tìm thấy thư mục");
        }
        if (!support.canEditFolder(parent, me)) {
            return Result.failure("Bạn không có quyền thêm vào thư mục này");
        }
        if (ChiaSeTepDocument.FORM_THU_MUC.equals(form)) {
            incoming.put("cap", nextLevel(support.fields(parent).get("cap")));
        }
        return create(form, parent.getId().toString(), incoming, me);
    }

    private Result<UUID> create(String form, String parentId, Map<String, Object> fields, String me) {
        fields.put("user_creator", me);
        fields.putIfAbsent("creator", support.user(me).map(u -> u.getFullName()).orElse(me));
        var document = new ChiaSeTepDocument(form, parentId, titleOf(form, fields, null), me);
        document.replaceDynamicFields(ChiaSeTepSupport.toJson(fields));
        return Result.success(repository.save(document).getId());
    }

    /** Xoá thư mục (chỉ chủ sở hữu) kèm toàn bộ thư mục con + tài liệu - Domino XuLyXoaThuMuc. */
    public Result<List<UUID>> deleteFolders(List<UUID> ids) {
        String me = support.username();
        UUID deletedBy = support.currentUserId();
        List<UUID> deleted = new ArrayList<>();
        for (UUID id : ids == null ? List.<UUID>of() : ids) {
            var folder = repository.findById(id).orElse(null);
            if (folder == null || !folder.isFolder()) {
                continue;
            }
            if (folder.isDeleted()) {
                deleted.add(id);
                continue;
            }
            if (!support.isOwner(folder, me)) {
                return Result.failure("Chỉ người tạo thư mục mới được xoá");
            }
            for (ChiaSeTepDocument item : subtree(folder)) {
                item.markAsDeleted(deletedBy);
                repository.save(item);
            }
            deleted.add(id);
        }
        return deleted.isEmpty() ? Result.failure("Không tìm thấy thư mục để xoá") : Result.success(deleted);
    }

    /** Xoá tài liệu: người tạo tài liệu hoặc người tạo/chỉnh sửa thư mục chứa - Domino XuLyXoaTaiLieu. */
    public Result<List<UUID>> deleteDocuments(List<UUID> ids) {
        String me = support.username();
        UUID deletedBy = support.currentUserId();
        List<UUID> deleted = new ArrayList<>();
        for (UUID id : ids == null ? List.<UUID>of() : ids) {
            var document = repository.findById(id).orElse(null);
            if (document == null || document.isFolder()) {
                continue;
            }
            if (!document.isDeleted()) {
                if (!support.canEditDocument(document, me)) {
                    return Result.failure("Bạn không có quyền xoá tài liệu này");
                }
                document.markAsDeleted(deletedBy);
                repository.save(document);
            }
            deleted.add(id);
        }
        return deleted.isEmpty() ? Result.failure("Không tìm thấy tài liệu để xoá") : Result.success(deleted);
    }

    /** Sao chép thư mục {@code id} (kèm con + tài liệu + tệp) vào thư mục {@code pastedId} - Domino XuLyDanThuMuc. */
    public Result<UUID> paste(ChiaSeTepRequests.Paste request) {
        String me = support.username();
        var source = request.getId() == null ? null : repository.findById(request.getId()).orElse(null);
        var target = request.getPastedId() == null ? null : repository.findById(request.getPastedId()).orElse(null);
        if (source == null || source.isDeleted() || !source.isFolder() || target == null) {
            return Result.failure("Chưa chọn thư mục sao chép hoặc thư mục đích");
        }
        if (!support.canReadFolder(source, me) || !support.canEditFolder(target, me)) {
            return Result.failure("Bạn không có quyền thực hiện thao tác này");
        }
        List<ChiaSeTepDocument> items = subtree(source); // chụp trước để dán vào chính con của nó không lặp vô hạn
        Map<String, String> newIds = new HashMap<>();
        String now = LocalDateTime.now().format(DATE_TIME);
        UUID rootCopy = null;
        for (ChiaSeTepDocument item : items) {
            String parentId = item == source ? target.getId().toString() : newIds.get(item.getParentId());
            if (parentId == null) {
                continue;
            }
            Map<String, Object> fields = support.fields(item);
            fields.put("user_creator", me);
            fields.put("saochep_date", now);
            if (item.isFolder()) {
                fields.put("cap", item == source ? nextLevel(support.fields(target).get("cap")) : fields.get("cap"));
            }
            var copy = new ChiaSeTepDocument(item.getForm(), parentId, item.getTitle(), me);
            copy.replaceDynamicFields(ChiaSeTepSupport.toJson(fields));
            copy = repository.save(copy);
            newIds.put(item.getId().toString(), copy.getId().toString());
            if (item == source) {
                rootCopy = copy.getId();
            }
            if (!item.isFolder()) {
                copyFiles(item.getId(), copy.getId());
            }
        }
        return Result.success(rootCopy);
    }

    /** Bỏ chia sẻ các thư mục cho 1 người (gỡ khỏi nguoichinhsua*, nguoixem*) - Domino XuLyBoChiaSeThuMuc. */
    public Result<List<UUID>> unshare(ChiaSeTepRequests.Unshare request) {
        String me = support.username();
        String username = trim(request.getUsername());
        String name = trim(request.getName());
        if (username.isEmpty()) {
            return Result.failure("Thiếu thông tin đầu vào!");
        }
        List<UUID> updated = new ArrayList<>();
        for (UUID id : request.getIds() == null ? List.<UUID>of() : request.getIds()) {
            var folder = repository.findById(id).filter(d -> !d.isDeleted() && d.isFolder()).orElse(null);
            if (folder == null || !support.canEditFolder(folder, me)) {
                continue;
            }
            Map<String, Object> fields = support.fields(folder);
            for (String prefix : List.of("nguoichinhsua", "nguoixem")) {
                fields.put(prefix + "hide", String.join(";", ChiaSeTepSupport.rawMembers(fields.get(prefix + "hide"))
                    .stream().filter(u -> !u.equalsIgnoreCase(username)).toList()));
                if (!name.isEmpty()) {
                    List<String> names = new ArrayList<>();
                    for (String part : String.valueOf(fields.getOrDefault(prefix, "")).split(";")) {
                        if (!part.trim().isEmpty() && !part.trim().equals(name)) {
                            names.add(part.trim());
                        }
                    }
                    fields.put(prefix, String.join("; ", names));
                }
                fields.put(prefix + "_obj", removeFromObj(fields.get(prefix + "_obj"), username));
            }
            folder.replaceDynamicFields(ChiaSeTepSupport.toJson(fields));
            repository.save(folder);
            updated.add(id);
        }
        return updated.isEmpty() ? Result.failure("Không có thư mục nào được bỏ chia sẻ") : Result.success(updated);
    }

    /** Gắn văn bản vbden/vbdi/phieutrinhtd vào thư mục: tạo tài liệu + sao chép tệp văn bản nguồn. */
    public Result<UUID> attach(ChiaSeTepRequests.Attach request) {
        String me = support.username();
        var folder = request.getParentId() == null ? null : support.folder(request.getParentId().toString()).orElse(null);
        if (folder == null) {
            return Result.failure("Vui lòng chọn thư mục để thêm văn bản!");
        }
        if (!support.canEditFolder(folder, me)) {
            return Result.failure("Bạn không có quyền thêm vào thư mục này");
        }
        Map<String, Object> fields = parseIncoming(request.getFields());
        var created = create(ChiaSeTepDocument.FORM_TAI_LIEU, folder.getId().toString(), fields, me);
        if (!created.isSuccess()) {
            return created;
        }
        for (UUID sourceId : request.getSourceIds() == null ? List.<UUID>of() : request.getSourceIds()) {
            if (sourceId != null && copyFiles(sourceId, created.getData()) > 0) {
                break;
            }
        }
        return created;
    }

    private int copyFiles(UUID sourceId, UUID targetId) {
        try {
            var result = copyDocumentFilesUseCase.execute(CopyDocumentFilesRequest.builder()
                .sourceDocumentId(sourceId).targetDocumentId(targetId).module(MODULE).build());
            return result.isSuccess() && result.getData() != null ? result.getData() : 0;
        } catch (Exception ex) {
            log.warn("Sao chép tệp {} -> {} thất bại: {}", sourceId, targetId, ex.getMessage());
            return 0;
        }
    }

    /** Thư mục + mọi con cháu (thư mục, tài liệu) chưa xoá, theo thứ tự cha trước con. */
    private List<ChiaSeTepDocument> subtree(ChiaSeTepDocument root) {
        List<ChiaSeTepDocument> result = new ArrayList<>();
        result.add(root);
        List<String> frontier = List.of(root.getId().toString());
        Set<UUID> seen = new java.util.HashSet<>(Set.of(root.getId()));
        while (!frontier.isEmpty() && result.size() < 10000) {
            List<String> next = new ArrayList<>();
            for (ChiaSeTepDocument child : repository.findChildren(frontier)) {
                if (seen.add(child.getId())) {
                    result.add(child);
                    if (child.isFolder()) {
                        next.add(child.getId().toString());
                    }
                }
            }
            frontier = next;
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

    private static String titleOf(String form, Map<String, Object> fields, String fallback) {
        String title = ChiaSeTepDocument.FORM_THU_MUC.equalsIgnoreCase(form)
            ? text(fields.get("ten"))
            : firstNonBlank(text(fields.get("trichyeu")), text(fields.get("sokyhieu")));
        if (title.isEmpty()) {
            title = fallback == null ? "" : fallback;
        }
        return AddDynamicDocumentRequest.labelTitle(title);
    }

    private static String nextLevel(Object cap) {
        try {
            return String.valueOf((int) Double.parseDouble(text(cap)) + 1);
        } catch (NumberFormatException ex) {
            return "2";
        }
    }

    private static Object removeFromObj(Object raw, String username) {
        Object value = raw;
        if (value instanceof List<?> list) {
            List<Object> kept = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("username") != null
                        && username.equalsIgnoreCase(String.valueOf(map.get("username")).trim())) {
                    continue;
                }
                kept.add(item);
            }
            return kept;
        }
        return value == null ? "" : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first.isEmpty() ? second : first;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
