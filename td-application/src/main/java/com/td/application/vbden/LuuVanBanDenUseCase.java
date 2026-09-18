package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documentregister.DocumentRegisterRepository;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.documentregister.DocumentRegister;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BL-210/211 (MVP, 21/08/2026) — tương thích 1 PHẦN {@code qlvb/vbden?action=luu} (Domino thật,
 * {@code LuuVanBanDen.java}) khi {@code id} có giá trị — nghĩa là HIỆU CHỈNH / "VÀO SỔ" 1 văn bản
 * đến ĐÃ TỒN TẠI (khác hẳn {@link TiepNhanVbdenUseCase}, chỉ xử lý tạo mới hoàn toàn).
 *
 * <p><b>Phạm vi đã port</b> (đọc lại {@code LuuVanBanDen.java} thật, ~770 dòng, phát hiện rất nhiều
 * việc gộp chung — xem D.18/D.19 tài liệu phân tích 01 và tài liệu phân tích 03, mục R1-R12):</p>
 * <ol>
 *   <li><b>R1 — Cập nhật field văn bản gốc</b>: merge {@code request.getFields()} (kiểu hoá qua
 *   {@code date_fields}/{@code number_fields}/{@code richtext_fields}, cùng cơ chế BL-101) vào
 *   {@code dynamic_fields} HIỆN CÓ của văn bản gốc (merge, không replace toàn bộ — field không có
 *   trong request giữ nguyên giá trị cũ).</li>
 *   <li><b>R2 — Tạo/cập nhật bản ghi "sổ"</b> (chỉ khi {@code documentRegisterId} có giá trị — đang
 *   "vào sổ" hoặc đổi sổ): tái dùng ĐÚNG logic đánh số {@code applyDocumentRegisterNumbering} (nhân
 *   bản có chủ đích từ {@link TiepNhanVbdenUseCase} — xem lý do KHÔNG refactor chung trong javadoc
 *   phương thức) + tạo bản ghi {@code vbden_so} mới (nếu {@code idso} rỗng — lần đầu vào sổ) hoặc
 *   cập nhật bản ghi đã có (nếu {@code idso} có giá trị — hiệu chỉnh lại sổ đã chọn trước đó).
 *   <b>Đã rà soát lại (22/08/2026, tài liệu 03 đề xuất copy thêm 9 field {@code form}/{@code code}/
 *   {@code subject}/{@code promulgation_date}/{@code deadline_date}/{@code unit_in_charge}/
 *   {@code signer}/{@code signer_position}/{@code priority} sang {@code vbden_so}) — QUYẾT ĐỊNH:
 *   KHÔNG áp dụng, vì mâu thuẫn trực tiếp với kiến trúc "đọc live, không nhân bản metadata" đã chốt
 *   ở Slice 9 (tài liệu 01, mục D.11) — {@link GetSoVanBanDenUseCase#joinWithParentDocument} đã đọc
 *   LIVE toàn bộ field hiển thị đó từ văn bản gốc qua {@code parentid} mỗi lần query, nên copy thêm
 *   sẽ tạo dữ liệu trùng lặp không ai đọc tới và có nguy cơ lệch khi văn bản gốc bị sửa sau — đúng
 *   rủi ro D.11 đã chủ động tránh. Người dùng đã xác nhận giữ nguyên thiết kế live-join này.</li>
 *   <li><b>R4 — Dọn khỏi danh sách "chờ vào sổ"</b> ({@code loai_khoi_ds_cho_vao_so}, bổ sung
 *   22/08/2026): khi {@code documentRegisterId} có giá trị (đang "vào sổ"), loại
 *   {@code identifier_creator} khỏi 3 field org {@code unit_process}/{@code unit_coordinate}/
 *   {@code unit_to_read} + field {@code contact_unit_identifier}, đồng thời đánh dấu tương ứng vào
 *   {@code *_registered} — khiến văn bản biến mất khỏi các hàng đợi "chờ vào sổ" đã port
 *   ({@code captrengiao}/{@code dennoibo}/{@code quamangnoibo}/{@code lienthong}/{@code dendientu},
 *   xem {@code ViewQueryService}), đúng hành vi Domino thật. Đồng thời loại khỏi field CÁ NHÂN
 *   tương ứng ({@code user_process}/{@code user_coordinate}/{@code user_to_read}) NẾU đơn vị không
 *   chọn người đại diện (identifier_creator có mặt trực tiếp trong field cá nhân đó — trường hợp
 *   hiếm, đa số field cá nhân chứa username, không phải identifier đơn vị, nên thường không đổi gì).
 *   Xem {@link #loaiKhoiDanhSachChoVaoSo} về cách đọc/viết field tương thích với 2 dạng lưu trữ đang
 *   tồn tại song song trong hệ thống (xem javadoc phương thức đó).</li>
 * </ol>
 *
 * <p><b>CHƯA PORT (ghi nhận là gap có chủ đích, KHÔNG suy đoán thêm — chờ yêu cầu/xác nhận cụ thể ở
 * slice sau)</b>, theo đúng thứ tự trong {@code LuuVanBanDen.java} thật:</p>
 * <ul>
 *   <li><b>R6/R7 — Diff quá trình xử lý</b> ({@code TaoQTXL}/{@code CapNhatXuly} — so sánh
 *   user_process/user_coordinate/user_to_read CŨ vs MỚI để tự tạo bản ghi "quá trình xử lý"
 *   chuyển-xử-lý/thu-hồi + cập nhật {@code lanhdaochutri}/{@code noinhanchutri}) — endpoint
 *   {@code ChuyenXuLy} (BL-211, {@link ChuyenXuLyVbdenUseCase}) đã port ĐÚNG luồng này cho hành
 *   động phân công/chuyển tiếp CHỦ ĐỘNG; đây là bản TỰ ĐỘNG PHÁT SINH khi lưu form chi tiết — có
 *   thể trùng lặp 1 phần, cần xác nhận thêm màn nào thực sự dựa vào nhánh tự động này trước khi
 *   port, tránh tạo 2 luồng ghi đè nhau. Quyết định đã chốt (xem tài liệu 03): KHÔNG auto-trigger
 *   TaoQTXL/CapNhatXuly (tạo {@code vbden_res}, append {@code chuyenxuly_obj}, tính lại status)
 *   trong use case này — FE gọi {@code ChuyenXuLy} riêng ngay sau {@code Luu()} khi cần (xem
 *   {@code ChiTietSoVanBanDenDayDu.jsx#onFinish}).</li>
 *   <li><b>R6-lite — Mặc định assigner làm user_process (bổ sung 22/08/2026, xem mục 7.5)</b>: đây
 *   là 1 đoạn NHỎ, TÁCH RIÊNG khỏi TaoQTXL/CapNhatXuly ở trên (dòng ~196-203 {@code LuuVanBanDen
 *   .java} thật) — đã đối chiếu dữ liệu mẫu thật {@code docs/views_domino/data_vbden_mau.txt}
 *   (Domino ↔ Java) và xác nhận: khi chỉ chọn "Lãnh đạo phân công" mà KHÔNG đụng widget "Đơn vị/
 *   người xử lý văn bản" (user_process rỗng), Domino tự gán {@code user_process = user_assigner}
 *   NGAY TRONG Luu() (không cần gọi ChuyenXuLy riêng) — nếu không port đoạn này, văn bản không bao
 *   giờ vào được hàng đợi "chờ xử lý" khi chỉ chọn lãnh đạo. Có port (xem
 *   {@link #applyDefaultAssignerAsProcessor}), CHỈ áp dụng phần "default value" này — việc tạo
 *   {@code chuyenxuly_obj}/tính lại status vẫn để FE gọi {@code ChuyenXuLy} riêng như trên, dựa
 *   trên {@code userProcess} trả về ở {@link LuuVanBanDenResultDto}.</li>
 *   <li><b>R10 — Gửi thông báo qua socket</b> ({@code sendSocketNotSave}) — hạ tầng realtime chưa
 *   có trong {@code JAVA/}, không có gì để gọi.</li>
 *   <li><b>R5 — Phản hồi trạng thái sang văn bản đi liên kết</b> ({@code phan_hoi_van_ban_di},
 *   {@code ResponseStatus}) — luồng liên thông văn bản đi/đến qua mạng, module {@code vbdi} chưa đủ
 *   để tích hợp.</li>
 *   <li><b>R9 — Nhóm đơn vị ngoài qua mailin</b> ({@code groupUsersByMailin}) — chỉ áp dụng cho đơn
 *   vị ngoài hệ thống, chưa phổ biến trong workflow nội bộ hiện tại.</li>
 *   <li><b>R11 — Cập nhật thông báo số lượng xử lý / view-error cache</b> ({@code setViewForUser}/
 *   {@code setViewForUnit}, {@code TDInit.setViewError}) — cơ chế làm mới cache VIEW của Domino,
 *   KHÔNG có khái niệm tương đương trong Postgres/JPA (query luôn đọc trực tiếp, không có view-index
 *   cần refresh) — không cần port.</li>
 *   <li><b>R12 — ACL reader/author fields</b> ({@code AddRolesToDocument}) — cơ chế phân quyền đọc/
 *   ghi ở MỨC DOCUMENT của Domino, hệ thống Java dùng RBAC (Spring Security roles) tách biệt —
 *   không có gì tương đương cần port.
 *   <p><b>SỬA 03/09/2026 (bao lỗi người dùng: nút "Lấy lại VB"/"Chuyển CĐĐH" không bao giờ hiện)</b>:
 *   đánh giá R12 ở trên chỉ ĐÚNG cho phần phân quyền đọc/ghi (đã có RBAC thay thế) — nhưng field
 *   {@code author} còn có 1 công dụng KHÁC, không phải ACL: FE đọc trực tiếp
 *   {@code resdata?.author?.includes(currentUser.username)} để quyết định hiện nút "Lấy lại VB"
 *   ({@code VBDenButtonActionVT.jsx#btnLayLaiVanBan}) và biến {@code isNguoisoanthao}
 *   ({@code VBDenButtonActionXuLy.jsx}, gate nút "Chuyển CĐĐH") — Java trước đây KHÔNG BAO GIỜ ghi
 *   field này nên 2 nút đó luôn ẩn. Đã bổ sung (KHÔNG ở use case này — xem
 *   {@code TiepNhanVbdenUseCase} seed lúc tạo mới, {@code ChuyenXuLyVbdenUseCase#accumulateAuthors}
 *   tích luỹ mỗi lần chuyển xử lý): chỉ tích luỹ username (không replicate phần identifier-chain của
 *   Domino thật, chưa có bằng chứng FE dùng phần đó).</p></li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LuuVanBanDenUseCase {

    private static final String DOCUMENT_TYPE = TiepNhanVbdenUseCase.DOCUMENT_TYPE;
    private static final String REGISTER_ENTRY_DOCUMENT_TYPE = TiepNhanVbdenUseCase.REGISTER_ENTRY_DOCUMENT_TYPE;
    private static final String DOCUMENT_NUMBER_FIELD = "document_number";
    private static final String DOCUMENT_REGISTER_FIELD = "document_register";
    private static final String DOCUMENT_REGISTER_ID_FIELD = "document_register_id";
    private static final String IDENTIFIER_CREATOR_FIELD = "identifier_creator";
    private static final String PARENT_ID_FIELD = "parentid";
    private static final String RECEIVED_DATE_FIELD = "received_date";
    // R6-lite (22/08/2026) - xem javadoc applyDefaultAssignerAsProcessor().
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_ASSIGNER_FIELD = "user_assigner";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // R4 (22/08/2026): moi phan tu la {orgField, registeredField, personalFieldOrNull} - dung
    // trong loaiKhoiDanhSachChoVaoSo(), xem javadoc phuong thuc do.
    private static final String[][] CHO_VAO_SO_FIELD_TRIOS = {
        {"unit_process", "unit_process_registered", "user_process"},
        {"unit_coordinate", "unit_coordinate_registered", "user_coordinate"},
        {"unit_to_read", "unit_to_read_registered", "user_to_read"},
        {"contact_unit_identifier", "contact_unit_registered", null}
    };

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentRegisterRepository documentRegisterRepository;
    private final AddDynamicDocumentUseCase addDynamicDocumentUseCase;
    private final com.td.application.vbdi.PublishVbdiReplyStatusUseCase publishVbdiReplyStatusUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<LuuVanBanDenResultDto> execute(LuuVanBanDenRequest request) {
        if (request.getId() == null) {
            return Result.failure("Thiếu id văn bản đến cần cập nhật");
        }
        BusinessDocument document = documentRepository.findById(request.getId()).orElse(null);
        if (document == null || !DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, String> fields = request.getFields() == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getFields());

        if (request.getDocumentRegisterId() != null) {
            Result<LuuVanBanDenResultDto> numberingError = applyDocumentRegisterNumbering(
                request.getDocumentRegisterId(), fields);
            if (numberingError != null) {
                return numberingError;
            }
        }

        Map<String, Object> beforeMerge = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        mergeFieldsIntoDocument(document, fields, request);
        if (request.getDocumentRegisterId() != null) {
            applyLoaiKhoiDanhSachChoVaoSo(document);
        }
        String userProcess = applyDefaultAssignerAsProcessor(document, beforeMerge);
        documentRepository.save(document);

        UUID idso = request.getIdso();
        if (request.getDocumentRegisterId() != null) {
            var entryResult = createOrUpdateRegisterEntry(idso, document.getId(), fields);
            if (!entryResult.isSuccess()) {
                log.error("Cap nhat van ban den {} thanh cong nhung cap nhat ban ghi so that bai: {}",
                    document.getId(), entryResult.getError());
                return Result.failure(
                    "Cập nhật văn bản thành công nhưng cập nhật bản ghi sổ thất bại: " + entryResult.getError());
            }
            idso = entryResult.getData();
            // Bo sung 07/09/2026 (yeu cau nguoi dung: hoan thien broker phan hoi trang thai "vao so"
            // - xem javadoc PublishVbdiReplyStatusUseCase): "vao so" THANH CONG (documentRegisterId
            // co gia tri, moi toi day) = STATUS_03 "Da tiep nhan" - CHI co y nghia neu van ban den
            // nay duoc tao qua giao lien thong (co send_log_id), use case tu bo qua neu khong phai.
            publishReplyStatusAfterVaoSo(document);
        }

        return Result.success(new LuuVanBanDenResultDto(document.getId(), idso, userProcess));
    }

    // Bugfix 07/09/2026 (2, bao loi nguoi dung: "Nguoi thuc hien" cua trang thai 03 dang la "He
    // thong tiep nhan" - SAI, vi CHI van thu moi thuc hien "vao so" - phai la TAI KHOAN van thu dang
    // dang nhap). currentUserProvider tra ve username (khong phai ten hien thi day du) - dung LUON
    // lam actorDisplayName vi day chinh xac la "tai khoan" nguoi dung yeu cau, khong phai ten hien
    // thi trang trong (LuuVanBanDenRequest KHONG mang field creatorName nhu ChuyenXuLyVbdenRequest).
    private void publishReplyStatusAfterVaoSo(BusinessDocument document) {
        var request = new com.td.application.vbdi.PublishVbdiReplyStatusRequest();
        request.setDynamicFields(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        request.setStatusCode("03");
        request.setStatusLabel("Đã tiếp nhận");
        currentUserProvider.currentUsername()
            .filter(value -> !value.isBlank())
            .ifPresent(request::setActorDisplayName);
        publishVbdiReplyStatusUseCase.execute(request);
    }

    /**
     * R6-lite ({@code LuuVanBanDen.java} thật, dòng ~196-203, TÁCH RIÊNG khỏi {@code TaoQTXL}) — đã
     * xác nhận đúng qua dữ liệu mẫu {@code docs/views_domino/data_vbden_mau.txt}: nếu widget "Đơn vị/
     * người xử lý văn bản" CHƯA gán ai ({@code user_process} rỗng SAU khi merge field từ request) VÀ
     * {@code user_assigner} vừa THAY ĐỔI so với trước khi lưu (chọn lãnh đạo mới, hoặc lần đầu chọn),
     * tự gán {@code user_process = user_assigner} — để văn bản xuất hiện ở hàng đợi "chờ xử lý" ngay
     * cả khi người dùng chỉ chọn "Lãnh đạo phân công" mà không đụng tới widget. Không đụng gì nếu đã
     * có người được gán qua widget (ưu tiên lựa chọn thật của người dùng), hoặc nếu assigner không
     * đổi (tránh ghi đè lại giá trị đã bị người dùng XOÁ có chủ đích ở 1 lần lưu trước).
     *
     * <p>CHƯA port (xem javadoc lớp, mục R6/R7): tạo {@code vbden_res}/append {@code chuyenxuly_obj}/
     * tính lại {@code status} — phần đó vẫn để FE gọi {@code ChuyenXuLy} riêng, dùng chính giá trị
     * {@code user_process} trả về ở đây ({@link LuuVanBanDenResultDto#getUserProcess()}) để biết có
     * cần gọi hay không (vì nếu chỉ đọc {@code request.getFields()}, FE sẽ không biết field này vừa
     * được BACKEND tự mặc định — đặc biệt ở nhánh hiệu chỉnh, {@code formData} gửi lên là DIFF nên có
     * thể không hề chứa {@code user_process}).</p>
     *
     * @return giá trị {@code user_process} SAU CÙNG (nối {@code ";"} nếu nhiều), rỗng nếu không ai.
     */
    private String applyDefaultAssignerAsProcessor(BusinessDocument document, Map<String, Object> beforeMergeFields) {
        Map<String, Object> currentFields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        List<String> userProcess = readMultiValue(currentFields.get(USER_PROCESS_FIELD));
        if (userProcess.isEmpty()) {
            String newAssigner = scalarValue(currentFields.get(USER_ASSIGNER_FIELD));
            String oldAssigner = scalarValue(beforeMergeFields.get(USER_ASSIGNER_FIELD));
            if (!newAssigner.isEmpty() && !newAssigner.equals(oldAssigner)) {
                userProcess = List.of(newAssigner);
                currentFields.put(USER_PROCESS_FIELD, userProcess);
                document.replaceDynamicFields(toJson(currentFields));
            }
        }
        return String.join(";", userProcess);
    }

    private String scalarValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    // Merge (KHONG replace toan bo) field moi vao dynamic_fields hien co - field khong nam trong
    // request.getFields() giu nguyen gia tri cu (khac AddDynamicDocumentUseCase - luon tao document
    // MOI hoan toan nen khong can merge).
    private void mergeFieldsIntoDocument(BusinessDocument document, Map<String, String> fields, LuuVanBanDenRequest request) {
        Map<String, Object> existing = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        Map<String, Object> incoming = dynamicDocumentFieldsService.parseDynamicFields(
            dynamicDocumentFieldsService.buildDynamicFieldsJson(
                fields, request.getDateFields(), request.getNumberFields(), request.getRichtextFields()));
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(incoming);
        // Xem AuthorAclFieldsService javadoc muc "CHUA PORT"/"applyOnUpdate" (16/09/2026 (2)) - R12
        // that ra van can port PHAN nghiep vu (khong phai ACL) - fields.keySet() = dung field client
        // GUI trong request nay (khop dung "user_fields dong" cua Domino, khong phai TOAN BO field
        // hien co tren van ban).
        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, merged, fields.keySet());
        document.replaceDynamicFields(toJson(merged));
        // Bugfix 22/08/2026 (bao loi nguoi dung: "trich yeu khong hien thi" - xem
        // GetVbdenDetailUseCase#executePlainDocument): TiepNhanVbdenUseCase luu "subject" (trich
        // yeu FE gui) vao COT TINH "title" cua entity (documents.title), KHONG vao dynamic_fields -
        // vi field nay bi loai truoc khi ghi (VBdenController#buildTiepNhanRequest). Neu chi merge
        // "subject" vao dynamic_fields o day ma khong dong bo lai "title", 2 gia tri se LECH nhau
        // sau khi hieu chinh (title = subject luc tao, dynamic_fields.subject = subject MOI nhat) -
        // anh huong bat ky noi nao khac doc entity.title (tim kiem/danh sach dung chung nhieu loai
        // document, khong chi vbden). Dong bo lai title = subject moi neu request co gui field nay.
        String subject = fields.get("subject");
        if (subject != null && !subject.isBlank()) {
            document.update(subject, null, null, null, null, null, null);
        }
    }

    // Nhan ban CO CHU DICH tu TiepNhanVbdenUseCase.applyDocumentRegisterNumbering - CHUA refactor
    // thanh 1 service dung chung vi TiepNhanVbdenUseCase la use case da qua nguoi dung xac nhan hoat
    // dong dung tren du lieu thuc (rui ro cao hon loi ich neu dong cham lai luc nay) - de lai TODO
    // refactor cho slice sau khi ca 2 nhanh da on dinh.
    // @return {@code null} neu OK de tiep tuc; {@code Result} loi neu phai dung lai.
    private Result<LuuVanBanDenResultDto> applyDocumentRegisterNumbering(UUID documentRegisterId, Map<String, String> fields) {
        var registerOpt = documentRegisterRepository.findByIdAndDeletedOnIsNull(documentRegisterId);
        if (registerOpt.isEmpty()) {
            return Result.failure("Không tìm thấy sổ văn bản với ID: " + documentRegisterId);
        }
        DocumentRegister register = registerOpt.get();

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

    // idso rong -> tao MOI ban ghi vbden_so (giong TiepNhanVbdenUseCase.createRegisterEntryDocument).
    // idso co gia tri -> hieu chinh lai ban ghi so DA CO (merge field, khong tao moi) - truong hop
    // nguoi dung doi sang so khac hoac sua so_den cho van ban da vao so tu truoc.
    private Result<UUID> createOrUpdateRegisterEntry(UUID idso, UUID parentId, Map<String, String> parentFields) {
        Map<String, String> entryFields = new LinkedHashMap<>();
        entryFields.put(PARENT_ID_FIELD, parentId.toString());
        putIfPresent(entryFields, DOCUMENT_NUMBER_FIELD, parentFields);
        putIfPresent(entryFields, DOCUMENT_REGISTER_ID_FIELD, parentFields);
        putIfPresent(entryFields, DOCUMENT_REGISTER_FIELD, parentFields);
        putIfPresent(entryFields, IDENTIFIER_CREATOR_FIELD, parentFields);
        putIfPresent(entryFields, RECEIVED_DATE_FIELD, parentFields);

        if (idso == null) {
            var childRequest = new AddDynamicDocumentRequest();
            childRequest.setDocumentType(REGISTER_ENTRY_DOCUMENT_TYPE);
            childRequest.setFields(entryFields);
            childRequest.setDateFields(RECEIVED_DATE_FIELD);
            return addDynamicDocumentUseCase.execute(childRequest);
        }

        BusinessDocument entry = documentRepository.findById(idso).orElse(null);
        if (entry == null || !REGISTER_ENTRY_DOCUMENT_TYPE.equalsIgnoreCase(entry.getDocumentType())) {
            return Result.failure("Không tìm thấy bản ghi sổ với ID: " + idso);
        }
        Map<String, Object> existing = dynamicDocumentFieldsService.parseDynamicFields(entry.getDynamicFieldsJson());
        Map<String, Object> incoming = dynamicDocumentFieldsService.parseDynamicFields(
            dynamicDocumentFieldsService.buildDynamicFieldsJson(entryFields, RECEIVED_DATE_FIELD, null, null));
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(incoming);
        entry.replaceDynamicFields(toJson(merged));
        documentRepository.save(entry);
        return Result.success(entry.getId());
    }

    // R4 (22/08/2026) - tuong duong Domino loai_khoi_ds_cho_vao_so() (LuuVanBanDen.java ~456-510).
    // Doc lai "identifier_creator" tu dynamic_fields HIEN TAI cua van ban (SAU khi mergeFieldsIntoDocument
    // da chay) - phu hop ca 2 truong hop: field vua duoc gui trong request nay, hoac da co tu truoc.
    private void applyLoaiKhoiDanhSachChoVaoSo(BusinessDocument document) {
        Map<String, Object> currentFields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        Object raw = currentFields.get(IDENTIFIER_CREATOR_FIELD);
        String identifierUser = raw == null ? null : raw.toString().trim();
        if (identifierUser == null || identifierUser.isBlank()) {
            return;
        }
        if (loaiKhoiDanhSachChoVaoSo(currentFields, identifierUser)) {
            document.replaceDynamicFields(toJson(currentFields));
        }
    }

    /**
     * R4 — tương đương Domino {@code loai_khoi_ds_cho_vao_so()}: khi 1 văn bản được "vào sổ"
     * (chọn {@code documentRegisterId}), coi như đơn vị {@code identifierUser} đã "nhận" văn bản —
     * loại khỏi 3 field tổ chức {@code unit_process}/{@code unit_coordinate}/{@code unit_to_read} +
     * {@code contact_unit_identifier} (đưa vào {@code *_registered} tương ứng), đồng thời loại khỏi
     * field CÁ NHÂN tương ứng ({@code user_process}/{@code user_coordinate}/{@code user_to_read})
     * NẾU {@code identifierUser} có mặt trực tiếp trong field đó (Domino: "đơn vị không chọn người
     * đại diện" — value cá nhân trùng luôn với identifier đơn vị).
     *
     * <p><b>Lưu ý dạng lưu trữ (khác pseudocode gốc trong tài liệu phân tích)</b>: các field này
     * đang được VIẾT ở 2 nơi khác nhau với 2 quy ước khác nhau — qua {@code TiepNhanVbdenUseCase}/
     * {@code LuuVanBanDenUseCase} (field TEXT thường, {@code FieldTypeInferrer} không match hậu tố
     * nào nên lưu SCALAR) hoặc qua {@link ChuyenXuLyVbdenUseCase} (luôn viết trực tiếp dạng
     * {@code List<String>}, bỏ qua {@code FieldTypeInferrer}). {@code findByAnyFieldContains} (dùng
     * cho các view N03 {@code captrengiao}/{@code dennoibo}/{@code quamangnoibo}...) đã tolerant cả
     * 2 dạng khi ĐỌC (OR containment + scalar-equality — xem bugfix D.14 tài liệu 01). Để KHÔNG làm
     * hỏng lần {@code ChuyenXuLy} kế tiếp trên cùng văn bản (nó đọc các field này bằng
     * {@code toStringList()}, CHỈ hiểu {@code List<String>}, âm thầm trả rỗng nếu gặp String — sẽ
     * mất dữ liệu), phương thức này LUÔN ghi lại các field đã sửa dưới dạng {@code List<String>}
     * (JSON array), bất kể dạng lưu trữ ban đầu là gì — an toàn với mọi nơi đọc/viết field này hiện
     * có trong hệ thống. Đọc thì tolerant cả 2 dạng (dùng {@link #readMultiValue}).
     *
     * @return {@code true} nếu có ít nhất 1 field bị thay đổi.
     */
    private boolean loaiKhoiDanhSachChoVaoSo(Map<String, Object> fields, String identifierUser) {
        boolean changed = false;
        for (String[] trio : CHO_VAO_SO_FIELD_TRIOS) {
            String orgField = trio[0];
            String registeredField = trio[1];
            String personalField = trio[2];

            List<String> orgValues = readMultiValue(fields.get(orgField));
            if (orgValues.remove(identifierUser)) {
                fields.put(orgField, orgValues);
                List<String> registeredValues = readMultiValue(fields.get(registeredField));
                if (!registeredValues.contains(identifierUser)) {
                    registeredValues.add(identifierUser);
                }
                fields.put(registeredField, registeredValues);
                changed = true;
            }

            if (personalField != null) {
                List<String> personalValues = readMultiValue(fields.get(personalField));
                if (personalValues.remove(identifierUser)) {
                    fields.put(personalField, personalValues);
                    changed = true;
                }
            }
        }
        return changed;
    }

    // Doc "khoan dung" (tolerant) ca 2 dang luu tru dang song song trong he thong (xem javadoc
    // loaiKhoiDanhSachChoVaoSo): List<String> (ChuyenXuLyVbdenUseCase) hoac chuoi phan tach ";"
    // (AddDynamicDocumentUseCase/FieldTypeInferrer, khi field khong khop hau tou _aut/_rdr/_tlist).
    // Luon tra ve 1 List MOI (khong dung chung reference), thu tu on dinh, khong trung lap.
    private List<String> readMultiValue(Object raw) {
        Set<String> deduped = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String value = item.toString().trim();
                    if (!value.isEmpty()) {
                        deduped.add(value);
                    }
                }
            }
        } else if (raw != null) {
            String text = raw.toString();
            for (String part : text.split(";")) {
                String value = part.trim();
                if (!value.isEmpty()) {
                    deduped.add(value);
                }
            }
        }
        return new ArrayList<>(deduped);
    }

    private void putIfPresent(Map<String, String> target, String key, Map<String, String> source) {
        String value = source.get(key);
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
