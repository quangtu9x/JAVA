package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrganizationUseCase {

    private final OrganizationRepository organizationRepository;

    public Result<OrganizationDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID tổ chức không được để trống");
            }

            var opt = organizationRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy tổ chức với ID: " + id);
            }

            return Result.success(OrganizationDtoMapper.map(opt.get()));
        } catch (Exception ex) {
            return Result.failure("Lấy tổ chức thất bại: " + ex.getMessage());
        }
    }
}
