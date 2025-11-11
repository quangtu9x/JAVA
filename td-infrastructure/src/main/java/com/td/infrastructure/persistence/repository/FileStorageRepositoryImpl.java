package com.td.infrastructure.persistence.repository;

import com.td.domain.storage.FileCategory;
import com.td.domain.storage.FileMetadata;
import com.td.domain.storage.FileStorageRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FileStorageRepositoryImpl extends JpaRepository<FileMetadata, UUID>, FileStorageRepository {

    @Override
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileMetadata f WHERE f.fileCategory = :category")
    Long sumFileSizeByFileCategory(@Param("category") FileCategory category);

    @Override
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileMetadata f WHERE f.uploadedBy = :uploadedBy")
    Long sumFileSizeByUploadedBy(@Param("uploadedBy") UUID uploadedBy);
}