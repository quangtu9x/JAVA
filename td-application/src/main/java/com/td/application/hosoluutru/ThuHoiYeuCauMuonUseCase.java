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
 * Port module "Hồ sơ lưu trữ" — người mượn tự thu hồi yêu cầu đang chờ giải quyết, đúng
 * {@code PhieuYeuCauModal.jsx#handleThuHoi()} (dòng 211-265): guard {@code status="0"};
 * {@code status="2"} + xoá khỏi {@code user_dangkymuon} trên hồ sơ.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ThuHoiYeuCauMuonUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy phiếu yêu cầu";
    private static final String GUARD_MESSAGE = "Phiếu yêu cầu không ở trạng thái chờ giải quyết";

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
        if (!HoSoLuuTruLifecycleStatus.PHIEU_CHO_GIAI_QUYET.equals(
                HoSoLuuTruFieldSupport.stringField(phieuFields, "status"))) {
            return Result.failure(GUARD_MESSAGE);
        }

        String creator = HoSoLuuTruFieldSupport.stringField(phieuFields, HoSoLuuTruLifecycleStatus.FIELD_USER_CREATOR);
        String hosoIdStr = HoSoLuuTruFieldSupport.stringField(phieuFields, HoSoLuuTruLifecycleStatus.FIELD_HOSO_ID);
        phieuFields.put("status", HoSoLuuTruLifecycleStatus.PHIEU_NGUOI_MUON_THU_HOI);
        phieu.replaceDynamicFields(HoSoLuuTruFieldSupport.toJson(phieuFields));
        hoSoLuuTruPhieuYeuCauDocumentRepository.save(phieu);

        removeFromDangKyMuon(hosoIdStr, creator);
        return Result.success(phieu.getId());
    }

    private void removeFromDangKyMuon(String hosoIdStr, String creator) {
        if (hosoIdStr == null || hosoIdStr.isBlank() || creator.isBlank()) {
            return;
        }
        try {
            UUID hosoId = UUID.fromString(hosoIdStr.trim());
            hoSoLuuTruDocumentRepository.findById(hosoId).ifPresent(hoso -> {
                Map<String, Object> hosoFields = new LinkedHashMap<>(
                    dynamicDocumentFieldsService.parseDynamicFields(hoso.getDynamicFieldsJson()));
                HoSoLuuTruFieldSupport.removeFromSemicolonField(
                    hosoFields, HoSoLuuTruLifecycleStatus.FIELD_USER_DANGKYMUON, creator);
                hoso.replaceDynamicFields(HoSoLuuTruFieldSupport.toJson(hosoFields));
                hoSoLuuTruDocumentRepository.save(hoso);
            });
        } catch (IllegalArgumentException ex) {
            // hoso_id khong hop le - bo qua dong bo, khong lam hong thao tac chinh.
        }
    }
}
