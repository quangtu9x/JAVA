package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends IRepository<Organization> {

    boolean existsByIdentifierAndDeletedOnIsNull(String identifier);

    boolean existsByIdentifierAndIdNotAndDeletedOnIsNull(String identifier, UUID id);

    Optional<Organization> findByIdAndDeletedOnIsNull(UUID id);

    /**
     * Yêu cầu người dùng 04/09/2026 ("hoàn thiện Gửi văn bản... publish RabbitMQ/Kafka"): tra
     * {@code Organization} theo {@code identifier} (định danh nơi nhận trong
     * {@code vbdi_send_log_documents}) để lấy {@code linkedTenantId} — quyết định tenant ĐÍCH cho
     * unit {@code vbdi.delivery} publish tới (xem {@code docs/architecture/11 §5.2}).
     */
    Optional<Organization> findByIdentifierAndDeletedOnIsNull(String identifier);

    List<Organization> findByParentIdAndDeletedOnIsNull(UUID parentId);

    Page<Organization> search(SearchOrganizationsRequest request, Pageable pageable);
}
