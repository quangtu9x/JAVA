package com.td.infrastructure.search;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.elasticsearch")
@Data
public class DocumentSearchProperties {
    private boolean enabled = true;
}
