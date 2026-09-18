package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bổ sung 07/09/2026 (2, yêu cầu người dùng: "STATUS_02 'Từ chối tiếp nhận' và khi vào sổ có gọi
 * qlvb/vbdi?...action=ReplyDocumentOut&id=... — kiểm tra và port luôn cả 2") — tương thích
 * {@code ReplyDocumentOut.java} thật (package {@code vn.com.tandan.xsp.services.vbdi}, gọi qua
 * {@code qlvb/vbdi?databasetype=vbden&databasethongtin=qlvb&action=ReplyDocumentOut}), NHÁNH
 * {@code type != "vaoso"} — 2 nút "Từ chối"/"Trả lại" ở {@code ItemsListTuChoi.jsx}/{@code
 * ItemsList.jsx} (FE gọi CHUNG 1 hàm {@code tu_choi_tra_lai_nhieu_van_ban(type)} → helper
 * {@code ReplyDocumentOut(id, type)} ở {@code vbdi/Components/ControllModal.jsx}).
 *
 * <p><b>Đối chiếu Domino thật (không suy đoán)</b>: {@code ReplyDocumentOut.java} nhận
 * {@code databasetype} làm THAM SỐ — khi FE gọi với {@code databasetype=vbden}, {@code docDocument}
 * chính là văn bản ĐẾN (KHÔNG PHẢI văn bản đi dù nằm ở module/package {@code vbdi} — lúc đầu hiểu
 * nhầm đây là 1 flow "văn bản đi" hoàn toàn tách biệt, xem javadoc {@code PublishVbdiReplyStatusUseCase}
 * addendum 07/09/2026 trước đợt này — ĐÃ SỬA LẠI hiểu nhầm đó). Điểm gọi {@code type=="vaoso"} (xử lý
 * văn bản "thay thế"/"thu hồi" — tìm bản ghi cũ qua view {@code vbden_by_id_documentout_davaoso}/
 * {@code vbden_by_id_documentout}, copy field, publish STATUS_03) là 1 TÍNH NĂNG KHÁC, LỚN HƠN nhiều
 * ("thay thế/thu hồi văn bản đến") — CHƯA port ở đây (ngoài phạm vi "Từ chối tiếp nhận" được yêu
 * cầu; STATUS_03 cho "vào sổ" THÔNG THƯỜNG đã có sẵn qua {@link LuuVanBanDenUseCase}, publish đúng
 * lúc "vào sổ" thành công với văn bản đến qua giao liên thông — hiệu quả tương đương nhánh
 * {@code type=="vaoso"} của {@code ReplyDocumentOut} cho trường hợp KHÔNG phải thay thế/thu hồi, chỉ
 * khác nơi trigger).
 *
 * <p><b>Bugfix 07/09/2026 (3) — người dùng mô tả rõ lại 2 chức năng (tránh nhầm lẫn do tên API/nhãn
 * trạng thái đặt chưa chuẩn ở lần port đầu)</b>:</p>
 * <ul>
 *   <li><b>"Không vào sổ"</b> (mọi {@code type} khác {@code "tralai"}, FE hiện gửi {@code "no"}):
 *   văn thư chọn không vào sổ, loại bỏ văn bản khỏi hàng đợi xử lý CỦA ĐƠN VỊ MÌNH — {@code
 *   is_document_refuse=1}, {@code nghiepvu="tuchoi"}, loại {@code identifier} khỏi {@code
 *   unit_process} (đúng {@code TDDocumentUlties.removeToFieldListStringHideVBden} thật — CHỈ field
 *   này), thêm {@code identifier} vào {@code unit_discard} + {@code actingUsername} vào
 *   {@code user_discard} (gộp {@code CapNhatVanBanDen.java#khongvaoso()} — Domino gọi 2 endpoint
 *   RIÊNG cho cùng 1 nút, ở đây gộp 1 giao dịch atomic). Publish STATUS_02 "Từ chối tiếp nhận" qua
 *   {@link com.td.application.vbdi.PublishVbdiReplyStatusUseCase} — ĐÃ ĐÚNG từ lần port đầu.</li>
 *   <li><b>"Từ chối/Trả lại"</b> ({@code type == "tralai"}): CÙNG bước loại khỏi
 *   {@code unit_process}/{@code unit_discard}/{@code user_discard} như trên, đánh dấu
 *   {@code is_document_reply=1}/{@code nghiepvu="tralai"} — KHÁC BIỆT DUY NHẤT với "Không vào sổ":
 *   PHẢI publish phản hồi trạng thái cho đơn vị gửi GIỐNG HỆT 01/03 (yêu cầu người dùng, đối chiếu
 *   {@code StatusProcess.java} thật) — lần port đầu SAI khi bỏ qua bước này chỉ vì
 *   {@code status_code="tralai"} không thuộc 6 mã chuẩn {@code StatusCode.java}: Domino thật VẪN
 *   publish qua ĐÚNG cơ chế {@code response_status}/{@code UpdateLogDocument} như mọi mã khác, chỉ
 *   khác ở literal string "tralai" thay vì mã số — xem {@code VbdiReplyTaskHandler#buildContent}
 *   (td-woker) để biết công thức nội dung cố định
 *   ("Đơn vị {tên đơn vị} đã trả lại văn bản", bỏ qua {@code opinion} người dùng nhập, đúng Domino
 *   literal) và bước ghi ngược {@code is_document_reply=1} lên CHÍNH văn bản {@code vbdi} gốc phía
 *   đơn vị gửi (worker chạy trong tenant đơn vị gửi nên làm được trực tiếp, JDBC thuần).</li>
 * </ul>
 *
 * <p><b>09/09/2026 (2, port view {@code vwDocumentIn_Internet_Refuse}, route FE
 * {@code /vbden/xuly/tuchoidenquamang}) — SỬA LẠI bản "đơn giản hoá" (09/09/2026, 1) sau khi người
 * dùng phát hiện thiếu case, đối chiếu lại {@code ReplyDocumentOut.java:324-371} thật</b>: Domino
 * gốc set {@code is_document_refuse="1"} + {@code status="5"} ở ĐÂY, BẤT KỂ {@code type} là
 * {@code "tralai"} hay không (khác field {@code is_document_reply}/{@code nghiepvu} set theo
 * {@code type} ở nhánh trên — 2 field ĐỘC LẬP, có thể cùng "1" một lúc), trong CHÍNH XÁC 2 trường
 * hợp:
 * <ul>
 *   <li>{@code internal != "1"} (văn bản KHÔNG gửi nội bộ trong Bộ, vd denquamang/lienthong/
 *   dendientu) — LUÔN set, không điều kiện gì thêm.</li>
 *   <li>{@code internal == "1"} (văn bản nội bộ Bộ, vd quamangnoibo — nhiều phòng/ban CÙNG 1 dòng
 *   {@code documents} có thể nhận) — CHỈ set khi đơn vị đang thao tác là đơn vị CUỐI CÙNG còn lại
 *   ({@code unit_process} VÀ {@code unit_process_registered} đều rỗng SAU KHI loại
 *   {@code identifier} khỏi {@code unit_process}) — khái niệm này áp dụng ĐƯỢC trong kiến trúc Java
 *   hiện tại vì {@code unit_process} vẫn multi-value TRONG CÙNG 1 dòng document (khác giả định sai
 *   ở bản 09/09/2026 (1): tưởng nhầm mỗi tenant nhận 1 dòng riêng như văn bản gửi liên thông ngoài
 *   Bộ, thực ra chỉ đúng cho {@code internal != "1"}).</li>
 * </ul>
 * Không port {@code delete_date}/{@code delete_people}/{@code delete_people_fullname}/
 * {@code Form="DocumentIn_Deleted"} (Domino, cùng khối) — không field nào trong số này được bất kỳ
 * view/use case Java nào đọc lại (đã grep xác nhận), và {@code Form=DocumentIn_Deleted} KHÔNG map
 * sang {@code deleted_on} thật (soft-delete) vì {@code findByVbdenOrgView} hard-code
 * {@code deleted_on IS NULL} — set thật sẽ khiến văn bản biến mất khỏi MỌI view vbden, kể cả
 * {@code vwDocumentIn_Internet_Refuse} vừa port; {@code status="5"} một mình đã đủ cho view đó hoạt
 * động đúng.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TuChoiTiepNhanVbdenUseCase {

    private static final String DOCUMENT_TYPE = "vbden";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";
    private static final String TYPE_TRA_LAI = "tralai";

    private static final String IS_DOCUMENT_REFUSE_FIELD = "is_document_refuse";
    private static final String IS_DOCUMENT_REPLY_FIELD = "is_document_reply";
    private static final String NGHIEPVU_FIELD = "nghiepvu";
    private static final String UNIT_PROCESS_FIELD = "unit_process";
    private static final String UNIT_PROCESS_REGISTERED_FIELD = "unit_process_registered";
    private static final String INTERNAL_FIELD = "internal";
    private static final String UNIT_DISCARD_FIELD = "unit_discard";
    private static final String USER_DISCARD_FIELD = "user_discard";
    // Bugfix 07/09/2026 (5, bao loi nguoi dung: van ban khong mat khoi danh sach "Cho xu ly"
    // (quamangnoibo/lienthong/dendientu/denquamang) sau khi Tu choi/Tra lai) - doi chieu lai
    // ReplyDocumentOut.java that: CUOI setReplyDocumentOut() (SAU switch-case tuchoi/tralai, ap
    // dung CHO CA 2 nhanh khong dieu kien) co doan:
    //   docDocument.replaceItemValue("reply","1");
    //   docDocument.replaceItemValue("timenow-Replydocument", ...);
    //   docDocument.save(true);
    // - "reply" la field RIENG, KHAC han is_document_refuse/is_document_reply da port truoc do -
    // chinh field nay duoc 4 view "Cho xu ly" doc qua dieu kien loai tru "reply != 1"
    // (ViewQueryService#quamangnoiboFilter/lienthongFilter/dendientuFilter/denquamangFilter) - thieu
    // field nay khien van ban KHONG BAO GIO bien mat khoi cac view do du da Tu choi/Tra lai thanh cong.
    private static final String REPLY_FIELD = "reply";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong" - unit_process/unit_discard KHONG
    // qua (khong bat dau "user_"/khong ket thuc "hide", Domino that CHI cong unit qua SET_AUTHOR_FIELDS
    // co dinh, khong bao gom 2 field nay).
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(USER_DISCARD_FIELD);

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final com.td.application.vbdi.PublishVbdiReplyStatusUseCase publishVbdiReplyStatusUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(TuChoiTiepNhanVbdenRequest request) {
        if (request.getId() == null) {
            return Result.failure("Thiếu id văn bản đến");
        }
        var documentOptional = documentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (actingUsername == null || actingUsername.isBlank()) {
            return Result.failure("Không xác định được người dùng hiện tại");
        }
        String identifier = resolveIdentifierByLevelUseCase.resolveForCurrentUser().orElse(null);

        boolean isTraLai = TYPE_TRA_LAI.equalsIgnoreCase(request.getType());

        var before = buildSnapshot(document);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        fields.put(isTraLai ? IS_DOCUMENT_REPLY_FIELD : IS_DOCUMENT_REFUSE_FIELD, "1");
        fields.put(NGHIEPVU_FIELD, isTraLai ? TYPE_TRA_LAI : "tuchoi");
        // Bugfix 07/09/2026 (5, xem javadoc REPLY_FIELD): ghi VO DIEU KIEN ca 2 nhanh, dung Domino that.
        fields.put(REPLY_FIELD, "1");

        if (identifier != null && !identifier.isBlank()) {
            List<String> unitProcess = toStringList(fields.get(UNIT_PROCESS_FIELD));
            if (unitProcess.remove(identifier)) {
                fields.put(UNIT_PROCESS_FIELD, unitProcess);
            }
            addOne(fields, UNIT_DISCARD_FIELD, identifier);
        }
        addOne(fields, USER_DISCARD_FIELD, actingUsername);

        // 09/09/2026 (2, xem javadoc lớp): Domino LUÔN set is_document_refuse+status="5" ở đây bất kể
        // type, khi internal!="1", hoặc khi internal=="1" VÀ đây là đơn vị cuối cùng còn lại.
        boolean isInternal = "1".equals(asString(fields.get(INTERNAL_FIELD)));
        boolean shouldMarkRefused;
        if (isInternal) {
            List<String> remainingUnitProcess = toStringList(fields.get(UNIT_PROCESS_FIELD));
            List<String> unitProcessRegistered = toStringList(fields.get(UNIT_PROCESS_REGISTERED_FIELD));
            shouldMarkRefused = remainingUnitProcess.isEmpty() && unitProcessRegistered.isEmpty();
        } else {
            shouldMarkRefused = true;
        }
        if (shouldMarkRefused) {
            fields.put(IS_DOCUMENT_REFUSE_FIELD, "1");
            document.update(null, null, VbdenStatusService.STATUS_TU_CHOI_TIEP_NHAN, null, null, null, null);
        }
        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        document.replaceDynamicFields(toJson(fields));
        var saved = documentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        publishReplyStatusAfterTuChoi(saved, actingUsername, request.getOpinion(), isTraLai);

        return Result.success(saved.getId());
    }

    // Bugfix 07/09/2026 (3, nguoi dung sua lai: "Tu choi/Tra lai" phai gui phan hoi trang thai cho
    // don vi gui GIONG NHU 01/03 - xem StatusProcess.java that: bussiness_doc_type.equals("tralai")
    // CUNG di qua CHINH co che UpdateLogDocument/response_status nhu moi ma khac, chi khac o cho
    // KHONG dung StatusCode enum chuan (01-06) - truoc day quyet dinh SAI la bo qua han broker cho
    // "tralai", dung ra phai VAN publish, chi la voi status_code phi chuan "tralai" (Domino dung
    // nguyen van chuoi nay, khong phai so). Ca 2 nhanh gio publish nhu nhau, chi khac statusCode/Label.
    private void publishReplyStatusAfterTuChoi(
            BusinessDocument document, String actingUsername, String opinion, boolean isTraLai) {
        var replyRequest = new com.td.application.vbdi.PublishVbdiReplyStatusRequest();
        replyRequest.setDynamicFields(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        replyRequest.setStatusCode(isTraLai ? "tralai" : "02");
        replyRequest.setStatusLabel(isTraLai ? "Trả lại" : "Từ chối tiếp nhận");
        // StatusProcess.java that: nhanh "tralai" LUON dung noi dung co dinh "Đơn vị X đã trả lại văn
        // bản" (bo qua opinion nguoi dung nhap) - build o phia consumer (VbdiReplyTaskHandler, da co
        // recipientName tra tu dong goc), KHONG gui statusDetails o day de tranh nham lan bi ghi de.
        if (!isTraLai && opinion != null && !opinion.isBlank()) {
            replyRequest.setStatusDetails(opinion);
        }
        replyRequest.setActorDisplayName(actingUsername);
        publishVbdiReplyStatusUseCase.execute(replyRequest);
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

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
