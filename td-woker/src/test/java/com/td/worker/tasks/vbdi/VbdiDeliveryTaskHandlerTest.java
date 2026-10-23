package com.td.worker.tasks.vbdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.UnitResult;
import com.td.worker.infrastructure.fileregistry.SharedFileRegistry;
import com.td.worker.infrastructure.minio.WorkerMinioClient;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.td.worker.transport.rabbit.RabbitPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test {@link VbdiDeliveryTaskHandler} — trọng tâm: nơi nhận NGOÀI TỈNH ({@code external_unit})
 * phải trả {@code WAITING_DEPENDENCY}, KHÔNG BAO GIỜ {@code success()} (chưa có gateway liên thông
 * thật — xem docs/11-vbdi-reference.md §3, §6). Đánh dấu DONE ở đây sẽ là "DONE giả": message bị
 * ACK/bỏ trong khi văn bản chưa thực sự rời hệ thống. {@code contact_unit}/{@code internal_unit}
 * (cùng thuộc tỉnh/cùng cơ quan) KHÔNG rơi vào nhánh này (bugfix 06/09/2026 — trước đó nhầm coi
 * {@code contact_unit} là "ngoài hệ thống" giống {@code external_unit}, xem
 * docs/architecture/11 addendum 06/09/2026 (3)).
 *
 * <p>Test cho nhánh {@code contact_unit} TẠO THẬT "văn bản đến" (JDBC + Postgres thật, cần
 * {@code documents.dynamic_fields} JSONB/"INSERT ... ON CONFLICT" mà H2 không parse được) nằm ở
 * {@link VbdiDeliveryIncomingDocumentIntegrationTest} (Testcontainers), không ở file này.</p>
 */
class VbdiDeliveryTaskHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TenantDataSourceManager tenantDataSources;
    private WorkerMinioClient minio;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RabbitPublisher> rabbitPublisher = mock(ObjectProvider.class);
    private WorkerProperties props;
    private VbdiDeliveryTaskHandler handler;

    @BeforeEach
    void setUp() {
        tenantDataSources = mock(TenantDataSourceManager.class);
        minio = mock(WorkerMinioClient.class);
        props = new WorkerProperties();
        props.getTasks().getVbdi().setEnabled(true);
        props.getTasks().getVbdi().setVerifyObjectExists(false);
        handler = new VbdiDeliveryTaskHandler(props, tenantDataSources, minio, objectMapper, rabbitPublisher,
            mock(SharedFileRegistry.class));
    }

    private ObjectNode basePayload(String recipientGroup) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sendLogId", "sl-1");
        node.put("documentId", "doc-1");
        node.put("senderTenantId", "H05");
        node.put("senderIdentifier", "H05");
        node.put("recipientGroup", recipientGroup);
        node.put("recipientIdentifier", "recv-1");
        node.put("sendType", "senddocument");
        return node;
    }

    private TaskContext context(String tenantId) {
        return new TaskContext("vbdi.delivery", "msg-1", "u1", "corr-1", tenantId,
            null, Map.of(), 5, null, Map.of());
    }

    @Test
    void externalUnitReturnsWaitingDependencyNotSuccess() {
        JsonNode payload = basePayload(VbdiTaskConstants.GROUP_EXTERNAL_UNIT);
        UnitResult result = handler.execute(context("H05"), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isWaitingDependency()).isTrue();
        assertThat(result.error()).contains(VbdiTaskConstants.GROUP_EXTERNAL_UNIT);
        // Chưa có gateway -> KHÔNG được đụng tới database tenant đích.
        verifyNoInteractions(tenantDataSources);
    }

    @Test
    void contactUnitIsNotTreatedAsExternalEvenWithMailin() {
        // Bugfix 06/09/2026: contact_unit = cơ quan/đơn vị KHÁC thuộc tỉnh (có tenant thật) -
        // KHÔNG phải "ngoài hệ thống", kể cả khi payload có kèm mailin (kênh liên hệ phụ, không
        // còn quyết định routing - xem javadoc VbdiDeliveryPayload#isExternal). Phải đi vào nhánh
        // nội bộ (cần datasource), không phải WAITING_DEPENDENCY.
        ObjectNode payload = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);
        payload.put("recipientMailin", "donvi@mail.gov.vn");
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.empty());
        UnitResult result = handler.execute(context("H06"), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isWaitingDependency()).isFalse();
        assertThat(result.error()).contains("H06"); // lỗi "không có connection pool", không phải gateway
    }

    @Test
    void internalUnitWithMailinIsNotTreatedAsExternalEither() {
        // internal_unit (đơn vị nội bộ CỦA CHÍNH cơ quan gửi) cũng KHÔNG phải "ngoài hệ thống" -
        // chỉ external_unit mới waiting_dependency (xem javadoc lớp).
        ObjectNode payload = basePayload(VbdiTaskConstants.GROUP_INTERNAL_UNIT);
        payload.put("recipientMailin", "donvi@mail.gov.vn");
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.empty());
        UnitResult result = handler.execute(context("H06"), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isWaitingDependency()).isFalse();
    }

    @Test
    void internalUnitWithoutTenantDataSourceFailsHard() {
        // Không phải WAITING_DEPENDENCY: thiếu connection pool tenant là lỗi vận hành/cấu hình,
        // không phải "chưa có gateway" — phải rơi vào RETRY/FAILED theo maxAttempts như bình thường.
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.empty());
        JsonNode payload = basePayload(VbdiTaskConstants.GROUP_INTERNAL_UNIT);
        UnitResult result = handler.execute(context("H06"), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isWaitingDependency()).isFalse();
        assertThat(result.error()).contains("H06");
    }

    @Test
    void missingRequiredFieldReturnsFailureNotWaitingDependency() {
        ObjectNode payload = basePayload(VbdiTaskConstants.GROUP_EXTERNAL_UNIT);
        payload.remove("documentId");
        UnitResult result = handler.execute(context("H05"), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isWaitingDependency()).isFalse();
    }

    @Test
    void disabledTaskReturnsFailure() {
        props.getTasks().getVbdi().setEnabled(false);
        JsonNode payload = basePayload(VbdiTaskConstants.GROUP_EXTERNAL_UNIT);
        UnitResult result = handler.execute(context("H05"), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isWaitingDependency()).isFalse();
    }
}
