package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentRepository;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nút "Cho ý kiến"/"Bổ sung ý kiến" ({@code VBDenButtonActionXuLy.jsx#btnChoYkienTVUB}/
 * {@code #btnChoYkien}, mở {@code ChoYKienModal.jsx} với {@code action="capnhatykien"} — modal DÙNG
 * CHUNG tiêu đề "Cập nhật kết quả xử lý văn bản" với {@link CapNhatKetQuaXuLyUseCase}/
 * {@link KetThucXuLyUseCase}/{@link CapNhatPhoiHopUseCase}, xem
 * {@code ChoYKienModal.jsx#title}). Yêu cầu người dùng 14/09/2026 ("modal Câp nhật kết quả xử lý văn
 * bản -> Đồng ý chưa cutoverFE (gọi qlvb/vbden/CapNhatVanBanDen cũ)") — điều tra qua đọc trực tiếp
 * Domino xác nhận action {@code capnhatykien} là 1 trong 4 action mà modal này gọi, KHÔNG nằm trong 3
 * action đã được {@code ChoYKienModal.jsx} cutover trước đó ({@code vanthuketthucxuly}/
 * {@code ketthucxuly}/{@code capnhatphoihop}) — vẫn rơi vào nhánh {@code requestPOST_FORM} Domino cũ.
 *
 * <p><b>Xác nhận qua đọc TRỰC TIẾP {@code CapNhatVanBanDen.java}</b>: switch-case
 * {@code "capnhatykien"} (dòng 186-188) VÀ hàm {@code capnhatykien()} riêng (dòng 409-423) ĐỀU bị
 * comment out hoàn toàn nội dung thật ({@code capnhatykien();} bị comment tại chỗ gọi, thân hàm chỉ
 * còn {@code msg.setStatus(true)}) — action này KHÔNG mutate BẤT KỲ field nghiệp vụ nào trên văn bản
 * đến, KHÔNG tính lại status, KHÔNG xử lý {@code res_obj} tiếp (khác hẳn
 * {@code vanthuketthucxuly}/{@code ketthucxuly} — xem javadoc 2 lớp đó).</p>
 *
 * <p><b>NHƯNG</b> {@code CapNhatThongTin()} (preamble, chạy TRƯỚC switch, VÔ ĐIỀU KIỆN với MỌI
 * action) LUÔN tạo 1 bản ghi {@code vbden_res} MỚI, ghi lại toàn bộ field multipart gửi lên
 * ({@code opinion}/{@code creator}/{@code position}/{@code identifier}/{@code res_obj}) — đây CHÍNH
 * LÀ hành vi quan sát được thật sự của action này (1 bản ghi lịch sử "đã cho ý kiến", hiển thị ở tab
 * "Quá trình xử lý" — cùng phát hiện đã áp dụng cho {@code capnhatvanbandi}, xem javadoc
 * {@code CapNhatVanBanDiUseCase}). Port ĐÚNG: tạo bản ghi {@code vbden_res}
 * ({@code action="capnhatykien"}), lưu {@code res_obj} nếu có (KHÔNG xử lý tiếp, chỉ lưu — đúng
 * Domino), KHÔNG đụng field nào trên văn bản cha, KHÔNG tính lại status.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CapNhatYKienVbdenUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_CAP_NHAT_YKIEN = "capnhatykien";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;

    public Result<UUID> execute(CapNhatYKienVbdenRequest request) {
        if (request == null || request.getParentid() == null) {
            return Result.failure("Thieu van ban den can cap nhat");
        }
        var parentOptional = documentRepository.findById(request.getParentid());
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (!DOCUMENT_TYPE.equalsIgnoreCase(parentOptional.get().getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (actingUsername == null || actingUsername.isBlank()) {
            return Result.failure("Khong xac dinh duoc nguoi dung hien tai");
        }

        return createProcessingRecord(request, actingUsername);
    }

    // Domino CapNhatThongTin() (preamble, chay VO DIEU KIEN truoc switch) - xem javadoc lop.
    private Result<UUID> createProcessingRecord(CapNhatYKienVbdenRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_CAP_NHAT_YKIEN);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", ACTION_CAP_NHAT_YKIEN);
        childFields.put("nguoi_thuc_hien", actingUsername);
        String displayCreator = request.getCreatorName() != null && !request.getCreatorName().isBlank()
            ? request.getCreatorName() : actingUsername;
        childFields.put("creator", displayCreator);
        if (request.getPosition() != null && !request.getPosition().isBlank()) {
            childFields.put("position", request.getPosition());
        }
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            childFields.put("opinion", request.getOpinion());
        }
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));
        addResObjIfPresent(childFields, request.getResObj());
        childRequest.setFields(childFields);

        return addVbdenResDocumentUseCase.execute(childRequest);
    }

    // Domino luu res_obj tren docQTXL du switch-case khong xu ly tiep (generic field save o preamble) -
    // xem javadoc CapNhatKetQuaXuLyUseCase/KetThucXuLyUseCase (cung helper, khong dung chung class vi
    // moi use case doc field request rieng).
    private void addResObjIfPresent(Map<String, String> childFields, List<VbdenReplyDocumentRef> resObj) {
        if (resObj == null || resObj.isEmpty()) {
            return;
        }
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        for (VbdenReplyDocumentRef ref : resObj) {
            if (ref == null) {
                continue;
            }
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("id", ref.getId() != null ? ref.getId().toString() : "");
            node.put("code", ref.getCode() != null ? ref.getCode() : "");
            // 05/10/2026: giữ "action" (FE mở chi tiết theo key này) - xem VbdenReplyDocumentRef#action.
            node.put("action", ref.resolvedAction());
            if (ref.getPromulgationDate() != null && !ref.getPromulgationDate().isBlank()) {
                node.put("promulgationDate", ref.getPromulgationDate());
            }
            if (ref.getUnitPublish() != null && !ref.getUnitPublish().isBlank()) {
                node.put("unitPublish", ref.getUnitPublish());
            }
            array.add(node);
        }
        if (!array.isEmpty()) {
            childFields.put("res_obj", array.toString());
        }
    }
}
