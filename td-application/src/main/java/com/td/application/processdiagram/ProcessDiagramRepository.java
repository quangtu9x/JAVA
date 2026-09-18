package com.td.application.processdiagram;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.processdiagram.ProcessDiagram;

import java.util.Optional;
import java.util.UUID;

/** R8 (tài liệu phân tích 03) — repository cho {@link ProcessDiagram}. */
public interface ProcessDiagramRepository extends IRepository<ProcessDiagram> {

    Optional<ProcessDiagram> findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(UUID documentId, String documentType);
}
