package com.td.worker.infrastructure.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.td.worker.config.WorkerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncomingDocumentNotifierTest {

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> upgradeHeaders = new CopyOnWriteArrayList<>();
    private CountDownLatch received;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private IncomingDocumentNotifier notifier;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/socket-batch", exchange -> {
            String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
            if (upgrade != null) {
                upgradeHeaders.add(upgrade);
            }
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
            received.countDown();
        });
        server.start();
        WorkerProperties props = new WorkerProperties();
        props.getRealtime().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
        props.getRealtime().setApiKey("TANDANJSC");
        notifier = new IncomingDocumentNotifier(props, objectMapper);
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void storesUnitInboxRowForNewIncomingDocument_andPushesToUnitRoom() throws Exception {
        received = new CountDownLatch(1);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        UUID docId = UUID.randomUUID();

        notifier.notifyIncomingDocument(jdbc, tx, "H05.01", docId, "23/GM-SNNMT", "V/v tăng cường phòng chống thiên tai",
            "Sở Nông nghiệp và Môi trường", null);

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(),
            args.capture(), args.capture(), args.capture());
        List<Object> values = args.getAllValues();
        UUID notificationId = (UUID) values.get(0);
        assertThat(values.get(1)).isEqualTo("H05.01");
        assertThat(values.get(2)).isEqualTo("unit");
        assertThat(values.get(3)).isEqualTo("vanbanden");
        assertThat(values.get(4)).isEqualTo(docId.toString());
        JsonNode fields = objectMapper.readTree((String) values.get(5));
        assertThat(fields.path("code").asText()).isEqualTo("23/GM-SNNMT");
        assertThat(fields.path("creator").asText()).isEqualTo("Sở Nông nghiệp và Môi trường");
        assertThat(fields.path("message").asText()).isEqualTo("V/v tăng cường phòng chống thiên tai");

        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        JsonNode item = objectMapper.readTree(bodies.get(0)).path("items").get(0);
        assertThat(item.path("channel").asText()).isEqualTo("coquan_H05.01");
        JsonNode data = item.path("data");
        assertThat(data.path("databasetype").asText()).isEqualTo("vbden");
        assertThat(data.path("action").asText()).isEqualTo("vanbanden");
        assertThat(data.path("docid").asText()).isEqualTo(docId.toString());
        assertThat(data.path("notification_id").asText()).isEqualTo(notificationId.toString());
        assertThat(upgradeHeaders).isEmpty();
    }

    @Test
    void tenantWithoutInboxTable_stillPushes_withoutNotificationId() throws Exception {
        received = new CountDownLatch(1);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);

        notifier.notifyIncomingDocument(jdbc, tx, "H05.01", UUID.randomUUID(), "1/X", "Trích yếu", "Đơn vị gửi", null);

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(objectMapper.readTree(bodies.get(0)).path("items").get(0).path("data").has("notification_id")).isFalse();
    }

    @Test
    void databaseError_isSwallowed() {
        received = new CountDownLatch(1);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenThrow(new IllegalStateException("db down"));

        notifier.notifyIncomingDocument(jdbc, tx, "H05.01", UUID.randomUUID(), "1/X", null, null, null);
    }
}
