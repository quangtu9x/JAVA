package com.td.application.sharedcore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegacyOrganizationTreeResponse {
    private Object exception;
    private int code;
    private String message;
    private boolean status;
    private int total;
    private Map<String, String> properties;
    private List<LegacyOrganizationTreeNode> data;

    public static LegacyOrganizationTreeResponse success(List<LegacyOrganizationTreeNode> nodes, String jedisKey) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("jedis_key", jedisKey);

        return new LegacyOrganizationTreeResponse(
            null,
            0,
            null,
            true,
            0,
            properties,
            nodes == null ? List.of() : nodes
        );
    }

    public static LegacyOrganizationTreeResponse failure(String message, String jedisKey, String exceptionMessage) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("jedis_key", jedisKey);

        return new LegacyOrganizationTreeResponse(
            exceptionMessage,
            1,
            message,
            false,
            0,
            properties,
            List.of()
        );
    }
}
