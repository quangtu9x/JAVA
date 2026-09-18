package com.td.domain.numbering;

import com.td.domain.common.contracts.BaseEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * BL-104 (Document Numbering Service): bộ đếm sinh số, scope theo {@code (documentType, year,
 * scopeKey)}. Không có setter/business constructor công khai — giá trị luôn được tăng nguyên tử
 * qua UPSERT native query ({@code NumberingCounterRepository.incrementAndGet}), không qua JPA
 * save thông thường, để tránh lost-update dưới concurrent access.
 */
@Entity
@Table(name = "numbering_counters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NumberingCounter extends BaseEntity implements IAggregateRoot {

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "scope_key", nullable = false, length = 100)
    private String scopeKey;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
