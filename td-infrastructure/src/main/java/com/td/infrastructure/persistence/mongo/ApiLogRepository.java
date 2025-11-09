package com.td.infrastructure.persistence.mongo;

import com.td.domain.logs.ApiLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApiLogRepository extends MongoRepository<ApiLog, String> {
    
    List<ApiLog> findByUserIdOrderByTimestampDesc(UUID userId);
    
    List<ApiLog> findByMethodOrderByTimestampDesc(String method);
    
    List<ApiLog> findBySuccessOrderByTimestampDesc(boolean success);
    
    @Query("{'timestamp': {$gte: ?0, $lte: ?1}}")
    Page<ApiLog> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    @Query("{'userId': ?0, 'timestamp': {$gte: ?1, $lte: ?2}}")
    List<ApiLog> findByUserIdAndTimestampBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("{'endpoint': {$regex: ?0, $options: 'i'}}")
    Page<ApiLog> findByEndpointContainingIgnoreCase(String endpoint, Pageable pageable);
    
    @Query("{'statusCode': {$gte: ?0, $lte: ?1}}")
    Page<ApiLog> findByStatusCodeBetween(int minStatus, int maxStatus, Pageable pageable);
    
    @Query("{'responseTimeMs': {$gte: ?0}}")
    List<ApiLog> findSlowRequests(long minResponseTime);
    
    @Query("{'success': false, 'timestamp': {$gte: ?0, $lte: ?1}}")
    List<ApiLog> findErrorsBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    void deleteByTimestampBefore(LocalDateTime cutoffDate);
    
    @Query(value = "{'timestamp': {$gte: ?0, $lte: ?1}}", count = true)
    long countRequestsBetween(LocalDateTime startDate, LocalDateTime endDate);
}