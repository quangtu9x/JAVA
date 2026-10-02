package com.td.application.vbdi;

import com.td.application.common.models.PaginationResponse;
import com.td.domain.vbdi.VbdiEditHistoryDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 28/08/2026: đọc lịch sử hiệu chỉnh văn bản đi — item giữ đúng khuôn field
 * legacy ({id,parentid,form=log,form_edit,edit_obj,creator,user_creator,created}) để
 * VetHieuChinh.jsx render không đổi — mirror {@code ListDuThaoEditHistoryUseCaseTest}.
 */
@ExtendWith(MockitoExtension.class)
class ListVbdiEditHistoryUseCaseTest {

    @Mock
    private VbdiEditHistoryDocumentRepository repository;
    @Mock(strictness = Mock.Strictness.LENIENT)
    private com.td.application.sharedcore.AppUserRepository appUserRepository;

    private ListVbdiEditHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListVbdiEditHistoryUseCase(
            new com.td.application.sharedcore.UserFullNameResolver(appUserRepository), repository);
    }

    @Test
    void execute_shouldMapLegacyShape_andSortAscending() {
        UUID parentId = UUID.randomUUID();
        var e1 = new VbdiEditHistoryDocument(parentId, "vbdi",
            "{\"subject\":{\"oldvalue\":\"A\",\"newvalue\":\"B\"}}", null, "demo1");
        var e2 = new VbdiEditHistoryDocument(parentId, "vbdi",
            "{\"priority\":{\"oldvalue\":\"Thường\",\"newvalue\":\"Khẩn\"}}", "Nguyen Van A", "skhcn");

        when(repository.findByParentIdNewestFirst(eq(parentId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(e1, e2), PageRequest.of(0, 20), 2));

        var request = new ListVbdiEditHistoryRequest();
        request.setParentId(parentId);

        PaginationResponse<Map<String, Object>> response = useCase.execute(request);

        assertThat(response.getTotalItems()).isEqualTo(2);
        assertThat(response.getItems()).hasSize(2);
        Map<String, Object> first = response.getItems().get(0);
        assertThat(first.get("parentid")).isEqualTo(parentId.toString());
        assertThat(first.get("form")).isEqualTo("log");
        assertThat(first.get("edit_obj")).isEqualTo(e1.getEditObjJson());
        // creatorName trong -> roi ve user_creator (dung logic record.creator || record.user_creator)
        assertThat(first.get("creator")).isEqualTo("demo1");
        assertThat(first.get("user_creator")).isEqualTo("demo1");
        // creatorName co -> uu tien ten hien thi
        assertThat(response.getItems().get(1).get("creator")).isEqualTo("Nguyen Van A");
        assertThat(response.getItems().get(0).get("created")).asString().matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByParentIdNewestFirst(eq(parentId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void execute_shouldClampPageSizeAboveMax() {
        UUID parentId = UUID.randomUUID();
        when(repository.findByParentIdNewestFirst(eq(parentId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        var request = new ListVbdiEditHistoryRequest();
        request.setParentId(parentId);
        request.setPageNumber(3);
        request.setPageSize(9999);

        useCase.execute(request);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByParentIdNewestFirst(eq(parentId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2); // 3-1
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200); // MAX_PAGE_SIZE
    }
}
