package com.td.application.sharedcore;

import com.td.application.categories.CategoryDto;
import com.td.application.categories.SearchCategoriesRequest;
import com.td.application.categories.SearchCategoriesUseCase;
import com.td.application.documentregister.DocumentRegisterDto;
import com.td.application.documentregister.SearchDocumentRegistersRequest;
import com.td.application.documentregister.SearchDocumentRegistersUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 11/10/2026 (yêu cầu "sửa đồng loạt" sau báo lỗi skhcn "không có danh mục để chọn"): ~100 lời gọi FE
 * còn dùng Domino {@code qlvb/search?databasetype=qlvb|tudien} với {@code query} là công thức Domino
 * (danh mục, người dùng, đơn vị, sổ) — tenant Java không có dữ liệu Domino nên mọi ô chọn trống. Thay vì
 * sửa từng call site, use case này DỊCH các mẫu công thức đã khảo sát (helper {@code utils/queryqlvb.js}
 * + chuỗi viết tay) sang dữ liệu Java:
 * <ul>
 *   <li>{@code Select Form = 'X'} (danh mục, có bí danh Domino cũ dokhan/domat/loaivanban/linhvuc/
 *   phuongthucgui/chucvu) → bảng {@code categories}.</li>
 *   <li>{@code Form = 'user'|'nguoidung'} + {@code agency_id/unit_id/department_id/nodeid = 'ID'}
 *   (+ {@code @Contains(roles; "QXxx")}, {@code username = '...'}) → người dùng đang hoạt động của cây
 *   tổ chức ID (không có ID = cơ quan của người dùng hiện tại), lọc theo mã quyền Nhóm vai trò.</li>
 *   <li>{@code Form = 'unit'|'department'|'agency'} (+ {@code parentid="ID"}) → đơn vị.</li>
 *   <li>{@code Form = 'document_register' & document_register_type = 'T' & unit_used_id = 'ID'
 *   [& is_default = '1']} → sổ văn bản theo mã định danh của ID.</li>
 * </ul>
 * Mẫu khác → {@code supported=false}, FE gọi lại Domino như cũ (không hồi quy chỗ chưa nhận diện).
 */
@Service
@RequiredArgsConstructor
public class SearchLegacyDanhMucUseCase {

