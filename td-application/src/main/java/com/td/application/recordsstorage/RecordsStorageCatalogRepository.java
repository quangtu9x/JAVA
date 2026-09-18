package com.td.application.recordsstorage;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.recordsstorage.RecordsStorageCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface RecordsStorageCatalogRepository extends IRepository<RecordsStorageCatalog> {

    Optional<RecordsStorageCatalog> findByIdAndDeletedOnIsNull(UUID id);

    Page<RecordsStorageCatalog> search(SearchRecordsStorageCatalogsRequest request, Pageable pageable);
}
