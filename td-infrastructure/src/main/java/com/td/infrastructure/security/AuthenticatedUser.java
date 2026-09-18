package com.td.infrastructure.security;

import lombok.Builder;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Principal cho request xác thực qua {@code Tdtoken} (JWT cũ của Domino).
 * Giữ nguyên toàn bộ claim gốc trong {@link #claims} (identifier, agency_id, unit_id, dbpath,
 * ipserver, ip_server_*, ...) để các cơ chế phía sau (vd. BL-303 multi-tenant routing) có thể
 * dùng lại mà không phải xác thực lại token — tránh tái tạo God Object nhưng cũng không làm mất
 * thông tin claim khi chưa có model tenant-routing chính thức.
 */
@Getter
@Builder
public class AuthenticatedUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String name;

    @Builder.Default
    private final Set<String> roles = new LinkedHashSet<>();

    @Builder.Default
    private final Map<String, Object> claims = Map.of();
}
