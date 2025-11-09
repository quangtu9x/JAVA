package com.td.infrastructure.persistence.mongo;

import com.td.domain.logs.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, UUID entityId);
    
    List<AuditLog> findByUserIdOrderByTimestampDesc(UUID userId);
    
    List<AuditLog> findByActionOrderByTimestampDesc(String action);
    
    @Query("{'timestamp': {$gte: ?0, $lte: ?1}}")
    Page<AuditLog> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    @Query("{'entityType': ?0, 'timestamp': {$gte: ?1, $lte: ?2}}")
    Page<AuditLog> findByEntityTypeAndTimestampBetween(String entityType, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    @Query("{'userId': ?0, 'timestamp': {$gte: ?1, $lte: ?2}}")
    List<AuditLog> findByUserIdAndTimestampBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("{'entityType': ?0}")
    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);
    
    void deleteByTimestampBefore(LocalDateTime cutoffDate);
}