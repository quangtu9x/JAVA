package com.td.infrastructure.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitDeliveryTrackerTest {

    @Test
    void consume_shouldReturnAndRemoveTrackedTenant() {
        var tracker = new RabbitDeliveryTracker();
        tracker.track("msg-1", "H05_01");

        assertThat(tracker.consume("msg-1")).contains("H05_01");
        // Da bi xoa - lan doc thu 2 phai rong (chi xu ly callback dau tien den).
        assertThat(tracker.consume("msg-1")).isEmpty();
    }

    @Test
    void consume_shouldReturnEmpty_whenNeverTracked() {
        var tracker = new RabbitDeliveryTracker();

        assertThat(tracker.consume("unknown")).isEmpty();
    }

    @Test
    void consume_shouldReturnEmpty_whenMessageIdIsNull() {
        var tracker = new RabbitDeliveryTracker();

        assertThat(tracker.consume(null)).isEmpty();
    }

    @Test
    void track_shouldIgnore_whenMessageIdOrTenantIsNull() {
        var tracker = new RabbitDeliveryTracker();
        tracker.track(null, "H05_01");
        tracker.track("msg-1", null);

        assertThat(tracker.consume("msg-1")).isEmpty();
    }
}
