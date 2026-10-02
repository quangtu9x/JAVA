package com.td.application.vbden;

import com.td.domain.vbden.VbdenUpdateHistoryDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Yêu cầu người dùng 24/09/2026 — xem javadoc {@link ListVbdenUpdateHistoryUseCase}. */
@ExtendWith(MockitoExtension.class)
class ListVbdenUpdateHistoryUseCaseTest {

    @Mock
    private VbdenUpdateHistoryDocumentRepository repository;

    @InjectMocks
    private ListVbdenUpdateHistoryUseCase useCase;

    @Test
    void execute_shouldReturnParsedUpdateObjAndCreatorFallback() {
        UUID parentId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        var history = new VbdenUpdateHistoryDocument(parentId, sourceId, VbdenUpdateHistoryDocument.ACTION_CAP_NHAT,
            "{\"superseded_files\":[{\"name\":\"cu.pdf\",\"size\":10}]}", null, "vanthu01");
        when(repository.findByParentIdDesc(eq(parentId), any())).thenReturn(new PageImpl<>(List.of(history)));

        var request = new ListVbdenUpdateHistoryRequest();
        request.setParentId(parentId);
        var result = useCase.execute(request);

        assertThat(result.getItems()).hasSize(1);
        Map<String, Object> item = result.getItems().get(0);
        assertThat(item.get("source_document_id")).isEqualTo(sourceId);
        assertThat(item.get("creator")).isEqualTo("vanthu01");
        assertThat(item.get("update_obj")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        var superseded = (List<Map<String, Object>>) ((Map<String, Object>) item.get("update_obj")).get("superseded_files");
        assertThat(superseded.get(0).get("name")).isEqualTo("cu.pdf");
    }

    @Test
    void execute_withoutParentId_shouldReturnEmpty() {
        var result = useCase.execute(new ListVbdenUpdateHistoryRequest());

        assertThat(result.getItems()).isEmpty();
        verifyNoInteractions(repository);
    }
}
