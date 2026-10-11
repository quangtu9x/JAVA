package com.td.application.privatecatalog;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.privatecatalog.PrivateCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 09/10/2026 (port "Sử dụng xin ý kiến" — cấu hình Cơ quan → Dự thảo, {@code cfg_xin_y_kien}): tương
 * đương 5 view Domino {@code tudien.nsf} {@code nhom_{TVUB,TVNB,BTV,BCH,TTDU}_by_identifier}
 * ({@code SELECT Form="group_people" & type="Thành viên …"}, khoá {@code @Explode(identifier + ";" +
 * donvi_sudung_identifier)}) — FE {@code YKienTVUBTable}/{@code YKienBTVTable}/
 * {@code InBaoCaoLayKienThanhVien} đọc {@code receiver_obj} của các nhóm này làm danh sách thành viên
 * được xin ý kiến. Trả bản ghi cùng dạng Domino (field động + id/name/identifier/sort_order),
 * {@code receiver_obj} đã bỏ thành viên bị Khoá/Xoá ({@link ActiveGroupMembersFilter}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetGroupPeopleByTypeUseCase {

    /** viewname Domino (viết thường) → giá trị field {@code type} của nhóm. */
    public static final Map<String, String> VIEW_TO_GROUP_TYPE = Map.of(
        "nhom_tvub_by_identifier", "Thành viên Ủy ban",
        "nhom_tvnb_by_identifier", "Thành viên nội bộ",
        "nhom_btv_by_identifier", "Thành viên BTV",
        "nhom_bch_by_identifier", "Thành viên BCH",
        "nhom_ttdu_by_identifier", "Thành viên TTĐU");

    private static final String RECEIVER_OBJ_FIELD = "receiver_obj";

    private final PrivateCategoryRepository privateCategoryRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ActiveGroupMembersFilter activeGroupMembersFilter;

    public static boolean supportsView(String viewname) {
        return viewname != null && VIEW_TO_GROUP_TYPE.containsKey(viewname.trim().toLowerCase(Locale.ROOT));
    }

    public Result<List<Map<String, Object>>> execute(String viewname, String identifier) {
        String type = viewname == null ? null : VIEW_TO_GROUP_TYPE.get(viewname.trim().toLowerCase(Locale.ROOT));
        if (type == null || identifier == null || identifier.isBlank()) {
            return Result.success(List.of());
        }
        List<Map<String, Object>> rows = privateCategoryRepository
            .findByFormAndTypeAndIdentifier(GetGroupPeopleForIdentifierUseCase.GROUP_PEOPLE_FORM, type, identifier.trim())
            .stream()
            .map(this::toRow)
            .toList();
        List<Object> filtered = activeGroupMembersFilter.filter(
            rows.stream().map(row -> row.get(RECEIVER_OBJ_FIELD)).toList());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put(RECEIVER_OBJ_FIELD, filtered.get(i));
        }
        return Result.success(rows);
    }

    private Map<String, Object> toRow(PrivateCategory category) {
        Map<String, Object> row = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(category.getDynamicFieldsJson()));
        row.put("id", category.getId().toString());
        row.put("name", category.getName());
        row.put("identifier", category.getIdentifier());
        row.put("sort_order", category.getSortOrder());
        row.put("form", GetGroupPeopleForIdentifierUseCase.GROUP_PEOPLE_FORM);
        row.putIfAbsent(RECEIVER_OBJ_FIELD, "");
        return row;
    }
}
