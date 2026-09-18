package com.td.application.numbering;

import com.td.domain.numbering.NumberingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BL-104 — Definition of Done (4): API cấu hình format hoạt động đúng.
 */
@ExtendWith(MockitoExtension.class)
class UpdateNumberingConfigUseCaseTest {

    @Mock
    private NumberingConfigRepository numberingConfigRepository;

    @Test
    void execute_shouldUpdateFormatAndActive() {
        var useCase = new UpdateNumberingConfigUseCase(numberingConfigRepository);
        NumberingConfig existing = new NumberingConfig("vbden", "{seq}/{year}", "YEARLY");
        when(numberingConfigRepository.findByDocumentType("vbden")).thenReturn(Optional.of(existing));
        when(numberingConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UpdateNumberingConfigRequest();
        request.setFormat("{seq:05d}/{year}");
        request.setActive(false);

        var result = useCase.execute("vbden", request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getFormat()).isEqualTo("{seq:05d}/{year}");
        assertThat(result.getData().isActive()).isFalse();
    }

    @Test
    void execute_withUnknownDocumentType_shouldFail() {
        var useCase = new UpdateNumberingConfigUseCase(numberingConfigRepository);
        when(numberingConfigRepository.findByDocumentType("khongtontai")).thenReturn(Optional.empty());

        var request = new UpdateNumberingConfigRequest();
        request.setFormat("{seq}/{year}");

        var result = useCase.execute("khongtontai", request);

        assertThat(result.isSuccess()).isFalse();
    }
}
