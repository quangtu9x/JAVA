package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.documentregister.DocumentRegisterRepository;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.domain.documentregister.DocumentRegister;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * BL-220 (VBDi Core) — DoD (1): soạn thảo văn bản đi. Không có servlet "tạo văn bản đi" riêng trong
 * source Domino thật — soạn thảo thật sự nằm trọn trong module {@code duthao} (dự thảo, BL-230,
 * chưa xây: {@code InitDraft.java}/{@code DraftProcesses.java}), và {@code vbdi} chỉ nhận document
 * chính thức sau khi {@code DraftToDocumentOut}/{@code XuLyDuThao.ChuyenTrangThaiDuThaoDaBanHanh}
 * chuyển đổi. Vì BL-230 chưa xây và không nằm trong phạm vi BL-220, use case này tạo thẳng 1
 * document {@code documentType=vbdi} ở trạng thái {@link VbdiStatus#DRAFT} qua hạ tầng chung
 * (BL-101) — đúng tinh thần tái dùng đã áp dụng cho BL-250 (không xây lại module phụ thuộc chưa
 * tồn tại), đơn giản hoá so với state machine đầy đủ của {@code duthao} thật.
 *
 * <p><b>Lưu ý về số đi</b>: {@code documentNumberingService} (BL-104) đã cấu hình sẵn
 * {@code document_type="vbdi"} là active, nên {@link AddVbdiDynamicDocumentUseCase} sẽ tự sinh
 * {@code document_number} ngay tại bước này — sớm hơn ngữ nghĩa thật ("Ban hành → sinh số đi",
 * DoD (3)). Đây là hành vi có sẵn từ BL-104 (thiết kế trước khi có chi tiết nghiệp vụ BL-220), nằm
 * ngoài phạm vi sửa của BL-220 (không đụng tới {@code AddVbdiDynamicDocumentUseCase}/cấu hình BL-104).
 * {@link BanHanhVbdiUseCase} sinh lại và GHI ĐÈ {@code document_number} tại đúng bước ban hành,
 * đảm bảo số đi cuối cùng luôn đúng thời điểm dù giá trị tạm ở bước soạn thảo có bị bỏ phí 1 lượt
 * counter — xem ghi chú đầy đủ tại Migration Status.
 *
 * <p><b>Bugfix 28/08/2026 (phát hiện khi port route FE "vbdi/xuly/cq_toanboso"):</b> FE thật
 * ({@code ChiTietModal.jsx}, hàm {@code onFinish}, nhánh "tạo mới" dòng ~1239-1252) LUÔN gửi
 * {@code status="2"} (mã trạng thái Domino thô, KHÔNG BAO GIỜ để trống) cho MỌI lần "Thêm mới" văn
 * bản đi — kể cả từ màn {@code cq_toanboso} lẫn mọi màn vbdi khác dùng chung modal này. Trước bugfix
 * này, {@code execute()} chỉ default {@link VbdiStatus#DRAFT} khi {@code status} rỗng, còn lại LƯU
 * NGUYÊN VĂN giá trị client gửi ("2") vào cột {@code status} — khiến văn bản mới tạo KHÔNG BAO GIỜ
 * khớp bất kỳ query nào lọc theo hằng số ngữ nghĩa ({@link VbdiStatus#ISSUED}/{@link
 * VbdiStatus#PENDING_SIGNATURE}) ở {@code ViewQueryService} ({@code vwvandi_dabanhanh},
 * {@code vwCreator_Drafting} phía vbdi, VÀ view {@code vwCreator_DocumentOut_TextBook} mới port) —
 * cũng khiến {@link BanHanhVbdiUseCase}/{@link com.td.application.vbdi.GuiEmailVbdiUseCase} không
 * bao giờ nhận diện đúng trạng thái (2 use case đó guard bằng đúng hằng số ngữ nghĩa). {@code "2"}
 * khớp {@code status="2"} trong {@code SELECT Form="DocumentOut" & status="2"} (dxl
 * {@code view_vanbandi.dxl} — mọi view "đã ban hành"/"sổ văn bản đi"), ĐÚNG {@link VbdiStatus#ISSUED};
 * {@code "0"} khớp {@code status="0"} (view "chờ vào sổ" — xem javadoc
 * {@code ViewQueryService#queryVbdiWaitingForRegistration}), ĐÚNG {@link VbdiStatus#DRAFT} (đã map
 * sẵn ở nơi đó). Chỉ map 2 mã CÓ BẰNG CHỨNG dxl trực tiếp này — KHÔNG đoán mã {@code "1"} (chưa xác
 * nhận), giữ nguyên hành vi passthrough cho mọi giá trị khác (kể cả giá trị test hiện có
 * {@code "CUSTOM"} — xem {@code SoanThaoVbdiUseCaseTest}).</p>
 *
 * <p><b>Bổ sung 28/08/2026 ("Chọn sổ văn bản" ở {@code ChiTietModal.jsx}, port
 * {@code SubFormThongTinCoBan.jsx}/{@code getSoMacDinh}/{@code updateSoVanBan})</b>: khi request có
 * {@code documentRegisterId} (chọn "sổ văn bản đi"), {@code document_number} được phát/ghi nhận
 * SCOPE THEO SỔ ĐÓ (mirror {@code applyDocumentRegisterNumbering} của
 * {@code TiepNhanVbdenUseCase}/BL-210: số kế tiếp = max lịch sử đã phát + 1, ghi thêm vào lịch sử
 * NGAY khi soạn thảo thành công) THAY VÌ rơi về counter phẳng {@link
 * com.td.application.numbering.DocumentNumberingService} (BL-104, xem "Lưu ý về số đi" ở trên) —
 * đúng cơ chế thật (mỗi phòng/ban 1 sổ riêng, không phải counter chung toàn hệ thống). Đọc/ghi
 * thẳng bảng {@code document_registers} DÙNG CHUNG (BL-116, {@link DocumentRegister}) — sổ văn bản
 * quản lý TẬP TRUNG cho cả văn bản đến lẫn văn bản đi, phân biệt bằng cột
 * {@code documentRegisterType} (yêu cầu người dùng 28/08/2026: "sổ quản lý tập trung và phân biệt
 * document_register_type, assigner" — KHÔNG tách bảng riêng cho vbdi). Không có
 * {@code documentRegisterId} (client cũ chưa cập nhật) → hành vi giữ nguyên như trước (rơi về
 * BL-104).</p>
 */
@Service
@RequiredArgsConstructor
public class SoanThaoVbdiUseCase {

    public static final String DOCUMENT_TYPE = "vbdi";

    /** Giá trị cột {@code document_registers.document_register_type} cho sổ văn bản đi (BL-116). */
    public static final String REGISTER_TYPE = "Văn bản đi";

    private static final String DOCUMENT_NUMBER_FIELD = "document_number";
    private static final String DOCUMENT_REGISTER_FIELD = "document_register";
    private static final String DOCUMENT_REGISTER_ID_FIELD = "document_register_id";

    private final AddVbdiDynamicDocumentUseCase addVbdiDynamicDocumentUseCase;
    private final DocumentRegisterRepository documentRegisterRepository;
    private final com.td.application.documentlinks.RelatedDocumentSnapshots relatedDocumentSnapshots;

    public Result<UUID> execute(SoanThaoVbdiRequest request) {
        if (request.getDocumentRegisterId() != null) {
            var numberingError = applyDocumentRegisterNumbering(request);
            if (numberingError != null) {
                return numberingError;
            }
        }

        var addRequest = new AddDynamicDocumentRequest();
        addRequest.setTitle(request.getSubject());
        addRequest.setDocumentType(DOCUMENT_TYPE);
        addRequest.setStatus(resolveStatus(request.getStatus()));
        // 06/10/2026 (bang document_links giai doan 2 - docs/architecture/document-links.md): documentin_obj /
        // vanban_lienquan_obj KHONG luu trong dynamic_fields nua - tao moi
        // thi chuyen phan tu thanh lien ket.
        Map<String, String> fields = request.getFields() == null
            ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(request.getFields());
        Map<String, Object> relatedPayload = relatedDocumentSnapshots.strip(fields);
        addRequest.setFields(fields);
        addRequest.setDateFields(request.getDateFields());
        addRequest.setNumberFields(request.getNumberFields());
        addRequest.setRichtextFields(request.getRichtextFields());
        Result<UUID> result = addVbdiDynamicDocumentUseCase.execute(addRequest);
        if (result.isSuccess()) {
            relatedDocumentSnapshots.importFromPayload(relatedPayload, result.getData(), com.td.application.documentlinks.RelatedDocumentSnapshots.SOURCE_VBDI);
        }
        return result;
    }

    private String resolveStatus(String rawStatus) {
        String translated = VbdiLegacyStatusCodes.toInternalOrNull(rawStatus);
        return translated == null ? VbdiStatus.DRAFT : translated;
    }

    /**
     * Mirror {@code TiepNhanVbdenUseCase#applyDocumentRegisterNumbering} (BL-210/211) — đọc/ghi
     * thẳng bảng {@code document_registers} DÙNG CHUNG (BL-116), không cần lọc lại theo
     * {@link #REGISTER_TYPE} vì {@code documentRegisterId} do FE gửi lên đã được chọn từ đúng
     * dropdown lọc theo loại sổ (xem {@code SubFormThongTinCoBan.jsx}/{@code getSoMacDinh}).
     *
     * @return {@code null} nếu OK để tiếp tục soạn thảo; {@code Result} lỗi nếu phải dừng lại.
     */
    private Result<UUID> applyDocumentRegisterNumbering(SoanThaoVbdiRequest request) {
        UUID documentRegisterId = request.getDocumentRegisterId();
        var registerOpt = documentRegisterRepository.findByIdAndDeletedOnIsNull(documentRegisterId);
        if (registerOpt.isEmpty()) {
            return Result.failure("Không tìm thấy sổ văn bản với ID: " + documentRegisterId);
        }
        DocumentRegister register = registerOpt.get();

        Map<String, String> fields = request.getFields();
        String existingNumber = fields.get(DOCUMENT_NUMBER_FIELD);
        int issuedNumber;
        if (existingNumber != null && !existingNumber.isBlank()) {
            try {
                issuedNumber = Integer.parseInt(existingNumber.trim());
            } catch (NumberFormatException ex) {
                return Result.failure("document_number không hợp lệ: " + existingNumber);
            }
        } else if (register.isAutoIncrement()) {
            issuedNumber = register.peekNextNumber();
            fields.put(DOCUMENT_NUMBER_FIELD, String.valueOf(issuedNumber));
        } else {
            return Result.failure("Sổ văn bản '" + register.getName()
                + "' không tự động đánh số — vui lòng nhập số văn bản đi thủ công");
        }

        try {
            register.recordIssuedNumber(issuedNumber);
        } catch (IllegalStateException ex) {
            return Result.failure(ex.getMessage());
        }
        documentRegisterRepository.save(register);

        fields.put(DOCUMENT_REGISTER_FIELD, register.getName());
        fields.put(DOCUMENT_REGISTER_ID_FIELD, register.getId().toString());
        return null;
    }
}
