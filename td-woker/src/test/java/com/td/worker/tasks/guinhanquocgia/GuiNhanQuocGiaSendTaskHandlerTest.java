package com.td.worker.tasks.guinhanquocgia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.UnitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test {@link GuiNhanQuocGiaSendTaskHandler} — bổ sung 09/09/2026. Mock toàn bộ collaborator
 * ({@link GuiNhanQuocGiaGatewayClient}/{@link GuiNhanQuocGiaSendLogStore}, cùng cách
 * {@code VbdiDeliveryTaskHandlerTest} mock {@code TenantDataSourceManager}) — không chạm DB thật,
 * chạy được không cần Docker.
 */
class GuiNhanQuocGiaSendTaskHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private GuiNhanQuocGiaProperties props;
    private GuiNhanQuocGiaGatewayClient gatewayClient;
    private GuiNhanQuocGiaSendLogStore sendLogStore;
    private GuiNhanQuocGiaSendTaskHandler handler;

    @BeforeEach
    void setUp() {
        props = new GuiNhanQuocGiaProperties();
        props.setEnabled(true);
        gatewayClient = mock(GuiNhanQuocGiaGatewayClient.class);
        sendLogStore = mock(GuiNhanQuocGiaSendLogStore.class);
        handler = new GuiNhanQuocGiaSendTaskHandler(props, gatewayClient, sendLogStore, objectMapper);
    }

    private ObjectNode basePayload() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("documentId", "doc-1");
        node.put("senderAgencyCode", "777.77.04.D77");
        node.put("recipientAgencyCode", "99.99.99.G21");
        node.put("docType", GuiNhanQuocGiaTaskConstants.DOC_TYPE_NEW);
        node.put("edxmlBase64", "dGVzdA==");
        return node;
    }

    private TaskContext context() {
        return new TaskContext(GuiNhanQuocGiaTaskConstants.TASK_SEND, "msg-1", "u1", "corr-1", "H05",
            null, Map.of(), 5, null, Map.of());
    }

    @Test
    void taskTypeMatchesConstant() {
        assertThat(handler.taskType()).isEqualTo("guinhanquocgia.send");
    }

    @Test
    void disabledTaskReturnsFailureWithoutTouchingCollaborators() {
        props.setEnabled(false);
        JsonNode payload = basePayload();
        UnitResult result = handler.execute(context(), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("enabled=false");
        verifyNoInteractions(gatewayClient, sendLogStore);
    }

    @Test
    void missingRequiredFieldReturnsFailure() {
        ObjectNode payload = basePayload();
        payload.remove("documentId");
        UnitResult result = handler.execute(context(), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("thiếu field bắt buộc");
        verifyNoInteractions(gatewayClient, sendLogStore);
    }

    @Test
    void alreadyClaimedReturnsSuccessWithoutCallingGateway() {
        when(sendLogStore.tryClaim(eq("u1"), eq("H05"), any())).thenReturn(false);
        UnitResult result = handler.execute(context(), basePayload());

        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(gatewayClient);
    }

    @Test
    void gatewaySuccessMarksSentAndReturnsSuccess() throws Exception {
        when(sendLogStore.tryClaim(eq("u1"), eq("H05"), any())).thenReturn(true);
        when(gatewayClient.sendEdoc(any())).thenReturn(GuiNhanQuocGiaSendOutcome.ok("center-doc-1"));

        UnitResult result = handler.execute(context(), basePayload());

        assertThat(result.isSuccess()).isTrue();
        verify(sendLogStore).markSent("u1", "center-doc-1");
        verify(sendLogStore, never()).markFailed(anyString(), anyString());
    }

    @Test
    void gatewayFailureMarksFailedAndReturnsFailure() throws Exception {
        when(sendLogStore.tryClaim(eq("u1"), eq("H05"), any())).thenReturn(true);
        when(gatewayClient.sendEdoc(any())).thenReturn(GuiNhanQuocGiaSendOutcome.failed("ERR01", "loi mau"));

        UnitResult result = handler.execute(context(), basePayload());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("ERR01");
        verify(sendLogStore).markFailed(eq("u1"), anyString());
    }

    @Test
    void gatewayThrowingMarksFailedAndReturnsFailure() throws Exception {
        when(sendLogStore.tryClaim(eq("u1"), eq("H05"), any())).thenReturn(true);
        when(gatewayClient.sendEdoc(any())).thenThrow(new RuntimeException("mang loi"));

        UnitResult result = handler.execute(context(), basePayload());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("mang loi");
        verify(sendLogStore).markFailed(eq("u1"), anyString());
    }
}
