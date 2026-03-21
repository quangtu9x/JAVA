package com.td.application.sharedcore;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OrganizationDto {
    private UUID id;
    private String identifier;
    private String name;
    private String parent;
    private String parentid;
    private String form;
    private Integer system;
    private String receiver_id;
    private String receiver;
    private String receiver_position;
    private String servername;
    private String server_id;
    private String ipserver;
    private String dbpath;
    private int level;
    private int sort_order;
    private Boolean is_active;
    private LocalDateTime created_on;
    private LocalDateTime last_modified_on;
}
