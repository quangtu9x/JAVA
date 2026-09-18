package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documentregister.DocumentRegisterRepository;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.domain.documentregister.DocumentRegister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-210 (VBDen Core) — DoD (1): tiếp nhận văn bản đến. Uỷ quyền cho
 * {@link AddDynamicDocumentUseCase} (BL-101) với {@code documentType} cố định {@code "vbden"}.
 *
 * <p>BL-210/211 (bổ sung 19/08/2026): khi request có {@code documentRegisterId} (chọn "sổ văn bản",
 * BL-116), số đến được phát/ghi nhận SCOPE THEO SỔ ĐÓ (mirror {@code LuuVanBanDen.java
 * tang_so_cho_so_van_ban} — số kế tiếp = max lịch sử đã phát + 1, ghi thêm vào lịch sử ngay khi tạo
 * thành công) thay vì rơi về counter phẳng {@code DocumentNumberingService} (BL-104) như trước — đúng
 * phát hiện đã ghi ở {@code docs/migration-backlog/05-review-p0-p1-bl210-vs-domino.md} mục 4 #1.
 * Không có {@code documentRegisterId} (caller cũ chưa cập nhật) → hành vi giữ nguyên như trước.</p>
 *
 * <p><b>Slice 9 (20/08/2026, sau đối chiếu lại Domino {@code GetSoVanBanDen.java})</b>: Domino thật
 * dùng 2 database TÁCH BIỆT — {@code vbden.nsf} (văn bản gốc) và {@code vbden_so.nsf} (bản ghi "sổ",
 * field {@code document_number} riêng, trỏ về gốc qua {@code parentid}, có đời sống độc lập — xoá/
 * thu hồi KHỎI SỔ không đụng văn bản gốc, xem view {@code sovanban_bixoa}/{@code sovanban_bithuhoi}
 * trong {@code docs/views_domino/view_vanbanden_so.dxl}). Bản Java TRƯỚC ĐÓ (19/08) chỉ gắn field lên
 * CHÍNH văn bản gốc (đơn-bản-ghi) — sau khi đối chiếu, bổ sung {@link #createRegisterEntryDocument}
 * tạo THÊM 1 document {@code documentType="vbden_so"} (con, {@code parentid} = văn bản gốc) — khớp
 * đúng kiến trúc 2-bản-ghi của Domino. CÓ CHỦ Ý giữ nguyên việc gắn field lên văn bản gốc (không xoá)
 * vì {@link KiemTrungSoDenUseCase} đang query field đó trên {@code documentType="vbden"} — xoá đi sẽ
 * làm hỏng tính năng đang hoạt động; field trên văn bản gốc và trên document {@code vbden_so} vừa tạo
 * là 2 bản SONG SONG có chủ đích (giống chính Domino cũng lưu {@code document_number} ở CẢ 2 nơi khi
 * nhìn theo field trong DXL — {@code vbden.nsf} không có view riêng cho field này nhưng field vẫn tồn
 * tại trên document gốc qua {@code TiepNhanVbdenUseCase} cũ, còn {@code vbden_so.nsf} là bản ghi sổ
 * chính thức).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TiepNhanVbdenUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    /** Slice 9: documentType của bản ghi "sổ" riêng — tương đương database {@code vbden_so.nsf}. */
    public static final String REGISTER_ENTRY_DOCUMENT_TYPE = "vbden_so";
    private static final String DOCUMENT_NUMBER_FIELD = "document_number";
    private static final String DOCUMENT_REGISTER_FIELD = "document_register";
    private static final String DOCUMENT_REGISTER_ID_FIELD = "document_register_id";
    private static final String IDENTIFIER_CREATOR_FIELD = "identifier_creator";
    private static final String PARENT_ID_FIELD = "parentid";
    // R6-lite (22/08/2026) - xem javadoc applyDefaultAssignerAsProcessor().
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_ASSIGNER_FIELD = "user_assigner";
    // dxl view_vanbanden_so.dxl 297-325 (sovanban_sx_ngayden): sort theo received_date can field nay
    // ton tai NGAY TREN document "so" (khong the sort SQL tren field cua document khac) - snapshot
    // tai thoi diem tao (CUNG giao dich voi van ban goc, khong bao gio lech vi chua co luong "hieu
    // chinh" nao trong Java sua lai received_date sau khi tao - xem javadoc GetSoVanBanDenUseCase).
    private static final String RECEIVED_DATE_FIELD = "received_date";
    // Bo sung 03/09/2026 (bao loi nguoi dung: nut "Lay lai VB" khong bao gio hien vi Java chua tung
    // ghi field nay - xem javadoc ChuyenXuLyVbdenUseCase#AUTHOR_FIELD, cung field, cung y nghia:
    // "ai tung lien quan van ban nay" - seed bang nguoi tao luc tiep nhan, ChuyenXuLyVbdenUseCase
    // tich luy tiep moi lan chuyen xu ly).
    private static final String AUTHOR_FIELD = "author";

    private final AddDynamicDocumentUseCase addDynamicDocumentUseCase;
    private final DocumentRegisterRepository documentRegisterRepository;
    private final CurrentUserProvider currentUserProvider;

    public Result<UUID> execute(TiepNhanVbdenRequest request) {
        if (request.getDocumentRegisterId() != null) {
            var result = applyDocumentRegisterNumbering(request);
            if (result != null) {
                return result;
            }
        }
        applyDefaultAssignerAsProcessor(request.getFields());
        // Server-authoritative (khong nhan tu FE) - cung nguyen tac voi identifier o
        // ChuyenXuLyVbdenUseCase (resolveIdentifierByLevelUseCase).
        currentUserProvider.currentUsername().ifPresent(username -> request.getFields().put(AUTHOR_FIELD, username));

        var addRequest = new AddDynamicDocumentRequest();
        addRequest.setTitle(request.getSubject());
        addRequest.setDocumentType(DOCUMENT_TYPE);
        addRequest.setStatus(request.getStatus());
        addRequest.setFields(request.getFields());
        addRequest.setDateFields(request.getDateFields());
        addRequest.setNumberFields(request.getNumberFields());
        addRequest.setRichtextFields(request.getRichtextFields());
        var result = addDynamicDocumentUseCase.execute(addRequest);

        if (result.isSuccess() && request.getDocumentRegisterId() != null) {
            var entryResult = createRegisterEntryDocument(result.getData(), request);
            if (!entryResult.isSuccess()) {
                log.error("Tao ban ghi so van ban (vbden_so) that bai sau khi da tao van ban goc {}: {}",
                    result.getData(), entryResult.getError());
                return Result.failure("Lưu văn bản thành công nhưng tạo bản ghi sổ thất bại: " + entryResult.getError());
            }
        }
        return result;
    }

    // Slice 9: tao document con documentType="vbden_so" (tuong duong 1 dong trong database
    // vbden_so.nsf cua Domino) - chi copy field can cho loc/sort/hien thi RIENG cua ban ghi so
    // (parentid, document_number, document_register*, identifier_creator, received_date); moi field
    // hien thi khac (subject, code, status...) doc LIVE tu van ban goc luc query (xem GetSoVanBanDenUseCase),
    // tranh nguy co lech du lieu cua viec nhan ban toan bo metadata.
    private Result<UUID> createRegisterEntryDocument(UUID parentId, TiepNhanVbdenRequest request) {
        Map<String, String> parentFields = request.getFields();
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(request.getSubject());
        childRequest.setDocumentType(REGISTER_ENTRY_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put(PARENT_ID_FIELD, parentId.toString());
        putIfPresent(childFields, DOCUMENT_NUMBER_FIELD, parentFields);
        putIfPresent(childFields, DOCUMENT_REGISTER_ID_FIELD, parentFields);
        putIfPresent(childFields, DOCUMENT_REGISTER_FIELD, parentFields);
        putIfPresent(childFields, IDENTIFIER_CREATOR_FIELD, parentFields);
        putIfPresent(childFields, RECEIVED_DATE_FIELD, parentFields);
        childRequest.setFields(childFields);
        childRequest.setDateFields(RECEIVED_DATE_FIELD);

        return addDynamicDocumentUseCase.execute(childRequest);
    }

    /**
     * R6-lite (22/08/2026, {@code LuuVanBanDen.java} thật dòng ~196-203 — đã xác nhận qua dữ liệu
     * mẫu {@code docs/views_domino/data_vbden_mau.txt}): khi tiếp nhận MỚI, nếu widget "Đơn vị/
     * người xử lý văn bản" chưa gán ai ({@code user_process} rỗng) nhưng đã chọn "Lãnh đạo phân
     * công" ({@code user_assigner} có giá trị) — tự gán {@code user_process = user_assigner}, để
     * văn bản xuất hiện ngay ở hàng đợi "chờ xử lý" của lãnh đạo đó. Đơn giản hơn bản trong
     * {@link LuuVanBanDenUseCase} (không cần so với "trước khi lưu" vì đây luôn là lần đầu — chưa
     * hề có {@code user_assigner} cũ).
     */
    private void applyDefaultAssignerAsProcessor(Map<String, String> fields) {
        String userProcess = fields.get(USER_PROCESS_FIELD);
        if (userProcess != null && !userProcess.isBlank()) {
            return;
        }
        String userAssigner = fields.get(USER_ASSIGNER_FIELD);
        if (userAssigner == null || userAssigner.isBlank()) {
            return;
        }
        fields.put(USER_PROCESS_FIELD, userAssigner);
    }

    private void putIfPresent(Map<String, String> target, String key, Map<String, String> source) {
        String value = source.get(key);
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    /** @return {@code null} nếu OK để tiếp tục lưu văn bản; {@code Result} lỗi nếu phải dừng lại. */
    private Result<UUID> applyDocumentRegisterNumbering(TiepNhanVbdenRequest request) {
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
                + "' không tự động đánh số — vui lòng nhập số văn bản đến thủ công");
        }

        try {
            register.recordIssuedNumber(issuedNumber);
        } catch (IllegalStateException ex) {
            return Result.failure(ex.getMessage());
        }
        documentRegisterRepository.save(register);

        fields.put(DOCUMENT_REGISTER_FIELD, register.getName());
        fields.put(DOCUMENT_REGISTER_ID_FIELD, register.getId().toString());
        String identifierCreator = fields.get(IDENTIFIER_CREATOR_FIELD);
        if (identifierCreator == null || identifierCreator.isBlank()) {
            fields.put(IDENTIFIER_CREATOR_FIELD, register.getIdentifier());
        }
        return null;
    }
}