    private static final Pattern FORM = Pattern.compile("form\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PURE_FORM = Pattern.compile(
        "^\\s*select\\s+form\\s*=\\s*['\"]([^'\"]+)['\"]\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERMISSION = Pattern.compile("@lowercase\\(\\s*\"(q\\w+)\"\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARENT_ID = Pattern.compile("parentid\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> CATALOG_FORMS = Map.ofEntries(
        Map.entry("document_type", "document_type"), Map.entry("loaivanban", "document_type"),
        Map.entry("sector", "sector"), Map.entry("linhvuc", "sector"),
        Map.entry("priority", "priority"), Map.entry("dokhan", "priority"),
        Map.entry("confidentiality", "confidentiality"), Map.entry("domat", "confidentiality"),
        Map.entry("method", "method"), Map.entry("phuongthucgui", "method"),
        Map.entry("position", "position"), Map.entry("chucvu", "position"),
        Map.entry("formality", "formality"), Map.entry("storage_period", "storage_period"));
    private static final List<String> USER_FORMS = List.of("user", "nguoidung");
    private static final List<String> ORG_FORMS = List.of("agency_level", "agency", "unit", "department");
    private static final int MAX_CATALOG_ROWS = 200;

    private final SearchCategoriesUseCase searchCategoriesUseCase;
    private final SearchDocumentRegistersUseCase searchDocumentRegistersUseCase;
    private final GetLegacyOrgLookupForDungChungUseCase orgLookup;
    private final OrganizationAgencyResolver organizationAgencyResolver;
    private final GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;

    public Map<String, Object> execute(String query, String keyword) {
        List<Map<String, Object>> items = translate(query == null ? "" : query.trim());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supported", items != null);
        List<Map<String, Object>> filtered = items == null ? List.of() : filterByKeyword(items, keyword);
        result.put("items", filtered);
        result.put("total", filtered.size());
        return result;
    }

    // null = mau cong thuc chua ho tro.
    List<Map<String, Object>> translate(String query) {
        Matcher formMatcher = FORM.matcher(query);
        if (!formMatcher.find()) {
            return null;
        }
        String form = formMatcher.group(1).trim().toLowerCase(Locale.ROOT);

        Matcher pureForm = PURE_FORM.matcher(query);
        if (pureForm.matches() && CATALOG_FORMS.containsKey(form)) {
            return searchCatalog(CATALOG_FORMS.get(form));
        }
        if (USER_FORMS.contains(form)) {
            return searchUsers(query);
        }
        if (ORG_FORMS.contains(form)) {
            return searchOrganizations(query, form);
        }
        if ("document_register".equals(form)) {
            return searchDocumentRegisters(query);
        }
        return null;
    }

    private List<Map<String, Object>> searchCatalog(String form) {
        SearchCategoriesRequest request = new SearchCategoriesRequest();
        request.setForms(List.of(form));
        request.setPageNumber(0);
        request.setPageSize(MAX_CATALOG_ROWS);
        request.setSortBy("sortOrder");
        request.setSortDirection("asc");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CategoryDto dto : searchCategoriesUseCase.execute(request).getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (dto.getDynamicFields() != null) {
                row.putAll(dto.getDynamicFields());
            }
            row.put("id", dto.getId() == null ? "" : dto.getId().toString());
            row.put("name", dto.getName());
            row.put("code", dto.getCode());
            row.put("form", dto.getForm());
            row.put("sort_order", dto.getSortOrder());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> searchUsers(String query) {
        // Uu tien cap sau nhat (query_by_nameform_cq_dv_pb_user AND ca 3); mau OR (query_danhsachnguoiky)
        // cung 1 id o moi field nen lay field nao cung duoc.
        String orgId = firstNonBlank(
            fieldValue(query, "department_id"), fieldValue(query, "unit_id"),
            fieldValue(query, "agency_id"), fieldValue(query, "nodeid"));
        if (orgId == null) {
            orgId = organizationAgencyResolver.resolveCurrentUserAgencyId().map(UUID::toString).orElse(null);
        }
        if (orgId == null) {
            return List.of();
        }
        List<Map<String, Object>> users = orgLookup.getOrganizationMembersDetailed(orgId, true);

        String username = fieldValue(query, "username");
        if (username != null) {
            users = users.stream().filter(row -> username.equalsIgnoreCase(String.valueOf(row.get("username")))).toList();
        }
        Matcher permission = PERMISSION.matcher(query);
        if (permission.find()) {
            String code = permission.group(1);
            users = users.stream().filter(row -> hasPermission(String.valueOf(row.get("username")), code)).toList();
        }
        return users;
    }

    private boolean hasPermission(String username, String code) {
        try {
            return getUserRoleGroupConfigUseCase.execute(username).getPermissionCodes().stream()
                .anyMatch(code::equalsIgnoreCase);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private List<Map<String, Object>> searchOrganizations(String query, String form) {
        Matcher parent = PARENT_ID.matcher(query);
        if (parent.find()) {
            return orgLookup.getOrganizationsByForm(parent.group(1).trim(), form, true);
        }
        String agencyId = organizationAgencyResolver.resolveCurrentUserAgencyId().map(UUID::toString).orElse(null);
        return agencyId == null ? List.of() : orgLookup.getOrganizationsByForm(agencyId, form, false);
    }

    private List<Map<String, Object>> searchDocumentRegisters(String query) {
        String unitUsedId = fieldValue(query, "unit_used_id");
        if (unitUsedId == null) {
            return null;
        }
        String identifier = orgLookup.getOrganizationIdentifier(unitUsedId);
        if (identifier == null || identifier.isBlank()) {
            return List.of();
        }
        SearchDocumentRegistersRequest request = new SearchDocumentRegistersRequest();
        request.setIdentifier(identifier);
        request.setDocumentRegisterType(fieldValue(query, "document_register_type"));
        request.setPageNumber(0);
        request.setPageSize(MAX_CATALOG_ROWS);
        boolean onlyDefault = "1".equals(fieldValue(query, "is_default"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DocumentRegisterDto dto : searchDocumentRegistersUseCase.execute(request).getItems()) {
            if (onlyDefault && !dto.isDefault()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", dto.getId() == null ? "" : dto.getId().toString());
            row.put("name", dto.getName());
            row.put("document_register_type", dto.getDocumentRegisterType());
            row.put("year", dto.getYear());
            row.put("identifier", dto.getIdentifier());
            row.put("code_notation", dto.getCodeNotation());
            row.put("code_start_number", dto.getCodeStartNumber());
            row.put("is_default", dto.isDefault() ? "1" : "0");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> filterByKeyword(List<Map<String, Object>> items, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return items;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return items.stream()
            .filter(row -> String.valueOf(row.getOrDefault("name", "")).toLowerCase(Locale.ROOT).contains(needle))
            .toList();
    }

    // Gia tri cua "field = 'x'" / "field='x'" / field="x" (dau tien) - null neu khong co/rong.
    private String fieldValue(String query, String field) {
        Matcher matcher = Pattern.compile("(?<![\\w.])" + Pattern.quote(field) + "\\s*=\\s*['\"]([^'\"]*)['\"]",
            Pattern.CASE_INSENSITIVE).matcher(query);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        return value.isEmpty() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
