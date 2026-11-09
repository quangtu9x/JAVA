package com.td.application.chiasetep;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListFilesForDocumentsUseCase;
import com.td.domain.chiasetep.ChiaSeTepDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 30/10/2026 (port chiasetep): phần ĐỌC - cây thư mục (qlvb/chiasetep/danhsachthumuc), người dùng đã/được
 * chia sẻ (danhsachnguoidung), tài liệu trong thư mục (qlvb/view/query vwTai_Lieu_By_ParentID), chi tiết
 * (qlvb/get). Người dùng luôn lấy từ phiên đăng nhập (không tin tham số FE).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChiaSeTepQueryUseCase {

    private final ChiaSeTepDocumentRepository repository;
    private final ChiaSeTepSupport support;
    private final ListFilesForDocumentsUseCase listFilesForDocumentsUseCase;

    public Result<List<Map<String, Object>>> tree(ChiaSeTepRequests.Tree request) {
        String me = support.username();
        if (me.isBlank()) {
            return Result.failure("Không xác định được người dùng");
        }
        String type = request == null || request.getType() == null || request.getType().isBlank()
            ? "canhan" : request.getType().trim();
        String key2 = request == null ? null : request.getKey2();
        if (key2 != null && ("undefined".equals(key2) || "null".equals(key2))) {
            key2 = null;
        }
        List<ChiaSeTepDocument> folders = switch (type) {
            case "dachiase" -> repository.findFoldersSharedBy(me, null);
            case "dachiase_theonguoi" -> key2 == null ? List.of() : repository.findFoldersSharedBy(me, key2);
            case "duocchiase" -> repository.findFoldersSharedWith(me, null);
            case "duocchiase_theonguoi" -> key2 == null ? List.of() : repository.findFoldersSharedWith(me, key2);
            default -> repository.findFoldersOwnedBy(me);
        };
        return Result.success(buildTree(folders));
    }

    /** Gốc = thư mục có cha không nằm trong tập (Domino trả mọi phần tử, FE filterDuplicateNodes lọc lại). */
    private List<Map<String, Object>> buildTree(List<ChiaSeTepDocument> folders) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (ChiaSeTepDocument folder : folders) {
            nodes.put(folder.getId().toString(), support.toNode(folder));
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (ChiaSeTepDocument folder : folders) {
            Map<String, Object> node = nodes.get(folder.getId().toString());
            Map<String, Object> parent = folder.getParentId() == null ? null : nodes.get(folder.getParentId());
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(node);
            }
        }
        return roots;
    }

    /**
     * {@code type=duocchiase}: những người MÌNH đã chia sẻ (data = chuỗi *_obj chứa người đó);
     * {@code type=dachiase}: những người đã chia sẻ CHO MÌNH (data = created_by_user_info). Khuôn {key, data}
     * giữ nguyên Domino DanhSachNguoiDung.
     */
    public Result<List<Map<String, Object>>> users(ChiaSeTepRequests.Users request) {
        String me = support.username();
        if (me.isBlank()) {
            return Result.failure("Không xác định được người dùng");
        }
        String type = request == null || request.getType() == null ? "duocchiase" : request.getType().trim();
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if ("dachiase".equals(type)) {
            for (ChiaSeTepDocument folder : repository.findFoldersSharedWith(me, null)) {
                String owner = folder.getUserCreator();
                if (owner == null || owner.isBlank() || !seen.add(owner.toLowerCase())) {
                    continue;
                }
                String info = ChiaSeTepSupport.asJsonString(support.fields(folder).get("created_by_user_info"));
                if (info.isBlank()) {
                    info = support.user(owner).map(user -> ChiaSeTepSupport.toJson(userInfo(user.getId(),
                        user.getFullName(), owner))).orElse(ChiaSeTepSupport.toJson(userInfo(null, owner, owner)));
                }
                result.add(Map.of("key", owner, "data", info));
            }
            return Result.success(result);
        }
        for (ChiaSeTepDocument folder : repository.findFoldersSharedBy(me, null)) {
            Map<String, Object> fields = support.fields(folder);
            String editorsObj = ChiaSeTepSupport.asJsonString(fields.get("nguoichinhsua_obj"));
            String viewersObj = ChiaSeTepSupport.asJsonString(fields.get("nguoixem_obj"));
            List<String> recipients = new ArrayList<>(ChiaSeTepSupport.rawMembers(fields.get(ChiaSeTepSupport.EDITORS)));
            recipients.addAll(ChiaSeTepSupport.rawMembers(fields.get(ChiaSeTepSupport.VIEWERS)));
            for (String recipient : recipients) {
                if (!seen.add(recipient.toLowerCase())) {
                    continue;
                }
                String data = editorsObj.contains(recipient) ? editorsObj : viewersObj;
                result.add(Map.of("key", recipient, "data", data));
            }
        }
        return Result.success(result);
    }

    private static Map<String, Object> userInfo(UUID id, String fullName, String username) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", id == null ? "" : id.toString());
        info.put("fullName", fullName == null ? username : fullName);
        info.put("username", username);
        info.put("jobTitle", "");
        return info;
    }

    public Result<PaginationResponse<Map<String, Object>>> listDocuments(ChiaSeTepRequests.ListDocuments request) {
        String me = support.username();
        int pageNumber = Math.max(1, request.getPageNumber());
        int pageSize = Math.min(Math.max(1, request.getPageSize()), 500);
        PageRequest pageable = PageRequest.of(pageNumber - 1, pageSize);
        Page<ChiaSeTepDocument> page;
        String parentId = request.getParentId();
        if (parentId != null && !parentId.isBlank()) {
            var folder = support.folder(parentId).orElse(null);
            if (!support.canReadFolder(folder, me)) {
                PaginationResponse<Map<String, Object>> empty =
                    new PaginationResponse<>(List.<Map<String, Object>>of(), pageNumber, pageSize, 0L, 0, true, true);
                return Result.success(empty);
            }
            page = repository.findDocumentsInFolder(parentId.trim(), request.getKeyword(), pageable);
        } else {
            page = repository.findDocumentsAccessibleTo(me, request.getKeyword(), pageable);
        }
        List<UUID> ids = page.getContent().stream().map(ChiaSeTepDocument::getId).toList();
        Map<UUID, List<FileDto>> files = ids.isEmpty() ? Map.of() : listFilesForDocumentsUseCase.execute(ids);
        List<Map<String, Object>> items = page.getContent().stream()
            .map(document -> support.toDocumentMap(document, files.getOrDefault(document.getId(), List.of())))
            .toList();
        return Result.success(new PaginationResponse<>(items, pageNumber, pageSize, page.getTotalElements(),
            page.getTotalPages(), page.isFirst(), page.isLast()));
    }

    public Result<Map<String, Object>> detail(ChiaSeTepRequests.Id request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id");
        }
        var document = repository.findById(request.getId()).filter(d -> !d.isDeleted()).orElse(null);
        if (document == null) {
            return Result.failure("Không tìm thấy dữ liệu");
        }
        if (!support.canReadDocument(document, support.username())) {
            return Result.failure("Bạn không có quyền xem dữ liệu này");
        }
        Map<UUID, List<FileDto>> files = listFilesForDocumentsUseCase.execute(List.of(document.getId()));
        return Result.success(support.toDocumentMap(document, files.getOrDefault(document.getId(), List.of())));
    }
}
