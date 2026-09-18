package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import com.td.domain.hosoluutru.HoSoLuuTruPhieuYeuCauDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — "Đăng ký mượn tài liệu" (đúng {@code PhieuYeuCauModal.jsx:407-491},
 * xem {@code docs/analysis/16-...md} mục 6.3/8.2): tạo phiếu yêu cầu ({@code status="0"}) + đăng ký
 * người mượn vào {@code user_dangkymuon} trên hồ sơ lưu trữ (chờ kho duyệt cấp quyền xem).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DangKyMuonTaiLieuUseCase {

    public static final String DOCUMENT_TYPE = "phieuyeucau";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ lưu trữ";
    private static final String FALLBACK_USERNAME = "system";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final HoSoLuuTruPhieuYeuCauDocumentRepository hoSoLuuTruPhieuYeuCauDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;

    public Result<UUID> execute(DangKyMuonTaiLieuRequest request) {
        if (request == null || request.getHosoId() == null) {
            return Result.failure("Thiếu id hồ sơ lưu trữ");
        }
        var hosoOptional = hoSoLuuTruDocumentRepository.findById(request.getHosoId());
        if (hosoOptional.isEmpty() || hosoOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        HoSoLuuTruDocument hoso = hosoOptional.get();
        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);

        Map<String, Object> hosoFields = dynamicDocumentFieldsService.parseDynamicFields(hoso.getDynamicFieldsJson());
        Map<String, String> phieuFields = new LinkedHashMap<>();
        phieuFields.put(HoSoLuuTruLifecycleStatus.FIELD_HOSO_ID, hoso.getId().toString());
        phieuFields.put(HoSoLuuTruLifecycleStatus.FIELD_IDENTIFIER, HoSoLuuTruFieldSupport.stringField(hosoFields, HoSoLuuTruLifecycleStatus.FIELD_IDENTIFIER));
        phieuFields.put("code", HoSoLuuTruFieldSupport.stringField(hosoFields, "code"));
        phieuFields.put("creator", actingUsername);
        phieuFields.put(HoSoLuuTruLifecycleStatus.FIELD_USER_CREATOR, actingUsername);
        phieuFields.put("create_date", DATE_FORMATTER.format(LocalDate.now()));
        phieuFields.put(HoSoLuuTruLifecycleStatus.FIELD_RETURN_DATE, request.getReturnDate());
        phieuFields.put(HoSoLuuTruLifecycleStatus.FIELD_SUBJECT, request.getSubject());
        phieuFields.put("status", HoSoLuuTruLifecycleStatus.PHIEU_CHO_GIAI_QUYET);

        String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(phieuFields, null, null, null);
        var phieu = new HoSoLuuTruPhieuYeuCauDocument(hoso.getTitle(), DOCUMENT_TYPE, null, null, null, null, null)
            .replaceDynamicFields(dynamicFieldsJson);
        var savedPhieu = hoSoLuuTruPhieuYeuCauDocumentRepository.save(phieu);

        Map<String, Object> mutableHosoFields = new LinkedHashMap<>(hosoFields);
        HoSoLuuTruFieldSupport.addToSemicolonField(
            mutableHosoFields, HoSoLuuTruLifecycleStatus.FIELD_USER_DANGKYMUON, actingUsername);
        hoso.replaceDynamicFields(HoSoLuuTruFieldSupport.toJson(mutableHosoFields));
        hoSoLuuTruDocumentRepository.save(hoso);

        return Result.success(savedPhieu.getId());
    }
}
