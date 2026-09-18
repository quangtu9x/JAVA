package com.td.application.processdiagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.domain.processdiagram.ProcessDiagram;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * R8 (tài liệu phân tích 03, bổ sung 22/08/2026) — port {@code ClassTreeSoDo} phía Domino (cây JSON
 * sơ đồ xử lý, document con {@code "sodoxuly"} trong {@code vbden_res}) sang 1 bảng riêng
 * {@code process_diagrams} (xem javadoc {@link ProcessDiagram}).
 *
 * <p><b>5 operations gốc của {@code ClassTreeSoDo}</b> → tương ứng ở đây:</p>
 * <ul>
 *   <li>{@code getOrCreateSodoxuly} → {@link #getOrCreate}</li>
 *   <li>{@code readTreeFromDocument} + {@code addChildNodeByUsername} + save → {@link #addNode}</li>
 *   <li>{@code readTreeFromDocument} + xoá theo username + save → {@link #removeNode}</li>
 *   <li>đọc cây hiện tại (FE vẽ sơ đồ) → {@link #getDiagramJson}</li>
 * </ul>
 *
 * <p><b>Giới hạn có chủ đích</b> (ghi rõ để không hiểu nhầm là sót): node {@code name} (tên hiển
 * thị) hiện KHÔNG được tự tra cứu từ {@code username} — hệ thống chưa có 1 nguồn tra tên hiển thị
 * dùng chung ở tầng {@code td-application} cho vbden (tránh thêm phụ thuộc chéo sang module
 * AppUser lúc này) — caller ({@code ChuyenXuLyVbdenUseCase}) truyền {@code username} làm {@code name}
 * khi chưa có tên hiển thị, để dành cải thiện ở slice sau nếu cần đúng tên hiển thị trên sơ đồ.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProcessDiagramService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProcessDiagramRepository repository;

    /** Tương đương {@code ClassTreeSoDo.getOrCreateSodoxuly()}. */
    public ProcessDiagram getOrCreate(UUID documentId, String documentType) {
        return repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, documentType)
            .orElseGet(() -> repository.save(ProcessDiagram.createNew(documentId, documentType)));
    }

    /**
     * Tương đương {@code readTreeFromDocument} + {@code addChildNodeByUsername} + save. Thêm
     * {@code node} làm con của node có {@code username == targetUsername} trong cây hiện tại (DFS).
     * Nếu KHÔNG tìm thấy {@code targetUsername} trong cây (kể cả lần đầu — cây mới chỉ có node
     * {@code "root"}), thêm {@code node} làm con TRỰC TIẾP của root — không throw, không bỏ dữ liệu
     * (an toàn hơn Domino gốc vốn giả định luôn tìm thấy do luôn khởi tạo tuần tự theo đúng thứ tự
     * phân công, điều kiện không phải lúc nào cũng đúng khi cổng vào là 1 API độc lập).
     *
     * @return {@code true} nếu thêm được đúng vào vị trí {@code targetUsername} (không phải fallback root).
     */
    public boolean addNode(UUID documentId, String documentType, String targetUsername, DiagramNodeRequest node) {
        ProcessDiagram diagram = getOrCreate(documentId, documentType);
        ObjectNode root = parseTree(diagram.getDiagramJson());
        ObjectNode newNode = buildNode(node);
        boolean attachedToTarget = targetUsername != null && addChildByUsername(root, targetUsername, newNode);
        if (!attachedToTarget) {
            appendChild(root, newNode);
        }
        diagram.updateDiagram(toJson(root));
        repository.save(diagram);
        return attachedToTarget;
    }

    /**
     * Tương đương {@code readTreeFromDocument} + xoá node theo {@code username} + save. Xoá MỌI
     * node khớp {@code username} ở bất kỳ cấp nào trong cây (giữ lại cây con của node bị xoá —
     * KHÔNG đệ quy xoá cây con, khớp hành vi {@code removeNodeByNodeUserName} gốc: chỉ cắt đúng
     * node đó ra khỏi danh sách con của node cha, cây con của nó bị "mất gốc", không xoá tiếp).
     *
     * @return danh sách {@code "Tên (username)"} của các node đã xoá (để log/hiển thị), rỗng nếu
     * không tìm thấy.
     */
    public List<String> removeNode(UUID documentId, String documentType, String username) {
        ProcessDiagram diagram = getOrCreate(documentId, documentType);
        ObjectNode root = parseTree(diagram.getDiagramJson());
        List<String> deleted = new ArrayList<>();
        removeByUsername(root, username, deleted);
        diagram.updateDiagram(toJson(root));
        repository.save(diagram);
        return deleted;
    }

    /** Đọc cây hiện tại (cho FE render sơ đồ) — {@code null} nếu văn bản chưa có sơ đồ nào. */
    @Transactional(readOnly = true)
    public String getDiagramJson(UUID documentId, String documentType) {
        return repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, documentType)
            .map(ProcessDiagram::getDiagramJson)
            .orElse(null);
    }

    /**
     * Bo sung 03/09/2026 (bao loi nguoi dung: so do xu ly thieu han node dau tien - "van thu"/nguoi
     * dang thao tac): cay moi luon bat dau tu 1 root AN DANH ({@code username="root"}) - lan chuyen
     * xu ly DAU TIEN cua 1 van ban, {@code actingUsername} KHONG khop bat ky node nao trong cay (cay
     * con rong) nen {@link #addNode} roi xuong nhanh fallback, gan nguoi duoc giao THANG vao root -
     * ban than nguoi dang thao tac (vd van thu) khong bao gio tro thanh 1 node co ten rieng, mat het
     * ngu canh "ai giao viec cho ai". Goi truoc {@link #addNode} (trong CUNG action) de dam bao co 1
     * node dai dien {@code username}, CHI tao neu CHUA co (idempotent - khong tao trung neu nguoi nay
     * da xuat hien o 1 nhanh khac tu truoc) - cac addNode ngay sau do se gan dung vao node nay thay vi
     * roi xuong root, tao ra pham vi cha-con dung (vd van thu -> {chi dao, chu tri} la ANH EM cung
     * cha trong 1 lan chuyen xu ly, khong phai chuoi tuan tu rieng le).
     */
    public void ensureNode(UUID documentId, String documentType, String username, String name, String chucvu) {
        if (username == null || username.isBlank()) {
            return;
        }
        ProcessDiagram diagram = getOrCreate(documentId, documentType);
        ObjectNode root = parseTree(diagram.getDiagramJson());
        if (findByUsername(root, username) != null) {
            return;
        }
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("username", username);
        node.put("name", name != null && !name.isBlank() ? name : username);
        putIfNotBlank(node, "chucvu", chucvu);
        node.putArray("children");
        appendChild(root, node);
        diagram.updateDiagram(toJson(root));
        repository.save(diagram);
    }

    private ObjectNode findByUsername(ObjectNode node, String username) {
        if (node == null) {
            return null;
        }
        if (username.equals(node.path("username").asText())) {
            return node;
        }
        for (JsonNode candidate : childrenOf(node)) {
            if (candidate.isObject()) {
                ObjectNode found = findByUsername((ObjectNode) candidate, username);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // --- private helpers — port trực tiếp từ ClassTreeSoDo ---

    private ObjectNode buildNode(DiagramNodeRequest request) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("username", request.getUsername());
        node.put("name", request.getName());
        putIfNotBlank(node, "chucvu", request.getChucvu());
        putIfNotBlank(node, "identifier", request.getIdentifier());
        putIfNotBlank(node, "ngaygui", request.getNgaygui());
        putIfNotBlank(node, "quytrinhid", request.getQuytrinhid());
        putIfNotBlank(node, "noidung", request.getNoidung());
        putIfNotBlank(node, "hanxuly", request.getHanxuly());
        putIfNotBlank(node, "nguoidaidien", request.getNguoidaidien());
        putIfNotBlank(node, "usergui", request.getUsergui());
        putIfNotBlank(node, "nguoigui", request.getNguoigui());
        node.putArray("children");
        return node;
    }

    private void putIfNotBlank(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private boolean addChildByUsername(ObjectNode root, String targetUsername, ObjectNode child) {
        if (root == null || targetUsername == null) {
            return false;
        }
        if (targetUsername.equals(root.path("username").asText())) {
            appendChild(root, child);
            return true;
        }
        for (JsonNode candidate : childrenOf(root)) {
            if (candidate.isObject() && addChildByUsername((ObjectNode) candidate, targetUsername, child)) {
                return true;
            }
        }
        return false;
    }

    private void appendChild(ObjectNode parent, ObjectNode child) {
        ArrayNode children = childrenArray(parent);
        children.add(child);
    }

    private ArrayNode childrenArray(ObjectNode node) {
        JsonNode existing = node.get("children");
        if (existing != null && existing.isArray()) {
            return (ArrayNode) existing;
        }
        return node.putArray("children");
    }

    private ArrayNode childrenOf(ObjectNode node) {
        JsonNode existing = node.get("children");
        return existing != null && existing.isArray() ? (ArrayNode) existing : OBJECT_MAPPER.createArrayNode();
    }

    private void removeByUsername(ObjectNode node, String username, List<String> deleted) {
        JsonNode existing = node.get("children");
        if (existing == null || !existing.isArray()) {
            return;
        }
        ArrayNode children = (ArrayNode) existing;
        for (int index = children.size() - 1; index >= 0; index--) {
            JsonNode candidate = children.get(index);
            if (!candidate.isObject()) {
                continue;
            }
            ObjectNode candidateObject = (ObjectNode) candidate;
            if (username.equals(candidateObject.path("username").asText())) {
                deleted.add(candidateObject.path("name").asText("") + " (" + candidateObject.path("username").asText("") + ")");
                children.remove(index);
            }
        }
        for (JsonNode remaining : children) {
            if (remaining.isObject()) {
                removeByUsername((ObjectNode) remaining, username, deleted);
            }
        }
    }

    private ObjectNode parseTree(String json) {
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(json);
            if (parsed instanceof ObjectNode objectNode) {
                return objectNode;
            }
        } catch (Exception ignored) {
            // roi xuong nhanh tra cay rong ben duoi - khong throw, tranh vo dong bo do du lieu hong.
        }
        ObjectNode fallback = OBJECT_MAPPER.createObjectNode();
        fallback.put("username", "root");
        fallback.put("name", "root");
        fallback.putArray("children");
        return fallback;
    }

    private String toJson(ObjectNode tree) {
        try {
            return OBJECT_MAPPER.writeValueAsString(tree);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
