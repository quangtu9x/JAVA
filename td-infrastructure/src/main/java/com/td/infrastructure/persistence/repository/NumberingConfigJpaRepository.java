package com.td.infrastructure.persistence.repository;

import com.td.domain.numbering.NumberingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NumberingConfigJpaRepository extends JpaRepository<NumberingConfig, UUID> {

    Optional<NumberingConfig> findByDocumentType(String documentType);
}
