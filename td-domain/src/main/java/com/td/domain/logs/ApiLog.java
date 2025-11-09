package com.td.domain.logs;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "api_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiLog {
    
    @Id
    private String id;
    
    @Field("request_id")
    private String requestId;
    
    @Field("user_id")
    private UUID userId;
    
    @Field("method")
    private String method; // GET, POST, PUT, DELETE
    
    @Field("endpoint")
    private String endpoint;
    
    @Field("status_code")
    private int statusCode;
    
    @Field("response_time_ms")
    private long responseTimeMs;
    
    @Field("request_size")
    private long requestSize;
    
    @Field("response_size")
    private long responseSize;
    
    @Field("ip_address")
    private String ipAddress;
    
    @Field("user_agent")
    private String userAgent;
    
    @Field("timestamp")
    private LocalDateTime timestamp;
    
    @Field("error_message")
    private String errorMessage;
    
    @Field("success")
    private boolean success;
    
    public ApiLog(String requestId, String method, String endpoint, String ipAddress, String userAgent) {
        this.requestId = requestId;
        this.method = method;
        this.endpoint = endpoint;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.timestamp = LocalDateTime.now();
    }
}