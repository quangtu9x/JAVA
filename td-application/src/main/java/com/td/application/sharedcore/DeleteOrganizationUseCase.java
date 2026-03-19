package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteOrganizationUseCase {

    private final OrganizationRepository organizationRepository;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = organizationRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy tổ chức với ID: " + id);
            }

            var children = organizationRepository.findByParentIdAndDeletedOnIsNull(id);
            if (!children.isEmpty()) {
                return Result.failure("Tổ chức đang có node con, vui lòng xóa hoặc chuyển node con trước");
            }

            var organization = opt.get();
            organization.markAsDeleted(UUID.randomUUID());
            var saved = organizationRepository.save(organization);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Xóa tổ chức thất bại: " + ex.getMessage());
        }
    }
}
