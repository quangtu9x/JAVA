package com.td.application.vbden;

import com.td.application.common.models.PaginationResponse;
import com.td.application.sharedcore.GetLegacyOrganizationTreeUseCase;
import com.td.application.sharedcore.LegacyOrganizationTreeNode;
import com.td.application.sharedcore.LegacyOrganizationTreeResponse;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 10/10/2026 (báo lỗi skhcn "Báo cáo tổng hợp theo đơn vị - cá nhân không ra dữ liệu"): màn
 * {@code vbden/baocaoinso/baocaotheophong} trước đây gọi 2 API Domino — cây đơn vị
 * ({@code qlvb/vbcddh/danhsachdonvithuchien?addchildren=true&addnhansu=true}) và danh sách văn bản
 * ({@code qlvb/vbden/TimKiemVanBanDen}, pageSize 10000) — tenant đã chuyển Java không có dữ liệu
 * Domino nên bảng luôn trống. Endpoint này trả CẢ HAI trong 1 lần gọi (FE trước đây còn bị chạy đua:
 * danh sách về trước cây thì bảng trống):
 * <ul>
 *   <li>{@code unit}: cây tổ chức (đã cắt theo cơ quan của người dùng —
 *   {@link GetLegacyOrganizationTreeUseCase}) đổi sang ĐÚNG shape Domino
 *   {@code DanhSachDonViThucHien.java}: node tổ chức {@code {key,name,form,identifier,children}}, nhân
 *   sự trực thuộc gom vào node con {@code {key:"NS_<id>", name:"Nhân sự", form:"nhansu"}} chứa các node
 *   {@code form:"user"} có {@code username}/{@code position} — FE {@code BaoCaoTheoPhong.jsx#cleanData}
 *   đọc đúng shape này, giữ nguyên toàn bộ logic tổng hợp phía FE.</li>
 *   <li>{@code data}: văn bản đến trong phạm vi tổ chức — tái dùng view {@code toanbovanban} của
 *   {@link ViewQueryService} (cùng khoá tổ chức, lọc Ngày đến 3 dạng, loại VB thu hồi, trường
 *   {@code files}) — lặp trang 200 phía server tới {@link #MAX_DOCUMENTS} (bằng pageSize 10000 cũ).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BaoCaoTheoPhongVbdenUseCase {

    static final int MAX_DOCUMENTS = 10000;
    private static final int PAGE_SIZE = 200;
    private static final String DB_TYPE = "vbden";
    private static final String VIEW_TOANBOVANBAN = "toanbovanban";

    private final GetLegacyOrganizationTreeUseCase getLegacyOrganizationTreeUseCase;
    private final ViewQueryService viewQueryService;

    public Map<String, Object> execute(BaoCaoTheoPhongVbdenRequest request) {
        BaoCaoTheoPhongVbdenRequest safeRequest = request == null ? new BaoCaoTheoPhongVbdenRequest() : request;
        List<Map<String, Object>> documents = new ArrayList<>();
        boolean truncated = loadDocuments(safeRequest, documents);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unit", buildUnitTree(safeRequest.getRootId()));
        result.put("data", documents);
        result.put("total", documents.size());
        result.put("truncated", truncated);
        return result;
    }

    private boolean loadDocuments(BaoCaoTheoPhongVbdenRequest request, List<Map<String, Object>> sink) {
        int pageNumber = 1;
        while (sink.size() < MAX_DOCUMENTS) {
            ViewQueryRequest viewRequest = new ViewQueryRequest();
            viewRequest.setViewname(VIEW_TOANBOVANBAN);
            viewRequest.setItems("getall");
            viewRequest.setPageNumber(pageNumber);
            viewRequest.setPageSize(PAGE_SIZE);
            viewRequest.setKeyword(request.getKeyword());
            if (request.getSearch() != null) {
                viewRequest.setSearch(new LinkedHashMap<>(request.getSearch()));
            }
            PaginationResponse<Map<String, Object>> page = viewQueryService.execute(DB_TYPE, viewRequest);
            List<Map<String, Object>> items = page.getItems() == null ? List.of() : page.getItems();
            for (Map<String, Object> item : items) {
                if (sink.size() >= MAX_DOCUMENTS) {
                    return true;
                }
                sink.add(item);
            }
            if (page.isLast() || items.isEmpty()) {
                return false;
            }
            pageNumber++;
        }
        return true;
    }

    private List<Map<String, Object>> buildUnitTree(String rootId) {
        LegacyOrganizationTreeResponse treeResponse = getLegacyOrganizationTreeUseCase.execute(null, false, true);
        List<LegacyOrganizationTreeNode> nodes = treeResponse == null || treeResponse.getData() == null
            ? List.of()
            : treeResponse.getData();
        if (rootId != null && !rootId.isBlank()) {
            LegacyOrganizationTreeNode root = findById(nodes, rootId.trim());
            if (root != null) {
                nodes = List.of(root);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (LegacyOrganizationTreeNode node : nodes) {
            if (!isUser(node)) {
                result.add(toDominoNode(node));
            }
        }
        return result;
    }

    private LegacyOrganizationTreeNode findById(List<LegacyOrganizationTreeNode> nodes, String id) {
        if (nodes == null) {
            return null;
        }
        for (LegacyOrganizationTreeNode node : nodes) {
            if (isUser(node)) {
                continue;
            }
            if (id.equalsIgnoreCase(node.getId())) {
                return node;
            }
            LegacyOrganizationTreeNode found = findById(node.getChildren(), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Map<String, Object> toDominoNode(LegacyOrganizationTreeNode node) {
        Map<String, String> data = node.getData() == null ? Map.of() : node.getData();
        List<Map<String, Object>> users = new ArrayList<>();
        List<Map<String, Object>> organizations = new ArrayList<>();
        for (LegacyOrganizationTreeNode child : node.getChildren() == null ? List.<LegacyOrganizationTreeNode>of() : node.getChildren()) {
            if (isUser(child)) {
                users.add(toUserNode(child));
            } else {
                organizations.add(toDominoNode(child));
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
        result.put("name", node.getText());
        result.put("form", node.getForm());
        result.put("identifier", data.getOrDefault("identifier", ""));
        result.put("children", children);
        return result;
    }

    private Map<String, Object> toUserNode(LegacyOrganizationTreeNode user) {
        Map<String, String> data = user.getData() == null ? Map.of() : user.getData();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", user.getId());
        result.put("name", user.getText());
        result.put("form", "user");
        result.put("position", data.getOrDefault("position", ""));
        result.put("username", data.getOrDefault("username", ""));
        return result;
    }

    private boolean isUser(LegacyOrganizationTreeNode node) {
        return node != null && "user".equals(node.getForm());
    }
}
