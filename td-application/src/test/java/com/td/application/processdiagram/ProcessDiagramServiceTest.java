package com.td.application.processdiagram;

import com.td.domain.processdiagram.ProcessDiagram;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** R8 (tài liệu phân tích 03) — {@link ProcessDiagramService}: port {@code ClassTreeSoDo} phía Domino. */
@ExtendWith(MockitoExtension.class)
class ProcessDiagramServiceTest {

    @Mock
    private ProcessDiagramRepository repository;

    @Test
    void getOrCreate_whenNotExists_shouldCreateAndSaveNewDiagram() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessDiagram result = service.getOrCreate(documentId, "vbden");

        assertThat(result.getDocumentId()).isEqualTo(documentId);
        assertThat(result.getDocumentType()).isEqualTo("vbden");
        assertThat(result.getDiagramJson()).contains("\"username\":\"root\"");
        verify(repository).save(any());
    }

    @Test
    void getOrCreate_whenExists_shouldReturnExistingWithoutCreatingNew() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        ProcessDiagram existing = ProcessDiagram.createNew(documentId, "vbden");
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.of(existing));

        ProcessDiagram result = service.getOrCreate(documentId, "vbden");

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void addNode_whenTargetIsRoot_shouldAttachAsDirectChildAndReturnTrue() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        ProcessDiagram diagram = ProcessDiagram.createNew(documentId, "vbden");
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.of(diagram));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiagramNodeRequest node = new DiagramNodeRequest();
        node.setUsername("nguyenvana");
        node.setName("Nguyễn Văn A");
        node.setUsergui("tranthib");

        boolean attached = service.addNode(documentId, "vbden", "root", node);

        assertThat(attached).isTrue();
        assertThat(diagram.getDiagramJson())
            .contains("\"username\":\"nguyenvana\"")
            .contains("\"name\":\"Nguyễn Văn A\"")
            .contains("\"usergui\":\"tranthib\"");
        verify(repository).save(diagram);
    }

    @Test
    void addNode_whenTargetNotFound_shouldFallbackToRootAndReturnFalse() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        ProcessDiagram diagram = ProcessDiagram.createNew(documentId, "vbden");
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.of(diagram));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiagramNodeRequest node = new DiagramNodeRequest();
        node.setUsername("nguyenvana");
        node.setName("Nguyễn Văn A");

        boolean attached = service.addNode(documentId, "vbden", "khongtontai", node);

        assertThat(attached).isFalse();
        assertThat(diagram.getDiagramJson()).contains("\"username\":\"nguyenvana\"");
    }

    @Test
    void addNode_secondCall_shouldNestUnderFirstAssigneeNotDuplicateRoot() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        ProcessDiagram diagram = ProcessDiagram.createNew(documentId, "vbden");
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.of(diagram));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiagramNodeRequest first = new DiagramNodeRequest();
        first.setUsername("truongphong");
        first.setName("Trưởng phòng");
        service.addNode(documentId, "vbden", "root", first);

        DiagramNodeRequest second = new DiagramNodeRequest();
        second.setUsername("nhanvien");
        second.setName("Nhân viên");
        boolean attached = service.addNode(documentId, "vbden", "truongphong", second);

        assertThat(attached).isTrue();
        // "nhanvien" phai nam TRONG children cua "truongphong", khong phai o cap root.
        int truongphongIndex = diagram.getDiagramJson().indexOf("\"truongphong\"");
        int nhanvienIndex = diagram.getDiagramJson().indexOf("\"nhanvien\"");
        assertThat(truongphongIndex).isGreaterThan(-1);
        assertThat(nhanvienIndex).isGreaterThan(truongphongIndex);
    }

    // Bo sung 03/09/2026 (bao loi nguoi dung: so do xu ly thieu han node dau tien "van thu" - xem
    // javadoc ensureNode).
    @Test
    void ensureNode_whenNotExists_shouldAddAsChildOfRoot() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        ProcessDiagram diagram = ProcessDiagram.createNew(documentId, "vbden");
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.of(diagram));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureNode(documentId, "vbden", "vanthu01", "Văn thư Cục", "Văn thư");

        assertThat(diagram.getDiagramJson())
            .contains("\"username\":\"vanthu01\"")
            .contains("\"name\":\"Văn thư Cục\"")
            .contains("\"chucvu\":\"Văn thư\"");
        verify(repository).save(diagram);
    }

    @Test
    void ensureNode_whenAlreadyExists_shouldNotDuplicate() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        ProcessDiagram diagram = ProcessDiagram.createNew(documentId, "vbden");
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.of(diagram));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.ensureNode(documentId, "vbden", "vanthu01", "Văn thư Cục", null);

        service.ensureNode(documentId, "vbden", "vanthu01", "Văn thư Cục", null);

        int count = diagram.getDiagramJson().split("vanthu01", -1).length - 1;
        assertThat(count).isEqualTo(1);
        verify(repository, org.mockito.Mockito.times(1)).save(diagram);
    }

    @Test
    void ensureNode_whenNodeExistsNestedDeeper_shouldStillDetectAndSkip() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        ProcessDiagram diagram = ProcessDiagram.createNew(documentId, "vbden");
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.of(diagram));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DiagramNodeRequest first = new DiagramNodeRequest();
        first.setUsername("vanthu01");
        first.setName("Văn thư Cục");
        service.addNode(documentId, "vbden", "root", first);

        service.ensureNode(documentId, "vbden", "vanthu01", "Ten khac", null);

        int count = diagram.getDiagramJson().split("Văn thư Cục", -1).length - 1;
        assertThat(count).isEqualTo(1);
        assertThat(diagram.getDiagramJson()).doesNotContain("Ten khac");
    }

    @Test
    void removeNode_whenMatchFound_shouldRemoveAndReturnDescription() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        ProcessDiagram diagram = ProcessDiagram.createNew(documentId, "vbden");
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.of(diagram));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DiagramNodeRequest node = new DiagramNodeRequest();
        node.setUsername("nguyenvana");
        node.setName("Nguyễn Văn A");
        service.addNode(documentId, "vbden", "root", node);

        List<String> deleted = service.removeNode(documentId, "vbden", "nguyenvana");

        assertThat(deleted).containsExactly("Nguyễn Văn A (nguyenvana)");
        assertThat(diagram.getDiagramJson()).doesNotContain("\"username\":\"nguyenvana\"");
    }

    @Test
    void removeNode_whenNoMatch_shouldReturnEmptyListWithoutError() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        ProcessDiagram diagram = ProcessDiagram.createNew(documentId, "vbden");
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.of(diagram));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<String> deleted = service.removeNode(documentId, "vbden", "khongtontai");

        assertThat(deleted).isEmpty();
    }

    @Test
    void getDiagramJson_whenNotExists_shouldReturnNull() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.empty());

        assertThat(service.getDiagramJson(documentId, "vbden")).isNull();
    }

    @Test
    void getDiagramJson_whenExists_shouldReturnStoredJson() {
        ProcessDiagramService service = new ProcessDiagramService(repository);
        UUID documentId = UUID.randomUUID();
        ProcessDiagram diagram = ProcessDiagram.createNew(documentId, "vbden");
        when(repository.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, "vbden"))
            .thenReturn(Optional.of(diagram));

        assertThat(service.getDiagramJson(documentId, "vbden")).isEqualTo(diagram.getDiagramJson());
    }
}
