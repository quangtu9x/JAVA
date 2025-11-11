package com.td.domain.storage;

import com.td.domain.common.contracts.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for FileMetadata entity
 */
public interface FileStorageRepository extends Repository<FileMetadata, UUID> {

    /**
     * Find file by stored filename
     */
    Optional<FileMetadata> findByStoredFilename(String storedFilename);

    /**
     * Find files by category
     */
    List<FileMetadata> findByFileCategory(FileCategory category);

    /**
     * Find files by category with pagination
     */
    Page<FileMetadata> findByFileCategory(FileCategory category, Pageable pageable);

    /**
     * Find files uploaded by specific user
     */
    List<FileMetadata> findByUploadedBy(UUID uploadedBy);

    /**
     * Find files uploaded by specific user with pagination
     */
    Page<FileMetadata> findByUploadedBy(UUID uploadedBy, Pageable pageable);

    /**
     * Find public files
     */
    List<FileMetadata> findByIsPublicTrue();

    /**
     * Find files by content type
     */
    List<FileMetadata> findByContentType(String contentType);

    /**
     * Find files uploaded within date range
     */
    List<FileMetadata> findByUploadedAtBetween(Instant startDate, Instant endDate);

    /**
     * Find files by original filename containing text (case-insensitive)
     */
    List<FileMetadata> findByOriginalFilenameContainingIgnoreCase(String filename);

    /**
     * Find files by category and uploaded by user
     */
    List<FileMetadata> findByFileCategoryAndUploadedBy(FileCategory category, UUID uploadedBy);

    /**
     * Find files by file size range
     */
    List<FileMetadata> findByFileSizeBetween(Long minSize, Long maxSize);

    /**
     * Count files by category
     */
    long countByFileCategory(FileCategory category);

    /**
     * Count files by user
     */
    long countByUploadedBy(UUID uploadedBy);

    /**
     * Calculate total file size by category
     */
    Long sumFileSizeByFileCategory(FileCategory category);

    /**
     * Calculate total file size by user
     */
    Long sumFileSizeByUploadedBy(UUID uploadedBy);

    /**
     * Delete files by category
     */
    void deleteByFileCategory(FileCategory category);

    /**
     * Delete files older than specified date
     */
    void deleteByUploadedAtBefore(Instant date);

    /**
     * Find most downloaded files
     */
    List<FileMetadata> findTop10ByOrderByDownloadCountDesc();

    /**
     * Find recently uploaded files
     */
    List<FileMetadata> findTop20ByOrderByUploadedAtDesc();
}