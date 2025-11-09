package com.td.infrastructure.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "com.td.infrastructure.persistence.mongo")
@RequiredArgsConstructor
public class MongoClientConfig extends AbstractMongoClientConfiguration {

    private final MongoConfig mongoConfig;

    @Override
    protected String getDatabaseName() {
        return mongoConfig.getDatabase();
    }

    @Override
    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongoConfig.getConnectionString());
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), getDatabaseName());
    }
}