package com.td.infrastructure.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String subject;
    private UUID userUuid;
    private String userId;
    private String username;
    private String issuer;
    private String activeOrgId;
    private String agencyId;

    @Builder.Default
    private Set<String> roles = new LinkedHashSet<>();

    @Builder.Default
    private Set<String> permissions = new LinkedHashSet<>();

    private long profileVersion;
    private Instant resolvedAt;
}