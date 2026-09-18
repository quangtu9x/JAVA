package com.td.application.numbering;

import com.td.domain.numbering.NumberingConfig;

import java.util.List;
import java.util.Optional;

public interface NumberingConfigRepository {

    Optional<NumberingConfig> findByDocumentType(String documentType);

    List<NumberingConfig> findAll();

    NumberingConfig save(NumberingConfig config);
}
