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
 * BL-116 (tiếp, 25/08/2026) — tương đương DXL Domino {@code nhom_nguoi_phathanh_by_identifier}
 * (view {@code tudien.nsf}, {@code SELECT Form="group_people" & type="Phát hành văn bản đi"},
 * key hoá {@code @Explode(donvi_sudung_identifier;";")}) — dùng bởi
 * {@code ThanhHoaDEV/src/app/components/JSTree/JSTree.jsx} để chèn thêm vài node "nhóm cấu hình
 * riêng" (nhóm người nhận đã lưu sẵn) phía trên cây tổ chức, giúp người dùng chọn nhanh 1 nhóm thay
 * vì chọn từng đơn vị/người.
 *
 * <p><b>Khác biệt CÓ CHỦ Ý so với endpoint quản trị hiện có</b>
 * ({@code SearchPrivateCategoriesUseCase}/{@code GET api/v1/private-categories?identifier=...}):
 * endpoint đó lọc theo cột thật {@code identifier} — identifier của NGƯỜI/ĐƠN VỊ ĐÃ TẠO nhóm (dùng
 * cho màn quản trị {@code nhomnguoinhan/ItemsList.jsx}). Ở đây cần lọc theo Ý NGHĨA KHÁC — đơn vị
 * nào ĐƯỢC PHÉP DÙNG nhóm khi soạn thảo/gửi văn bản, field {@code donvi_sudung_identifier} (JSONB,
 * danh sách nối {@code ";"}, do {@code nhomnguoinhan/components/ChiTietModal.jsx#onFinish} ghi) —
 * đúng field Domino gốc dùng làm khoá {@code @Explode}. 2 identifier này có thể KHÁC NHAU (1 đơn vị
 * tạo nhóm nhưng cho phép NHIỀU đơn vị khác cùng dùng) — không dùng nhầm lẫn 2 khái niệm.</p>
 *
 * <p>KHÔNG lọc thêm {@code type="Phát hành văn bản đi"} như DXL gốc — {@code JSTree.jsx} dùng
 * chung 1 lời gọi cho MỌI ngữ cảnh chọn người nhận (không chỉ "phát hành văn bản đi"), và
 * {@code type} chỉ là 1 trong 7 lựa chọn tự do ở {@code ChiTietModal.jsx} (Select "Loại nhóm") —
 * không có bằng chứng FE cần lọc riêng theo type ở bước này, thêm vào sẽ là suy diễn không cần
 * thiết.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetGroupPeopleForIdentifierUseCase {

    public static final String GROUP_PEOPLE_FORM = "group_people";
    private static final String DONVI_SUDUNG_IDENTIFIER_FIELD = "donvi_sudung_identifier";
    private static final String RECEIVER_OBJ_FIELD = "receiver_obj";

    private final PrivateCategoryRepository privateCategoryRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ActiveGroupMembersFilter activeGroupMembersFilter;

    public Result<List<Map<String, Object>>> execute(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Result.success(List.of());
        }

        var categories = privateCategoryRepository.findByFormAndDonViSuDungIdentifier(
            GROUP_PEOPLE_FORM, identifier.trim());

        List<Map<String, Object>> nodes = categories.stream()
            .map(this::toNode)
            // 25/09/2026 (quy ước "Thứ tự hiển thị"): thứ tự tăng dần, trùng số thì theo tên A-Z.
            .sorted(Comparator.<Map<String, Object>>comparingInt(node -> asInt(node.get("sort_order")))
                .thenComparing(node -> String.valueOf(node.getOrDefault("name", "")), java.text.Collator.getInstance(new java.util.Locale("vi", "VN"))))
            .toList();

        // 06/10/2026: bỏ thành viên đã bị Khoá/Xoá khỏi ảnh chụp receiver_obj - xem ActiveGroupMembersFilter.
        List<Object> filtered = activeGroupMembersFilter.filter(
            nodes.stream().map(node -> node.get(RECEIVER_OBJ_FIELD)).toList());
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).put(RECEIVER_OBJ_FIELD, filtered.get(i));
        }

        return Result.success(nodes);
    }

    private Map<String, Object> toNode(com.td.domain.privatecatalog.PrivateCategory category) {
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(category.getDynamicFieldsJson());

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", category.getId().toString());
        node.put("name", category.getName());
        node.put("sort_order", category.getSortOrder());
        node.put("form", GROUP_PEOPLE_FORM);
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
