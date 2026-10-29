package com.td.infrastructure.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.td.application.realtime.RealtimeNotification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Gọi thật qua HTTP tới 1 server giả lập REST của socket.io. */
class SocketIoRealtimeNotificationGatewayTest {

    private HttpServer server;
    private final Map<String, String> bodiesByPath = new ConcurrentHashMap<>();
    private final Map<String, String> queriesByPath = new ConcurrentHashMap<>();
    private final List<String> batchBodies = new CopyOnWriteArrayList<>();
    private volatile boolean batchSupported = true;
    private CountDownLatch received;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/socket/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            bodiesByPath.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            queriesByPath.put(path, exchange.getRequestURI().getQuery());
            respond(exchange, 200);
            received.countDown();
        });
        server.createContext("/api/socket-batch", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!batchSupported) {
                respond(exchange, 404);
                return;
            }
            batchBodies.add(body);
            queriesByPath.put("/api/socket-batch", exchange.getRequestURI().getQuery());
            respond(exchange, 200);
            received.countDown();
        });
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status) throws java.io.IOException {
        byte[] ok = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, ok.length);
        exchange.getResponseBody().write(ok);
        exchange.close();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private SocketIoRealtimeNotificationGateway gateway() {
        SocketIoProperties properties = new SocketIoProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/");
        properties.setApiKey("TANDANJSC");
        return new SocketIoRealtimeNotificationGateway(properties);
    }

    private RealtimeNotification sample(UUID docId) {
        return RealtimeNotification.of("duthao", "chuyenkyduyet", docId, "Dự thảo quyết định")
            .to("lanhdao.a;H56.07.01")
            .creator("Nguyễn Văn A")
            .opinion("Trình ký");
    }

    @Test
    void publish_sendsOneBatchRequestForAllRecipients() throws Exception {
        received = new CountDownLatch(1);
        UUID docId = UUID.randomUUID();

        gateway().publish(sample(docId));

        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(batchBodies).hasSize(1);
        assertThat(bodiesByPath).isEmpty();
        assertThat(queriesByPath.get("/api/socket-batch")).isEqualTo("api_key=TANDANJSC");

        JsonNode items = new ObjectMapper().readTree(batchBodies.get(0)).path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("channel").asText()).isEqualTo("user_lanhdao.a");
        assertThat(items.get(1).path("channel").asText()).isEqualTo("coquan_H56.07.01");
        JsonNode data = items.get(0).path("data");
        assertThat(data.path("databasetype").asText()).isEqualTo("duthao");
        assertThat(data.path("action").asText()).isEqualTo("chuyenkyduyet");
        assertThat(data.path("docid").asText()).isEqualTo(docId.toString());
        assertThat(data.path("code").asText()).isEqualTo("Dự thảo quyết định");
        assertThat(data.path("creator").asText()).isEqualTo("Nguyễn Văn A");
        assertThat(data.path("opinion").asText()).isEqualTo("Trình ký");
        assertThat(data.path("user").asText()).isEqualTo("lanhdao.a");
        // Không lặp lại cả danh sách người nhận trong từng payload (phình request khi phát hành rộng).
        assertThat(data.has("users")).isFalse();
        assertThat(data.has("deadline")).isFalse();
    }

    @Test
    void publish_oldServerWithoutBatch_fallsBackToOneRequestPerRecipient() throws Exception {
        batchSupported = false;
        received = new CountDownLatch(2);

        SocketIoRealtimeNotificationGateway gateway = gateway();
        gateway.publish(sample(UUID.randomUUID()));

        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(bodiesByPath).containsOnlyKeys("/api/socket/user_lanhdao.a", "/api/socket/coquan_H56.07.01");
        assertThat(queriesByPath.get("/api/socket/user_lanhdao.a")).isEqualTo("api_key=TANDANJSC");
        JsonNode body = new ObjectMapper().readTree(bodiesByPath.get("/api/socket/coquan_H56.07.01"));
        assertThat(body.path("user").asText()).isEqualTo("H56.07.01");

        // Lần sau trong 10 phút: đi thẳng từng người nhận, không thử batch nữa.
        bodiesByPath.clear();
        received = new CountDownLatch(2);
        gateway.publish(sample(UUID.randomUUID()));
        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(batchBodies).isEmpty();
    }

    @Test
    void publish_withoutBaseUrl_doesNothing() {
        SocketIoProperties properties = new SocketIoProperties();
        new SocketIoRealtimeNotificationGateway(properties)
            .publish(RealtimeNotification.of("vbden", "xoa", UUID.randomUUID(), "x").to("u1"));
        assertThat(bodiesByPath).isEmpty();
        assertThat(batchBodies).isEmpty();
    }

    @Test
    void publish_serverUnreachable_doesNotThrow() {
        SocketIoProperties properties = new SocketIoProperties();
        properties.setBaseUrl("http://127.0.0.1:1/api");
        new SocketIoRealtimeNotificationGateway(properties)
            .publish(RealtimeNotification.of("vbden", "xoa", UUID.randomUUID(), "x").to("u1"));
    }
}
