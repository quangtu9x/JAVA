package com.td.application.vbcddh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 30/10/2026 (cutover FE vbcddh): thay {@code qlvb/delete?databasetype=vbcddh} (deleteoption=trash) và
 * {@code qlvb/restore?databasetype=vbcddh}. Đúng Domino: "xoá" = chuyển {@code form} sang
 * {@code <form>_Deleted} (các view thùng rác {@code vwNhiem_Vu_Da_Xoa}/{@code vwVan_Ban_Da_Xoa} lọc theo
 * form này); xoá bản ghi đã ở thùng rác = xoá mềm ({@code markAsDeleted}, không xoá cứng). Xoá/phục hồi
 * văn bản ({@code form=vanban}) kéo theo các nhiệm vụ có {@code idvanban} = văn bản đó (trước đây FE tự
 * lặp từng nhiệm vụ). Quyền: người tạo / người theo dõi / người chuyển CĐĐH / quản trị.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VbcddhTrashUseCase {

    private static final String DELETED_SUFFIX = "_Deleted";
    private static final String FORM_VANBAN = "vanban";
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "CVQUANTRI");
    private static final List<String> OWNER_FIELDS = List.of(
        "user_creator", "nguoitheodoihide", "user_nguoichuyencddh", "nguoichidaohide", "nguoiphutrachhide");
    private static final int MAX_CHILDREN = 2000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final VbcddhFieldMapper fieldMapper;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<List<UUID>> xoa(List<String> rawIds) {
        List<UUID> ids = VbcddhIds.parseAll(rawIds);
        if (ids.isEmpty()) {
            return Result.failure("Danh sách cần xoá không được để trống");
        }
        String username = currentUserProvider.currentUsername().orElse("");
        UUID deletedBy = resolveCurrentUserId(username);
        List<UUID> done = new ArrayList<>();
        for (UUID id : ids) {
            VbcddhDocument document = vbcddhDocumentRepository.findById(id).orElse(null);
            if (document == null || document.isDeleted()) {
                continue;
            }
            Map<String, Object> fields = fieldMapper.parse(document);
            if (!canManage(fields, username)) {
                log.warn("Từ chối xoá vbcddh {} - {} không phải người tạo/theo dõi", id, username);
                continue;
            }
            String form = asString(fields.get("form"));
            if (form.endsWith(DELETED_SUFFIX)) {
                document.markAsDeleted(deletedBy);
                vbcddhDocumentRepository.save(document);
                documentLogService.logDelete(id, fields);
            } else {
                moveForm(document, fields, form + DELETED_SUFFIX);
                if (FORM_VANBAN.equals(form)) {
                    for (VbcddhDocument child : children(id, List.of("nhiemvu", "nhiemvu_chuaduyet"))) {
                        Map<String, Object> childFields = fieldMapper.parse(child);
                        moveForm(child, childFields, asString(childFields.get("form")) + DELETED_SUFFIX);
                    }
                }
            }
            done.add(id);
        }
        documentCacheService.evictAllListCaches();
        return done.isEmpty() ? Result.failure("Không xoá được bản ghi nào (không tồn tại hoặc không có quyền)")
            : Result.success(done);
    }

    public Result<List<UUID>> khoiPhuc(List<String> rawIds) {
        List<UUID> ids = VbcddhIds.parseAll(rawIds);
        if (ids.isEmpty()) {
            return Result.failure("Danh sách cần phục hồi không được để trống");
        }
        String username = currentUserProvider.currentUsername().orElse("");
        List<UUID> done = new ArrayList<>();
        for (UUID id : ids) {
            VbcddhDocument document = vbcddhDocumentRepository.findById(id).orElse(null);
            if (document == null || document.isDeleted()) {
                continue;
            }
            Map<String, Object> fields = fieldMapper.parse(document);
            if (!canManage(fields, username)) {
                log.warn("Từ chối phục hồi vbcddh {} - {} không phải người tạo/theo dõi", id, username);
                continue;
            }
            String form = asString(fields.get("form"));
            if (form.endsWith(DELETED_SUFFIX)) {
                String original = form.substring(0, form.length() - DELETED_SUFFIX.length());
                moveForm(document, fields, original);
                if (FORM_VANBAN.equals(original)) {
                    for (VbcddhDocument child : children(id, List.of("nhiemvu_Deleted", "nhiemvu_chuaduyet_Deleted"))) {
                        Map<String, Object> childFields = fieldMapper.parse(child);
                        String childForm = asString(childFields.get("form"));
                        moveForm(child, childFields, childForm.substring(0, childForm.length() - DELETED_SUFFIX.length()));
                    }
                }
            }
            done.add(id);
        }
        documentCacheService.evictAllListCaches();
        return done.isEmpty() ? Result.failure("Không phục hồi được bản ghi nào (không tồn tại hoặc không có quyền)")
            : Result.success(done);
    }

    private List<VbcddhDocument> children(UUID idVanBan, List<String> forms) {
        VbcddhListCriteria criteria = VbcddhListCriteria.builder()
            .documentType("vbcddh")
            .forms(forms)
            .memberFields(List.of("idvanban"))
            .member(idVanBan.toString())
            .build();
        return vbcddhDocumentRepository.findByCriteria(criteria, PageRequest.of(0, MAX_CHILDREN)).getContent();
    }

    private void moveForm(VbcddhDocument document, Map<String, Object> fields, String newForm) {
        Map<String, Object> before = new LinkedHashMap<>(fields);
        fields.put("form", newForm);
        document.replaceDynamicFields(toJson(fields));
        vbcddhDocumentRepository.save(document);
        documentLogService.logUpdate(document.getId(), before, fields);
    }

    private boolean canManage(Map<String, Object> fields, String username) {
        Set<String> roles = currentUserProvider.currentRoles();
        if (roles != null && roles.stream().anyMatch(ADMIN_ROLES::contains)) {
            return true;
        }
        if (username == null || username.isBlank()) {
            return false;
        }
        String needle = username.trim().toLowerCase();
        for (String field : OWNER_FIELDS) {
            for (String part : asString(fields.get(field)).toLowerCase().split(";")) {
                if (part.trim().equals(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private UUID resolveCurrentUserId(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return appUserRepository.findByUsernameAndDeletedOnIsNull(username)
            .map(AppUser::getId)
            .orElse(null);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
