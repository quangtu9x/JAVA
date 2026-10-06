package com.td.application.hosoluutru;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.hosocv.HoSoCVDocumentRepository;
import com.td.application.recordsstorage.RecordsStorageCatalogRepository;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.hosocv.HoSoCVDocument;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import com.td.domain.hosoluutru.HoSoLuuTruVanBanDocument;
import com.td.domain.recordsstorage.RecordsStorageCatalog;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Port module "Hồ sơ lưu trữ": {@link TaoHoSoLuuTruTuHoSoCVUseCase} — bước 1/3 cơ chế đồng bộ
 * {@code hosocv} → {@code hosoluutru} (xem docs/analysis/16-...md mục 5.1/5.6).
 */
@ExtendWith(MockitoExtension.class)
class TaoHoSoLuuTruTuHoSoCVUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private RecordsStorageCatalogRepository recordsStorageCatalogRepository;
    @Mock
    private HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    @Mock
    private HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final com.td.application.documentlinks.InMemoryDocumentLinkRepository linkRepository = new com.td.application.documentlinks.InMemoryDocumentLinkRepository();
    private TaoHoSoLuuTruTuHoSoCVUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TaoHoSoLuuTruTuHoSoCVUseCase(
            hoSoCVDocumentRepository, recordsStorageCatalogRepository, hoSoLuuTruDocumentRepository,
            hoSoLuuTruVanBanDocumentRepository, documentRepository, vbdiDocumentRepository,
            dynamicDocumentFieldsService, currentUserProvider, authorAclFieldsService,
            com.td.application.documentlinks.InMemoryDocumentLinkRepository.newService(linkRepository));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("khols01"));
        lenient().when(hoSoLuuTruDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(hoSoLuuTruVanBanDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void execute_withMissingHosocv_shouldReturnFailure() {
        UUID hosocvId = UUID.randomUUID();
        when(hoSoCVDocumentRepository.findById(hosocvId)).thenReturn(Optional.empty());

        var result = useCase.execute(request(hosocvId, UUID.randomUUID()));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withMissingCatalog_shouldReturnFailure() {
        HoSoCVDocument hosocv = newHosoCV(UUID.randomUUID(), "{}");
        when(hoSoCVDocumentRepository.findById(hosocv.getId())).thenReturn(Optional.of(hosocv));
        when(recordsStorageCatalogRepository.findByIdAndDeletedOnIsNull(any())).thenReturn(Optional.empty());

        var result = useCase.execute(request(hosocv.getId(), UUID.randomUUID()));

        assertThat(result.isSuccess()).isFalse();
        verify(hoSoLuuTruDocumentRepository, never()).save(any());
    }

    @Test
    void execute_withValidInput_shouldCreateHoSoLuuTruWithForm_ChoTiepNhan() {
        HoSoCVDocument hosocv = newHosoCV(UUID.randomUUID(), "{\"unit_creator\":\"Van phong\"}");
        RecordsStorageCatalog catalog = newCatalog();
        when(hoSoCVDocumentRepository.findById(hosocv.getId())).thenReturn(Optional.of(hosocv));
        when(recordsStorageCatalogRepository.findByIdAndDeletedOnIsNull(catalog.getId())).thenReturn(Optional.of(catalog));

        var result = useCase.execute(request(hosocv.getId(), catalog.getId()));

        assertThat(result.isSuccess()).isTrue();
        var captor = org.mockito.ArgumentCaptor.forClass(HoSoLuuTruDocument.class);
        verify(hoSoLuuTruDocumentRepository).save(captor.capture());
        HoSoLuuTruDocument saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(HoSoLuuTruLifecycleStatus.FORM_CHO_TIEP_NHAN);
        assertThat(saved.getDocumentType()).isEqualTo("hosoluutru");
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(saved.getDynamicFieldsJson());
        assertThat(fields.get("document_profiles_id")).isEqualTo(hosocv.getId().toString());
        assertThat(fields.get("category_id")).isEqualTo(catalog.getId().toString());
        assertThat(fields.get("chuyenluutru")).isEqualTo("0");
    }

    @Test
    void execute_withAttachedVbdenAndVbdi_shouldCopyVanBan() {
        UUID vbdenId = UUID.randomUUID();
        UUID vbdiId = UUID.randomUUID();
        HoSoCVDocument hosocv = newHosoCV(UUID.randomUUID(), "{}");
        // 06/10/2026: lien ket doc tu bang document_links.
        linkRepository.seed(com.td.domain.documentlinks.DocumentLinkType.HOSO_VBDEN, hosocv.getId(), vbdenId)
            .seed(com.td.domain.documentlinks.DocumentLinkType.HOSO_VBDI, hosocv.getId(), vbdiId);
        RecordsStorageCatalog catalog = newCatalog();
        when(hoSoCVDocumentRepository.findById(hosocv.getId())).thenReturn(Optional.of(hosocv));
        when(recordsStorageCatalogRepository.findByIdAndDeletedOnIsNull(catalog.getId())).thenReturn(Optional.of(catalog));

        BusinessDocument vbden = newVbden(vbdenId, "{\"subject\":\"Cong van test\",\"code\":\"123/CV\"}");
        when(documentRepository.findById(vbdenId)).thenReturn(Optional.of(vbden));
        VbdiDocument vbdi = newVbdi(vbdiId, "{\"code_full\":\"456/QD\"}");
        when(vbdiDocumentRepository.findById(vbdiId)).thenReturn(Optional.of(vbdi));

        var result = useCase.execute(request(hosocv.getId(), catalog.getId()));

        assertThat(result.isSuccess()).isTrue();
        verify(hoSoLuuTruVanBanDocumentRepository, times(2)).save(any());
    }

    private TaoHoSoLuuTruTuHoSoCVRequest request(UUID hosocvId, UUID dmhsId) {
        var request = new TaoHoSoLuuTruTuHoSoCVRequest();
        request.setHosocvId(hosocvId);
        request.setDmhsId(dmhsId);
        return request;
    }

    private HoSoCVDocument newHosoCV(UUID id, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Ho so test", "hosocv", "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private RecordsStorageCatalog newCatalog() {
        RecordsStorageCatalog catalog = new RecordsStorageCatalog(
            "Hanh chinh", "HC01", "Phong A", null, "De muc 1", null, "De muc con 1", null,
            "Vĩnh viễn", 2026, 1, null, 10, null, null, null, "tinh");
        ReflectionTestUtils.setField(catalog, "id", UUID.randomUUID());
        return catalog;
    }

    private BusinessDocument newVbden(UUID id, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban den test", "vbden", "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private VbdiDocument newVbdi(UUID id, String dynamicFieldsJson) {
        VbdiDocument document = new VbdiDocument("Van ban di test", "vbdi", "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
