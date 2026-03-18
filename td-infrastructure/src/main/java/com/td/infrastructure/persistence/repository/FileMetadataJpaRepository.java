package com.td.infrastructure.persistence.repository;

import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileMetadataJpaRepository extends JpaRepository<FileMetadataEntity, UUID> {

    List<FileMetadataEntity> findAllByDocumentId(UUID documentId);

    Optional<FileMetadataEntity> findByIdAndDocumentId(UUID id, UUID documentId);
}
