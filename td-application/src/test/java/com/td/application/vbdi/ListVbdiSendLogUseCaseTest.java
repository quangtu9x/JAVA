package com.td.application.vbdi;

import com.td.application.common.models.PaginationResponse;
import com.td.domain.vbdi.VbdiSendLogDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 28/08/2026: đọc lịch sử Nhật ký gửi nhận theo văn bản đi cha.
 */
@ExtendWith(MockitoExtension.class)
class ListVbdiSendLogUseCaseTest {

    @Mock
    private VbdiSendLogDocumentRepository repository;
    @Mock
    private com.td.application.sharedcore.AppUserRepository appUserRepository;

    private ListVbdiSendLogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListVbdiSendLogUseCase(repository,
            new com.td.application.sharedcore.UserFullNameResolver(appUserRepository));
    }

    @Test
    void execute_shouldMapFields() {
        UUID parentId = UUID.randomUUID();
        var entry = new VbdiSendLogDocument(parentId, VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.COORDINATOR, "hoangnt.skhcn", "Nong Thanh Hoang", null,
            "Gửi văn bản", "y kien gui kem", "xuanlt.skhcn");

        when(repository.findByParentId(eq(parentId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        var request = new ListVbdiSendLogRequest();
        request.setDocumentId(parentId);

        PaginationResponse<Map<String, Object>> response = useCase.execute(request);

        assertThat(response.getTotalItems()).isEqualTo(1);
        Map<String, Object> item = response.getItems().get(0);
        assertThat(item.get("parentid")).isEqualTo(parentId.toString());
        assertThat(item.get("send_type")).isEqualTo(VbdiSendType.SEND_DOCUMENT);
        assertThat(item.get("recipient_group")).isEqualTo(VbdiSendRecipientGroup.COORDINATOR);
        assertThat(item.get("recipient_identifier")).isEqualTo("hoangnt.skhcn");
        assertThat(item.get("status")).isEqualTo("Gửi văn bản");
        assertThat(item.get("content")).isEqualTo("y kien gui kem");
        // Khong tra duoc ho so -> giu gia tri da luu; username gốc luon co o field an.
        assertThat(item.get("user_creator")).isEqualTo("xuanlt.skhcn");
        assertThat(item.get("user_creator_username")).isEqualTo("xuanlt.skhcn");
        assertThat(item.get("created")).asString().matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
    }

    // 28/09/2026: dong luu tai khoan -> hien TEN DAY DU; dong cu da luu ten/"He thong tiep nhan" giu nguyen.
    @Test
    void execute_shouldResolveFullNameAndKeepUsernameHidden() {
        UUID parentId = UUID.randomUUID();
        var byUsername = new VbdiSendLogDocument(parentId, VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.CONTACT_UNIT, "H05.02", "So NN", null, "Gửi văn bản", null, "skhcn");
        var bySystem = new VbdiSendLogDocument(parentId, "phan_hoi_trang_thai",
            VbdiSendRecipientGroup.CONTACT_UNIT, "H05.02", "So NN", null, "Đã đến", null, "Hệ thống tiếp nhận");
        var appUser = org.mockito.Mockito.mock(com.td.domain.sharedcore.AppUser.class);
        when(appUser.getFullName()).thenReturn("Văn thư Sở KHCN");
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn")).thenReturn(java.util.Optional.of(appUser));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("Hệ thống tiếp nhận")).thenReturn(java.util.Optional.empty());
        when(repository.findByParentId(eq(parentId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(byUsername, bySystem), PageRequest.of(0, 20), 2));

        var request = new ListVbdiSendLogRequest();
        request.setDocumentId(parentId);
        var items = useCase.execute(request).getItems();

        assertThat(items.get(0)).containsEntry("user_creator", "Văn thư Sở KHCN").containsEntry("user_creator_username", "skhcn");
        assertThat(items.get(1)).containsEntry("user_creator", "Hệ thống tiếp nhận");
    }

    @Test
    void execute_whenDocumentIdMissing_shouldReturnEmptyWithoutQuerying() {
        var request = new ListVbdiSendLogRequest();

        var response = useCase.execute(request);

        assertThat(response.getTotalItems()).isZero();
        org.mockito.Mockito.verifyNoInteractions(repository);
    }
}
