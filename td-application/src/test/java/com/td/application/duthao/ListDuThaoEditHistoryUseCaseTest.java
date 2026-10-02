package com.td.application.duthao;

import com.td.application.common.models.PaginationResponse;
import com.td.domain.duthao.DuThaoEditHistoryDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 27/08/2026: đọc lịch sử hiệu chỉnh — item giữ đúng khuôn field legacy
 * ({id,parentid,form=log,form_edit,edit_obj,creator,user_creator,created}) để VetHieuChinh.jsx
 * render không đổi.
 */
@ExtendWith(MockitoExtension.class)
class ListDuThaoEditHistoryUseCaseTest {

    @Mock
    private DuThaoEditHistoryDocumentRepository repository;
    @Mock(strictness = Mock.Strictness.LENIENT)
    private com.td.application.sharedcore.AppUserRepository appUserRepository;

    private ListDuThaoEditHistoryUseCase useCase;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Khởi tạo trong BeforeEach (KHÔNG phải field initializer): MockitoExtension inject mock
        // SAU khi tạo thể hiện test nên initializer sẽ thấy null.
        useCase = new ListDuThaoEditHistoryUseCase(
            new com.td.application.sharedcore.UserFullNameResolver(appUserRepository), repository);
    }

    @Test
    void execute_shouldMapLegacyShape_andSortAscending() {
        UUID parentId = UUID.randomUUID();
        var e1 = new DuThaoEditHistoryDocument(parentId, "draft_document",
            "{\"subject\":{\"oldvalue\":\"A\",\"newvalue\":\"B\"}}", null, "demo1");
        var e2 = new DuThaoEditHistoryDocument(parentId, "draft_document",
            "{\"priority\":{\"oldvalue\":\"Thường\",\"newvalue\":\"Khẩn\"}}", "Nguyen Van A", "skhcn");

        when(repository.findByParentIdNewestFirst(org.mockito.ArgumentMatchers.eq(parentId),
            org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(e1, e2), PageRequest.of(0, 20), 2));

        var request = new ListDuThaoEditHistoryRequest();
        request.setParentId(parentId);

        PaginationResponse<Map<String, Object>> response = useCase.execute(request);

        assertThat(response.getTotalItems()).isEqualTo(2);
        assertThat(response.getItems()).hasSize(2);
        Map<String, Object> first = response.getItems().get(0);
        assertThat(first.get("parentid")).isEqualTo(parentId.toString());
        assertThat(first.get("form")).isEqualTo("log");
        assertThat(first.get("edit_obj")).isEqualTo(e1.getEditObjJson());
        // creatorName trống -> rơi về user_creator (đúng logic record.creator || record.user_creator)
        assertThat(first.get("creator")).isEqualTo("demo1");
        assertThat(first.get("user_creator")).isEqualTo("demo1");
        // creatorName có -> ưu tiên tên hiển thị
        assertThat(response.getItems().get(1).get("creator")).isEqualTo("Nguyen Van A");
        assertThat(response.getItems().get(0).get("created")).asString().matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByParentIdNewestFirst(org.mockito.ArgumentMatchers.eq(parentId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void execute_shouldClampPageSizeAboveMax() {
        UUID parentId = UUID.randomUUID();
        when(repository.findByParentIdNewestFirst(org.mockito.ArgumentMatchers.eq(parentId),
            org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        var request = new ListDuThaoEditHistoryRequest();
        request.setParentId(parentId);
        request.setPageNumber(3);
        request.setPageSize(9999);

        useCase.execute(request);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByParentIdNewestFirst(org.mockito.ArgumentMatchers.eq(parentId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2); // 3-1
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(ListDuThaoEditHistoryUseCaseTest.maxSize());
    }

    /** 25/09/2026: dòng vết thiếu tên (creator_name rỗng) được tra bù tên đầy đủ theo user_creator. */
    @Test
    void execute_shouldResolveFullName_whenStoredNameMissing() {
        UUID parentId = UUID.randomUUID();
        var e1 = new DuThaoEditHistoryDocument(parentId, "draft_document", "{}", null, "skhcn");
        var e2 = new DuThaoEditHistoryDocument(parentId, "draft_document", "{}", "skhcn", "skhcn");
        when(repository.findByParentIdNewestFirst(org.mockito.ArgumentMatchers.eq(parentId),
            org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(e1, e2), PageRequest.of(0, 20), 2));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn"))
            .thenReturn(java.util.Optional.of(new com.td.domain.sharedcore.AppUser(
                "kc", "skhcn", "Văn thư Sở KHCN", null, null, null, true)));

        var request = new ListDuThaoEditHistoryRequest();
        request.setParentId(parentId);
        var response = useCase.execute(request);

        assertThat(response.getItems()).extracting(item -> item.get("creator"))
            .containsExactly("Văn thư Sở KHCN", "Văn thư Sở KHCN");
        assertThat(response.getItems()).extracting(item -> item.get("user_creator"))
            .containsExactly("skhcn", "skhcn");
        // tra 1 lần cho cùng username trong 1 lượt đọc
        verify(appUserRepository, org.mockito.Mockito.times(1)).findByUsernameAndDeletedOnIsNull("skhcn");
    }

    private static int maxSize() {
        return 200; // MAX_PAGE_SIZE của use case — hard-code khớp để bắt regression
    }
}
