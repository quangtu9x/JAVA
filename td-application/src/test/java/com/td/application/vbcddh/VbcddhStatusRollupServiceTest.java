package com.td.application.vbcddh;

import com.td.domain.vbcddh.VbcddhDocument;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-250 (VBCDDH Core) — tương đương {@code CapNhatTinhTrangVBCD.java} thật (branch {@code form ==
 * "nhiemvu"}): tính lại {@code tinhtrang} tổng hợp + gộp field người thực hiện/phối hợp trên văn
 * bản chỉ đạo gốc từ toàn bộ nhiệm vụ con.
 */
@ExtendWith(MockitoExtension.class)
class VbcddhStatusRollupServiceTest {

    @Mock
    private VbcddhDocumentRepository vbcddhDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private VbcddhStatusRollupService service;

    @BeforeEach
    void setUp() {
        service = new VbcddhStatusRollupService(vbcddhDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService);
    }

    @Test
    void recomputeParentStatus_withUnknownParent_shouldDoNothing() {
        UUID idVanBan = UUID.randomUUID();
        when(vbcddhDocumentRepository.findById(idVanBan)).thenReturn(Optional.empty());

        service.recomputeParentStatus(idVanBan);

        verify(vbcddhDocumentRepository, never()).save(any());
    }

    @Test
    void recomputeParentStatus_withNoSiblings_shouldDoNothing() {
        VbcddhDocument parent = newDocument("vbcddh", "{}");
        when(vbcddhDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        stubSiblings(parent.getId(), List.of());

        service.recomputeParentStatus(parent.getId());

        verify(vbcddhDocumentRepository, never()).save(any());
    }

    @Test
    void recomputeParentStatus_whenAllSiblingsChuaThucHien_shouldSetChuaThucHien() {
        VbcddhDocument parent = newDocument("vbcddh", "{}");
        when(vbcddhDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(vbcddhDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VbcddhDocument sibling = newDocument("vbcddh",
            "{\"form\":\"nhiemvu\",\"idvanban\":\"" + parent.getId() + "\",\"tinhtrang\":\"Chưa thực hiện\","
                + "\"donvithuchienhide\":\"donvi1\"}");
        stubSiblings(parent.getId(), List.of(sibling));

        service.recomputeParentStatus(parent.getId());

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated).containsEntry("tinhtrang", "Chưa thực hiện");
        assertThat(updated).containsEntry("donvithuchienhide", "donvi1");
    }

    @Test
    void recomputeParentStatus_whenAllSiblingsDaThucHien_shouldSetDaHoanThanh() {
        VbcddhDocument parent = newDocument("vbcddh", "{\"tinhtrang\":\"Chưa thực hiện\"}");
        when(vbcddhDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(vbcddhDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VbcddhDocument sibling1 = newDocument("vbcddh",
            "{\"form\":\"nhiemvu\",\"idvanban\":\"" + parent.getId() + "\",\"tinhtrang\":\"Đã thực hiện\"}");
        VbcddhDocument sibling2 = newDocument("vbcddh",
            "{\"form\":\"nhiemvu\",\"idvanban\":\"" + parent.getId() + "\",\"tinhtrang\":\"Đã thực hiện\"}");
        stubSiblings(parent.getId(), List.of(sibling1, sibling2));

        service.recomputeParentStatus(parent.getId());

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated).containsEntry("tinhtrang", "Đã hoàn thành");
        verify(documentLogService).logUpdate(any(), any(), any());
        verify(documentCacheService).evict(parent.getId());
    }

    @Test
    void recomputeParentStatus_whenMixOfChuaAndDangAndDa_shouldSetDangThucHien() {
        VbcddhDocument parent = newDocument("vbcddh", "{}");
        when(vbcddhDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(vbcddhDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VbcddhDocument sibling1 = newDocument("vbcddh",
            "{\"form\":\"nhiemvu\",\"idvanban\":\"" + parent.getId() + "\",\"tinhtrang\":\"Chưa thực hiện\"}");
        VbcddhDocument sibling2 = newDocument("vbcddh",
            "{\"form\":\"nhiemvu\",\"idvanban\":\"" + parent.getId() + "\",\"tinhtrang\":\"Đã thực hiện\"}");
        stubSiblings(parent.getId(), List.of(sibling1, sibling2));

        service.recomputeParentStatus(parent.getId());

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated).containsEntry("tinhtrang", "Đang thực hiện");
    }

    @Test
    void recomputeParentStatus_shouldAggregateDonViFieldsFromAllSiblings() {
        VbcddhDocument parent = newDocument("vbcddh", "{}");
        when(vbcddhDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(vbcddhDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VbcddhDocument sibling1 = newDocument("vbcddh",
            "{\"form\":\"nhiemvu\",\"idvanban\":\"" + parent.getId() + "\",\"tinhtrang\":\"Chưa thực hiện\","
                + "\"donvithuchienhide\":\"donvi1\"}");
        VbcddhDocument sibling2 = newDocument("vbcddh",
            "{\"form\":\"nhiemvu\",\"idvanban\":\"" + parent.getId() + "\",\"tinhtrang\":\"Chưa thực hiện\","
                + "\"donvithuchienhide\":\"donvi2\"}");
        stubSiblings(parent.getId(), List.of(sibling1, sibling2));

        service.recomputeParentStatus(parent.getId());

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("donvithuchienhide")).isEqualTo("donvi1;donvi2");
    }

    private void stubSiblings(UUID idVanBan, List<VbcddhDocument> siblings) {
        Page<VbcddhDocument> page = new PageImpl<>(siblings);
        when(vbcddhDocumentRepository.findByFieldValue(eq("vbcddh"), eq("idvanban"), eq(idVanBan.toString()), any()))
            .thenReturn(page);
    }

    private VbcddhDocument newDocument(String documentType, String dynamicFieldsJson) {
        VbcddhDocument document = new VbcddhDocument("Van ban chi dao", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
