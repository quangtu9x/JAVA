package com.td.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.td")
@EntityScan("com.td.domain")
@EnableJpaRepositories("com.td.infrastructure.persistence.repository")
public class TdWebApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TdWebApiApplication.class, args);
    }
}