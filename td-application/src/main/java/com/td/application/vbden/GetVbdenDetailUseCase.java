package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.GetDynamicDocumentRequest;
import com.td.application.documents.GetDynamicDocumentUseCase;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.vbden.XinYKienDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * BL-210 (VBDen Core) — DoD (3): xem chi tiết văn bản đến với tất cả field (tĩnh + động), tương
 * thích API legacy {@code qlvb/vbden/XemChiTiet} ({@code XemChiTiet.java} thật).
 *
 * <p>Bugfix 21/08/2026 (Slice 9, phát hiện qua test thực tế trên màn "Vào sổ văn bản đến"): thêm
 * nhánh xử lý {@code idso} (id bản ghi "sổ", {@code documentType=vbden_so}) — trước bản này chỉ ủy
 * quyền thẳng cho {@link GetDynamicDocumentUseCase} (BL-101) với 1 {@code id} phẳng, không hiểu
 * kiến trúc 2-document nên xem chi tiết từ danh sách "vào sổ" luôn báo "Không tìm thấy văn bản
 * đến" ({@code document_type} của bản ghi sổ là {@code vbden_so}, không phải {@code vbden}). Join
 * lại với văn bản gốc CÙNG logic {@code GetSoVanBanDenUseCase.joinWithParentDocument} (field hiển
 * thị đọc LIVE từ văn bản gốc, field riêng của bản ghi sổ — document_number/document_register_id/
 * identifier_creator/received_date — ghi đè lên trên).</p>
 *
 * <p>Bugfix 21/08/2026 (lần 2, đọc lại {@code XemChiTiet.java} thật khi chuyển tiếp ~14 màn khác
 * đang gọi trực tiếp endpoint này sang Java) — 2 hành vi CHỨC NĂNG (không phải kỹ thuật) của nhánh
 * "phẳng" ({@code idso} rỗng, dùng bởi phần lớn các màn danh sách vbden) trước đây bị bỏ sót:</p>
 * <ol>
 *   <li><b>Ẩn document_number/document_register* theo tổ chức xem</b>: 1 văn bản gốc có thể được
 *   NHIỀU tổ chức "vào sổ" độc lập (mỗi tổ chức 1 bản ghi {@code vbden_so} riêng — kiến trúc
 *   2-document). Domino thật LUÔN xoá trắng 3 field này trước, chỉ điền lại nếu tìm được bản ghi sổ
 *   khớp ĐÚNG tổ chức người xem (qua {@code idso} client gửi, hoặc — khi {@code edit=="true"} hoặc
 *   {@code vt=="true"} — tự tra view {@code sovanban_by_idvbden} theo khoá
 *   {@code (parentid, identifier_creator)} với {@code identifier_creator} = identifier PHẲNG của
 *   người dùng hiện tại, {@code connection.user_token.identifier} — KHÔNG dùng identifier suy theo
 *   cấp, xem javadoc {@link KiemTrungSoDenUseCase} về sự không nhất quán này ngay trong Domino gốc).
 *   Thiếu bước này sẽ lộ document_number/sổ của tổ chức KHÁC cho người xem không liên quan.</li>
 *   <li><b>Đánh dấu "đã xem để biết"</b>: nếu username hiện tại có trong {@code user_to_read} của
 *   văn bản, chuyển sang {@code user_to_readed} (loại khỏi {@code user_to_read}) — ĐÂY LÀ CƠ CHẾ DUY
 *   NHẤT chuyển văn bản từ view {@code xemdebiet} (N10, "chưa xem để biết") sang {@code daxemdebiet}
 *   ("đã xem để biết") đã port ở {@code ViewQueryService} (lọc {@code user_to_read}/
 *   {@code user_to_readed} — 2 field khác nhau, xem dòng 239-244). KHÔNG port hành vi tương tự cho
 *   {@code user_manage}/{@code user_managed} (Domino cũ có làm) — chưa có view nào đã port dùng
 *   {@code user_managed}, tránh suy diễn thêm hành vi chưa xác nhận cần.</li>
 * </ol>
 *
 * <p><b>Bổ sung 18/09/2026 (người dùng báo "Nhật ký đã xem" trống — {@code LogReadCollapse.jsx} đọc
 * {@code resdata.log_read_obj} nhưng chưa use case nào ghi field này)</b>: port nốt
 * {@code log_read_obj} (đã cố ý HOÃN ở bugfix 21/08/2026 phía trên, "để dành nếu sau này cần") —
 * xem {@link #appendLogReadEntry}, mirror {@code XemChiTiet.java#markDocumentViewedFlat} thật: mỗi
 * lần xem chi tiết (CẢ 2 nhánh phẳng/{@code idso}), nếu username hiện tại CHƯA từng xuất hiện trong
 * {@code log_read_obj} thì nối thêm {@code "username|dd/MM/yyyy HH:mm:ss"} (dedupe theo username —
 * chỉ ghi LẦN XEM ĐẦU TIÊN của mỗi người, không ghi mỗi lần xem lại, đúng Domino gốc).</p>
 *
 * <p><b>Bổ sung 30/08/2026 ("port thêm tệp đính kèm cho văn bản đến")</b>: thêm field {@code files}
 * vào response — TRƯỚC ĐÓ hoàn toàn thiếu, khiến file vừa tải lên qua Java (endpoint chung
 * {@code POST /api/v1/documents/{id}/files}, dùng ngay sau {@code TiepNhan}/{@code Luu} — xem
 * {@code vbdenApi.js#uploadVbdenFile}) không bao giờ hiển thị lại khi mở "Xem chi tiết". Đúng khuôn
 * {@code GetVbdiDetailUseCase#collectFiles}/{@code GetDuThaoDetailUseCase#collectFiles} (đã làm cho
 * vbdi/dự thảo, cùng ngày 28/08/2026) — 1 query {@code ListDocumentFilesUseCase} theo id văn bản GỐC
 * (không phải id bản ghi "sổ"), KHÔNG N+1, GHI ĐÈ (không merge) field {@code files} nào đã có sẵn
 * trong {@code dynamic_fields} — shape {@code {name,size,type,file_id}} khớp {@code Files/
 * filesVBden.jsx} đang đọc ({@code resdata?.files}). <b>Đã port luôn (cùng ngày, theo yêu cầu)</b>:
 * {@code filesVBden.jsx} (dùng chung bởi vbden/vbdi/duthao) đã được cập nhật rẽ nhánh theo
 * {@code file.file_id} tại 9 vị trí (mở/xem, tải đơn lẻ, tải zip nhiều file, xoá, đổi tên) — đúng
 * cơ chế {@code filesVBdi.jsx} đã làm 28/08/2026 — file KHÔNG có {@code file_id} (Domino cũ) vẫn đi
 * qua đường cũ nguyên vẹn.</p>
 *
 * <p><b>Bugfix 07/09/2026 (người dùng báo lỗi hiển thị: "Văn bản đến qua mạng ngày undefined")</b>:
 * Domino thật ({@code XemChiTiet.java} dòng ~192-193, {@code XemChiTietVBdenNoiBo.java} tương tự)
 * LUÔN tính field {@code created} (định dạng {@code dd/MM/yyyy HH:mm:ss}, từ metadata
 * {@code @Created} của NotesDocument) trên MỌI response — FE ({@code ChiTietVbden.jsx} và 3 file
 * tương tự) đọc THẲNG {@code resdata?.created} cho dòng "Văn bản đến qua mạng ngày ...". Java trước
 * đây KHÔNG BAO GIỜ set key {@code created} (chỉ có {@code created_on}, khác tên VÀ khác kiểu — đối
 * tượng ngày giờ thô, không phải chuỗi đã định dạng) ở CẢ 3 nhánh response — FE nhận {@code undefined}.
 * Bổ sung {@code created} (định dạng GIỐNG {@code received_date} phía td-worker —
 * {@code dd/MM/yyyy HH:mm:ss}, xem {@code VbdenIncomingDocumentWriter}, td-woker) ở cả 3 nhánh,
 * tính từ {@code createdOn} của ĐÚNG entity đang trả (văn bản gốc, hoặc {@code XinYKienDocument})
 * — KHÔNG đổi/xoá {@code created_on} đã có (nhiều nơi khác có thể đang đọc field đó).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GetVbdenDetailUseCase {

    public static final String DOCUMENT_TYPE = "vbden";

    // N08 (31/08/2026 - "cho y kien" TVUB/TVNB/BCH/BTV/TTDU, xem javadoc XinYKienDocument):
    // "van ban den gia" (Form="xinykien" phia Domino) dung BANG VAT LY RIENG (yeu cau nguoi dung
    // "tao bang rieng"), nhung ItemsTVUB.jsx (FE man chochoykien/dachoykien) van goi CHUNG
    // XemChiTiet nhu van ban den that - executePlainDocument FALLBACK sang
    // xinYKienDocumentRepository khi tra cuu bang chung (GetDynamicDocumentUseCase, chi biet bang
    // "documents") khong tim thay - xem tryLoadXinYkienDocument. Khac ban truoc (widening 1 dieu
    // kien check) da bi sai tu khi xinykien chuyen bang - GetDynamicDocumentUseCase se KHONG BAO GIO
    // tim thay xinykien nua du co widening gi di nua.
    private static final String XINYKIEN_DOCUMENT_TYPE = "vbden_xinykien";

    private static final String REGISTER_ENTRY_DOCUMENT_TYPE = TiepNhanVbdenUseCase.REGISTER_ENTRY_DOCUMENT_TYPE;
    private static final String PARENT_ID_FIELD = "parentid";
    private static final String IDENTIFIER_CREATOR_FIELD = "identifier_creator";
    private static final String DOCUMENT_REGISTER_FIELD = "document_register";
    private static final String DOCUMENT_NUMBER_FIELD = "document_number";
    private static final String DOCUMENT_REGISTER_ID_FIELD = "document_register_id";
    private static final String IDSO_FIELD = "idso";
    // Bugfix 24/08/2026: key rieng cho module discriminator (GetDynamicDocumentUseCase cung doi
    // dong bo) - KHONG con dung "document_type" (dung voi field nghiep vu "Loai van ban").
    private static final String DATABASETYPE_FIELD = "databasetype";
    private static final String USER_TO_READ_FIELD = "user_to_read";
    private static final String USER_TO_READED_FIELD = "user_to_readed";
    // Bo sung 18/09/2026 (nguoi dung bao "Nhat ky da xem dang trong"): field NAY ket thuc "_obj" nen
    // FieldTypeInferrer se suy FieldType.JSON cho MOI luong ghi khac (Luu/hieu chinh) - nhung o day
    // ghi TRUC TIEP qua toJson(fields) (khong qua DynamicFieldValueConverter), luu dung 1 CHUOI TEXT
    // (khong phai JSON long) dung format XemChiTiet.java that (markDocumentViewedFlat): "username|dd/
    // MM/yyyy HH:mm:ss", noi nhieu dong bang ";" - KHONG dung VbdenObjSnapshotFields (field nay khong
    // phai JSON array, xem memory "vbden-obj-field-json-vs-string-trap" - bay do CHI ap dung khi field
    // duoc ghi qua FieldTypeInferrer voi noi dung JSON hop le, khong ap dung o day).
    private static final String LOG_READ_FIELD = "log_read_obj";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_TO_READ_FIELD, USER_TO_READED_FIELD);
    // Bugfix 07/09/2026 (xem javadoc lop) - CUNG dinh dang voi VbdenIncomingDocumentWriter (td-woker).
    private static final DateTimeFormatter CREATED_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final GetDynamicDocumentUseCase getDynamicDocumentUseCase;
    private final DocumentRepository documentRepository;
    private final XinYKienDocumentRepository xinYKienDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;
    private final com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;
    // 05/10/2026: "Nhật ký đã xem" có lần xem đầu + lần xem cuối (log_seen_document_detail).
    private final com.td.application.documents.SeenDocumentDetailRecorder seenDocumentDetailRecorder;

    public Result<Map<String, Object>> execute(GetVbdenDetailRequest request) {
        UUID idso = parseUuid(request.getIdso());
        if (idso != null) {
            return executeWithRegisterEntry(idso, parseUuid(request.getIdvbden()), request.getItems());
        }
        boolean lookupRegisterEntry = "true".equalsIgnoreCase(request.getEdit()) || Boolean.TRUE.equals(request.getVt());
        return executePlainDocument(parseUuid(request.getIdvbden()), request.getItems(), lookupRegisterEntry);
    }

    // Tuong duong GetSoVanBanDenUseCase.joinWithParentDocument, nhung cho 1 document (khong phai
    // danh sach) va tra ve "id" = id van ban goc (khong phai id ban ghi so) - cac hanh dong tiep
    // theo tren man chi tiet (vd. "Gui sang VBCDDH", resdata?.id) deu can id van ban goc thuc.
    private Result<Map<String, Object>> executeWithRegisterEntry(UUID idso, UUID idvbden, String items) {
        BusinessDocument entry = documentRepository.findById(idso).orElse(null);
        if (entry == null || !REGISTER_ENTRY_DOCUMENT_TYPE.equalsIgnoreCase(entry.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        Map<String, Object> entryFields = dynamicDocumentFieldsService.parseDynamicFields(entry.getDynamicFieldsJson());

        UUID parentId = idvbden != null ? idvbden : parseUuid(asString(entryFields.get(PARENT_ID_FIELD)));
        BusinessDocument parent = parentId == null ? null : documentRepository.findById(parentId).orElse(null);
        if (parent == null || !DOCUMENT_TYPE.equalsIgnoreCase(parent.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        markViewedIfPending(parent);

        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("title", parent.getTitle());
        merged.put("status", parent.getStatus());
        merged.put("created_on", parent.getCreatedOn());
        merged.put("created", formatCreated(parent.getCreatedOn()));
        merged.put("last_modified_on", parent.getLastModifiedOn());
        merged.putAll(dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));
        // Field cua CHINH ban ghi so ghi de len tren (document_number/document_register_id/
        // identifier_creator/received_date - cung ngu nghia GetSoVanBanDenUseCase).
        merged.putAll(entryFields);
        // Bugfix 22/08/2026 (bao loi nguoi dung: "chua thay hien thi trich yeu"): Domino luu "trich
        // yeu" trong field dong "subject"; TiepNhanVbdenUseCase lai luu no vao cot TINH "title" cua
        // entity (VBdenController#buildTiepNhanRequest loai "subject" khoi fields truoc khi ghi
        // dynamic_fields) - van ban MOI TAO qua Java khong co "subject" trong dynamic_fields, chi co
        // "title" -> FE (doc resdata.subject) hien thi rong. putIfAbsent (KHONG de len) de van ban
        // da tung duoc HIEU CHINH qua Luu() (co "subject" rieng trong dynamic_fields, co the da lech
        // khoi "title" goc) giu dung gia tri da luu, chi fallback ve "title" khi chua co bao gio.
        merged.putIfAbsent("subject", parent.getTitle());
        merged.put("id", parent.getId());
        merged.put(IDSO_FIELD, idso);
        // Bo sung 14/09/2026 (xem javadoc XoaVanBanDenUseCase/PhucHoiVanBanDenUseCase): FE
        // (VBDenButtonAction.jsx) can biet van ban da bi xoa mem hay chua de hien nut "Phuc hoi" -
        // Domino goc dung field "form" (hau to "_Deleted"), Java dung dung cot deleted_on/deleted_by
        // that (ISoftDelete) thay vi mo phong lai field "form".
        putDeletionInfo(merged, parent);
        // Bugfix 30/08/2026 (xem javadoc lop): file dinh kem thuoc ve VAN BAN GOC (parent.getId()),
        // KHONG phai id ban ghi so (idso) - dung id nao gan file luc upload (uploadVbdenFile goi
        // ngay sau TiepNhan/Luu, luon dung id van ban goc).
        merged.put("files", collectFiles(parent.getId()));
        // Bugfix 24/08/2026 (nguoi dung phat hien: response tra "document_type": "vbden" du fix
        // write-path da luu dung "Loai van ban" vao dynamic_fields): dong put() nay unconditional,
        // chay SAU khi merge dynamic_fields nen luon de len gia tri nghiep vu thuc bang module
        // discriminator noi bo - doi sang key rieng "databasetype" de khong con dung ten.
        merged.put(DATABASETYPE_FIELD, DOCUMENT_TYPE);

        return Result.success(dynamicDocumentFieldsService.filterItems(merged, items));
    }

    private Result<Map<String, Object>> executePlainDocument(UUID id, String items, boolean lookupRegisterEntry) {
        var legacyRequest = new GetDynamicDocumentRequest();
        legacyRequest.setId(id);
        legacyRequest.setItems(items);
        Result<Map<String, Object>> result = getDynamicDocumentUseCase.execute(legacyRequest);
        if (!result.isSuccess()) {
            // N08 fallback (31/08/2026, xem javadoc XINYKIEN_DOCUMENT_TYPE): "van ban den gia" song o
            // bang RIENG vbden_xinykien_documents - GetDynamicDocumentUseCase (chi biet bang chung
            // "documents") se KHONG BAO GIO tim thay, luon fail truoc khi toi day - thu tiep bang rieng.
            Optional<Map<String, Object>> xinYkienData = tryLoadXinYkienDocument(id, items);
            return xinYkienData.<Result<Map<String, Object>>>map(Result::success).orElse(result);
        }

        Object documentType = result.getData().get(DATABASETYPE_FIELD);
        if (!(documentType instanceof String type) || !DOCUMENT_TYPE.equalsIgnoreCase(type)) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> data = new LinkedHashMap<>(result.getData());
        // Xoa trang truoc - chi dien lai neu tim duoc ban ghi so KHOP dung to chuc nguoi xem (xem
        // javadoc lop: 1 van ban goc co the duoc nhieu to chuc "vao so" doc lap).
        data.put(DOCUMENT_REGISTER_FIELD, "");
        data.put(DOCUMENT_NUMBER_FIELD, "");
        data.put(DOCUMENT_REGISTER_ID_FIELD, "");

        BusinessDocument parent = documentRepository.findById(id).orElse(null);
        if (parent != null) {
            markViewedIfPending(parent);
            // 05/10/2026: "data" đọc TRƯỚC khi ghi "đã xem" -> chép lại 2 field nhật ký mới nhất để người
            // đang xem thấy ngay lần xem này của mình ("Nhật ký đã xem").
            Map<String, Object> viewedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
            for (String logField : List.of(LOG_READ_FIELD, com.td.application.documents.SeenDocumentDetailRecorder.FIELD)) {
                if (viewedFields.containsKey(logField)) {
                    data.put(logField, viewedFields.get(logField));
                }
            }
            // Bugfix 22/08/2026 - xem javadoc chi tiet o executeWithRegisterEntry (cung ly do: van
            // ban tao qua TiepNhanVbdenUseCase khong co "subject" trong dynamic_fields, chi co
            // "title" tren entity).
            data.putIfAbsent("subject", parent.getTitle());
            // Bugfix 07/09/2026 - xem javadoc lop ("Van ban den qua mang ngay undefined").
            data.put("created", formatCreated(parent.getCreatedOn()));
            // Bo sung 14/09/2026 - xem javadoc executeWithRegisterEntry. Trong nhanh nay tren thuc te
            // parent luon isDeleted()==false (GetDynamicDocumentUseCase da chan tu truoc, xem javadoc
            // lop) nhung van dien du field de FE khong phai phan biet 2 nhanh.
            putDeletionInfo(data, parent);
        }

        if (lookupRegisterEntry && id != null) {
            findRegisterEntryForCurrentUser(id).ifPresent(entry -> {
                Map<String, Object> entryFields = dynamicDocumentFieldsService.parseDynamicFields(entry.getDynamicFieldsJson());
                data.put(DOCUMENT_REGISTER_FIELD, entryFields.getOrDefault(DOCUMENT_REGISTER_FIELD, ""));
                data.put(DOCUMENT_NUMBER_FIELD, entryFields.getOrDefault(DOCUMENT_NUMBER_FIELD, ""));
                data.put(DOCUMENT_REGISTER_ID_FIELD, entryFields.getOrDefault(DOCUMENT_REGISTER_ID_FIELD, ""));
                data.put(IDSO_FIELD, entry.getId());
            });
        }

        // Bugfix 30/08/2026 (xem javadoc lop): "id" o day luon la id van ban GOC (khong phai idso -
        // nhanh nay khong di qua executeWithRegisterEntry).
        data.put("files", collectFiles(id));

        return Result.success(data);
    }

    // N08 (31/08/2026) - dung khuon GetDuThaoDetailUseCase (module co bang vat ly rieng, KHONG di qua
    // GetDynamicDocumentUseCase - tu dung repository rieng, tu dung shape response). KHONG goi
    // markViewedIfPending (nhan BusinessDocument, khac kieu XinYKienDocument - "danh dau da xem" cung
    // khong co bang chung ap dung cho xinykien, workflow rieng qua ChoYKienXinYKienUseCase).
    private Optional<Map<String, Object>> tryLoadXinYkienDocument(UUID id, String items) {
        if (id == null) {
            return Optional.empty();
        }
        XinYKienDocument document = xinYKienDocumentRepository.findById(id).orElse(null);
        if (document == null || document.isDeleted()
                || !XINYKIEN_DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Optional.empty();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", document.getId());
        data.put("title", document.getTitle());
        data.put(DATABASETYPE_FIELD, document.getDocumentType());
        data.put("status", document.getStatus());
        data.put("created_on", document.getCreatedOn());
        data.put("created", formatCreated(document.getCreatedOn()));
        data.put("last_modified_on", document.getLastModifiedOn());
        data.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        data.putIfAbsent("subject", document.getTitle());
        data.put("files", collectFiles(document.getId()));
        return Optional.of(dynamicDocumentFieldsService.filterItems(data, items));
    }

    // Bo sung 14/09/2026 (xem javadoc XoaVanBanDenUseCase/PhucHoiVanBanDenUseCase): "isDeleted" +
    // "delete_date"/"delete_people_fullname" - dat ten field theo dung Domino goc (docVBden field
    // delete_date/delete_people_fullname) de VBDenButtonAction.jsx doi it nhat co the, chi doi dieu
    // kien check tu resdata.form.indexOf("_Deleted") sang resdata.isDeleted.
    private void putDeletionInfo(Map<String, Object> target, BusinessDocument document) {
        target.put("isDeleted", document.isDeleted());
        if (!document.isDeleted()) {
            return;
        }
        target.put("delete_date", formatCreated(document.getDeletedOn()));
        target.put("delete_people_fullname", resolveUserFullName(document.getDeletedBy()));
    }

    private String resolveUserFullName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return appUserRepository.findById(userId).map(AppUser::getFullName).orElse(null);
    }

    // Dung khuon GetVbdiDetailUseCase#collectFiles/GetDuThaoDetailUseCase#collectFiles (da lam cho
    // vbdi/du thao, cung ngay 28/08/2026): TOAN BO tep luu theo document_id = id van ban den (endpoint
    // chung /api/v1/documents/{id}/files), 1 query duy nhat - KHONG merge voi "files" da co san trong
    // dynamic_fields (neu co) - GHI DE, dung tien le da co.
    private List<Map<String, Object>> collectFiles(UUID documentId) {
        if (documentId == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> files = new ArrayList<>();
        listDocumentFilesUseCase.execute(documentId).getItems()
            .forEach(fileDto -> files.add(toFileMap(fileDto)));
        return files;
    }

    // Khop dung shape {name,size,type,file_id} ma Files/filesVBden.jsx dang doc (resdata?.files) -
    // cung tien le GetVbdiDetailUseCase#toFileMap/GetDuThaoDetailUseCase#toFileMap.
    private Map<String, Object> toFileMap(FileDto fileDto) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", fileDto.getOriginalFileName());
        file.put("size", fileDto.getFileSize());
        file.put("type", extractExtension(fileDto.getOriginalFileName()));
        file.put("file_id", fileDto.getFileId());
        // Yeu cau nguoi dung 15/09/2026 (is_primary that - xem javadoc FileMetadataEntity#isPrimary).
        file.put("is_primary", Boolean.TRUE.equals(fileDto.getIsPrimary()));
        return file;
    }

    // Cung logic UploadFileUseCaseImpl#extractExtension/GetVbdiDetailUseCase#extractExtension - copy
    // y het cho nhat quan (khac module, khong tai dung truc tiep duoc).
    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    // Tuong duong sovanban_by_idvbden (view_vanbanden_so.dxl:38-65) - khoa (parentid,
    // identifier_creator). identifier_creator so sanh voi identifier PHANG (AppUser.getIdentifier(),
    // = connection.user_token.identifier phia Domino) - KHONG dung identifier suy theo cap.
    private Optional<BusinessDocument> findRegisterEntryForCurrentUser(UUID idvbden) {
        String flatIdentifier = resolveCurrentUserIdentifier().orElse(null);
        if (flatIdentifier == null || flatIdentifier.isBlank()) {
            return Optional.empty();
        }
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of(IDENTIFIER_CREATOR_FIELD))
            .includeFieldValues(Map.of(PARENT_ID_FIELD, List.of(idvbden.toString())))
            // 30/09/2026: tra cuu chi tiet (khong phai danh sach) - van ban da bi thu hoi (mo tu thung
            // rac "Bi thu hoi") van phai lay duoc ban ghi so - xem VbdenOrgViewFilter#includeRevoked.
            .includeRevoked(true)
            .build();
        filter.setOrgValue(flatIdentifier);
        Page<BusinessDocument> page = documentRepository.findByVbdenOrgView(
            REGISTER_ENTRY_DOCUMENT_TYPE, filter, PageRequest.of(0, 1));
        return page.getContent().stream().findFirst();
    }

    private Optional<String> resolveCurrentUserIdentifier() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getIdentifier);
    }

    // Tuong duong doan "markDocumentViewedFlat" + chuyen user_to_read -> user_to_readed trong
    // XemChiTiet.java thuc (chi phan anh huong toi view da port - user_to_read/user_to_readed, xem
    // ViewQueryService.VIEW_VBDEN_XEMDEBIET/VIEW_VBDEN_DAXEMDEBIET). Best-effort: loi khong duoc lam
    // hong request doc (chi log, giong Domino goc nuot moi exception o day).
    //
    // Bo sung 18/09/2026 (nguoi dung bao "Nhat ky da xem dang trong" - LogReadCollapse.jsx doc
    // resdata.log_read_obj nhung chua co use case nao GHI field nay, xem javadoc lop muc "KHONG port
    // log_read_obj... de danh neu sau nay can" - gio la luc can): GOP chung 1 lan doc/luu voi nhanh
    // user_to_read/user_to_readed o tren (giam 1 lan ghi DB khong can thiet) - 2 nhanh DOC LAP nhau
    // (mot van ban KHONG nam trong user_to_read van phai duoc ghi log_read_obj, khac voi nhanh kia -
    // do la ly do KHONG the giu nguyen early-return cu).
    private void markViewedIfPending(BusinessDocument document) {
        try {
            String username = currentUserProvider.currentUsername().orElse(null);
            if (username == null || username.isBlank()) {
                return;
            }
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
            boolean changed = false;

            List<String> userToRead = toStringList(fields.get(USER_TO_READ_FIELD));
            if (userToRead.remove(username)) {
                List<String> userToReaded = toStringList(fields.get(USER_TO_READED_FIELD));
                if (!userToReaded.contains(username)) {
                    userToReaded.add(username);
                }
                fields.put(USER_TO_READ_FIELD, userToRead);
                fields.put(USER_TO_READED_FIELD, userToReaded);
                changed = true;
            }

            if (appendLogReadEntry(fields, username)) {
                changed = true;
            }
            // 05/10/2026: log_read_obj chỉ giữ LẦN XEM ĐẦU (danh sách dùng để in đậm "chưa đọc"); thêm
            // log_seen_document_detail cập nhật lần xem cuối MỖI lần mở — cùng định dạng dự thảo/văn bản đi.
            if (seenDocumentDetailRecorder.record(fields, username)) {
                changed = true;
            }

            if (!changed) {
                return;
            }
            authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
            document.replaceDynamicFields(toJson(fields));
            documentRepository.save(document);
        } catch (Exception ex) {
            log.debug("Bo qua danh dau 'da xem' cho van ban {}: {}", document.getId(), ex.getMessage());
        }
    }

    // Tuong duong XemChiTiet.java#markDocumentViewedFlat that: dedupe THEO USERNAME (chi ghi LAN XEM
    // DAU TIEN cua moi nguoi, cac lan xem lai sau KHONG ghi them - dung Domino goc, kiem tra substring
    // "username|"). Luu duoi dang 1 CHUOI TEXT PHANG (khong phai List) noi bang ";", dung format
    // "username|dd/MM/yyyy HH:mm:ss" - LogReadCollapse.jsx (FE) tach lai bang split(/[,;]+/) roi "|".
    private boolean appendLogReadEntry(Map<String, Object> fields, String username) {
        String currentLog = asString(fields.get(LOG_READ_FIELD));
        if (currentLog == null) {
            currentLog = "";
        }
        if (currentLog.contains(username + "|")) {
            return false;
        }
        String newEntry = username + "|" + formatCreated(LocalDateTime.now());
        fields.put(LOG_READ_FIELD, currentLog.isEmpty() ? newEntry : currentLog + ";" + newEntry);
        return true;
    }

    // Bugfix 22/08/2026 (cung dang D.14/R4/ChuyenXuLyVbdenUseCase): "user_to_read" co the duoc ghi
    // dang SCALAR TEXT (";"-joined) qua TiepNhan/Luu (AddDynamicDocumentUseCase/FieldTypeInferrer,
    // field khong khop hau to nao nen luu scalar) - truoc day chi hieu List nen "danh dau da xem"
    // se im lang khong hoat dong (khong tim thay username can loai) cho van ban tao/hieu chinh qua
    // luong do. Doc khoan dung ca 2 dang, giu nguyen cach ghi lai (List) nhu cu.
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

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // Bugfix 07/09/2026 (xem javadoc lop): tuong duong XemChiTiet.java that (SimpleDateFormat
    // "dd/MM/yyyy HH:mm:ss" tren doc.getCreated()) - null-safe (van ban chua tung co createdOn,
    // truong hop KHONG nen xay ra voi @PrePersist da co san, nhung van phong ho de tranh NPE).
    private String formatCreated(LocalDateTime createdOn) {
        return createdOn == null ? "" : createdOn.format(CREATED_DATE_FORMAT);
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
