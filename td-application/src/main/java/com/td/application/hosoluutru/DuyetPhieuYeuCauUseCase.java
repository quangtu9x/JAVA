package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosoluutru.HoSoLuuTruPhieuYeuCauDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — kho duyệt/từ chối phiếu yêu cầu mượn, đúng
 * {@code PhieuYeuCauModal.jsx#handleChapNhan()/handleTuChoi()} (dòng 96-209): guard
 * {@code status="0"}; chấp nhận → {@code status="1"} + cấp quyền xem ({@code user_quyenxem}) trên hồ
 * sơ; từ chối → {@code status="3"}. Cả 2 trường hợp đều xoá khỏi {@code user_dangkymuon}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DuyetPhieuYeuCauUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy phiếu yêu cầu";
    private static final String GUARD_MESSAGE = "Phiếu yêu cầu không ở trạng thái chờ giải quyết";

    private final HoSoLuuTruPhieuYeuCauDocumentRepository hoSoLuuTruPhieuYeuCauDocumentRepository;
    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(DuyetPhieuYeuCauRequest request) {
        if (request == null || request.getPhieuId() == null) {
            return Result.failure("Thiếu id phiếu yêu cầu");
        }
        var phieuOptional = hoSoLuuTruPhieuYeuCauDocumentRepository.findById(request.getPhieuId());
        if (phieuOptional.isEmpty() || phieuOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        HoSoLuuTruPhieuYeuCauDocument phieu = phieuOptional.get();
        Map<String, Object> phieuFields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(phieu.getDynamicFieldsJson()));
        if (!HoSoLuuTruLifecycleStatus.PHIEU_CHO_GIAI_QUYET.equals(
                HoSoLuuTruFieldSupport.stringField(phieuFields, "status"))) {
            return Result.failure(GUARD_MESSAGE);
        }

        String creator = HoSoLuuTruFieldSupport.stringField(phieuFields, HoSoLuuTruLifecycleStatus.FIELD_USER_CREATOR);
        String newStatus = request.isDuyet()
            ? HoSoLuuTruLifecycleStatus.PHIEU_CHAP_NHAN : HoSoLuuTruLifecycleStatus.PHIEU_KHONG_CHAP_NHAN;
        phieuFields.put("status", newStatus);
        phieu.replaceDynamicFields(HoSoLuuTruFieldSupport.toJson(phieuFields));
        hoSoLuuTruPhieuYeuCauDocumentRepository.save(phieu);

        String hosoIdStr = HoSoLuuTruFieldSupport.stringField(phieuFields, HoSoLuuTruLifecycleStatus.FIELD_HOSO_ID);
        updateHoSo(hosoIdStr, creator, request.isDuyet());

        return Result.success(phieu.getId());
    }

    private void updateHoSo(String hosoIdStr, String creator, boolean duyet) {
        if (hosoIdStr == null || hosoIdStr.isBlank() || creator.isBlank()) {
            return;
        }
        try {
            UUID hosoId = UUID.fromString(hosoIdStr.trim());
            hoSoLuuTruDocumentRepository.findById(hosoId).ifPresent(hoso -> {
                Map<String, Object> hosoFields = new LinkedHashMap<>(
                    dynamicDocumentFieldsService.parseDynamicFields(hoso.getDynamicFieldsJson()));
                if (duyet) {
                    HoSoLuuTruFieldSupport.addToSemicolonField(
                        hosoFields, HoSoLuuTruLifecycleStatus.FIELD_USER_QUYENXEM, creator);
                }
                HoSoLuuTruFieldSupport.removeFromSemicolonField(
                    hosoFields, HoSoLuuTruLifecycleStatus.FIELD_USER_DANGKYMUON, creator);
                hoso.replaceDynamicFields(HoSoLuuTruFieldSupport.toJson(hosoFields));
                hoSoLuuTruDocumentRepository.save(hoso);
            });
        } catch (IllegalArgumentException ex) {
            // hoso_id khong hop le - bo qua dong bo, khong lam hong thao tac chinh (duyet phieu).
        }
    }
}
