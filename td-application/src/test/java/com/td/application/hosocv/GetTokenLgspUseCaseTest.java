package com.td.application.hosocv;

import com.td.application.common.models.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G8: {@link GetTokenLgspUseCase} chỉ uỷ quyền cho
 * {@link LgspTokenClient} (Dependency Inversion — xem javadoc interface).
 */
class GetTokenLgspUseCaseTest {

    @Test
    void execute_shouldDelegateToLgspTokenClient() {
        LgspTokenClient client = mock(LgspTokenClient.class);
        when(client.fetchToken()).thenReturn(Result.success("abc-token"));
        var useCase = new GetTokenLgspUseCase(client);

        var result = useCase.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("abc-token");
    }

    @Test
    void execute_whenClientFails_shouldPropagateFailure() {
        LgspTokenClient client = mock(LgspTokenClient.class);
        when(client.fetchToken()).thenReturn(Result.failure("Tích hợp LGSP chưa được cấu hình"));
        var useCase = new GetTokenLgspUseCase(client);

        assertThat(useCase.execute().isSuccess()).isFalse();
    }
}
