package com.td.application.vbcddh;

import com.td.application.sharedcore.GetLegacyOrganizationTreeUseCase;
import com.td.application.sharedcore.LegacyOrganizationTreeNode;
import com.td.application.sharedcore.LegacyOrganizationTreeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 30/10/2026 (cutover FE vbcddh): thay Domino {@code qlvb/vbcddh/danhsachdonvithuchien} (modal chọn đơn
 * vị thực hiện/phối hợp — DonViThucHienModal; cây báo cáo theo đơn vị/chuyên viên/người theo dõi).
 * Trả {@code {unit: [...]}} đúng shape Domino: node tổ chức {@code {key,name,form,identifier,mailin,
 * receiver,user_receiver,children}}; {@code addNhanSu} gom nhân sự vào node con
 * {@code {key:"NS_<id>",name:"Nhân sự",form:"nhansu"}} chứa node {@code form:"user"}
 * (cùng cách BaoCaoTheoPhongVbdenUseCase). {@code addChildren=false} chỉ lấy tới cấp con trực tiếp.
 * Cây cắt theo cơ quan của người dùng ({@link GetLegacyOrganizationTreeUseCase}, có cache Redis).
 */
@Service
@RequiredArgsConstructor
public class DanhSachDonViThucHienUseCase {

    private static final int SHALLOW_DEPTH = 2;

    private final GetLegacyOrganizationTreeUseCase getLegacyOrganizationTreeUseCase;

    public Map<String, Object> execute(boolean addChildren, boolean addNhanSu) {
        LegacyOrganizationTreeResponse tree = getLegacyOrganizationTreeUseCase.execute(null, false, addNhanSu);
        List<LegacyOrganizationTreeNode> nodes = tree == null || tree.getData() == null ? List.of() : tree.getData();
        int maxDepth = addChildren ? Integer.MAX_VALUE : SHALLOW_DEPTH;
        List<Map<String, Object>> unit = new ArrayList<>();
        for (LegacyOrganizationTreeNode node : nodes) {
            if (!isUser(node)) {
                unit.add(toOrgNode(node, 1, maxDepth, addNhanSu));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unit", unit);
        return result;
    }

    private Map<String, Object> toOrgNode(LegacyOrganizationTreeNode node, int depth, int maxDepth, boolean addNhanSu) {
        Map<String, String> data = node.getData() == null ? Map.of() : node.getData();
        List<Map<String, Object>> users = new ArrayList<>();
        List<Map<String, Object>> organizations = new ArrayList<>();
        for (LegacyOrganizationTreeNode child : node.getChildren() == null
                ? List.<LegacyOrganizationTreeNode>of() : node.getChildren()) {
            if (isUser(child)) {
                if (addNhanSu) {
                    users.add(toUserNode(child));
                }
            } else if (depth < maxDepth) {
                organizations.add(toOrgNode(child, depth + 1, maxDepth, addNhanSu));
            }
        }
        List<Map<String, Object>> children = new ArrayList<>();
        if (!users.isEmpty()) {
            Map<String, Object> nhansu = new LinkedHashMap<>();
            nhansu.put("key", "NS_" + node.getId());
            nhansu.put("name", "Nhân sự");
            nhansu.put("form", "nhansu");
            nhansu.put("children", users);
            children.add(nhansu);
        }
        children.addAll(organizations);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", node.getId());
        result.put("id", node.getId());
        result.put("name", node.getText());
        result.put("form", node.getForm());
        result.put("identifier", data.getOrDefault("identifier", ""));
        result.put("mailin", data.getOrDefault("mailin", ""));
        result.put("receiver", data.getOrDefault("receiver", ""));
        result.put("user_receiver", data.getOrDefault("user_receiver", ""));
        if (!children.isEmpty()) {
            result.put("children", children);
        }
        return result;
    }

    private Map<String, Object> toUserNode(LegacyOrganizationTreeNode user) {
        Map<String, String> data = user.getData() == null ? Map.of() : user.getData();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", user.getId());
        result.put("id", user.getId());
        result.put("name", user.getText());
        result.put("form", "user");
        result.put("identifier", data.getOrDefault("identifier", ""));
        result.put("position", data.getOrDefault("position", ""));
        result.put("username", data.getOrDefault("username", ""));
        return result;
    }

    private boolean isUser(LegacyOrganizationTreeNode node) {
        return node != null && "user".equals(node.getForm());
    }
}
