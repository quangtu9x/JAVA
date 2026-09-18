package com.td.domain.processdiagram;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * R8 (tài liệu phân tích 03, bổ sung 22/08/2026) — sơ đồ xử lý 1 văn bản (tương đương
 * {@code ClassTreeSoDo}/document con {@code "sodoxuly"} trong {@code vbden_res} phía Domino):
 * cây JSON ghi lại chuỗi phân công/chuyển tiếp, dùng để FE vẽ sơ đồ trên tab "Quá trình xử lý"
 * (khác {@code QuaTrinhXuLy} — BL-210 — chỉ trả timeline PHẲNG, không đủ dựng cây cha-con).
 *
 * <p>1 văn bản ({@code documentId} + {@code documentType}) có ĐÚNG 1 sơ đồ xử lý (ràng buộc UNIQUE
 * ở migration) — khác {@code vbden_so} (1 văn bản có thể có nhiều bản ghi "sổ" theo từng tổ chức).
 * {@code diagramJson} lưu nguyên cây JSON (xem {@code com.td.application.processdiagram.
 * ProcessDiagramService} về cấu trúc node và các phép biến đổi cây).</p>
 */
@Entity
@Table(name = "process_diagrams")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessDiagram extends AuditableEntity<UUID> implements IAggregateRoot {

    private static final String EMPTY_ROOT_DIAGRAM_JSON = "{\"username\":\"root\",\"name\":\"root\",\"children\":[]}";

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "diagram_json", columnDefinition = "TEXT", nullable = false)
    private String diagramJson;

    public static ProcessDiagram createNew(UUID documentId, String documentType) {
        ProcessDiagram diagram = new ProcessDiagram();
        diagram.id = UUID.randomUUID();
        diagram.documentId = documentId;
        diagram.documentType = documentType;
        diagram.diagramJson = EMPTY_ROOT_DIAGRAM_JSON;
        return diagram;
    }

    public void updateDiagram(String newDiagramJson) {
        this.diagramJson = newDiagramJson;
    }
}
