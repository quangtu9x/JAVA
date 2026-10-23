package com.td.application.tonghop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Trạng thái hiện tại của 1 nơi nhận + dòng thời gian sự kiện. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDispatchRecipientItem {

    private String recipientKey;
    private String recipientGroup;
    private String recipientIdentifier;
    private String recipientName;
    private String state;
    private String stateLabel;
    private int sendCount;
    private OffsetDateTime firstSentOn;
    private OffsetDateTime lastSentOn;
    private OffsetDateTime arrivedOn;
    private OffsetDateTime acceptedOn;
    private OffsetDateTime completedOn;
    private OffsetDateTime lastEventOn;
    @Builder.Default
    private List<Event> events = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Event {
        private String sendType;
        private String status;
        private String content;
        private String userCreator;
        private OffsetDateTime createdOn;
    }
}
