package com.td.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.database")
public class DatabaseConfig {
    private String url;
    private String username;
    private String password;
    private String driverClassName = "org.postgresql.Driver";
    private Jpa jpa = new Jpa();
    private Hikari hikari = new Hikari();

    @Data
    public static class Jpa {
        private boolean generateDdl = false;
        private boolean showSql = false;
        private String ddlAuto = "validate";
        private String dialect = "org.hibernate.dialect.PostgreSQLDialect";
    }

    @Data
    public static class Hikari {
        private int minimumIdle = 5;
        private int maximumPoolSize = 20;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
        private boolean autoCommit = true;
    }
}