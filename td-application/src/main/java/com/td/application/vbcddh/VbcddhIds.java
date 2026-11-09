package com.td.application.vbcddh;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 30/10/2026: tiện ích parse id vbcddh (FE hay gửi UUID viết HOA, hoặc nhiều id nối bằng dấu phẩy). */
final class VbcddhIds {

    private VbcddhIds() {
    }

    static UUID parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static List<UUID> parseAll(List<String> values) {
        List<UUID> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String part : value.split("[,;]")) {
                UUID id = parse(part);
                if (id != null && !result.contains(id)) {
                    result.add(id);
                }
            }
        }
        return result;
    }
}
