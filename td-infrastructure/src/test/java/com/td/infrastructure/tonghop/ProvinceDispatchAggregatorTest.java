package com.td.infrastructure.tonghop;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProvinceDispatchAggregatorTest {

    private static final UUID VBDI = UUID.randomUUID();
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 10, 9, 9, 0, 0, 0, ZoneOffset.ofHours(7));

    private static ProvinceDispatchAggregator.LogEntry entry(int minute, String type, String group, String identifier,
                                                             String status) {
        ProvinceDispatchAggregator.LogEntry e = new ProvinceDispatchAggregator.LogEntry();
        e.setId(UUID.randomUUID());
        e.setVbdiId(VBDI);
        e.setSendType(type);
        e.setRecipientGroup(group);
        e.setRecipientIdentifier(identifier);
        e.setRecipientName("Đơn vị " + identifier);
        e.setStatus(status);
        e.setCreatedOn(T0.plusMinutes(minute));
        return e;
    }

    @Test
    void progressesThroughRepliesAndIgnoresOutOfOrderDowngrade() {
        List<ProvinceDispatchAggregator.LogEntry> log = new ArrayList<>(List.of(
            entry(0, "senddocument", "contact_unit", "H05.02", "Gửi văn bản"),
            entry(1, "phan_hoi_trang_thai", "contact_unit", "H05.02", "Đã đến"),
            entry(30, "phan_hoi_trang_thai", "contact_unit", "H05.02", "Phân công xử lý"),
            // Phản hồi "Đã tiếp nhận" đến trễ hơn "Phân công" — không được làm lùi trạng thái.
            entry(31, "phan_hoi_trang_thai", "contact_unit", "H05.02", "Đã tiếp nhận")));

        var result = ProvinceDispatchAggregator.aggregate(log);

        assertThat(result).hasSize(1);
        var d = result.get(0);
        assertThat(d.getState()).isEqualTo("ASSIGNED");
        assertThat(d.getSendCount()).isEqualTo(1);
        assertThat(d.getArrivedOn()).isEqualTo(T0.plusMinutes(1));
        assertThat(d.getAcceptedOn()).isEqualTo(T0.plusMinutes(30));
        assertThat(d.getLastEventOn()).isEqualTo(T0.plusMinutes(31));
    }

    @Test
    void resendStartsNewCycleAndRevokeOverrides() {
        var log = List.of(
            entry(0, "senddocument", "internal_unit", "H05.01.05", "Gửi văn bản"),
            entry(1, "phan_hoi_trang_thai", "internal_unit", "H05.01.05", "Đã đến"),
            entry(60, "resenddocument", "internal_unit", "H05.01.05", "Gửi lại"),
            entry(0, "senddocument", "contact_unit", "H05.03", "Gửi văn bản"),
            entry(5, "laylaivanban", "contact_unit", "H05.03", "Lấy lại ngay"));

        var byKey = ProvinceDispatchAggregator.aggregate(log).stream()
            .collect(java.util.stream.Collectors.toMap(ProvinceDispatchAggregator.Dispatch::getRecipientIdentifier, x -> x));

        var internal = byKey.get("H05.01.05");
        assertThat(internal.getState()).isEqualTo("SENT");
        assertThat(internal.getSendCount()).isEqualTo(2);
        assertThat(internal.getArrivedOn()).isNull();
        assertThat(internal.getFirstSentOn()).isEqualTo(T0);
        assertThat(internal.getLastSentOn()).isEqualTo(T0.plusMinutes(60));
        assertThat(byKey.get("H05.03").getState()).isEqualTo("REVOKED");
    }

    @Test
    void refusalAndCoordinatorHandling() {
        var log = List.of(
            entry(0, "senddocument", "contact_unit", "H05.02", "Gửi văn bản"),
            entry(2, "phan_hoi_trang_thai", "contact_unit", "H05.02", "Trả lại"),
            entry(0, "senddocument", "coordinator", "nguyenvana", "Gửi văn bản"));

        var result = ProvinceDispatchAggregator.aggregate(log);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getState()).isEqualTo("REFUSED");
    }

    @Test
    void replyLabelsMapToStates() {
        assertThat(ProvinceDispatchAggregator.replyState("Từ chối tiếp nhận")).isEqualTo("REFUSED");
        assertThat(ProvinceDispatchAggregator.replyState("Đã tiếp nhận")).isEqualTo("ACCEPTED");
        assertThat(ProvinceDispatchAggregator.replyState("Đang xử lý")).isEqualTo("PROCESSING");
        assertThat(ProvinceDispatchAggregator.replyState("Hoàn thành")).isEqualTo("DONE");
        assertThat(ProvinceDispatchAggregator.replyState("Đã lấy lại")).isEqualTo("REVOKED");
        assertThat(ProvinceDispatchAggregator.replyState("Gửi văn bản")).isNull();
        assertThat(ProvinceDispatchAggregator.replyState("Đã gửi lên Trục")).isEqualTo("ARRIVED");
        assertThat(ProvinceDispatchAggregator.replyState("Gửi lỗi")).isNull();
    }
}
