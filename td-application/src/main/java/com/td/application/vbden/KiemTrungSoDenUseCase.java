package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * BL-210/211 (Nhập mới VBDen): tương thích {@code qlvb/vbden/KiemTrungSoDen} (Domino thật,
 * {@code KiemTrungSoDen.java}) — kiểm tra {@code document_number} đã được cấp cho văn bản khác
 * trong CÙNG 1 sổ đăng ký ({@code document_register_id}) hay chưa, chỉ tính văn bản do CHÍNH đơn vị
 * (identifier) của người dùng hiện tại tạo — đúng {@code identifier_user = connection.user_token.
 * identifier} phía Domino (field phẳng, KHÁC identifier suy theo cấp {@code get_Identifier_user_by_level}
 * mà {@code LuuVanBanDen.java} dùng ở chỗ khác — 2 nơi này vốn không nhất quán ngay trong code Domino
 * gốc, giữ đúng nguyên bản của riêng file KiemTrungSoDen.java).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class KiemTrungSoDenUseCase {

    private static final String DOCUMENT_TYPE = "vbden";
    private static final String CODE_FIELD = "code";
    private static final String SUBJECT_FIELD = "subject";
    private static final String DOCUMENT_NUMBER_FIELD = "document_number";
    private static final String PROMULGATION_DATE_FIELD = "promulgation_date";
    private static final String DOCUMENT_REGISTER_ID_FIELD = "document_register_id";
    private static final String IDENTIFIER_CREATOR_FIELD = "identifier_creator";
    private static final int MAX_CANDIDATES = 200;

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<List<VbdenDuplicateDto>> execute(KiemTrungSoDenRequest request) {
        try {
            if (request.getDocumentRegisterId() == null
                    || request.getDocumentNumber() == null || request.getDocumentNumber().isBlank()) {
                return Result.success(List.of());
            }
            String currentIdentifier = resolveCurrentUserIdentifier().orElse(null);
            if (currentIdentifier == null || currentIdentifier.isBlank()) {
                // Không xác định được đơn vị của người dùng hiện tại — không có gì để so khớp
                // "identifier_creator", trả rỗng thay vì báo lỗi (giữ trải nghiệm tạo mới không bị chặn).
                return Result.success(List.of());
            }

            var candidates = documentRepository.findByFieldValue(
                DOCUMENT_TYPE, DOCUMENT_REGISTER_ID_FIELD, request.getDocumentRegisterId().toString(),
                PageRequest.of(0, MAX_CANDIDATES));

            List<VbdenDuplicateDto> duplicates = new ArrayList<>();
            for (BusinessDocument doc : candidates) {
                Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(doc.getDynamicFieldsJson());
                if (!request.getDocumentNumber().trim().equals(asString(fields.get(DOCUMENT_NUMBER_FIELD)))) {
                    continue;
                }
                String identifierCreator = asString(fields.get(IDENTIFIER_CREATOR_FIELD));
                if (!currentIdentifier.trim().equals(identifierCreator)) {
                    continue;
                }
                var dto = new VbdenDuplicateDto();
                dto.setId(doc.getId());
                dto.setCode(asString(fields.get(CODE_FIELD)));
                dto.setSubject(asString(fields.get(SUBJECT_FIELD)));
                dto.setDocumentNumber(asString(fields.get(DOCUMENT_NUMBER_FIELD)));
                dto.setPromulgationDate(asString(fields.get(PROMULGATION_DATE_FIELD)));
                dto.setIdentifierCreator(identifierCreator);
                duplicates.add(dto);
            }
            return Result.success(duplicates);
        } catch (Exception ex) {
            log.error("Kiểm tra trùng số đến thất bại", ex);
            return Result.failure("Kiểm tra trùng số đến thất bại: " + ex.getMessage());
        }
    }

    private Optional<String> resolveCurrentUserIdentifier() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getIdentifier);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
