package com.td.infrastructure.persistence.identity;

import com.td.domain.sharedcore.IdentityFileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Nằm trong package "identity" (khớp IdentityJpaRepositoriesConfig#basePackages) để Spring Data tự
// nối bean này với identityEntityManagerFactory (không theo tenant) - xem javadoc IdentityFileMetadata.
public interface IdentityFileMetadataJpaRepository extends JpaRepository<IdentityFileMetadata, UUID> {

    List<IdentityFileMetadata> findAllByDocumentId(UUID documentId);

    Optional<IdentityFileMetadata> findByIdAndDocumentId(UUID id, UUID documentId);

    List<IdentityFileMetadata> findAllByDocumentIdAndIsPrimaryTrue(UUID documentId);
}
