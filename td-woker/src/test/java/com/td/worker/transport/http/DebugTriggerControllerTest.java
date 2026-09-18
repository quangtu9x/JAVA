package com.td.worker.transport.http;

import com.td.worker.transport.InboundMessageRouter;
import com.td.worker.transport.RoutedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test {@link DebugTriggerController} — chỉ kiểm tra mapping {@link RoutedResult} -> HTTP status/
 * body (KHÔNG chạm {@link InboundMessageRouter} thật, mock hoàn toàn — logic dispatch/checkpoint
 * thật đã có test riêng ở {@code core.flow}/{@code core.dispatch}).
 */
class DebugTriggerControllerTest {

    private InboundMessageRouter router;
    private DebugTriggerController controller;

    @BeforeEach
    void setUp() {
        router = mock(InboundMessageRouter.class);
        controller = new DebugTriggerController(router);
    }

    @Test
    void flowOkReturns200WithCanAck() {
        when(router.route(anyString())).thenReturn(RoutedResult.flow(true));

        ResponseEntity<DebugTriggerController.DebugPublishResponse> response = controller.publish("{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().type()).isEqualTo("FLOW_OK");
        assertThat(response.getBody().canAck()).isTrue();
        verify(router).route("{}");
    }

    @Test
    void parseErrorReturns400() {
        when(router.route(anyString())).thenReturn(RoutedResult.parseError("thieu messageId"));

        ResponseEntity<DebugTriggerController.DebugPublishResponse> response = controller.publish("not-json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().type()).isEqualTo("PARSE_ERROR");
        assertThat(response.getBody().detail()).isEqualTo("thieu messageId");
    }

    @Test
    void transientErrorReturns503() {
        when(router.route(anyString())).thenReturn(RoutedResult.transientError("checkpoint DB down"));

        ResponseEntity<DebugTriggerController.DebugPublishResponse> response = controller.publish("{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().detail()).isEqualTo("checkpoint DB down");
    }
}
