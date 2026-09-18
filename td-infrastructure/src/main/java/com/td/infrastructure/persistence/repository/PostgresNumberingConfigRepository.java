package com.td.infrastructure.persistence.repository;

import com.td.application.numbering.NumberingConfigRepository;
import com.td.domain.numbering.NumberingConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresNumberingConfigRepository implements NumberingConfigRepository {

    private final NumberingConfigJpaRepository jpa;

    @Override
    public Optional<NumberingConfig> findByDocumentType(String documentType) {
        return jpa.findByDocumentType(documentType);
    }

    @Override
    public List<NumberingConfig> findAll() {
        return jpa.findAll();
    }

    @Override
    public NumberingConfig save(NumberingConfig config) {
        return jpa.save(config);
    }
}
