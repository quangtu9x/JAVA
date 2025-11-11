package com.td.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Elasticsearch Configuration Properties
 * 
 * Cấu hình properties cho Elasticsearch connection và settings.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {

    /**
     * Danh sách Elasticsearch hosts
     * Format: host:port (ví dụ: localhost:9200)
     */
    private List<String> hosts = List.of("localhost:9200");

    /**
     * Cluster name
     */
    private String clusterName = "td-cluster";

    /**
     * Node name prefix
     */
    private String nodeNamePrefix = "td-node";

    /**
     * Connection timeout
     */
    private Duration connectionTimeout = Duration.ofSeconds(10);

    /**
     * Socket timeout
     */
    private Duration socketTimeout = Duration.ofSeconds(30);

    /**
     * Index settings
     */
    private IndexSettings index = new IndexSettings();

    @Getter
    @Setter
    public static class IndexSettings {
        
        /**
         * Số lượng shards cho mỗi index
         */
        private int numberOfShards = 1;

        /**
         * Số lượng replicas cho mỗi index
         */
        private int numberOfReplicas = 0;

        /**
         * Refresh interval
         */
        private String refreshInterval = "1s";

        /**
         * Max result window
         */
        private int maxResultWindow = 10000;

        /**
         * Analysis settings
         */
        private AnalysisSettings analysis = new AnalysisSettings();
    }

    @Getter
    @Setter
    public static class AnalysisSettings {
        
        /**
         * Default analyzer
         */
        private String defaultAnalyzer = "standard";

        /**
         * Search analyzer
         */
        private String searchAnalyzer = "standard";

        /**
         * Vietnamese text analysis
         */
        private boolean enableVietnameseAnalysis = true;

        /**
         * Autocomplete settings
         */
        private AutocompleteSettings autocomplete = new AutocompleteSettings();
    }

    @Getter
    @Setter
    public static class AutocompleteSettings {
        
        /**
         * Enable autocomplete indexing
         */
        private boolean enabled = true;

        /**
         * Min gram size
         */
        private int minGram = 2;

        /**
         * Max gram size
         */
        private int maxGram = 20;

        /**
         * Token chars
         */
        private List<String> tokenChars = List.of("letter", "digit");
    }
}