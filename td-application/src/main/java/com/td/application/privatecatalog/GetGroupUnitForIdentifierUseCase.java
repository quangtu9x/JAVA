package com.td.application.privatecatalog;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Yêu cầu người dùng 28/08/2026 ("port luôn nhóm phát hành nhanh") — tương đương 3 view Domino
 * {@code nhom_CQN_phathanh_by_identifier}/{@code nhom_CQLH_phathanh_by_identifier}/
 * {@code nhom_DVNB_phathanh_by_identifier} (view {@code tudien.nsf}, cùng Form {@code group_unit},
 * chỉ khác field {@code type} — xem dxl {@code docs/views_domino/view_tudien_danh_muc_rieng.dxl}
 * dòng 367-449). Cả 3 view: {@code SELECT Form="group_unit" & type="<nhãn loại>"}, categorize theo
 * {@code @Explode(donvi_sudung_identifier;";")}.
 *
 * <p>Sinh đôi với {@link GetGroupPeopleForIdentifierUseCase} ({@code group_people}, KHÔNG lọc thêm
 * theo {@code type}) — CHỈ khác 2 điểm: form {@link #GROUP_UNIT_FORM} thay vì {@code group_people},
 * VÀ có lọc thêm theo {@code type} (bắt buộc, vì 1 Form {@code group_unit} gộp chung cả 3 view gốc
 * — không lọc sẽ trộn lẫn cả 3 loại "Cơ quan ngoài"/"Cơ quan, đơn vị thuộc"/"Đơn vị nội bộ" vào 1
 * cây). Lọc {@code type} làm TRONG BỘ NHỚ (sau khi đã lọc theo
 * {@code donvi_sudung_identifier} bằng {@link PrivateCategoryRepository}) thay vì thêm 1 native
 * query biến thể — số nhóm phát hành nhanh của 1 đơn vị thường rất nhỏ (vài chục), không đáng thêm
 * 1 query SQL riêng cho mỗi loại.</p>
 *
 * <p>3 nhãn {@code type} hợp lệ khớp ĐÚNG Select "Loại nhóm" ở màn quản trị
 * {@code ThanhHoaDEV/src/app/pages/qlvb/nhomdonvi/components/ChiTietModal.jsx} (nơi DUY NHẤT tạo
 * dữ liệu {@code group_unit} mới qua Java) — xem {@link GroupUnitType}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetGroupUnitForIdentifierUseCase {

    public static final String GROUP_UNIT_FORM = "group_unit";
    private static final String TYPE_FIELD = "type";
    private static final String DONVI_SUDUNG_IDENTIFIER_FIELD = "donvi_sudung_identifier";
    private static final String RECEIVER_OBJ_FIELD = "receiver_obj";

    private final PrivateCategoryRepository privateCategoryRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<List<Map<String, Object>>> execute(String identifier, String type) {
        if (identifier == null || identifier.isBlank() || type == null || type.isBlank()) {
            return Result.success(List.of());
        }
        String targetType = type.trim();

        var categories = privateCategoryRepository.findByFormAndDonViSuDungIdentifier(
            GROUP_UNIT_FORM, identifier.trim());

        List<Map<String, Object>> nodes = categories.stream()
            .map(category -> toNode(category, targetType))
            .filter(node -> node != null)
            .sorted(Comparator.comparingInt(node -> asInt(node.get("sort_order"))))
            .toList();

        return Result.success(nodes);
    }

    private Map<String, Object> toNode(com.td.domain.privatecatalog.PrivateCategory category, String targetType) {
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(category.getDynamicFieldsJson());
        Object actualType = dynamicFields.get(TYPE_FIELD);
        if (actualType == null || !targetType.equals(String.valueOf(actualType).trim())) {
            return null;
        }

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", category.getId().toString());
        node.put("name", category.getName());
        node.put("sort_order", category.getSortOrder());
        node.put("form", GROUP_UNIT_FORM);
        node.put(RECEIVER_OBJ_FIELD, dynamicFields.getOrDefault(RECEIVER_OBJ_FIELD, ""));
        node.put(DONVI_SUDUNG_IDENTIFIER_FIELD, dynamicFields.getOrDefault(DONVI_SUDUNG_IDENTIFIER_FIELD, ""));
        return node;
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }
}
