package com.td.worker.tasks.simpki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.UnitResult;
import com.td.worker.transport.rabbit.RabbitPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Base64;
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
 * Test {@link SimPkiSignTaskHandler} — mock toàn bộ collaborator ({@link SimPkiGatewayClient}/
 * {@link SimPkiSignLogStore}, cùng cách {@code GuiNhanQuocGiaSendTaskHandlerTest}/
 * {@code VbdiDeliveryTaskHandlerTest} mock collaborator) — không chạm DB/SDK MSSP thật, chạy được
 * không cần Docker/mạng.
 */
class SimPkiSignTaskHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private SimPkiProperties props;
    private WorkerProperties workerProps;
    private SimPkiGatewayClient gatewayClient;
    private SimPkiSignLogStore signLogStore;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RabbitPublisher> rabbitPublisher = mock(ObjectProvider.class);
    private SimPkiSignTaskHandler handler;

    @BeforeEach
    void setUp() {
        props = new SimPkiProperties();
        props.setEnabled(true);
        workerProps = new WorkerProperties();
        workerProps.getRabbit().setEnabled(false);
        gatewayClient = mock(SimPkiGatewayClient.class);
        signLogStore = mock(SimPkiSignLogStore.class);
        handler = new SimPkiSignTaskHandler(props, workerProps, gatewayClient, signLogStore, objectMapper, rabbitPublisher);
    }

    private ObjectNode basePayload() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("documentId", "doc-1");
        node.put("fileId", "file-1");
        node.put("msisdn", "84972495193");
        node.put("hashBase64", Base64.getEncoder().encodeToString("noi dung mau".getBytes()));
        return node;
    }

    private TaskContext context() {
        return new TaskContext(SimPkiTaskConstants.TASK_SIGN, "msg-1", "u1", "corr-1", "H05",
            null, Map.of(), 5, null, Map.of());
    }

    @Test
    void taskTypeMatchesConstant() {
        assertThat(handler.taskType()).isEqualTo("simpki.sign");
    }

    @Test
    void disabledTaskReturnsFailureWithoutTouchingCollaborators() {
        props.setEnabled(false);
        JsonNode payload = basePayload();
        UnitResult result = handler.execute(context(), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("enabled=false");
        verifyNoInteractions(gatewayClient, signLogStore);
    }

    @Test
    void missingRequiredFieldReturnsFailure() {
        ObjectNode payload = basePayload();
        payload.remove("msisdn");
        UnitResult result = handler.execute(context(), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("thiếu field bắt buộc");
        verifyNoInteractions(gatewayClient, signLogStore);
    }

    @Test
    void alreadyClaimedReturnsSuccessWithoutCallingGateway() {
        when(signLogStore.tryClaim(eq("u1"), eq("H05"), any())).thenReturn(false);
        UnitResult result = handler.execute(context(), basePayload());

        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(gatewayClient);
    }

    @Test
    void gatewaySuccessMarksSignedAndReturnsSuccess() throws Exception {
        when(signLogStore.tryClaim(eq("u1"), eq("H05"), any())).thenReturn(true);
        when(gatewayClient.sign(any())).thenReturn(SimPkiSignOutcome.ok("c2lnbmF0dXJl", "trans-1", "1234", 500L));

        UnitResult result = handler.execute(context(), basePayload());

        assertThat(result.isSuccess()).isTrue();
        verify(signLogStore).markSigned("u1", "trans-1", "1234", 500L);
        verify(signLogStore, never()).markFailed(anyString(), anyString());
        assertThat(result.detail())
            .containsEntry("transId", "trans-1")
            .containsEntry("verificationCode", "1234")
            .containsEntry("statusCode", 500L)
            .containsEntry("signatureBase64", "c2lnbmF0dXJl");
    }

    @Test
    void gatewayFailureMarksFailedAndReturnsFailure() throws Exception {
        when(signLogStore.tryClaim(eq("u1"), eq("H05"), any())).thenReturn(true);
        when(gatewayClient.sign(any())).thenReturn(SimPkiSignOutcome.failed("ERR01", "loi mau"));

        UnitResult result = handler.execute(context(), basePayload());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("ERR01");
        verify(signLogStore).markFailed(eq("u1"), anyString());
        assertThat(result.detail())
            .containsEntry("errorCode", "ERR01")
            .containsEntry("errorDesc", "loi mau");
    }

    @Test
    void gatewayThrowingMarksFailedAndReturnsFailure() throws Exception {
        when(signLogStore.tryClaim(eq("u1"), eq("H05"), any())).thenReturn(true);
        when(gatewayClient.sign(any())).thenThrow(new RuntimeException("mang loi"));

        UnitResult result = handler.execute(context(), basePayload());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("mang loi");
        verify(signLogStore).markFailed(eq("u1"), anyString());
    }
}
