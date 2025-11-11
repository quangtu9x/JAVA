package com.td.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch Configuration
 * 
 * Cấu hình Elasticsearch client và Spring Data Elasticsearch repositories.
 * Hỗ trợ kết nối đến Elasticsearch cluster với custom settings.
 */
@Configuration
@EnableConfigurationProperties(ElasticsearchProperties.class)
@EnableElasticsearchRepositories(basePackages = "com.td.infrastructure.search.repository")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private final ElasticsearchProperties elasticsearchProperties;

    public ElasticsearchConfig(ElasticsearchProperties elasticsearchProperties) {
        this.elasticsearchProperties = elasticsearchProperties;
    }

    /**
     * Cấu hình Elasticsearch client configuration
     */
    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo(elasticsearchProperties.getHosts().toArray(new String[0]))
                .withConnectTimeout(elasticsearchProperties.getConnectionTimeout())
                .withSocketTimeout(elasticsearchProperties.getSocketTimeout())
                .build();
    }

    /**
     * Tạo Elasticsearch RestClient bean
     */
    @Bean
    public RestClient restClient() {
        HttpHost[] httpHosts = elasticsearchProperties.getHosts().stream()
                .map(host -> {
                    String[] hostPort = host.split(":");
                    return new HttpHost(
                            hostPort[0], 
                            hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 9200, 
                            "http"
                    );
                })
                .toArray(HttpHost[]::new);
                
        return RestClient.builder(httpHosts)
                .setRequestConfigCallback(requestConfigBuilder -> 
                    requestConfigBuilder
                            .setConnectTimeout((int) elasticsearchProperties.getConnectionTimeout().toMillis())
                            .setSocketTimeout((int) elasticsearchProperties.getSocketTimeout().toMillis())
                )
                .build();
    }

    /**
     * Tạo Elasticsearch Transport bean
     */
    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    /**
     * Tạo Elasticsearch Java API Client bean
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}