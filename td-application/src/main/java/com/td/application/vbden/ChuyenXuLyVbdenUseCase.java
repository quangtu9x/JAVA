package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import com.td.application.messaging.OutboxPublisherService;
import com.td.application.processdiagram.DiagramNodeRequest;
import com.td.application.processdiagram.ProcessDiagramService;
import com.td.application.sharedcore.OrganizationRepository;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BL-211 (VBDen — Xử lý & Chuyển tiếp) — DoD (1)/(2)/(3): phân công xử lý, ghi ý kiến xử lý, và
 * chuyển tiếp văn bản đến — tất cả cùng MỘT cơ chế, đúng thiết kế Domino thật
 * ({@code ChuyenXuLy.java}: 1 action, phân biệt bởi tham số {@code action}, không phải 3 luồng
 * riêng biệt). Domino thật làm 2 việc trong 1 lần gọi:
 * <ol>
 *   <li>Tạo document con trong {@code vbden_res} ({@code parentid} trỏ về văn bản gốc) chứa ý kiến
 *   — đọc lại được ngay qua {@code QuaTrinhXuLy} (BL-210, đã có sẵn).</li>
 *   <li>{@code updateChutri()}: loại người thực hiện khỏi {@code user_process}, thêm người được
 *   giao/chuyển tiếp — cùng field {@code user_process} mà BL-102's {@code vwDocument_ChoXuLy}/
 *   {@code vwDocument_DaXuLy} đã dùng để lọc "chờ xử lý"/"đã xử lý", nên tác động của hành động này
 *   phản ánh đúng ngay trong 2 view đó mà không cần sửa gì thêm.</li>
 * </ol>
 * Không dùng "Workflow module hiện tại" ({@code WorkflowsController}/{@code WorkflowRuntimeFacade})
 * — module đó hiện là scaffold thuần (không có entity/repository, mọi method trả UUID ngẫu
 * nhiên/DTO giả) nên không có gì để tích hợp; đây cũng đúng tinh thần "kế thừa business logic từ
 * Domino" vì bản thân Domino cũng không dùng workflow engine tổng quát cho luồng này, mà dùng đúng
 * cơ chế document/field động đã có sẵn từ BL-101/BL-102/BL-210.
 *
 * <p><b>Slice 2 (đối chiếu lại Domino {@code ChuyenXuLy.java:129-454})</b>: Domino xử lý TẤT CẢ
 * field đích (chủ trì/phối hợp/xem để biết/theo dõi/phụ trách) có mặt trong CÙNG một request —
 * đúng như FE thực tế gửi (1 submit của {@code SubFormChuyenXuLy.jsx} chứa
 * {@code user_process, user_coordinate, user_track, user_to_read} cùng lúc, không phải 1 field
 * riêng theo {@code action}) — {@code action} chỉ quyết định các cờ phụ (vd.
 * {@code user_distributed}, {@code user_delivered}) và tiêu đề bản ghi quá trình xử lý. Vì vậy
 * use case này áp dụng mọi field non-blank có mặt trong request, không rẽ nhánh switch theo
 * action.</p>
 *
 * <p>Chưa cài trong slice này (để dành slice sau, có đối chiếu riêng): xoá khỏi
 * {@code user_receiver}/{@code user_distributor}/{@code user_returned} khi phân công lại chủ trì,
 * chuỗi chỉ đạo ({@code assigner_info_next}), mailin đa server, webhook {@code ResponseStatus} ra
 * đơn vị ngoài, và vai trò {@code QPhanPhoi} ảnh hưởng {@code user_distributed} khi
 * {@code action=phanxuly} (đơn giản hoá: chỉ set khi {@code action=phanphoi}, đúng
 * {@code ChuyenXuLy.java:425-441}).</p>
 *
 * <p><b>R8 (tài liệu phân tích 03, bổ sung 22/08/2026, sửa lại 03/09/2026 — xem
 * {@link #recordProcessDiagramNodes})</b> — sơ đồ xử lý ({@code diagram_obj}): MỖI người mới được
 * thêm vào {@code user_process} (chủ trì) được ghi thêm 1 node vào {@code process_diagrams} (xem
 * {@link ProcessDiagramService}) — KHÔNG port cho phối hợp/xem để biết/theo dõi/phụ trách (chỉ chủ
 * trì, đúng phạm vi tài liệu 03 đã mô tả). Việc ghi sơ đồ được BỌC try/catch riêng — lỗi ở đây (vd
 * dữ liệu cây hỏng) KHÔNG được để làm hỏng giao dịch {@code ChuyenXuLy} chính (phân công/chuyển
 * tiếp là nghiệp vụ CHÍNH đang chạy, sơ đồ chỉ là dữ liệu PHỤ TRỢ hiển thị — chỉ log cảnh báo,
 * không rollback). {@code name} hiển thị của node hiện tạm dùng {@code username} (chưa có nguồn
 * tra tên hiển thị ở tầng này — xem javadoc {@link ProcessDiagramService}).</p>
 *
 * <p><b>Cutover FE (22/08/2026)</b> — {@code SubFormChuyenXuLy.jsx} ("Chuyển văn bản"/"Chuyển xử
 * lý", dùng chung bởi hầu hết màn xử lý vbden qua {@code ChuyenXuLyModalCapN.jsx}) trước đó vẫn gọi
 * Domino {@code qlvb/vbden?action=...} thay vì endpoint này dù đã có từ BL-211 — phát hiện qua báo
 * lỗi người dùng. Khi rà lại để chuyển FE sang gọi endpoint này, phát hiện 2 field FE ĐANG gửi mà
 * request cũ chưa có (sẽ mất dữ liệu nếu cutover thẳng, không phải giả thuyết): {@code deadline_date}
 * (Domino cập nhật hạn xử lý ngay trong CÙNG lần chuyển xử lý, không qua bước "Luu" riêng) và 3
 * field {@code _obj} (snapshot hiển thị đầy đủ tên/chức vụ — nếu bỏ, tab "Thông tin phân phối" sẽ
 * hiển thị SAI/CŨ sau khi chuyển xử lý dù {@code user_process} vẫn đúng). Đã bổ sung
 * {@code deadlineDate}/{@code userProcessObj}/{@code userCoordinateObj}/{@code userToReadObj} vào
 * {@link ChuyenXuLyVbdenRequest} — xem {@link #updateDeadlineDate}/{@link #updateRichObjFields}.</p>
 *
 * <p><b>Bổ sung 01/09/2026 (chuẩn bị cho "Thu hồi" — N11)</b>: NGOÀI việc cập nhật hiển thị văn bản
 * CHA ({@link #updateRichObjFields}), nay CŨNG ghi 3 field {@code user_process_obj}/
 * {@code user_coordinate_obj}/{@code user_to_read_obj} (tên field ĐÚNG như Domino — khác tên
 * {@code process_obj}/{@code coordinate_obj}/{@code to_read_obj} dùng cho văn bản cha) lên CHÍNH bản
 * ghi {@code vbden_res} vừa tạo — đối chiếu {@code ChuyenXuLy.java:129-172} (copy MỌI field FE post
 * thẳng vào {@code docQTXL}, không riêng gì 3 field này). {@link ThuHoiVanBanUseCase} đọc lại đúng 3
 * field này trên CÁC bản ghi lịch sử để biết chính xác ai đã được giao ở lần chuyển xử lý nào, từ đó
 * thu hồi đúng người. CHỈ áp dụng cho bản ghi tạo MỚI từ nay — không hồi tố dữ liệu cũ (xem javadoc
 * {@link ThuHoiVanBanUseCase}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ChuyenXuLyVbdenUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";

    /** Domino: {@code action.equals("phanphoi")} — {@code ChuyenXuLy.java:429-430}. */
    public static final String ACTION_PHAN_PHOI = "phanphoi";
    /** Domino: {@code action.equals("phanxuly")} — {@code ChuyenXuLy.java:434-439}. */
    public static final String ACTION_PHAN_XU_LY = "phanxuly";
    /** Domino: {@code action.equals("chuyenxuly")} — {@code ChuyenXuLy.java:440-441}. */
    public static final String ACTION_CHUYEN_XU_LY = "chuyenxuly";
    /** Domino: {@code action.equals("chuyenphoihop")} — {@code ChuyenXuLy.java:411-412}. */
    public static final String ACTION_CHUYEN_PHOI_HOP = "chuyenphoihop";

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";
    // Bo sung 03/09/2026, THAY THE 16/09/2026 (2) boi AuthorAclFieldsService dung chung (xem javadoc
    // lop do) - ban cu chi tich luy actingUsername + newAssignees (thieu SET_AUTHOR_FIELDS/*hide/
    // identifier-chain nguoi thao tac) - xem cac field trong AUTHOR_TOUCHED_FIELDS ben duoi.
    private static final String DEADLINE_DATE_FIELD = "deadline_date";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String UNIT_PROCESS_FIELD = "unit_process";
    private static final String USER_DISTRIBUTED_FIELD = "user_distributed";
    private static final String USER_DELIVERED_FIELD = "user_delivered";
    private static final String USER_COORDINATE_FIELD = "user_coordinate";
    private static final String UNIT_COORDINATE_FIELD = "unit_coordinate";
    private static final String USER_COORDINATED_MOVEMENT_FIELD = "user_coordinated_movement";
    private static final String USER_TO_READ_FIELD = "user_to_read";
    private static final String UNIT_TO_READ_FIELD = "unit_to_read";
    private static final String USER_TRACK_FIELD = "user_track";
    private static final String USER_MANAGE_FIELD = "user_manage";
    private static final String USER_PROCESS_OBJ_FIELD = "process_obj";
    private static final String USER_COORDINATE_OBJ_FIELD = "coordinate_obj";
    private static final String USER_TO_READ_OBJ_FIELD = "to_read_obj";
    // Ten field CHINH XAC Domino dung tren ban ghi CON vbden_res (KHAC ten field tren van ban CHA o
    // tren - "user_process_obj" khong phai "process_obj") - ThuHoiVanBan.java doc lai dung ten nay.
    static final String USER_PROCESS_OBJ_FIELD_RAW = "user_process_obj";
    static final String USER_COORDINATE_OBJ_FIELD_RAW = "user_coordinate_obj";
    static final String USER_TO_READ_OBJ_FIELD_RAW = "user_to_read_obj";
    // Bo sung 02/09/2026 (xem javadoc ChuyenXuLyVbdenRequest#userAssignerObj) - ten field dung y het
    // key ma QuatrinhxulyVBden.jsx da doc san cho cot "-Chi dao:".
    static final String USER_ASSIGNER_OBJ_FIELD_RAW = "user_assigner_obj";
    private static final String CHUYENXULY_HISTORY_FIELD = "chuyenxuly_obj";
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong" - moi field "user_*" ma
    // SubFormChuyenXuLy.jsx (FE) co the gui trong 1 lan submit (khong phan biet theo action, dung
    // javadoc lop "ap dung moi field non-blank co mat trong request").
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_PROCESS_FIELD, USER_COORDINATE_FIELD, USER_TO_READ_FIELD, USER_TRACK_FIELD, USER_MANAGE_FIELD);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DIAGRAM_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final VbdenStatusService vbdenStatusService;
    private final ProcessDiagramService processDiagramService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final com.td.application.vbdi.PublishVbdiReplyStatusUseCase publishVbdiReplyStatusUseCase;
    // Bo sung 08/09/2026 (yeu cau nguoi dung "kiem tra Java da co chua, neu chua thi port luon" -
    // xem javadoc publishForwardUnitIfCrossTenant): port DocumentInProcess.java
    // #copyDocumentProcessToDocumentIn (Domino, form document_process_unit).
    private final OrganizationRepository organizationRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final OutboxPublisherService outboxPublisherService;
    // Bo sung 08/09/2026 (3, nguoi dung: "thieu files nua... doc chung voi don vi chuyen xu ly luon"):
    // xem javadoc VbdenForwardUnitMessagePayload#files.
    private final com.td.application.documents.GetDocumentFileRefsUseCase getDocumentFileRefsUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(ChuyenXuLyVbdenRequest request) {
        var parentOptional = documentRepository.findById(request.getParentid());
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var parent = parentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(parent.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse("system");
        List<String> newAssignees = splitSemicolon(request.getUserProcess());
        // Bo sung 07/09/2026 (2, "lam not" STATUS_05 - xem javadoc publishReplyStatusAfterPhanCong):
        // phai chup status TRUOC khi updateParentDocument goi recomputeStatusOnFirstAssignment (ham
        // do MUTATE thang parent.status) - chup SAU se luon doc duoc gia tri MOI, lam dieu kien
        // "status truoc do la STATUS_MOI" khong bao gio dung.
        String statusBeforeUpdate = parent.getStatus();

        var childResult = createProcessingRecord(request, actingUsername);
        if (!childResult.isSuccess()) {
            return childResult;
        }

        updateParentDocument(parent, request, actingUsername, newAssignees);
        recordProcessDiagramNodes(parent, request, actingUsername, newAssignees, childResult.getData());

        // Bo sung 07/09/2026 (yeu cau nguoi dung: hoan thien broker phan hoi trang thai "phan xu
        // ly" - xem javadoc PublishVbdiReplyStatusUseCase, doi chieu Domino that ChuyenXuLy.java
        // goi StatusCode): CHI khi co it nhat 1 nguoi MOI duoc giao chu tri (newAssignees) - phan
        // cong/chuyen tiep khong dong nguoi xu ly (chi ghi y kien/phoi hop) khong tinh la "phan
        // cong xu ly" theo dung StatusCode 04. Use case tu bo qua neu van ban khong tao qua giao
        // lien thong (khong co send_log_id).
        if (!newAssignees.isEmpty()) {
            // Bo sung 07/09/2026 (2, yeu cau nguoi dung "lam not" STATUS_05/06 - doi chieu
            // ChuyenXuLy.java:240-260 that): STATUS_05 "Dang xu ly" CHI ban khi day la LAN DAU van
            // ban duoc phan cong chu tri (status TRUOC do = STATUS_MOI "0", dung DIEU KIEN
            // `status.equals("0")` cua Domino that) - KHONG ban lai o moi lan phan cong/chuyen tiep
            // sau do (khac han STATUS_04, luon ban moi lan). Ban TRUOC STATUS_04, dung thu tu Domino
            // (status_pack STATUS_05 o nhanh status=="0" chay truoc, STATUS_04 luon chay sau, khong
            // dieu kien).
            if (VbdenStatusService.STATUS_MOI.equals(statusBeforeUpdate)) {
                publishReplyStatusAfterPhanCong(parent, actingUsername, "05", "Đang xử lý", null);
            }
            publishReplyStatusAfterPhanCong(parent, actingUsername, "04", "Phân công xử lý",
                String.join("; ", newAssignees));
        }

        // Bo sung 08/09/2026 (yeu cau nguoi dung "kiem tra Java da co chua, neu chua thi port
        // luon" - xem javadoc phuong thuc): best-effort, KHONG chan giao dich chinh - dung
        // nguyen tac giong publishReplyStatusAfterPhanCong.
        publishForwardUnitIfCrossTenant(parent, request, actingUsername);

        // Bo sung 08/09/2026 (yeu cau nguoi dung: "muon phan hoi trang thai 'Da den' va cac trang
        // thai nhu chuyen xu ly... cho don vi 'Chuyen xu ly' vao qua trinh xu ly" - phan 2/2, xem
        // javadoc phuong thuc): echo NGUOC hanh dong xu ly nay ve tenant GOC neu van ban NAY tu no
        // la 1 ban sao nhan qua vbden.forward_unit.
        publishStatusBackToOriginIfForwardedCopy(parent, request, actingUsername);

        return Result.success(childResult.getData());
    }

    /**
     * Bổ sung 08/09/2026 (yêu cầu người dùng "kiểm tra JAVA đã có chưa, nếu chưa thì port luôn" —
     * sau khi rà soát xác nhận KHÔNG có tương đương Java nào): port
     * {@code DocumentInProcess.java#copyDocumentProcessToDocumentIn()} (Domino, tạo mail form
     * {@code document_process_unit} khi "chuyển xử lý" giao {@code unit_process} cho 1 đơn vị chạy
     * CSDL/tenant RIÊNG — "đơn vị con có văn thư riêng, vào sổ riêng", người dùng xác nhận đây LÀ
     * tenant/database khác, giống Domino).
     *
     * <p><b>Điều kiện kích hoạt</b>: TỰ ĐỘNG phát hiện (người dùng xác nhận, không cần FE chọn tường
     * minh) — với MỖI identifier trong {@code request.getUnitProcess()} (";"-nối), tra
     * {@code Organization.linkedTenantId}; khác tenant hiện tại ({@link CurrentTenantProvider}) thì
     * publish 1 unit {@code vbden.forward_unit} tới tenant đó. Cùng identifier KHÔNG có
     * {@code linkedTenantId} (hoặc trùng tenant hiện tại) → bỏ qua, coi là đơn vị nội bộ bình thường
     * (đã xử lý xong bởi {@code updateChutri}, không cần relay).</p>
     *
     * <p><b>Phạm vi đã port (giai đoạn 1/2 — xem javadoc {@link VbdenForwardUnitMessagePayload})</b>:
     * publish snapshot tối thiểu + tạo bản sao "văn bản đến" tại tenant đích (
     * {@code VbdenForwardUnitTaskHandler}, td-woker). <b>CHƯA port (giai đoạn 2, ghi nhận gap có chủ
     * đích — chờ xác nhận sau khi giai đoạn 1 hoạt động đúng)</b>: (1) đồng bộ ngược — xoá đơn vị khỏi
     * {@code unit_process} của văn bản GỐC sau khi tenant đích xác nhận đã tạo bản sao (Domino:
     * {@code DocumentInProcessUnit.copyToDocumentIn()} làm việc này qua reply, xem
     * {@code docs/agent_domino/io/BeforeMailin.ja/DocumentInProcessUnit.java}); (2) "Lấy lại"/"Thu
     * hồi" ({@code LayLaiVanBanUseCase}/{@code ThuHoiVanBanUseCase}) CHƯA cascade revoke sang bản sao
     * cross-tenant này. Cả 2 phụ thuộc giai đoạn 1 (bản sao) đã tồn tại và được người dùng xác nhận
     * đúng hành vi trước khi port tiếp — tránh đoán sai 2 lớp cùng lúc trên 1 use case xử lý CHÍNH
     * đang chạy production (rủi ro cao hơn revoke/vbdi vì đụng thẳng {@code user_process} đang hoạt
     * động).</p>
     *
     * <p>Lỗi ở bước này (tổ chức không có linkedTenantId hợp lệ, outbox publish thất bại...) KHÔNG
     * được làm hỏng giao dịch "Chuyển xử lý" chính (đã lưu DB xong ở {@link #updateParentDocument}) —
     * best-effort, cùng nguyên tắc {@link #publishReplyStatusAfterPhanCong}/
     * {@link #recordProcessDiagramNodes}.</p>
     */
    private void publishForwardUnitIfCrossTenant(
            BusinessDocument parent, ChuyenXuLyVbdenRequest request, String actingUsername) {
        List<String> unitIdentifiers = splitSemicolon(request.getUnitProcess());
        if (unitIdentifiers.isEmpty()) {
            return;
        }
        String currentTenantId = currentTenantProvider.currentTenantId().orElse(null);
        if (currentTenantId == null || currentTenantId.isBlank()) {
            return;
        }
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        for (String identifier : unitIdentifiers) {
            try {
                var orgOpt = organizationRepository.findByIdentifierAndDeletedOnIsNull(identifier);
                if (orgOpt.isEmpty()) {
                    continue;
                }
                var org = orgOpt.get();
                String linkedTenantId = org.getLinkedTenantId();
                if (linkedTenantId == null || linkedTenantId.isBlank() || linkedTenantId.equals(currentTenantId)) {
                    continue; // Don vi noi bo/cung tenant - da xu ly du boi updateChutri, khong can relay.
                }
                publishForwardUnitMessage(parent, fields, actingUsername, request.getOpinion(),
                    currentTenantId, identifier, org.getName(), linkedTenantId);
            } catch (Exception ex) {
                log.warn("Khong the publish vbden.forward_unit cho van ban {} - don vi {}: {}",
                    parent.getId(), identifier, ex.getMessage());
            }
        }
    }

    private void publishForwardUnitMessage(
            BusinessDocument parent, Map<String, Object> fields, String actingUsername, String opinion,
            String currentTenantId, String targetIdentifier, String targetName, String targetTenantId) {
        var payload = VbdenForwardUnitMessagePayload.builder()
            .originDocumentId(parent.getId().toString())
            .originTenantId(currentTenantId)
            .targetIdentifier(targetIdentifier)
            .targetName(targetName)
            .actingUsername(actingUsername)
            .opinion(opinion)
            .subject(parent.getTitle())
            .code(asString(fields.get("code")))
            .promulgationDate(asString(fields.get("promulgation_date")))
            // Bo sung 08/09/2026 (2, bao loi nguoi dung: "van thu/nguoi dai dien nhan dang khong
            // thay van ban o bat ky dau" - xem javadoc VbdenForwardUnitMessagePayload): forward
            // nguyen ven tu van ban goc - deadline_date/unit_in_charge(_identifier) KHONG doi y
            // nghia khi chuyen tiep noi bo (van la han xu ly/co quan ban hanh GOC, khong phai cua
            // don vi dang chuyen tiep).
            .deadlineDate(asString(fields.get("deadline_date")))
            .unitInCharge(asString(fields.get("unit_in_charge")))
            .unitInChargeIdentifier(asString(fields.get("unit_in_charge_identifier")))
            // Bo sung 08/09/2026 (3, xem javadoc VbdenForwardUnitMessagePayload#files): doc lai
            // (KHONG copy MinIO) - best-effort, loi o day (vd DB tam thoi loi) KHONG duoc lam hong
            // giao dich "Chuyen xu ly" chinh, xem catch o publishForwardUnitIfCrossTenant.
            .files(getDocumentFileRefsUseCase.execute(parent.getId()))
            .build();

        var unit = new MessageUnitSpec(UUID.randomUUID().toString(), targetTenantId, payload);
        outboxPublisherService.enqueue("vbden", parent.getId(),
            new OutboundMessage("vbden.forward_unit", parent.getId().toString(), List.of(unit)));
    }

    /**
     * Bổ sung 08/09/2026 — yêu cầu người dùng: "muốn phản hồi trạng thái 'Đã đến' và các trạng thái
     * như chuyển xử lý... cho đơn vị 'Chuyển xử lý' vào quá trình xử lý" — phần 2/2 (phần 1 là "Đã
     * đến" MỘT LẦN duy nhất lúc tạo bản sao, xem {@code VbdenForwardUnitReplyTaskHandler
     * #insertArrivalHistoryRow}, td-woker): TỔNG QUÁT HOÁ thành MỌI hành động xử lý ({@code action}
     * bất kỳ, không riêng {@code chuyenxuly} dù đó là ví dụ người dùng nêu — bản sao có thể được
     * phân công/phối hợp/chuyển tiếp nhiều lần, kể cả forward tiếp sang tenant thứ 3) được echo NGƯỢC
     * về "quá trình xử lý" ({@code vbden_res_documents}) của văn bản GỐC, nếu văn bản ĐANG được xử lý
     * ở ĐÂY (tenant hiện tại) chính là 1 bản sao nhận qua {@code vbden.forward_unit}.
     *
     * <p>Nhận diện "đây là bản sao": có {@code origin_document_id}/{@code origin_tenant_id} trên
     * {@code dynamic_fields} — 2 field DO {@code VbdenForwardUnitTaskHandler#insertForwardedCopy} ghi
     * lúc tạo bản sao (td-woker) và KHÔNG method nào trong lớp này động tới (không nằm trong danh
     * sách field mà {@link #updateParentDocument} cập nhật) — luôn đọc được nguyên vẹn dù đã qua bao
     * nhiêu lần chuyển xử lý nội bộ tại tenant này.</p>
     *
     * <p>Best-effort — lỗi ở đây (tổ chức không tồn tại, publish thất bại...) KHÔNG được làm hỏng
     * giao dịch "Chuyển xử lý" chính (đã lưu DB xong ở {@link #updateParentDocument}), cùng nguyên
     * tắc {@link #publishForwardUnitIfCrossTenant}.</p>
     */
    private void publishStatusBackToOriginIfForwardedCopy(
            BusinessDocument parent, ChuyenXuLyVbdenRequest request, String actingUsername) {
        try {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
            String originDocumentId = asString(fields.get("origin_document_id"));
            String originTenantId = asString(fields.get("origin_tenant_id"));
            if (isBlank(originDocumentId) || isBlank(originTenantId)) {
                return; // Khong phai ban sao nhan qua forward_unit - khong co gi de echo nguoc.
            }

            String reportingIdentifier = asString(fields.get("identifier_creator"));
            String reportingName = reportingIdentifier;
            if (reportingIdentifier != null && !reportingIdentifier.isBlank()) {
                reportingName = organizationRepository.findByIdentifierAndDeletedOnIsNull(reportingIdentifier)
                    .map(com.td.domain.sharedcore.Organization::getName)
                    .orElse(reportingIdentifier);
            }
            List<String> newAssignees = splitSemicolon(request.getUserProcess());

            var payload = VbdenForwardUnitStatusMessagePayload.builder()
                .originDocumentId(originDocumentId)
                .action(request.getAction())
                .actionLabel(actionLabel(request.getAction()))
                .reportingIdentifier(reportingIdentifier)
                .reportingName(reportingName)
                .actingUsername(actingUsername)
                .opinion(request.getOpinion())
                .newAssigneesLabel(newAssignees.isEmpty() ? null : String.join("; ", newAssignees))
                .build();

            var unit = new MessageUnitSpec(UUID.randomUUID().toString(), originTenantId, payload);
            outboxPublisherService.enqueue("vbden", parent.getId(),
                new OutboundMessage("vbden.forward_unit.status", parent.getId().toString(), List.of(unit)));
        } catch (Exception ex) {
            log.warn("Khong the echo trang thai xu ly ve tenant goc cho van ban {}: {}", parent.getId(), ex.getMessage());
        }
    }

    // Nhan hien thi tieng Viet cho ACTION_* - dung khi echo trang thai ve tenant goc (xem
    // publishStatusBackToOriginIfForwardedCopy). Action la nao khac (khong nam trong 4 hang so o
    // tren) thi tra nguyen ma, khong doan them nhan moi.
    private String actionLabel(String action) {
        if (ACTION_PHAN_PHOI.equals(action)) {
            return "Phân phối";
        }
        if (ACTION_PHAN_XU_LY.equals(action)) {
            return "Phân xử lý";
        }
        if (ACTION_CHUYEN_XU_LY.equals(action)) {
            return "Chuyển xử lý";
        }
        if (ACTION_CHUYEN_PHOI_HOP.equals(action)) {
            return "Chuyển phối hợp";
        }
        return action;
    }

    private void publishReplyStatusAfterPhanCong(
            BusinessDocument parent, String actingUsername, String statusCode, String statusLabel, String statusDetails) {
        var replyRequest = new com.td.application.vbdi.PublishVbdiReplyStatusRequest();
        replyRequest.setDynamicFields(dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));
        replyRequest.setStatusCode(statusCode);
        replyRequest.setStatusLabel(statusLabel);
        replyRequest.setStatusDetails(statusDetails);
        replyRequest.setActorDisplayName(actingUsername);
        publishVbdiReplyStatusUseCase.execute(replyRequest);
    }

    // R8: ghi 1 node so do xu ly cho MOI nguoi moi duoc giao chu tri - xem javadoc lop ve ly do bao
    // ve bang try/catch (loi o day khong duoc lam hong giao dich ChuyenXuLy chinh).
    private void recordProcessDiagramNodes(
            BusinessDocument parent, ChuyenXuLyVbdenRequest request, String actingUsername,
            List<String> newAssignees, UUID processingRecordId) {
        // Bo sung 03/09/2026 (bao loi nguoi dung, kem 2 file du lieu mau Domino that -
        // docs/views_domino/data_vbden_quatrinhxuly_mau*.txt): truoc day CHI ve node cho "chu tri"
        // (newAssignees) - "chi dao" (nguoi duoc chon o "Lanh dao phan cong", userAssignerObj) KHONG
        // BAO GIO thanh 1 node so do, du van hien tren tab "Quan trinh xu ly" (cot "-Chi dao:", da
        // sua o slice truoc).
        //
        // Sua lai LAN 2 cung ngay 03/09/2026 (nguoi dung mo ta lai chi tiet luong thuc te): ban dau ve
        // "chi dao" va "chu tri" la ANH EM cung cha duoi actingUsername - SAI, vi FE
        // (SubFormPhanVanBan.jsx: DonViNguoiNhanXuLy disabled={... || !assigner_watch}) CHI cho chon
        // "Lanh dao phan cong" (assigner) TRUOC, xong moi mo khoa duoc chon "Don vi/nguoi xu ly"
        // (chu tri) - dung la 1 CHUOI TUAN TU van thu -> lanh dao -> don vi/chuyen vien, khong phai
        // van thu chuyen dong thoi cho ca 2. Nay: "chi dao" (assigners) van gan duoi actingUsername,
        // nhung "chu tri" (newAssignees) gan duoi CHINH "chi dao" do (neu co) thay vi duoi
        // actingUsername - chi fallback ve actingUsername khi KHONG co assigner (luong khac khong di
        // qua buoc chon "Lanh dao phan cong", vd chuyen tiep truc tiep tu 1 modal khac).
        List<String> assigners = extractUsernames(request.getUserAssignerObj());
        if (newAssignees.isEmpty() && assigners.isEmpty()) {
            return;
        }
        try {
            // Bo sung 03/09/2026 (bao loi nguoi dung: so do thieu han node dau tien - "van thu"/nguoi
            // dang thao tac - xem javadoc ProcessDiagramService#ensureNode): dam bao actingUsername co
            // 1 node dai dien TRUOC khi gan con vao, de KHONG bi roi xuong root nhu 1 nguoi vo danh.
            String actorDisplayName = request.getCreatorName() != null && !request.getCreatorName().isBlank()
                ? request.getCreatorName() : actingUsername;
            processDiagramService.ensureNode(
                request.getParentid(), DOCUMENT_TYPE, actingUsername, actorDisplayName, request.getPosition());

            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
            String deadline = asString(fields.get(DEADLINE_DATE_FIELD));
            String ngaygui = LocalDateTime.now().format(DIAGRAM_TIMESTAMP_FORMAT);

            // Bo sung 04/09/2026 (bao loi nguoi dung, kem anh chup man hinh: chon 1 ĐON VI lam chu
            // tri - so do xu ly hien THANG USERNAME cua nguoi dai dien ("demo1") thay vi TEN DON VI
            // ("Phong Cong nghe") - muon dung phai la TEN DON VI tren node, con nguoi dai dien chi
            // hien trong tooltip (FE tree_model/sodoxuly.jsx da co san co che nay qua field
            // "nguoidaidien" - DiagramNodeRequest/ProcessDiagramService da co san, chi chua tung duoc
            // dien tu luc BE ghi node). Tra "ten hien thi" + "nguoi dai dien" tu CHINH _obj JSON (co
            // ca 2 tin: identifier=don vi -> name=ten don vi, user_receiver=dai dien -> lay lam
            // username thuc su cua node; nguoc lai la 1 nguoi cu the -> name=ten nguoi do).
            Map<String, DisplayInfo> displays = new java.util.HashMap<>();
            displays.putAll(extractDisplayInfo(request.getUserProcessObj()));
            displays.putAll(extractDisplayInfo(request.getUserAssignerObj()));

            for (String assigner : assigners) {
                addDiagramNode(request, actingUsername, actingUsername, assigner, displays, ngaygui, processingRecordId, deadline);
            }

            // "chi dao" lam cha cua "chu tri" khi co chon "Lanh dao phan cong"; khong thi fallback ve
            // actingUsername (van thu) nhu truoc day.
            List<String> processParents = assigners.isEmpty() ? List.of(actingUsername) : assigners;
            for (String assignee : newAssignees) {
                for (String processParent : processParents) {
                    if (assignee.equals(processParent)) {
                        // Lanh dao tu phan cong cho chinh minh lam chu tri - bo qua, tranh node tu lam con cua chinh no.
                        continue;
                    }
                    addDiagramNode(request, actingUsername, processParent, assignee, displays, ngaygui, processingRecordId, deadline);
                }
            }
        } catch (Exception ex) {
            log.warn("Khong the ghi so do xu ly cho van ban {}: {}", request.getParentid(), ex.getMessage());
        }
    }

    private void addDiagramNode(
            ChuyenXuLyVbdenRequest request, String actingUsername, String targetUsername, String assigneeUsername,
            Map<String, DisplayInfo> displays, String ngaygui, UUID processingRecordId, String deadline) {
        DisplayInfo display = displays.get(assigneeUsername);
        DiagramNodeRequest node = new DiagramNodeRequest();
        node.setUsername(assigneeUsername);
        node.setName(display != null ? display.name() : assigneeUsername);
        node.setNguoidaidien(display != null ? display.nguoidaidien() : null);
        node.setNgaygui(ngaygui);
        node.setQuytrinhid(processingRecordId == null ? null : processingRecordId.toString());
        node.setNoidung(request.getOpinion());
        node.setHanxuly(deadline);
        node.setUsergui(actingUsername);
        node.setNguoigui(actingUsername);
        processDiagramService.addNode(request.getParentid(), DOCUMENT_TYPE, targetUsername, node);
    }

    // Ten hien thi ("name" tren node so do) + "nguoi dai dien" (chi hien o tooltip FE, xem
    // tree_model/sodoxuly.jsx#Parent) cho 1 username cu the, tra tu JSON _obj (userProcessObj/
    // userAssignerObj - CUNG shape {id,name,identifier,username,user_receiver,receiver,...}).
    private record DisplayInfo(String name, String nguoidaidien) {
    }

    // key = username SE dung lam danh tinh node (giong newAssignees da build - user_receiver neu la
    // 1 DON VI, username neu la 1 NGUOI cu the - xem SubFormChuyenXuLy.jsx#resolveProcessAssignees
    // phia FE, BE khong tu suy luan lai ma chi khop nguoc theo dung 2 truong hop do). Loi parse/rong
    // -> map rong, KHONG throw (du lieu hien thi phu).
    private Map<String, DisplayInfo> extractDisplayInfo(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Map.of();
        }
        try {
            var arrayNode = OBJECT_MAPPER.readTree(json);
            Map<String, DisplayInfo> result = new java.util.HashMap<>();
            if (arrayNode.isArray()) {
                for (var item : arrayNode) {
                    String identifier = item.path("identifier").asText(null);
                    String name = item.path("name").asText(null);
                    if (identifier != null && !identifier.isBlank()) {
                        String userReceiver = item.path("user_receiver").asText(null);
                        if (userReceiver != null && !userReceiver.isBlank()) {
                            String receiverName = item.path("receiver").asText(null);
                            result.put(userReceiver, new DisplayInfo(
                                isBlank(name) ? userReceiver : name,
                                isBlank(receiverName) ? userReceiver : receiverName));
                        }
                    } else {
                        String username = item.path("username").asText(null);
                        if (username != null && !username.isBlank()) {
                            result.put(username, new DisplayInfo(isBlank(name) ? username : name, null));
                        }
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            return java.util.Map.of();
        }
    }

    // Parse JSON array kieu [{"username":"...","name":"...",...}] (dung chung shape voi
    // userProcessObj/userAssignerObj) -> danh sach username. Loi parse/rong -> tra danh sach rong,
    // KHONG throw (du lieu hien thi phu, khong duoc lam hong luong chinh).
    private List<String> extractUsernames(String json) {
        if (json == null || json.isBlank()) {
            return java.util.List.of();
        }
        try {
            var arrayNode = OBJECT_MAPPER.readTree(json);
            List<String> usernames = new java.util.ArrayList<>();
            if (arrayNode.isArray()) {
                for (var item : arrayNode) {
                    String username = item.path("username").asText(null);
                    if (username != null && !username.isBlank()) {
                        usernames.add(username);
                    }
                }
            }
            return usernames;
        } catch (Exception ex) {
            return java.util.List.of();
        }
    }

    // Domino: dbVBden_res.createDocument(), parentid = docVBden.getUniversalID() (ChuyenXuLy.java)
    private Result<UUID> createProcessingRecord(ChuyenXuLyVbdenRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(request.getAction());
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", request.getAction());
        childFields.put("nguoi_thuc_hien", actingUsername);
        // Bo sung 02/09/2026 (bao loi nguoi dung: tab "Qua trinh xu ly" khong hien "Nguoi thuc hien"
        // vi QuatrinhxulyVBden.jsx doc field "creator"/"position", con o day truoc day chi ghi
        // "nguoi_thuc_hien" - xem javadoc ChuyenXuLyVbdenRequest#creatorName. Chi de hien thi, KHONG
        // thay the "nguoi_thuc_hien" (van la nguon du lieu trust-critical cho LichSuXuLyUseCase).
        String displayCreator = request.getCreatorName() != null && !request.getCreatorName().isBlank()
            ? request.getCreatorName() : actingUsername;
        childFields.put("creator", displayCreator);
        if (request.getPosition() != null && !request.getPosition().isBlank()) {
            childFields.put("position", request.getPosition());
        }
        // Bo sung 02/09/2026 (2 file du lieu mau Domino that nguoi dung cung cap -
        // docs/views_domino/data_vbden_quatrinhxuly_mau*.txt - xac nhan Domino LUON ghi "identifier"
        // (identifier cua nguoi thuc hien, o dung cap "level" cua ho - rong voi chuyen vien thuong,
        // co gia tri voi lanh dao/van thu don vi) tren MOI ban ghi vbden_res). Tab "Xem lien quan"
        // (QuatrinhxulyVBden.jsx#dataRelated) loc theo field nay - truoc day KHONG BAO GIO duoc ghi
        // nen tab do luon rong voi moi ban ghi Java tao ra. Resolve SERVER-SIDE (khong nhan tu FE,
        // dung thuat toan da co ResolveIdentifierByLevelUseCase - giong cach GetSoVanBanDenUseCase
        // resolve) - an toan hon nhan tu request (client khong tu xung nhan sai identifier).
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            childFields.put("opinion", request.getOpinion());
        }
        if (request.getUserProcess() != null && !request.getUserProcess().isBlank()) {
            childFields.put(USER_PROCESS_FIELD, request.getUserProcess());
        }
        // Bo sung 01/09/2026 (chuan bi cho "Thu hoi" - N11, xem docs/analysis/02-checklist-port-
        // vbden-con-lai.md muc Phan 4): Domino that (ChuyenXuLy.java:129-172) copy MOI field posted
        // tu FE (bao gom ca 3 field JSON "rich object" nay) THANG vao docQTXL - ThuHoiVanBan.java sau
        // nay doc lai CHINH 3 field nay tren TUNG ban ghi lich su de biet CHINH XAC ai da duoc giao o
        // LAN chuyen xu ly do de con thu hoi dung nguoi. Java truoc day CHI dung 3 field nay de cap
        // nhat hien thi van ban CHA (updateRichObjFields), CHUA bao gio ghi len ban ghi con - neu
        // khong ghi, "Thu hoi" se KHONG BAO GIO co du lieu de hoat dong dung (moi ban ghi moi tu bay
        // gio tro di se co, du lieu cu van thieu - chap nhan duoc, da ghi ro trong javadoc ThuHoiVanBanUseCase).
        if (request.getUserProcessObj() != null && !request.getUserProcessObj().isBlank()) {
            childFields.put(USER_PROCESS_OBJ_FIELD_RAW, request.getUserProcessObj());
        }
        if (request.getUserCoordinateObj() != null && !request.getUserCoordinateObj().isBlank()) {
            childFields.put(USER_COORDINATE_OBJ_FIELD_RAW, request.getUserCoordinateObj());
        }
        if (request.getUserToReadObj() != null && !request.getUserToReadObj().isBlank()) {
            childFields.put(USER_TO_READ_OBJ_FIELD_RAW, request.getUserToReadObj());
        }
        if (request.getUserAssignerObj() != null && !request.getUserAssignerObj().isBlank()) {
            childFields.put(USER_ASSIGNER_OBJ_FIELD_RAW, request.getUserAssignerObj());
        }
        childRequest.setFields(childFields);

        return addVbdenResDocumentUseCase.execute(childRequest);
    }

    // Domino: ChuyenXuLy.setChuyenXuLy() goi lien tiep updateChutri()/updatePhoihop()/updateDebiet()/
    // updateTheodoi()/updatePhutrach() - moi ham tu quyet dinh co mutate hay khong dua tren field
    // form tuong ung co blank hay khong (khong re nhanh theo action) - xem ChuyenXuLy.java:129-454.
    private void updateParentDocument(
            BusinessDocument parent, ChuyenXuLyVbdenRequest request, String actingUsername, List<String> newAssignees) {
        var before = buildSnapshot(parent);
        Map<String, Object> fields = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        // Sua loi Slice 1: "status" workflow (0-4) cua vbden la COT TINH document.status (bi tach
        // ra khoi dynamic_fields o VbdenController.buildTiepNhanRequest() - xem TiepNhanVbdenUseCase
        // dong 53 addRequest.setStatus(...)), KHONG phai key trong dynamic_fields - doc/ghi dung cho
        // "status" cua ChuyenXuLy.java:240-259 phai qua parent.getStatus()/parent.update(...), khong
        // qua fields map (truoc day sai cho nay, N13 se query cot tinh nay nen phai sua o day truoc).
        String currentStatus = parent.getStatus();
        updateDeadlineDate(fields, request);
        boolean chutriChanged = updateChutri(fields, request, actingUsername, newAssignees);
        updatePhoihop(fields, request, actingUsername);
        updateDebiet(fields, request, actingUsername);
        updateTheodoi(fields, request);
        updatePhutrach(fields, request);
        updateRichObjFields(fields, request);
        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);

        appendChuyenXuLyHistory(fields, request, actingUsername, newAssignees);

        parent.replaceDynamicFields(toJson(fields));
        if (chutriChanged) {
            recomputeStatusOnFirstAssignment(parent, fields, currentStatus);
        }
        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Domino: ChuyenXuLy.updateChutri() (dòng 425-454) — loại người thực hiện khỏi user_process,
    // thêm người được giao/chuyển tiếp; cờ phụ user_distributed (phanphoi)/user_delivered
    // (phanxuly, chuyenxuly). Bỏ qua nhánh vai trò QPhanPhoi ảnh hưởng user_distributed khi
    // action=phanxuly (cần role context riêng — ghi nhận trong Javadoc lớp, để slice sau).
    private boolean updateChutri(
            Map<String, Object> fields, ChuyenXuLyVbdenRequest request, String actingUsername, List<String> newAssignees) {
        if (newAssignees.isEmpty() && isBlank(request.getUnitProcess())) {
            return false;
        }

        List<String> userProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        userProcess.remove(actingUsername);
        addAllDistinct(userProcess, newAssignees);
        fields.put(USER_PROCESS_FIELD, userProcess);

        List<String> unitProcess = splitSemicolon(request.getUnitProcess());
        if (!unitProcess.isEmpty()) {
            fields.put(UNIT_PROCESS_FIELD, unitProcess);
        }

        String action = request.getAction();
        if (ACTION_PHAN_PHOI.equals(action)) {
            addOne(fields, USER_DISTRIBUTED_FIELD, actingUsername);
        }
        if (ACTION_PHAN_XU_LY.equals(action) || ACTION_CHUYEN_XU_LY.equals(action)) {
            addOne(fields, USER_DELIVERED_FIELD, actingUsername);
        }
        return true;
    }

    // Domino: ChuyenXuLy.updatePhoihop() (dòng 408-424).
    private void updatePhoihop(Map<String, Object> fields, ChuyenXuLyVbdenRequest request, String actingUsername) {
        List<String> newCoordinate = splitSemicolon(request.getUserCoordinate());
        if (newCoordinate.isEmpty()) {
            return;
        }

        List<String> userCoordinate = toStringList(fields.get(USER_COORDINATE_FIELD));
        addAllDistinct(userCoordinate, newCoordinate);
        userCoordinate.remove(actingUsername);
        fields.put(USER_COORDINATE_FIELD, userCoordinate);

        List<String> unitCoordinate = splitSemicolon(request.getUnitCoordinate());
        if (!unitCoordinate.isEmpty()) {
            fields.put(UNIT_COORDINATE_FIELD, unitCoordinate);
        }

        if (ACTION_CHUYEN_PHOI_HOP.equals(request.getAction())) {
            addOne(fields, USER_COORDINATED_MOVEMENT_FIELD, actingUsername);
        }
    }

    // Domino: ChuyenXuLy.updateDebiet() (dòng 375-391). Cờ phụ "user_to_readed" trong Domino được
    // guard bởi action.equals("xemdebiet") — action khác với "chuyenxemdebiet" ở dispatch table
    // (VBdenController.java:87-113) nên KHÔNG áp dụng ở đây (khả năng là 1 luồng tự đánh dấu đã
    // xem, tách biệt — để xác nhận ở slice sau thay vì đoán).
    private void updateDebiet(Map<String, Object> fields, ChuyenXuLyVbdenRequest request, String actingUsername) {
        List<String> newToRead = splitSemicolon(request.getUserToRead());
        if (newToRead.isEmpty()) {
            return;
        }

        List<String> userToRead = toStringList(fields.get(USER_TO_READ_FIELD));
        addAllDistinct(userToRead, newToRead);
        userToRead.remove(actingUsername);
        fields.put(USER_TO_READ_FIELD, userToRead);

        List<String> unitToRead = splitSemicolon(request.getUnitToRead());
        if (!unitToRead.isEmpty()) {
            fields.put(UNIT_TO_READ_FIELD, unitToRead);
        }
    }

    // Domino: ChuyenXuLy.updateTheodoi() (dòng 397-406) — chỉ thêm, không loại người thực hiện.
    private void updateTheodoi(Map<String, Object> fields, ChuyenXuLyVbdenRequest request) {
        List<String> newTrack = splitSemicolon(request.getUserTrack());
        if (newTrack.isEmpty()) {
            return;
        }
        List<String> userTrack = toStringList(fields.get(USER_TRACK_FIELD));
        addAllDistinct(userTrack, newTrack);
        fields.put(USER_TRACK_FIELD, userTrack);
    }

    // Domino: ChuyenXuLy.updatePhutrach() (dòng 392-395) — chỉ thêm, không loại người thực hiện.
    private void updatePhutrach(Map<String, Object> fields, ChuyenXuLyVbdenRequest request) {
        List<String> newManage = splitSemicolon(request.getUserManage());
        if (newManage.isEmpty()) {
            return;
        }
        List<String> userManage = toStringList(fields.get(USER_MANAGE_FIELD));
        addAllDistinct(userManage, newManage);
        fields.put(USER_MANAGE_FIELD, userManage);
    }

    // Bo sung 22/08/2026 (bao loi FE "Chuyen van ban" mat field nay khi cutover khoi Domino):
    // Domino goc cap nhat deadline_date TRUC TIEP trong CUNG 1 lan goi ChuyenXuLy (khong phai buoc
    // rieng qua "Luu") - ap dung SOM (truoc updateChutri) de recomputeStatusOnFirstAssignment doc
    // duoc han xu ly MOI NHAT neu ca 2 thay doi cung luc.
    private void updateDeadlineDate(Map<String, Object> fields, ChuyenXuLyVbdenRequest request) {
        if (isBlank(request.getDeadlineDate())) {
            return;
        }
        fields.put(DEADLINE_DATE_FIELD, request.getDeadlineDate());
    }

    // Bo sung 22/08/2026: 3 field "_obj" (snapshot day du de hien thi lai dung ten/chuc vu/don vi -
    // SubViewPhanVanBan.jsx/DonViNguoiNhanXuLy.jsx) - KHONG bat buoc, khong gui thi giu nguyen gia
    // tri cu (khac hoan toan user_process/unit_process... vi day la field HIEN THI thuan tuy, khong
    // anh huong queue/view nao). Parse JSON that (khop FieldType.JSON - field ket thuc "_obj") - loi
    // parse thi luu nguyen chuoi tho (khop fallback cua DynamicFieldValueConverter.parseJson).
    private void updateRichObjFields(Map<String, Object> fields, ChuyenXuLyVbdenRequest request) {
        putParsedJsonIfPresent(fields, USER_PROCESS_OBJ_FIELD, request.getUserProcessObj());
        putParsedJsonIfPresent(fields, USER_COORDINATE_OBJ_FIELD, request.getUserCoordinateObj());
        putParsedJsonIfPresent(fields, USER_TO_READ_OBJ_FIELD, request.getUserToReadObj());
    }

    private void putParsedJsonIfPresent(Map<String, Object> fields, String fieldName, String rawJson) {
        if (isBlank(rawJson)) {
            return;
        }
        try {
            fields.put(fieldName, OBJECT_MAPPER.readValue(rawJson, Object.class));
        } catch (Exception ex) {
            fields.put(fieldName, rawJson);
        }
    }

    // Bugfix 04/09/2026 (bao loi nguoi dung: tab "Dang xu ly" - dangxuly, status IN ('1','2') - luon
    // rong): Domino that (ChuyenXuLy.java:240-247) KHONG bo qua khi thieu deadline_date - luon goi
    // tinhSoNgayConLai(session, docVBden) VO DIEU KIEN khi status=="0" va co nguoi chu tri, va ham do
    // (TDDocumentUlties.java:2208-2215) tra ve 0 (KHONG throw) neu @Date(deadline_date) loi/rong -
    // nghia la van bang phan cong ma KHONG co deadline_date VAN chuyen status "0" -> "2" (con han,
    // dang xu ly) ngay lap tuc, giong het bugfix da ap dung o KetThucXuLyUseCase/
    // CapNhatKetQuaXuLyUseCase#recomputeStatus (cung 1 ket luan, cung tinhSoNgayConLai that). Truoc
    // day method nay SAI: tu them dieu kien "bo qua neu thieu deadline_date" KHONG co trong Domino
    // that - khien MOI van ban duoc phan cong ma chua co deadline_date (thuc te: HAU HET du lieu that
    // trong tenant, xac nhan qua truy van DB truc tiep) bi ket cung o status "0" mai mai, khong bao
    // gio len duoc "1"/"2" - lam tab "Dang xu ly" luon rong du user_processing dung.
    // "status" la cot tinh document.status (xem ghi chu o updateParentDocument) - ghi qua
    // parent.update(...), KHONG qua fields map (dynamic_fields).
    private void recomputeStatusOnFirstAssignment(BusinessDocument parent, Map<String, Object> fields, String statusBeforeUpdate) {
        if (!VbdenStatusService.STATUS_MOI.equals(statusBeforeUpdate)) {
            return;
        }
        LocalDate deadlineDate = parseDate(asString(fields.get(DEADLINE_DATE_FIELD)));
        int remainingDays = deadlineDate != null
            ? vbdenStatusService.computeRemainingDays(deadlineDate, null)
            : 0;
        // Vua phan cong chu tri lan dau => chac chan chua "da xu ly xong" (hasProcessed=false).
        String newStatus = vbdenStatusService.determineStatus(remainingDays, true, false);
        parent.update(null, null, newStatus, null, null, null, null);
    }

    // Bugfix 27/09/2026 (bao loi nguoi dung "van ban den -> chu tri xu ly -> cot Nguoi chuyen thinh
    // thoang khong the hien (demo3)"): truoc day ghi entry theo khoa TU DAT (nguoi_chuyen/thoi_gian/
    // user_process dang List, KHONG co unit_*) - KHAC hop dong Domino that TDUlties.updateChuyenXuLy
    // History() ma FE (ColumnsDen*.jsx#findUserCreatorByUsername) doc: user_creator + timeupdate
    // "dd/MM/yyyy HH:mm:ss" + user_process/user_coordinate/user_to_read/user_manage/unit_process/
    // unit_coordinate/unit_to_read (chuoi ";", CHI dat khi co gia tri; rong thi lay tu *_obj theo
    // username/identifier). Hau qua: moi van ban phan cong qua Java mat ten nguoi chuyen, van ban
    // du lieu Domino cu van hien -> "thinh thoang". Giu them action/opinion (bo sung cua Java, FE
    // khong doc, vo hai). Du lieu cu da ghi sai khoa: chuan hoa bang migration V1.0.66.
    //
    // KHAC CO Y Domino: Domino CHI ghi voi chuyenxuly/vanthuchuyenvanban (ChuyenXuLy.java:193), bo sot
    // phanxuly/phanphoi (ChuyenXuLy.java:432-439 - CUNG giao chu tri) => van ban giao qua "Phan xu ly"/
    // "Phan phoi" KHONG BAO GIO co "Nguoi chuyen" ngay ca tren Domino (nguyen nhan "thinh thoang").
    // Ghi cho 4 action giao chu tri; KHONG ghi chuyenphoihop/xemdebiet/capnhat* (dung Domino - cot
    // "Nguoi chuyen" chi o tab chu tri, ghi them se hien sai nguoi chuyen phoi hop).
    static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    static final java.util.Set<String> HISTORY_ACTIONS =
        java.util.Set.of("chuyenxuly", "vanthuchuyenvanban", "phanxuly", "phanphoi");

    private void appendChuyenXuLyHistory(
            Map<String, Object> fields, ChuyenXuLyVbdenRequest request, String actingUsername, List<String> newAssignees) {
        String action = request.getAction() == null ? "" : request.getAction().trim();
        if (!HISTORY_ACTIONS.contains(action.toLowerCase())) {
            return;
        }
        List<Object> history = new ArrayList<>();
        Object existing = fields.get(CHUYENXULY_HISTORY_FIELD);
        if (existing instanceof List<?> existingList) {
            history.addAll(existingList);
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        putIfNotBlank(entry, "user_creator", actingUsername);
        String userProcess = firstNonBlank(request.getUserProcess(),
            joinObjValues(request.getUserProcessObj(), "username") + joinObjValues(request.getUserAssignerObj(), "username"));
        if (userProcess.isBlank() && newAssignees != null && !newAssignees.isEmpty()) {
            userProcess = String.join(";", newAssignees);
        }
        putIfNotBlank(entry, USER_PROCESS_FIELD, userProcess);
        putIfNotBlank(entry, "user_coordinate",
            firstNonBlank(request.getUserCoordinate(), joinObjValues(request.getUserCoordinateObj(), "username")));
        putIfNotBlank(entry, "user_to_read",
            firstNonBlank(request.getUserToRead(), joinObjValues(request.getUserToReadObj(), "username")));
        putIfNotBlank(entry, "user_manage", request.getUserManage());
        putIfNotBlank(entry, "unit_process",
            firstNonBlank(request.getUnitProcess(), joinObjValues(request.getUserProcessObj(), "identifier")));
        putIfNotBlank(entry, "unit_coordinate",
            firstNonBlank(request.getUnitCoordinate(), joinObjValues(request.getUserCoordinateObj(), "identifier")));
        putIfNotBlank(entry, "unit_to_read",
            firstNonBlank(request.getUnitToRead(), joinObjValues(request.getUserToReadObj(), "identifier")));
        entry.put("timeupdate", LocalDateTime.now().format(HISTORY_TIME_FORMAT));
        entry.put("action", action);
        putIfNotBlank(entry, "opinion", request.getOpinion());
        history.add(entry);

        fields.put(CHUYENXULY_HISTORY_FIELD, history);
    }

    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }

    // Domino TDUlties.get_itemsValue_by_stringArrObj: noi gia tri itemName cua tung phan tu mang JSON,
    // moi gia tri kem ";" o cuoi. JSON loi/rong -> "".
    private static String joinObjValues(String jsonArray, String itemName) {
        if (jsonArray == null || jsonArray.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        try {
            for (var node : OBJECT_MAPPER.readTree(jsonArray)) {
                var value = node.get(itemName);
                if (value != null && !value.asText().isBlank()) {
                    result.append(value.asText().trim()).append(';');
                }
            }
        } catch (Exception ex) {
            return "";
        }
        return result.toString();
    }

    private Map<String, Object> buildSnapshot(BusinessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    // Bo sung 22/08/2026 (bugfix nghiem trong, cung dang voi D.14/R4): truoc day CHI hieu dang
    // List<String> - im lang tra RONG neu field dang la chuoi scalar (";"-joined). Dieu nay TRUOC
    // KIA "an toan" vi khong co nhanh nao khac ghi user_process/user_coordinate/... dang scalar -
    // nhung sau khi sua DonViNguoiNhanXuLy.jsx (22/08/2026) de TiepNhan/Luu cung ghi cac field nay
    // (qua AddDynamicDocumentUseCase/FieldTypeInferrer, luu SCALAR TEXT vi khong khop hau to nao),
    // lan ChuyenXuLy DAU TIEN tren 1 van ban moi tao/hieu chinh se doc nham RONG va am tham lam mat
    // du lieu chu tri/phoi hop/de biet da chon truoc do. Doc khoan dung ca 2 dang (cung triet ly
    // readMultiValue() cua LuuVanBanDenUseCase) - LUON ghi lai duoi dang List (khong doi, giu nguyen
    // hanh vi ghi hien co cua class nay).
    private List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        } else if (value != null) {
            for (String part : value.toString().split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private List<String> splitSemicolon(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String part : value.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void addAllDistinct(List<String> target, List<String> toAdd) {
        for (String value : toAdd) {
            if (!target.contains(value)) {
                target.add(value);
            }
        }
    }

    private void addOne(Map<String, Object> fields, String fieldName, String value) {
        List<String> list = toStringList(fields.get(fieldName));
        if (!list.contains(value)) {
            list.add(value);
        }
        fields.put(fieldName, list);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    // Bugfix 31/08/2026 (khao sat sau khi port "toanbovanban" - xem docs/analysis/02-checklist-port-
    // vbden-con-lai.md): comment cu o day chep quy uoc ISO tu NhacViecUseCase/ThongKeNhiemVuUseCase -
    // SAI cho field nay cu the. deadline_date cua vbden LUON den tu FE dang chuoi "dd/MM/yyyy"
    // (request.getDeadlineDate() <- SubFormChuyenXuLy.jsx dong ~417 goi date_to_dd_mm_yyyy() truoc
    // khi gui, khong co ngoai le) - LocalDate.parse(value) mac dinh chi nhan ISO nen truoc day LUON
    // nem DateTimeParseException (bi catch, tra null) voi du lieu that -> recomputeStatusOnFirstAssignment
    // luon bi bo qua am tham voi moi van ban. Sua dung DateTimeFormatter rieng cho dinh dang that.
    private static final DateTimeFormatter DEADLINE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DEADLINE_DATE_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }
}
