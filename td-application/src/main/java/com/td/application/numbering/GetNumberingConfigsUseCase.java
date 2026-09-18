package com.td.application.numbering;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BL-104 — Definition of Done (4): API cấu hình format (đọc danh sách).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetNumberingConfigsUseCase {

    private final NumberingConfigRepository numberingConfigRepository;

    public List<NumberingConfigDto> execute() {
        return numberingConfigRepository.findAll().stream()
            .map(NumberingConfigDtoMapper::map)
            .toList();
    }
}
