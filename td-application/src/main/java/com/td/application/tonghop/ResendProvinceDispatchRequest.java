package com.td.application.tonghop;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class ResendProvinceDispatchRequest {

    private String tenantId;

    private UUID documentId;

    /** {@code recipientKey} lấy từ chi tiết giám sát. */
    private List<String> recipientKeys = new ArrayList<>();
}
