package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.vbden.AddVbdenResDocumentUseCase;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 11/09/2026 (yêu cầu người dùng: "Lỗi cập nhật văn bản đến khi thao tác lưu văn bản đi vì chưa có
 * qlvb/vbden/CapNhatVanBanDen?databasetype=vbden&action=capnhatvanbandi&parentid=..." — đọc và port
 * sang, cutover FE, bổ sung tài liệu): port action Domino {@code capnhatvanbandi}
 * ({@code ChiTietModal.jsx} nhánh {@code else} dòng ~1517-1530, gọi MỖI KHI lưu văn bản đi bình thường
 * — KHÁC {@code status=="0"}/"Vào sổ" đã port ở {@link com.td.application.vbden.CapNhatKetQuaXuLyUseCase}
 * qua action {@code vaosovanbandi}) khi văn bản đi đó có {@code documentin_obj} (đã "Trả lời" ít nhất 1
 * văn bản đến qua {@link TraLoiVanBanDenUseCase}).
 *
 * <p><b>Xác nhận qua đọc TRỰC TIẾP Domino gốc</b> (agent điều tra riêng, đọc toàn bộ
 * {@code CapNhatVanBanDen.java} 808 dòng + {@code TDDocument.java#CreateOrUpdateDocument()}):</p>
 * <ul>
 *   <li><b>KHÔNG có case switch nào tên {@code "capnhatvanbandi"}</b> trong
 *   {@code CapNhatVanBanDen.java} (dòng 76-209) — rơi vào nhánh {@code default} (dòng 206-208), và bản
 *   thân nhánh {@code default} đó KHÔNG làm gì (chỉ 1 dòng comment + {@code break}). Tức là: field
 *   nghiệp vụ trên CHÍNH văn bản đến (parent, {@code user_process}/{@code documentins_obj}/...)
 *   KHÔNG hề bị thay đổi bởi action này — khớp với comment cũ trong {@code ChiTietModal.jsx} (đã xác
 *   nhận ĐÚNG phần này).</li>
 *   <li><b>NHƯNG</b>: {@code CapNhatThongTin()} (dòng 58-73, chạy TRƯỚC switch, VÔ ĐIỀU KIỆN với MỌI
 *   action kể cả action không khớp case nào) LUÔN tạo 1 document MỚI trong CSDL {@code vbden_res}
 *   (qua {@code CreateOrUpdateDocument()}, {@code TDDocument.java} dòng 357-360 — không có tham số
 *   {@code id} nên luôn {@code createDocument()}, không bao giờ cập nhật document có sẵn), ghi toàn bộ
 *   field multipart form-data gửi lên ({@code Form}, {@code action}, {@code opinion}, {@code creator},
 *   {@code position}, {@code identifiers}, {@code identifier}, {@code parentid_send}, {@code res_obj})
 *   qua {@code ProcessDocument()} generic (dòng 404-508) rồi {@code save()} — comment cũ ĐÃ BỎ SÓT
 *   phần side-effect này (chỉ xét trong switch, không xét preamble của servlet).</li>
 *   <li>Đây CHÍNH LÀ lý do {@code "capnhatvanbandi"} xuất hiện trong danh sách action được
 *   {@code Ketquaxuly.jsx} (tab "Quá trình xử lý" của văn bản đến) hiển thị cùng
 *   {@code vaosovanbandi}/{@code taoduthao}/... — nó tạo 1 bản ghi lịch sử xử lý thật (KHÔNG phải
 *   no-op tuyệt đối), chỉ là bản ghi đó không kèm thay đổi field nghiệp vụ nào trên văn bản đến.</li>
 * </ul>
 *
 * <p><b>Nguồn gốc lỗi người dùng báo cáo</b>: {@code ChiTietModal.jsx} nhánh {@code else} (status khác
 * "0") gửi NGUYÊN {@code resdata.documentin_obj} KHÔNG lọc sang endpoint Domino cũ — khác nhánh
 * {@code status=="0"} ("Vào sổ", đã tách {@code javaNativeItems}/{@code legacyItems}). Khi văn bản đến
 * liên kết là Java-native ({@code id} = UUID Postgres 36 ký tự có gạch ngang), Domino
 * {@code dbDocument.getDocumentByUNID(parentUUID)} ({@code TDDocument.java:380}) nhận 1 chuỗi không
 * phải UNID hợp lệ — gây lỗi ở tầng Domino, FE nhận về thất bại (toast "Lỗi Cập nhật văn bản đến").</p>
 *
 * <p><b>Thiết kế Java — KHÔNG nhận lại {@code documentin_obj} từ client</b> (khác
 * {@link TraLoiVanBanDenUseCase}): server tự đọc {@code documentin_obj} đã lưu SẴN trên văn bản đi
 * (persist từ lần "Trả lời" trước đó) — vừa tránh lặp lại lỗi "gửi nguyên mảng không lọc" ở trên, vừa
 * tránh 1 vòng round-trip thừa (FE không cần tự dựng lại payload documentin). Với MỖI phần tử có
 * {@code id} là UUID Postgres hợp lệ (phần tử UNID Domino — vbden legacy — bị BỎ QUA, không thể tạo
 * bản ghi vbden_res Java cho 1 văn bản đến chỉ tồn tại bên Domino): tạo 1 bản ghi {@code vbden_res}
 * ({@code action="capnhatvanbandi"}) qua {@link AddVbdenResDocumentUseCase} — KHÔNG kiểm tra văn bản
 * đến có thực sự tồn tại hay không (đúng Domino gốc: preamble tạo {@code docQTXL} vô điều kiện, không
 * xác thực lại {@code docParentDocument}), KHÔNG động tới field nghiệp vụ nào trên văn bản đến (đúng
 * switch {@code default}).</p>
 *
 * <p>{@code documentin_obj} rỗng/không có phần tử Java-native nào hợp lệ → vẫn trả THÀNH CÔNG (no-op),
 * không có gì để ghi log thì không phải lỗi.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CapNhatVanBanDiUseCase {

    public static final String ACTION_CAP_NHAT_VAN_BAN_DI = "capnhatvanbandi";
    private static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    private static final String CODE_FULL_FIELD = "code_full";
    private static final String PROMULGATION_DATE_FIELD = "promulgation_date";
    private static final String DEFAULT_OPINION = "Cập nhật văn bản đi";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final com.td.application.documentlinks.RelatedDocumentSnapshots relatedDocumentSnapshots;

    public Result<UUID> execute(CapNhatVanBanDiRequest request) {
        if (request == null || request.getVbdiId() == null) {
            return Result.failure("Thieu van ban di can cap nhat");
        }

        var vbdiOptional = vbdiDocumentRepository.findById(request.getVbdiId());
        if (vbdiOptional.isEmpty() || vbdiOptional.get().isDeleted()
                || !SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(vbdiOptional.get().getDocumentType())) {
            return Result.failure("Khong tim thay van ban di");
        }
        var vbdiDocument = vbdiOptional.get();

        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (isBlank(actingUsername)) {
            return Result.failure("Khong xac dinh duoc nguoi dung hien tai");
        }

        Map<String, Object> vbdiFields = dynamicDocumentFieldsService.parseDynamicFields(
            vbdiDocument.getDynamicFieldsJson());
        // 06/10/2026: van ban den "tra loi" doc tu bang document_links (vbdi_traloi_vbden) - khong con
        // field documentin_obj trong dynamic_fields (docs/architecture/document-links.md).
        List<UUID> documentIn = relatedDocumentSnapshots.replyTargetIds(
            vbdiDocument.getId(), com.td.application.documentlinks.RelatedDocumentSnapshots.SOURCE_VBDI);
        if (documentIn.isEmpty()) {
            return Result.success(vbdiDocument.getId());
        }

        String vbdiDisplayCode = resolveVbdiDisplayCode(vbdiDocument, vbdiFields);
        String vbdiPromulgationDate = asString(vbdiFields.get(PROMULGATION_DATE_FIELD));
        String opinion = (request.getOpinion() != null && !request.getOpinion().isBlank())
            ? request.getOpinion().trim() : DEFAULT_OPINION;

        for (UUID vbdenId : documentIn) {
            var childResult = createProcessingRecord(
                vbdenId, actingUsername, request, vbdiDocument, vbdiDisplayCode, vbdiPromulgationDate, opinion);
            if (!childResult.isSuccess()) {
                log.warn("Khong tao duoc ban ghi cap nhat van ban di cho van ban den {} (tu vbdi {}): {}",
                    vbdenId, vbdiDocument.getId(), childResult.getError());
            }
        }

        return Result.success(vbdiDocument.getId());
    }

    // Domino CapNhatThongTin() dong 58-73 (preamble, chay VO DIEU KIEN truoc switch) - xem javadoc lop.
    private Result<UUID> createProcessingRecord(
            UUID vbdenId, String actingUsername, CapNhatVanBanDiRequest request, VbdiDocument vbdiDocument,
            String vbdiDisplayCode, String vbdiPromulgationDate, String opinion) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_CAP_NHAT_VAN_BAN_DI);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", vbdenId.toString());
        childFields.put("action", ACTION_CAP_NHAT_VAN_BAN_DI);
        childFields.put("nguoi_thuc_hien", actingUsername);
        String displayCreator = (request.getCreatorName() != null && !request.getCreatorName().isBlank())
            ? request.getCreatorName() : actingUsername;
        childFields.put("creator", displayCreator);
        if (request.getPosition() != null && !request.getPosition().isBlank()) {
            childFields.put("position", request.getPosition());
        }
        childFields.put("opinion", opinion);
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));

        ObjectNode resObj = OBJECT_MAPPER.createObjectNode();
        resObj.put("id", vbdiDocument.getId().toString());
        resObj.put("dbpath", "");
        resObj.put("code", vbdiDisplayCode);
        if (!vbdiPromulgationDate.isBlank()) {
            resObj.put("promulgation_date", vbdiPromulgationDate);
        }
        childFields.put("res_obj", resObj.toString());

        childRequest.setFields(childFields);
        return addVbdenResDocumentUseCase.execute(childRequest);
    }

    private String resolveVbdiDisplayCode(VbdiDocument vbdiDocument, Map<String, Object> vbdiFields) {
        Object codeFull = vbdiFields.get(CODE_FULL_FIELD);
        if (codeFull != null && !codeFull.toString().isBlank()) {
            return codeFull.toString();
        }
        return vbdiDocument.getTitle() == null ? "" : vbdiDocument.getTitle();
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
