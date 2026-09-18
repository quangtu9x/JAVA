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

    private ListVbdiSendLogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListVbdiSendLogUseCase(repository);
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
        assertThat(item.get("user_creator")).isEqualTo("xuanlt.skhcn");
        assertThat(item.get("created")).asString().matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void execute_whenDocumentIdMissing_shouldReturnEmptyWithoutQuerying() {
        var request = new ListVbdiSendLogRequest();

        var response = useCase.execute(request);

        assertThat(response.getTotalItems()).isZero();
        org.mockito.Mockito.verifyNoInteractions(repository);
    }
}
