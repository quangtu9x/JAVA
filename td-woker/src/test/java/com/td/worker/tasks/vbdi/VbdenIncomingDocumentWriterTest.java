package com.td.worker.tasks.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bổ sung 13/09/2026 (báo lỗi người dùng: "gửi lại văn bản đi chưa gán đúng trạng thái cho đơn vị
 * nhận, lần đầu gửi là văn bản mới, gửi lại phải là văn bản cập nhật") — xem javadoc
 * {@link VbdenIncomingDocumentWriter#resolveEffectiveBussinessDocType}/{@link
 * VbdenIncomingDocumentWriter#resolveRevokeTargetIds}.
 */
class VbdenIncomingDocumentWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private VbdiDeliveryPayload payload(String sendType, String documentReplaceRetrieve,
            String replacesDocumentId) {
        var p = new VbdiDeliveryPayload();
        p.setDocumentId(UUID.randomUUID().toString());
        p.setSendType(sendType);
        var snapshot = new VbdiDeliveryPayload.VbdiDocumentSnapshot();
        snapshot.setDocumentReplaceRetrieve(documentReplaceRetrieve);
        snapshot.setReplacesDocumentId(replacesDocumentId);
        p.setDocumentSnapshot(snapshot);
        return p;
    }

    @Test
    void resolveEffectiveBussinessDocType_guiBoSung_shouldAlwaysForceZero() {
        // Domino SendDocument.java: bosung=="guibosung" -> document_replace_retrieve="0", BAT KE
        // van ban co dang danh dau thay the/thu hoi hay khong.
        var p = payload("guibosung", "4", UUID.randomUUID().toString());
        assertThat(VbdenIncomingDocumentWriter.resolveEffectiveBussinessDocType(p)).isEqualTo("0");
    }

    @Test
    void resolveEffectiveBussinessDocType_resendDocument_shouldForceCapNhat() {
        var p = payload("resenddocument", "0", null);
        assertThat(VbdenIncomingDocumentWriter.resolveEffectiveBussinessDocType(p)).isEqualTo("2");
    }

    @Test
    void resolveEffectiveBussinessDocType_guiLaiToanBo_shouldForceCapNhat() {
        var p = payload("guilaitoanbo", "0", null);
        assertThat(VbdenIncomingDocumentWriter.resolveEffectiveBussinessDocType(p)).isEqualTo("2");
    }

    @Test
    void resolveEffectiveBussinessDocType_senddocument_shouldUseVerbatimValue() {
        var p = payload("senddocument", "4", UUID.randomUUID().toString());
        assertThat(VbdenIncomingDocumentWriter.resolveEffectiveBussinessDocType(p)).isEqualTo("4");
    }

    @Test
    void resolveEffectiveBussinessDocType_senddocument_blankRetrieve_shouldDefaultZero() {
        var p = payload("senddocument", null, null);
        assertThat(VbdenIncomingDocumentWriter.resolveEffectiveBussinessDocType(p)).isEqualTo("0");
    }

    @Test
    void resolveEffectiveBussinessDocType_unitRevoke_shouldUseVerbatimValue() {
        // UnitRevoke KHONG thuoc 2 nhom override (guibosung/resend) - di qua nhu senddocument.
        var p = payload("UnitRevoke", "1", UUID.randomUUID().toString());
        assertThat(VbdenIncomingDocumentWriter.resolveEffectiveBussinessDocType(p)).isEqualTo("1");
    }

    @Test
    void resolveRevokeTargetIds_capNhat_shouldSearchBySelfDocumentId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var p = payload("resenddocument", "0", null);
        when(jdbc.queryForList(any(String.class), eq(String.class), eq(p.getDocumentId())))
            .thenReturn(List.of("old-id-1"));

        var result = VbdenIncomingDocumentWriter.resolveRevokeTargetIds(jdbc, p, "2");

        assertThat(result).containsExactly("old-id-1");
        verify(jdbc).queryForList(any(String.class), eq(String.class), eq(p.getDocumentId()));
    }

    @Test
    void resolveRevokeTargetIds_thayTheThuHoi_shouldSearchByReplacesDocumentId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String replacesId = UUID.randomUUID().toString();
        var p = payload("senddocument", "4", replacesId);
        when(jdbc.queryForList(any(String.class), eq(String.class), eq(replacesId)))
            .thenReturn(List.of("old-id-A"));

        var result = VbdenIncomingDocumentWriter.resolveRevokeTargetIds(jdbc, p, "4");

        assertThat(result).containsExactly("old-id-A");
    }

    @Test
    void resolveRevokeTargetIds_effectiveTypeZero_shouldNotQueryAtAll() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var p = payload("senddocument", "0", null);

        var result = VbdenIncomingDocumentWriter.resolveRevokeTargetIds(jdbc, p, "0");

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void resolveRevokeTargetIds_missingReplacesDocumentId_shouldReturnEmptyWithoutQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var p = payload("senddocument", "4", null);

        var result = VbdenIncomingDocumentWriter.resolveRevokeTargetIds(jdbc, p, "4");

        assertThat(result).isEmpty();
        verify(jdbc, never()).queryForList(any(String.class), eq(String.class), any());
    }

    @Test
    void resolveRevokeTargetIds_queryThrows_shouldReturnEmptyNotPropagate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var p = payload("resenddocument", "0", null);
        when(jdbc.queryForList(any(String.class), eq(String.class), any()))
            .thenThrow(new RuntimeException("bang chua san sang"));

        var result = VbdenIncomingDocumentWriter.resolveRevokeTargetIds(jdbc, p, "2");

        assertThat(result).isEmpty();
    }

    @Test
    void buildDynamicFieldsJson_effectiveTypeZero_shouldNotWriteBussinessDocType() throws Exception {
        var p = payload("senddocument", "0", null);
        String json = VbdenIncomingDocumentWriter.buildDynamicFieldsJson(
            objectMapper, p, List.of(), "0", List.of());
        Map<String, Object> fields = objectMapper.readValue(json, Map.class);
        assertThat(fields).doesNotContainKey("bussiness_doc_type");
        assertThat(fields).doesNotContainKey("documentoutrevoke");
    }

    @Test
    void buildDynamicFieldsJson_capNhatWithTarget_shouldWriteBothFields() throws Exception {
        var p = payload("resenddocument", "0", null);
        String json = VbdenIncomingDocumentWriter.buildDynamicFieldsJson(
            objectMapper, p, List.of(), "2", List.of("old-id-1"));
        Map<String, Object> fields = objectMapper.readValue(json, Map.class);
        assertThat(fields.get("bussiness_doc_type")).isEqualTo("2");
        assertThat((List<?>) fields.get("documentoutrevoke")).hasSize(1);
        Map<?, ?> entry = (Map<?, ?>) ((List<?>) fields.get("documentoutrevoke")).get(0);
        assertThat(entry.get("id")).isEqualTo("old-id-1");
    }

    @Test
    void buildDynamicFieldsJson_thayTheWithoutTargetFound_shouldStillWriteBussinessDocTypeOnly() throws Exception {
        // Bugfix xac nhan: bussiness_doc_type PHAI phan anh Y DINH nguoi gui du KHONG tim thay van
        // ban cu tai tenant nay (truoc day bi gate sai theo "co tim thay revoke target hay khong").
        var p = payload("senddocument", "4", UUID.randomUUID().toString());
        String json = VbdenIncomingDocumentWriter.buildDynamicFieldsJson(
            objectMapper, p, List.of(), "4", List.of());
        Map<String, Object> fields = objectMapper.readValue(json, Map.class);
        assertThat(fields.get("bussiness_doc_type")).isEqualTo("4");
        assertThat(fields).doesNotContainKey("documentoutrevoke");
    }
}
