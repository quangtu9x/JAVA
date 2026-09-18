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
 * Port module "Hồ sơ lưu trữ" — người mượn tự trả tài liệu, đúng
 * {@code PhieuYeuCauModal.jsx#handleTraHoSo()} (dòng 324-380): guard {@code status="1"} &&
 * {@code status1} rỗng/{@code "0"}; {@code status1="1"} + xoá khỏi {@code user_quyenxem} trên hồ sơ.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TraTaiLieuUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy phiếu yêu cầu";
    private static final String GUARD_MESSAGE = "Phiếu yêu cầu không ở trạng thái đang mượn";

    private final HoSoLuuTruPhieuYeuCauDocumentRepository hoSoLuuTruPhieuYeuCauDocumentRepository;
    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(PhieuYeuCauActionRequest request) {
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
        if (!isDangMuon(phieuFields)) {
            return Result.failure(GUARD_MESSAGE);
        }

        String creator = HoSoLuuTruFieldSupport.stringField(phieuFields, HoSoLuuTruLifecycleStatus.FIELD_USER_CREATOR);
        String hosoIdStr = HoSoLuuTruFieldSupport.stringField(phieuFields, HoSoLuuTruLifecycleStatus.FIELD_HOSO_ID);
        phieuFields.put(HoSoLuuTruLifecycleStatus.FIELD_STATUS1, HoSoLuuTruLifecycleStatus.PHIEU_DA_TRA);
        phieu.replaceDynamicFields(HoSoLuuTruFieldSupport.toJson(phieuFields));
        hoSoLuuTruPhieuYeuCauDocumentRepository.save(phieu);

        removeFromQuyenXem(hosoIdStr, creator);
        return Result.success(phieu.getId());
    }

    private boolean isDangMuon(Map<String, Object> phieuFields) {
        boolean isAccepted = HoSoLuuTruLifecycleStatus.PHIEU_CHAP_NHAN.equals(
            HoSoLuuTruFieldSupport.stringField(phieuFields, "status"));
        String status1 = HoSoLuuTruFieldSupport.stringField(phieuFields, HoSoLuuTruLifecycleStatus.FIELD_STATUS1);
        boolean isPending = status1.isBlank() || HoSoLuuTruLifecycleStatus.PHIEU_DANG_MUON.equals(status1)
            || "0".equals(status1);
        return isAccepted && isPending;
    }

    private void removeFromQuyenXem(String hosoIdStr, String creator) {
        if (hosoIdStr == null || hosoIdStr.isBlank() || creator.isBlank()) {
            return;
        }
        try {
            UUID hosoId = UUID.fromString(hosoIdStr.trim());
            hoSoLuuTruDocumentRepository.findById(hosoId).ifPresent(hoso -> {
                Map<String, Object> hosoFields = new LinkedHashMap<>(
                    dynamicDocumentFieldsService.parseDynamicFields(hoso.getDynamicFieldsJson()));
                HoSoLuuTruFieldSupport.removeFromSemicolonField(
                    hosoFields, HoSoLuuTruLifecycleStatus.FIELD_USER_QUYENXEM, creator);
                hoso.replaceDynamicFields(HoSoLuuTruFieldSupport.toJson(hosoFields));
                hoSoLuuTruDocumentRepository.save(hoso);
            });
        } catch (IllegalArgumentException ex) {
            // hoso_id khong hop le - bo qua dong bo, khong lam hong thao tac chinh.
        }
    }
}
