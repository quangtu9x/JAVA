package com.td.application.phieutrinhtd;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documentlinks.InMemoryDocumentLinkRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.domain.documentlinks.DocumentLinkType;
import com.td.domain.phieutrinhtd.PhieuTrinhTdProcessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 06/10/2026 - cutover FE phiếu trình: tên view Domino thật -> tiêu chí (view_phieutrinh.dxl). */
@ExtendWith(MockitoExtension.class)
class PhieuTrinhTdViewQueryTest {

    @Mock
    private PhieuTrinhTdDocumentRepository documentRepository;
    @Mock
    private PhieuTrinhTdProcessDocumentRepository processRepository;
    @Mock
    private ListDocumentFilesUseCase listDocumentFilesUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final InMemoryDocumentLinkRepository linkRepository = new InMemoryDocumentLinkRepository();
    private PhieuTrinhTdViewQuery viewQuery;

    @BeforeEach
    void setUp() {
        DynamicDocumentFieldsService fields = new DynamicDocumentFieldsService();
        viewQuery = new PhieuTrinhTdViewQuery(documentRepository, processRepository,
            new PhieuTrinhTdFieldMapper(fields, listDocumentFilesUseCase),
            InMemoryDocumentLinkRepository.newService(linkRepository), currentUserProvider);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        lenient().when(documentRepository.findByCriteria(any(), any())).thenReturn(new PageImpl<>(List.of()));
        lenient().when(listDocumentFilesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 1, 20, 0L, 0, true, true));
    }

    private PhieuTrinhTdListCriteria run(String viewname, String... keys) {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname(viewname);
        request.setKeys(List.of(keys));
        viewQuery.execute(request);
        ArgumentCaptor<PhieuTrinhTdListCriteria> captor = ArgumentCaptor.forClass(PhieuTrinhTdListCriteria.class);
        verify(documentRepository).findByCriteria(captor.capture(), any());
        return captor.getValue();
    }

    @Test
    void creatorStatusViews_shouldResolveUsernameTokenAndExcludeStoraged() {
        var criteria = run("vwUser_Creator_5_25", "$username$");

        assertThat(criteria.getMember()).isEqualTo("nguyenvana");
        assertThat(criteria.getMemberRule()).isEqualTo(PhieuTrinhTdListCriteria.MemberRule.CREATOR);
        assertThat(criteria.getStatuses()).containsExactly("5", "25");
        assertThat(criteria.getStoraged()).isFalse();
        assertThat(criteria.isTrash()).isFalse();
    }

    @Test
    void vwUserAll_shouldUseParticipants_andLegacyJavaNameStillWorks() {
        assertThat(run("vwUser_All", "$username$").getMemberRule())
            .isEqualTo(PhieuTrinhTdListCriteria.MemberRule.THAM_GIA);
    }

    @Test
    void vwStoraged_shouldOnlyReturnFinished() {
        var criteria = run("vwStoraged", "$username$");
        assertThat(criteria.getStoraged()).isTrue();
        assertThat(criteria.getMemberRule()).isEqualTo(PhieuTrinhTdListCriteria.MemberRule.THAM_GIA);
    }

    @Test
    void vwTrash_shouldBeOwnTrashOnly() {
        var criteria = run("vwTrash", "$username$");
        assertThat(criteria.isTrash()).isTrue();
        assertThat(criteria.getMemberRule()).isEqualTo(PhieuTrinhTdListCriteria.MemberRule.CREATOR);
    }

    @Test
    void creatorCoordinatedStatus_secondKeySelectsResponseState() {
        assertThat(run("vwUser_Creator_Coordinated_Status", "$username$", "1").getCoordinatedStatus())
            .isEqualTo(PhieuTrinhTdListCriteria.CoordinatedStatus.DA_DU);
    }

    @Test
    void coordinatorViews_shouldMapChuaAndDa() {
        assertThat(run("vwUser_Coordinator_da", "$username$").getMemberRule())
            .isEqualTo(PhieuTrinhTdListCriteria.MemberRule.PHOI_HOP_DA);
    }

    @Test
    void documentProfilesId_shouldReadHoSoPhieuTrinhLinks() {
        UUID hosocv = UUID.randomUUID();
        UUID phieuTrinh = UUID.randomUUID();
        linkRepository.seed(DocumentLinkType.HOSO_PHIEUTRINH, hosocv, phieuTrinh);

        var criteria = run("vwDocument_Profiles_ID", hosocv.toString().toUpperCase());

        assertThat(criteria.getIds()).containsExactly(phieuTrinh);
        assertThat(criteria.getMemberRule()).isNull();
    }

    @Test
    void keywordWithoutColumns_shouldUseDefaultColumns() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("timkiem");
        request.setKeyword("ke hoach");
        viewQuery.execute(request);
        ArgumentCaptor<PhieuTrinhTdListCriteria> captor = ArgumentCaptor.forClass(PhieuTrinhTdListCriteria.class);
        verify(documentRepository).findByCriteria(captor.capture(), any());

        assertThat(captor.getValue().getKeywordColumns()).contains("title", "code");
        assertThat(captor.getValue().getStoraged()).isNull();
    }

    @Test
    void processViews_shouldFilterByForm() {
        UUID id = UUID.randomUUID();
        var quaTrinh = new PhieuTrinhTdProcessDocument("q").replaceDynamicFields("{\"form\":\"quatrinh\",\"action_type\":\"5\"}");
        var yKien = new PhieuTrinhTdProcessDocument("y")
            .replaceDynamicFields("{\"form\":\"document_coordinated_opinion\",\"subject\":\"Dong y\"}");
        when(processRepository.findByParentId(id)).thenReturn(List.of(quaTrinh, yKien));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCoordinated_Opinion_Submission_Dossier_ID");
        request.setKeys(List.of(id.toString().toUpperCase()));
        var result = viewQuery.execute(request);

        assertThat(result.getItems()).singleElement()
            .satisfies(item -> assertThat(item).containsEntry("subject", "Dong y"));
    }

    // Tra cứu nhóm (vwBy_*): 1 cấp, nhóm theo người tạo; ischild + navposition = phiếu trình trong nhóm.
    @Test
    void groupView_shouldListGroupsThenMembers() {
        var a = new com.td.domain.phieutrinhtd.PhieuTrinhTdDocument("PT A", "0", null)
            .replaceDynamicFields("{\"creator\":\"Tran B\"}");
        var b = new com.td.domain.phieutrinhtd.PhieuTrinhTdDocument("PT B", "0", null)
            .replaceDynamicFields("{\"creator\":\"Nguyen A\"}");
        var c = new com.td.domain.phieutrinhtd.PhieuTrinhTdDocument("PT C", "0", null)
            .replaceDynamicFields("{\"creator\":\"Nguyen A\"}");
        when(documentRepository.findByCriteria(any(), any())).thenReturn(new PageImpl<>(List.of(a, b, c)));

        ViewQueryRequest groups = new ViewQueryRequest();
        groups.setViewname("vwBy_Creator");
        groups.setKeys(List.of("$username$"));
        var rows = viewQuery.execute(groups).getItems();
        assertThat(rows).extracting(row -> row.get("name") + "=" + row.get("count"))
            .containsExactly("Nguyen A=2", "Tran B=1");

        ViewQueryRequest child = new ViewQueryRequest();
        child.setViewname("vwBy_Creator");
        child.setIschild(true);
        child.setNavposition("1");
        assertThat(viewQuery.execute(child).getItems()).extracting(item -> item.get("title"))
            .containsExactlyInAnyOrder("PT B", "PT C");
    }

    @Test
    void unknownView_shouldThrow() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwKhongTonTai");
        assertThatThrownBy(() -> viewQuery.execute(request)).isInstanceOf(IllegalArgumentException.class);
    }
}
