package com.td.application.vbcddh;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BL-250 (VBCDDH Core) — DoD (2): phân công nhiệm vụ cho 1..N đơn vị thực hiện, từ 1 văn bản chỉ
 * đạo điều hành ({@code documentType=vbcddh}, {@code form=vanban} — đã tạo được qua cơ chế chung
 * BL-101 {@code POST /api/v1/documents/add}, đúng DoD (1), không cần code riêng). Domino thật
 * ({@code PhanCongNhiemVu.java}) tự nó chỉ là handler lưu document tổng quát (không có logic
 * "idvanban + donvithuchien" cụ thể) — nghiệp vụ thật "1 văn bản → N nhiệm vụ" nằm ở FE (vòng lặp
 * gọi {@code qlvb/add} theo từng đơn vị) cộng với hook nền {@code CapNhatTinhTrangVBCD} tính lại
 * trạng thái tổng hợp trên văn bản gốc sau mỗi lần thêm/sửa nhiệm vụ con. Use case này gộp cả 2 vế
 * (tạo N nhiệm vụ + rollup) vào 1 lần gọi để đảm bảo tính nhất quán (tránh N lần gọi rời rạc từ FE
 * dẫn tới rollup chạy N lần thay vì 1 lần cuối cùng).
 *
 * <p><b>Cập nhật 08/09/2026</b> (yêu cầu người dùng "tạo bảng riêng, sử dụng dynamic fields"): di
 * trú từ {@code DocumentRepository}/{@code AddDynamicDocumentUseCase} (bảng {@code documents} dùng
 * chung, BL-101) sang {@link VbcddhDocumentRepository}/{@link AddVbcddhDynamicDocumentUseCase} (bảng
 * vật lý ĐỘC LẬP {@code vbcddh_documents} — xem javadoc {@code com.td.domain.vbcddh.VbcddhDocument}).
 * Hành vi nghiệp vụ KHÔNG đổi — chỉ đổi tầng lưu trữ, đúng tiền lệ vbdi (BL-220).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PhanCongNhiemVuUseCase {

    public static final String DOCUMENT_TYPE = "vbcddh";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản chỉ đạo điều hành";
    private static final String FORM_VANBAN = "vanban";
    private static final String DEFAULT_TINHTRANG = "Chưa thực hiện";

    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final AddVbcddhDynamicDocumentUseCase addVbcddhDynamicDocumentUseCase;
    private final VbcddhStatusRollupService statusRollupService;

    public Result<List<UUID>> execute(PhanCongNhiemVuRequest request) {
        var parentOptional = vbcddhDocumentRepository.findById(request.getIdVanBan());
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var parent = parentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(parent.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        List<UUID> created = new ArrayList<>();
        for (NhiemVuAssignment assignment : request.getAssignments()) {
            var childResult = createNhiemVu(request.getIdVanBan(), assignment);
            if (!childResult.isSuccess()) {
                throw new IllegalStateException(
                    "Tạo nhiệm vụ thất bại cho đơn vị " + assignment.getDonViThucHien() + ": " + childResult.getError());
            }
            created.add(childResult.getData());
        }

        statusRollupService.recomputeParentStatus(request.getIdVanBan());

        return Result.success(created);
    }

    // Domino: qlvb/add?databasetype=vbcddh (form=nhiemvu), 1 bản ghi/1 đơn vị (NhiemVuChiTietPage.jsx).
    private Result<UUID> createNhiemVu(UUID idVanBan, NhiemVuAssignment assignment) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(AddDynamicDocumentRequest.labelTitle(assignment.getDonViThucHien())); // 19/10/2026
        childRequest.setDocumentType(DOCUMENT_TYPE);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("form", "nhiemvu");
        fields.put("idvanban", idVanBan.toString());
        fields.put("donvithuchien", assignment.getDonViThucHien());
        fields.put("donvithuchienhide", assignment.getDonViThucHienHide());
        fields.put("tinhtrang", DEFAULT_TINHTRANG);
        fields.put("quahan", "0");
        if (assignment.getNoiDungChiDao() != null && !assignment.getNoiDungChiDao().isBlank()) {
            fields.put("noidungchidao", assignment.getNoiDungChiDao());
        }
        if (assignment.getHanXuLy() != null && !assignment.getHanXuLy().isBlank()) {
            fields.put("hanxuly_date", assignment.getHanXuLy());
        }
        childRequest.setFields(fields);

        return addVbcddhDynamicDocumentUseCase.execute(childRequest);
    }
}
