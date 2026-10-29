package com.td.application.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.realtime.RealtimeNotification;
import com.td.application.realtime.RealtimeNotifier;
import com.td.application.processdiagram.ProcessDiagramService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.vbden.VbdenResDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * N11 (01/09/2026 — "Thu hồi" văn bản đến, xem {@code choxuly/components/ItemsActiveList.jsx} hàm
 * {@code handleThuHoi}/{@code thuhoivanban}): tương đương {@code ThuHoiVanBan.java} thật.
 *
 * <p><b>Xác nhận nhánh vận hành thật (D.42, {@code docs/analysis/02-checklist-port-vbden-con-lai.md})
 * </b>: FE gửi CHỈ {@code {opinion}} trong body (không {@code type}/{@code user_process_obj}...) —
 * đối chiếu điều kiện Domino gốc ({@code !parentid.equals("") && !type.equals("all")}, cả 2 đọc từ
 * body) → với dữ liệu thật, nhánh vận hành LUÔN là {@code thuHoiToanBo()} (thu hồi TOÀN BỘ người đã
 * được giao qua các lần chuyển xử lý DO CHÍNH người thực hiện thao tác này tạo ra), KHÔNG phải
 * {@code thuHoi()} (thu hồi 1 người cụ thể) — use case này CHỈ port {@code thuHoiToanBo()}.</p>
 *
 * <p><b>Thuật toán {@code thuHoiToanBo()}</b>:</p>
 * <ol>
 *   <li>Quét MỌI bản ghi {@code vbden_res} có {@code parentid} = văn bản này, lọc còn lại bản ghi có
 *   {@code nguoi_thuc_hien} = người đang thao tác VÀ {@code action} thuộc
 *   {@link #REVOCABLE_ACTIONS} (đúng {@code chuyenxuly}/{@code vanthuchuyenvanban}/{@code phanphoi}
 *   — "vanthuchuyenvanban" không tồn tại trong hành động Java hiện có nên không bao giờ khớp, giữ lại
 *   cho đúng danh sách Domino gốc).</li>
 *   <li>Với MỖI bản ghi khớp, đọc 3 field snapshot {@code user_process_obj}/{@code user_coordinate_obj}/
 *   {@code user_to_read_obj} (JSON mảng {@code {username|user_receiver, identifier, name}} — snapshot
 *   TẠI THỜI ĐIỂM chuyển xử lý đó, mới được {@link ChuyenXuLyVbdenUseCase} ghi thêm — xem javadoc lớp
 *   đó) — MỖI người/đơn vị trong snapshot mà HIỆN VẪN CÒN trong field tương ứng của văn bản CHA
 *   ({@code user_process}/{@code user_coordinate}/{@code user_to_read} — "còn chưa xử lý") thì bị
 *   LOẠI khỏi field đó (và field {@code unit_*} tương ứng nếu khớp theo identifier).</li>
 *   <li>Thêm người đang thao tác trở lại {@code user_process} + {@code user_recalled}, loại khỏi
 *   {@code user_processed}. Tính lại {@code status} (công thức giống {@link XuLyLaiUseCase}).</li>
 *   <li>Tạo 1 bản ghi {@code vbden_res} MỚI ({@code action="thuhoi"}, {@code opinion}=lý do,
 *   snapshot lại 3 field {@code _obj} = DANH SÁCH người/đơn vị VỪA bị thu hồi — đúng
 *   {@code TaoQTXL()} thật).</li>
 *   <li>Xoá node sơ đồ xử lý ({@link ProcessDiagramService#removeNode}) cho MỖI username vừa bị thu
 *   hồi — hạ tầng đã có sẵn từ R8, không cần xây mới.</li>
 * </ol>
 *
 * <p><b>Giới hạn ĐÃ BIẾT, có chủ đích</b> (không phải thiếu sót): (1) CHỈ hoạt động ĐÚNG cho bản ghi
 * {@code vbden_res} được tạo TỪ 01/09/2026 trở đi (khi {@link ChuyenXuLyVbdenUseCase} bắt đầu ghi 3
 * field snapshot) — bản ghi cũ hơn không có snapshot nên không đóng góp gì vào danh sách thu hồi
 * (không lỗi, chỉ đơn giản KHÔNG tìm thấy ai để thu hồi từ các lần chuyển xử lý CŨ); (2) không port
 * "theo dõi" ({@code user_track_obj}) — trường này Domino gốc cũng đọc nhưng KHÔNG BAO GIỜ có dữ
 * liệu thật (FE {@code SubFormChuyenXuLy.jsx} không gửi {@code user_track_obj}, xác nhận qua
 * {@link ChuyenXuLyVbdenRequest} không có field này) nên luôn là no-op ở CẢ 2 bên, không đáng port;
 * (3) không port "phụ trách" ({@code user_manage}) — Domino gốc CŨNG không thu hồi field này trong
 * {@code thuHoiToanBo()}.</p>
 *
 * <p><b>Bổ sung 18/09/2026 (cutover {@code ModalThuHoiVanBan.jsx} — màn chi tiết văn bản đến)</b>:
 * modal đó còn dùng nhánh {@code thuHoi()} (Domino gốc, "Chọn đơn vị/người để thu hồi") — KHÁC hẳn
 * {@code thuHoiToanBo()} ở trên: KHÔNG derive từ lịch sử {@code vbden_res}, mà tin THẲNG 4 mảng
 * {@code *Obj} FE gửi trong request ({@link ThuHoiVanBanRequest#getType()}{@code =="select"}) — FE
 * đã tự lọc theo lựa chọn người dùng trong bảng (nguồn dữ liệu bảng đó = {@code GetNguoiThuHoiUseCase},
 * port riêng {@code GetNguoiThuHoi.java} thật). Tái dùng NGUYÊN VẸN {@link #revokeFromSnapshot} (đã
 * có sẵn, đã test) cho nhánh này — chỉ khác NGUỒN {@code rawValue} (từ request thay vì snapshot lịch
 * sử). Nhánh {@code "all"}/mặc định (null/rỗng, callsite {@code ItemsActiveList.jsx} không gửi
 * {@code type}) GIỮ NGUYÊN 100% thuật toán {@code thuHoiToanBo()} cũ, không đổi 1 dòng — tránh hồi
 * quy callsite đang chạy thật.</p>
 *
 * <p><b>"Theo dõi" ({@code user_track}) ĐƯỢC thu hồi ở nhánh {@code "select"}</b> (khác giới hạn (2)
 * ở trên — giới hạn đó vẫn đúng cho nhánh {@code "all"}): {@code GetNguoiThuHoiUseCase} đọc
 * {@code user_track} SỐNG (không qua snapshot {@code vbden_res} — field này được ghi bởi
 * {@link TheoDoiVanBanDenUseCase} qua 1 luồng hoàn toàn khác, CÓ dữ liệu thật), nên nhánh chọn lọc
 * portable được đúng hành vi Domino {@code thuHoi()} (có đọc {@code user_track_obj}).</p>
 *
 * <p><b>Bổ sung 08/09/2026 — giai đoạn 2 {@code vbden.forward_unit}</b> (yêu cầu người dùng: "giai
 * đoạn 1 cơ bản đã xong, làm giai đoạn 2 LayLaiVanBanUseCase/ThuHoiVanBanUseCase"): NGOÀI việc thu hồi
 * cục bộ ở trên, nếu văn bản này đã từng "chuyển xử lý" sang đơn vị khác tenant
 * ({@code forwarded_units}) thì CASCADE revoke sang TẤT CẢ bản sao đó — xem javadoc
 * {@link PublishForwardUnitRevokeUseCase} về phạm vi (cascade TOÀN BỘ entry còn hiệu lực, không lọc
 * thêm theo "ai đã forward" — lý do ghi rõ ở đó, cùng áp dụng cho cả 2 use case "Lấy lại VB"/"Thu
 * hồi").</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ThuHoiVanBanUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_THU_HOI = "thuhoi";

    // Bo 'private' 18/09/2026: GetNguoiThuHoiUseCase (cung package) can doc lai DUNG danh sach action
    // nay de khop chinh xac tap hop ban ghi vbden_res ma ThuHoiVanBanUseCase se xu ly (nhanh "all").
    // 11/10/2026 (báo lỗi demo9 "thu hồi báo không còn người để thu hồi"): thêm "phanxuly" — ChuyenXuLy
    // ghi user_delivered cho CẢ phanxuly lẫn chuyenxuly (nút "Thu hồi" hiện), nhưng tập này (chép từ
    // Domino ThuHoiVanBan/GetNguoiThuHoi, thiếu sót gốc) bỏ phanxuly → chuyển bằng "Phân xử lý" thì
    // danh sách thu hồi luôn rỗng dù người nhận chưa làm gì.
    static final Set<String> REVOCABLE_ACTIONS = Set.of(
        ChuyenXuLyVbdenUseCase.ACTION_CHUYEN_XU_LY, ChuyenXuLyVbdenUseCase.ACTION_PHAN_PHOI,
        ChuyenXuLyVbdenUseCase.ACTION_PHAN_XU_LY, "vanthuchuyenvanban");

    // Bo sung 18/09/2026 (nhanh "select" - xem javadoc lop): field nay TRUOC DAY khong duoc
    // ThuHoiVanBanUseCase dung toi (chi GetNguoiThuHoiUseCase doc SONG qua TheoDoiVanBanDenUseCase),
    // gio nhanh chon loc THAT SU ghi field nay nen can khai bao rieng.
    static final String USER_TRACK_FIELD = "user_track";
    private static final String USER_DELIVERED_FIELD = "user_delivered";
    private static final String TYPE_SELECT = "select";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String UNIT_PROCESS_FIELD = "unit_process";
    private static final String USER_PROCESSED_FIELD = "user_processed";
    private static final String USER_RECALLED_FIELD = "user_recalled";
    private static final String USER_COORDINATE_FIELD = "user_coordinate";
    private static final String UNIT_COORDINATE_FIELD = "unit_coordinate";
    private static final String USER_TO_READ_FIELD = "user_to_read";
    private static final String UNIT_TO_READ_FIELD = "unit_to_read";
    private static final String DEADLINE_DATE_FIELD = "deadline_date";
    private static final String NGUOI_THUC_HIEN_FIELD = "nguoi_thuc_hien";
    private static final String PARENT_ID_FIELD = "parentid";
    private static final String ACTION_FIELD = "action";
    private static final DateTimeFormatter DEADLINE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // "Toan bo" ban ghi vbden_res cua 1 van ban thuc te khong nhieu (vai chuc lan xu ly la cung) -
    // dung 1 trang lon duy nhat thay vi phan trang, cung tien le pageSize:1000 da dung o QuaTrinhXuLy.
    private static final int MAX_HISTORY_RECORDS = 1000;
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong". USER_TRACK_FIELD them 18/09/2026 -
    // chi THAT SU bi ghi o nhanh "select" (fields.put co dieu kien, xem revokeFromSnapshot), nhung
    // khai bao san trong tap "field ACL lien quan" khong gay hai cho nhanh "all" (applyOnUpdate chi
    // doc gia tri HIEN CO trong map fields, khong tu suy dien them).
    private static final Set<String> AUTHOR_TOUCHED_FIELDS = Set.of(
        USER_PROCESS_FIELD, UNIT_PROCESS_FIELD, USER_PROCESSED_FIELD, USER_RECALLED_FIELD,
        USER_COORDINATE_FIELD, UNIT_COORDINATE_FIELD, USER_TO_READ_FIELD, UNIT_TO_READ_FIELD,
        USER_TRACK_FIELD);

    private final DocumentRepository documentRepository;
    private final VbdenResDocumentRepository vbdenResDocumentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final VbdenStatusService vbdenStatusService;
    private final ProcessDiagramService processDiagramService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    // Bo sung 08/09/2026 (giai doan 2 vbden.forward_unit - xem javadoc lop).
    private final PublishForwardUnitRevokeUseCase publishForwardUnitRevokeUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;
    private final VbdenRevokeRecipientMatcher recipientMatcher;
    private final RealtimeNotifier realtimeNotifier;

    public Result<UUID> execute(ThuHoiVanBanRequest request) {
        var parentOptional = documentRepository.findById(request.getParentid());
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var parent = parentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(parent.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (isBlank(actingUsername)) {
            return Result.failure("Khong xac dinh duoc nguoi dung hien tai");
        }

        List<VbdenResDocument> history = vbdenResDocumentRepository.findByParentId(
            request.getParentid().toString(), PageRequest.of(0, MAX_HISTORY_RECORDS)).getContent();
        if (request.isCleanupOnly()) {
            return cleanupDeliveredMarkersOnly(parent, history, actingUsername);
        }
        RevokeResult revoked;
        if (TYPE_SELECT.equalsIgnoreCase(request.getType())) {
            revoked = computeSelectiveRevocation(parent, request);
        } else {
            revoked = computeRevocation(parent, history, actingUsername);
        }
        clearDeliveredMarkersIfNothingLeft(revoked.fields(), history, actingUsername);

        updateParentDocument(parent, actingUsername, revoked, request.getOpinion());
        var childResult = createProcessingRecord(request, actingUsername, revoked);

        removeDiagramNodes(request.getParentid(), revoked.revokedUsernames());
        if (childResult.isSuccess()) {
            // ThuHoiVanBan.java#sendSocketNotSave: Domino báo user_process CÒN LẠI; ở đây báo đúng người BỊ thu hồi
            // (văn bản biến khỏi danh sách của họ).
            realtimeNotifier.notify(RealtimeNotification.of(DOCUMENT_TYPE, "thuhoi", parent.getId(),
                    java.util.Objects.toString(revoked.fields().get("code"), null))
                .to(revoked.revokedUsernames())
                .exclude(actingUsername)
                .opinion(request.getOpinion()));
        }

        return childResult;
    }

    // Domino thuHoiToanBo(): quet moi ban ghi vbden_res do CHINH nguoi nay tao (action chuyenxuly/
    // vanthuchuyenvanban/phanphoi), doi chieu snapshot _obj voi field HIEN TAI cua van ban cha de biet
    // ai CON CHUA xu ly (con o trong user_process/user_coordinate/user_to_read) - CHI nhung nguoi do
    // moi bi thu hoi (khong dung nham nguoi da xu ly xong hoac da duoc nguoi KHAC giao sau do).
    private RevokeResult computeRevocation(
            BusinessDocument parent, List<VbdenResDocument> historyRecords, String actingUsername) {
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        List<String> userProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        List<String> unitProcess = toStringList(fields.get(UNIT_PROCESS_FIELD));
        List<String> userCoordinate = toStringList(fields.get(USER_COORDINATE_FIELD));
        List<String> unitCoordinate = toStringList(fields.get(UNIT_COORDINATE_FIELD));
        List<String> userToRead = toStringList(fields.get(USER_TO_READ_FIELD));
        List<String> unitToRead = toStringList(fields.get(UNIT_TO_READ_FIELD));

        ArrayNode chutriRevoked = OBJECT_MAPPER.createArrayNode();
        ArrayNode phoihopRevoked = OBJECT_MAPPER.createArrayNode();
        ArrayNode xemdebietRevoked = OBJECT_MAPPER.createArrayNode();
        Set<String> revokedUsernames = new LinkedHashSet<>();
        Map<String, Set<String>> memberCache = recipientMatcher.newMemberCache();

        for (VbdenResDocument historyRecord : historyRecords) {
            Map<String, Object> historyFields = dynamicDocumentFieldsService.parseDynamicFields(
                historyRecord.getDynamicFieldsJson());
            String recordCreator = asString(historyFields.get(NGUOI_THUC_HIEN_FIELD));
            String recordAction = asString(historyFields.get(ACTION_FIELD));
            if (!actingUsername.equals(recordCreator) || !REVOCABLE_ACTIONS.contains(recordAction)) {
                continue;
            }

            revokeFromSnapshot(
                historyFields.get(ChuyenXuLyVbdenUseCase.USER_PROCESS_OBJ_FIELD_RAW),
                userProcess, unitProcess, revokedUsernames, chutriRevoked, memberCache);
            revokeFromSnapshot(
                historyFields.get(ChuyenXuLyVbdenUseCase.USER_COORDINATE_OBJ_FIELD_RAW),
                userCoordinate, unitCoordinate, revokedUsernames, phoihopRevoked, memberCache);
            revokeFromSnapshot(
                historyFields.get(ChuyenXuLyVbdenUseCase.USER_TO_READ_OBJ_FIELD_RAW),
                userToRead, unitToRead, revokedUsernames, xemdebietRevoked, memberCache);
        }

        fields.put(USER_PROCESS_FIELD, userProcess);
        fields.put(UNIT_PROCESS_FIELD, unitProcess);
        fields.put(USER_COORDINATE_FIELD, userCoordinate);
        fields.put(UNIT_COORDINATE_FIELD, unitCoordinate);
        fields.put(USER_TO_READ_FIELD, userToRead);
        fields.put(UNIT_TO_READ_FIELD, unitToRead);

        // "theodoiRevoked" luon RONG o nhanh nay (gioi han (2) trong javadoc lop - user_track_obj
        // khong bao gio co du lieu that qua snapshot vbden_res) - giu nguyen hanh vi cu, chi them cho
        // khop shape RevokeResult dung chung voi nhanh "select" (computeSelectiveRevocation).
        return new RevokeResult(fields, revokedUsernames, chutriRevoked, phoihopRevoked, xemdebietRevoked,
            OBJECT_MAPPER.createArrayNode());
    }

    // Bo sung 18/09/2026 (cutover ModalThuHoiVanBan.jsx - xem javadoc lop muc "Bo sung 18/09/2026"):
    // nhanh "select" (Domino thuHoi(), khac thuHoiToanBo() o tren) - KHONG doc lich su vbden_res, tin
    // THANG 4 mang *Obj FE gui (da tu loc theo lua chon nguoi dung trong bang GetNguoiThuHoiUseCase).
    // Tai dung NGUYEN VEN revokeFromSnapshot (khong sua) - chi khac NGUON rawValue.
    private RevokeResult computeSelectiveRevocation(BusinessDocument parent, ThuHoiVanBanRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        List<String> userProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        List<String> unitProcess = toStringList(fields.get(UNIT_PROCESS_FIELD));
        List<String> userCoordinate = toStringList(fields.get(USER_COORDINATE_FIELD));
        List<String> unitCoordinate = toStringList(fields.get(UNIT_COORDINATE_FIELD));
        List<String> userToRead = toStringList(fields.get(USER_TO_READ_FIELD));
        List<String> unitToRead = toStringList(fields.get(UNIT_TO_READ_FIELD));
        List<String> userTrack = toStringList(fields.get(USER_TRACK_FIELD));
        // Domino goc khong co "unit_track" - dung 1 danh sach rong lam pendingUnits cho nhanh nay
        // (revokeFromSnapshot van an toan voi danh sach rong, chi khop duoc theo username).
        List<String> noUnitTrack = new ArrayList<>();

        ArrayNode chutriRevoked = OBJECT_MAPPER.createArrayNode();
        ArrayNode phoihopRevoked = OBJECT_MAPPER.createArrayNode();
        ArrayNode xemdebietRevoked = OBJECT_MAPPER.createArrayNode();
        ArrayNode theodoiRevoked = OBJECT_MAPPER.createArrayNode();
        Set<String> revokedUsernames = new LinkedHashSet<>();
        Map<String, Set<String>> memberCache = recipientMatcher.newMemberCache();

        revokeFromSnapshot(request.getUserProcessObj(), userProcess, unitProcess, revokedUsernames, chutriRevoked, memberCache);
        revokeFromSnapshot(request.getUserCoordinateObj(), userCoordinate, unitCoordinate, revokedUsernames, phoihopRevoked, memberCache);
        revokeFromSnapshot(request.getUserToReadObj(), userToRead, unitToRead, revokedUsernames, xemdebietRevoked, memberCache);
        revokeFromSnapshot(request.getUserTrackObj(), userTrack, noUnitTrack, revokedUsernames, theodoiRevoked, memberCache);

        fields.put(USER_PROCESS_FIELD, userProcess);
        fields.put(UNIT_PROCESS_FIELD, unitProcess);
        fields.put(USER_COORDINATE_FIELD, userCoordinate);
        fields.put(UNIT_COORDINATE_FIELD, unitCoordinate);
        fields.put(USER_TO_READ_FIELD, userToRead);
        fields.put(UNIT_TO_READ_FIELD, unitToRead);
        fields.put(USER_TRACK_FIELD, userTrack);

        return new RevokeResult(fields, revokedUsernames, chutriRevoked, phoihopRevoked, xemdebietRevoked, theodoiRevoked);
    }

    // Domino getJsonArray(): moi node {username|user_receiver, identifier, name} trong snapshot ma
    // HIEN VAN CON trong pendingUsers (theo username) HOAC pendingUnits (theo identifier) thi bi LOAI
    // khoi 2 danh sach do + ghi vao revokedOut de lam snapshot cho ban ghi thu hoi moi.
    //
    // Bugfix 14/09/2026 (phat hien khi dieu tra bao loi GetNguoiTraLai tra rong cho demo2 - xem javadoc
    // VbdenObjSnapshotFields): nhan Object THO (khong con String) - du lieu that luu field "_obj" LA
    // List/Map da parse san (FieldTypeInferrer suy luan FieldType.JSON theo hau to "_obj"), KHONG con
    // la chuoi JSON. Truoc ban fix nay, asString(...).toString() tren cau truc do KHONG phai JSON hop
    // le, readTree luon nem loi, bi bat/bo qua AM THAM - "thu hoi toan bo" KHONG BAO GIO thu hoi duoc
    // ai qua nhanh nay trong thuc te (tests cu dung fixture chuoi JSON tho, khong khop du lieu that).
    /**
     * 09/10/2026 (báo lỗi demo9, VB 1/QĐ-SNNMT: chuyển cho demo15 rồi thu hồi 3 lần, mở lại VB vẫn hiện
     * "Phân công bổ sung / Bổ sung ý kiến / Thu hồi / Hủy theo dõi"): FE hiện các nút đó theo
     * {@code user_delivered}/{@code user_track} chứa người dùng, nhưng thu hồi (cả Domino — dòng gỡ
     * user_delivered trong ThuHoiVanBan.java bị comment) chưa bao giờ gỡ người thu hồi ra. Sau thu hồi,
     * nếu KHÔNG còn người/đơn vị nào do chính người này chuyển (chủ trì/phối hợp/xem để biết) đang xử lý
     * hoặc đã xử lý, thì gỡ người này khỏi user_delivered và user_track ("Trực tiếp theo dõi xử lý" tự
     * tích lúc chuyển). Còn ai thì giữ nguyên như cũ.
     */
    private void clearDeliveredMarkersIfNothingLeft(
            Map<String, Object> fields, List<VbdenResDocument> history, String actingUsername) {
        Set<String> recipientUsers = new LinkedHashSet<>();
        Set<String> recipientUnits = new LinkedHashSet<>();
        for (VbdenResDocument historyRecord : history) {
            Map<String, Object> historyFields = dynamicDocumentFieldsService.parseDynamicFields(
                historyRecord.getDynamicFieldsJson());
            if (!actingUsername.equals(asString(historyFields.get(NGUOI_THUC_HIEN_FIELD)))
                    || !REVOCABLE_ACTIONS.contains(asString(historyFields.get(ACTION_FIELD)))) {
                continue;
            }
            for (String key : List.of(ChuyenXuLyVbdenUseCase.USER_PROCESS_OBJ_FIELD_RAW,
                    ChuyenXuLyVbdenUseCase.USER_COORDINATE_OBJ_FIELD_RAW, ChuyenXuLyVbdenUseCase.USER_TO_READ_OBJ_FIELD_RAW)) {
                for (JsonNode node : VbdenObjSnapshotFields.readJsonArray(historyFields.get(key))) {
                    String username = node.hasNonNull("username") ? node.path("username").asText()
                        : node.path("user_receiver").asText("");
                    if (!username.isBlank()) {
                        recipientUsers.add(username);
                    }
                    String identifier = node.path("identifier").asText("");
                    if (!identifier.isBlank()) {
                        recipientUnits.add(identifier);
                    }
                }
            }
            recipientUsers.addAll(toStringList(historyFields.get(USER_PROCESS_FIELD)));
        }
        recipientUsers.remove(actingUsername);

        Set<String> involvedUsers = new LinkedHashSet<>();
        for (String field : List.of(USER_PROCESS_FIELD, USER_COORDINATE_FIELD, USER_TO_READ_FIELD, USER_PROCESSED_FIELD)) {
            involvedUsers.addAll(toStringList(fields.get(field)));
        }
        Set<String> involvedUnits = new LinkedHashSet<>();
        for (String field : List.of(UNIT_PROCESS_FIELD, UNIT_COORDINATE_FIELD, UNIT_TO_READ_FIELD)) {
            involvedUnits.addAll(toStringList(fields.get(field)));
        }
        if (recipientUsers.stream().anyMatch(involvedUsers::contains)
                || recipientUnits.stream().anyMatch(involvedUnits::contains)) {
            return;
        }
        for (String field : List.of(USER_DELIVERED_FIELD, USER_TRACK_FIELD)) {
            List<String> values = toStringList(fields.get(field));
            if (values.remove(actingUsername)) {
                fields.put(field, values);
            }
        }
    }

    private void revokeFromSnapshot(
            Object rawValue, List<String> pendingUsers, List<String> pendingUnits,
            Set<String> revokedUsernamesOut, ArrayNode revokedOut, Map<String, Set<String>> memberCache) {
        JsonNode parsed = VbdenObjSnapshotFields.readJsonArray(rawValue);
        for (JsonNode node : parsed) {
            String username = VbdenRevokeRecipientMatcher.username(node);
            String identifier = node.path("identifier").asText("");
            String name = node.path("name").asText("");
            // 11/10/2026: xem VbdenRevokeRecipientMatcher - don vi chi thu hoi khi con thanh vien dang giu
            // van ban, va thu hoi don vi thi go luon cac thanh vien do (truoc day sot nguoi duoc chuyen tiep
            // trong cung don vi).
            Set<String> holders = recipientMatcher.pendingHolders(node, pendingUsers, pendingUnits, memberCache);
            if (holders.isEmpty()) {
                continue;
            }
            for (String holder : holders) {
                if (!holder.isBlank() && pendingUsers.remove(holder)) {
                    revokedUsernamesOut.add(holder);
                }
            }
            ObjectNode entry = OBJECT_MAPPER.createObjectNode();
            if (!name.isBlank()) {
                entry.put("name", name);
            }
            if (!username.isBlank()) {
                entry.put("username", username);
            }
            if (!identifier.isBlank()) {
                entry.put("identifier", identifier);
                pendingUnits.remove(identifier);
            }
            if (!entry.isEmpty()) {
                revokedOut.add(entry);
            }
        }
    }

    private void updateParentDocument(
            BusinessDocument parent, String actingUsername, RevokeResult revoked, String opinion) {
        var before = buildSnapshot(parent);
        Map<String, Object> fields = revoked.fields();

        List<String> userProcessed = toStringList(fields.get(USER_PROCESSED_FIELD));
        userProcessed.remove(actingUsername);
        fields.put(USER_PROCESSED_FIELD, userProcessed);

        addOne(fields, USER_PROCESS_FIELD, actingUsername);
        addOne(fields, USER_RECALLED_FIELD, actingUsername);

        // Bo sung 08/09/2026 (giai doan 2 vbden.forward_unit - xem javadoc lop): cascade sang cac
        // ban sao da "chuyen xu ly" cho don vi khac tenant - PHAI goi TRUOC replaceDynamicFields de
        // co "revoked=true" duoc luu CUNG 1 giao dich voi thay doi routing chinh.
        publishForwardUnitRevokeUseCase.cascadeRevoke(fields, parent.getId(), actingUsername, opinion, ACTION_THU_HOI);

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));
        recomputeStatus(parent, fields);

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Xem ThuHoiVanBanRequest#cleanupOnly - chi luu khi that su go duoc dau.
    private Result<UUID> cleanupDeliveredMarkersOnly(
            BusinessDocument parent, List<VbdenResDocument> history, String actingUsername) {
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));
        List<String> deliveredBefore = toStringList(fields.get(USER_DELIVERED_FIELD));
        List<String> trackBefore = toStringList(fields.get(USER_TRACK_FIELD));
        clearDeliveredMarkersIfNothingLeft(fields, history, actingUsername);
        if (deliveredBefore.equals(toStringList(fields.get(USER_DELIVERED_FIELD)))
                && trackBefore.equals(toStringList(fields.get(USER_TRACK_FIELD)))) {
            return Result.success(parent.getId());
        }
        var before = buildSnapshot(parent);
        parent.replaceDynamicFields(toJson(fields));
        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        return Result.success(saved.getId());
    }

    private void recomputeStatus(BusinessDocument parent, Map<String, Object> fields) {
        LocalDate deadlineDate = parseDate(asString(fields.get(DEADLINE_DATE_FIELD)));
        if (deadlineDate == null) {
            log.debug("Bo qua recompute status (thuhoi): thieu hoac khong parse duoc deadline_date");
            return;
        }
        int remainingDays = vbdenStatusService.computeRemainingDays(deadlineDate, null);
        String newStatus = vbdenStatusService.determineStatus(remainingDays, true, false);
        parent.update(null, null, newStatus, null, null, null, null);
    }

    // Domino TaoQTXL(): ban ghi MOI action=thuhoi, snapshot lai CHINH danh sach vua bi thu hoi (de
    // audit/hien thi) - dung ten field Java thuc su ghi (nguoi_thuc_hien, khong phai user_creator).
    private Result<UUID> createProcessingRecord(ThuHoiVanBanRequest request, String actingUsername, RevokeResult revoked) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_THU_HOI);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put(PARENT_ID_FIELD, request.getParentid().toString());
        childFields.put(ACTION_FIELD, ACTION_THU_HOI);
        childFields.put(NGUOI_THUC_HIEN_FIELD, actingUsername);
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            childFields.put("opinion", request.getOpinion());
        }
        if (!revoked.chutriRevoked().isEmpty()) {
            childFields.put(ChuyenXuLyVbdenUseCase.USER_PROCESS_OBJ_FIELD_RAW, toJsonArray(revoked.chutriRevoked()));
        }
        if (!revoked.phoihopRevoked().isEmpty()) {
            childFields.put(ChuyenXuLyVbdenUseCase.USER_COORDINATE_OBJ_FIELD_RAW, toJsonArray(revoked.phoihopRevoked()));
        }
        if (!revoked.xemdebietRevoked().isEmpty()) {
            childFields.put(ChuyenXuLyVbdenUseCase.USER_TO_READ_OBJ_FIELD_RAW, toJsonArray(revoked.xemdebietRevoked()));
        }
        // Bo sung 18/09/2026 (nhanh "select" - xem javadoc lop): "user_track_obj" khong co constant
        // dung chung (ChuyenXuLyVbdenUseCase khong ghi field nay, xem gioi han (2)) - dung ten field
        // Domino goc truc tiep, CHI thuc su non-empty o nhanh "select".
        if (!revoked.theodoiRevoked().isEmpty()) {
            childFields.put("user_track_obj", toJsonArray(revoked.theodoiRevoked()));
        }
        // Bo sung 03/09/2026 (cung gap voi ChuyenXuLyVbdenUseCase, xem javadoc lop do -
        // docs/views_domino/data_vbden_quatrinhxuly_mau*.txt): Domino ghi "identifier" tren MOI ban
        // ghi vbden_res, tab "Xem lien quan" loc theo field nay - resolve server-side, khong nhan tu FE.
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));
        childRequest.setFields(childFields);

        return addVbdenResDocumentUseCase.execute(childRequest);
    }

    private void removeDiagramNodes(UUID parentId, Set<String> revokedUsernames) {
        for (String username : revokedUsernames) {
            try {
                processDiagramService.removeNode(parentId, DOCUMENT_TYPE, username);
            } catch (Exception ex) {
                log.warn("Khong the xoa node so do xu ly cho van ban {} (username {}): {}",
                    parentId, username, ex.getMessage());
            }
        }
    }

    private Map<String, Object> buildSnapshot(BusinessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

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

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String toJsonArray(ArrayNode value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    // theodoiRevoked bo sung 18/09/2026 (nhanh "select" - xem javadoc lop): LUON rong o nhanh "all".
    private record RevokeResult(
            Map<String, Object> fields, Set<String> revokedUsernames,
            ArrayNode chutriRevoked, ArrayNode phoihopRevoked, ArrayNode xemdebietRevoked,
            ArrayNode theodoiRevoked) {
    }
}
