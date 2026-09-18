package com.td.application.documents;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 16/09/2026 (yêu cầu người dùng: "modal Cập nhật kết quả xử lý văn bản -> Gắn văn bản trả lời" trống
 * dữ liệu, sau đó "làm đầy đủ luôn" cơ chế `author` cho MỌI module) — port
 * {@code services.duthao.AddRolesToDocument#addRolesToDocument} + phần gọi nó trong
 * {@code TDDocument.java#CreateOrUpdateDocument()} (base class DÙNG CHUNG mọi form qua {@code qlvb/
 * add} — vbden/vbdi/duthao/hosocv/hosoluutru/phieutrinhtd/vbden_xinykien).
 *
 * <p><b>Bản chất field {@code author}</b>: Domino dùng nó vừa làm ACL (Reader/Author — AI được sửa
 * document, quyết định 27/08/2026 "không port, RBAC/JWT thay thế" ĐÚNG cho mục đích này), vừa làm
 * KHOÁ của 1 số view nghiệp vụ liệt kê văn bản (vd {@code vanbandi_by_author}, xem javadoc
 * {@code ViewQueryService#VIEW_VBDI_BY_AUTHOR}) — port này CHỈ phục vụ mục đích THỨ HAI (dữ liệu để
 * tìm kiếm/liệt kê), lưu như 1 field động thường (";"-nối), KHÔNG dựng lại cơ chế ACL cấp document.</p>
 *
 * <p><b>Xác nhận thêm của người dùng (16/09/2026)</b>: "author" phải gom mọi tài khoản/mã định danh
 * LIÊN QUAN TRỰC TIẾP tới văn bản — người phối hợp, người xem để biết, người chủ trì xử lý, cơ quan/
 * đơn vị — khớp ĐÚNG những gì {@code TDKeySettings.SET_AUTHOR_FIELDS} + cơ chế {@code user_fields}
 * động dưới đây đã liệt kê (không suy đoán thêm field nào khác ngoài bằng chứng đọc được từ Domino).</p>
 *
 * <p><b>Thuật toán {@code CreateOrUpdateDocument()} thật</b> — {@code author} CỘNG DỒN (không bao giờ
 * xoá bớt phần tử cũ, kể cả khi field nguồn sau đó bị xoá) từ 3 nguồn mỗi lần LƯU (tạo mới hoặc hiệu
 * chỉnh):</p>
 * <ol>
 *   <li><b>{@code user_fields} động</b>: giá trị của MỌI field tên bắt đầu {@code "user_"} HOẶC kết
 *   thúc {@code "hide"} MÀ REQUEST HIỆN TẠI thực sự gửi lên — CHỈ áp dụng khi {@code form} thuộc
 *   {@code draft_document}/{@code DocumentIn}/{@code DocumentOut}/{@code document_profiles}/
 *   {@code submission_dossier} (dòng ~605-618 Domino). Ở Java, "field request hiện tại gửi lên" =
 *   {@code request.getFields()} lúc TẠO (chưa có khái niệm "chỉ field vừa đổi" lúc SỬA vì mỗi module
 *   có 1 use case Luu riêng, không đi qua lớp này — xem giới hạn CHƯA PORT bên dưới).</li>
 *   <li><b>Identifier của NGƯỜI THỰC HIỆN lưu</b> ({@code connection.user_token.identifiers}, chuỗi
 *   tổ tiên LÁ→GỐC) — áp dụng cho MỌI form/databasetype TRỪ denylist ở mục 3.</li>
 *   <li><b>{@link #SET_AUTHOR_FIELDS}</b> (hằng số CỐ ĐỊNH {@code TDKeySettings.SET_AUTHOR_FIELDS},
 *   đọc GIÁ TRỊ HIỆN TẠI trên document) — áp dụng cho MỌI form/databasetype TRỪ:
 *   {@code draft_process}/{@code log_chuyencddh}/{@code document_coordinated_opinion} (form),
 *   {@code lichct} kèm form {@code calendar}/{@code cauhinh}, {@code vbcddh}/{@code chiasetep}
 *   (databasetype), action {@code addlogseendocument} — TRONG ĐÓ {@code draft_process}/
 *   {@code vbden_res}/{@code vbcddh}/{@code chiasetep} đã tự động loại trừ vì các document đó ở Java
 *   đi qua use case Add RIÊNG (bảng vật lý riêng, KHÔNG gọi lớp này) — chỉ còn {@code vbcddh} cần loại
 *   trừ TƯỜNG MINH (xem {@link #EXCLUDED_DOCUMENT_TYPES}) vì {@code AddVbcddhDynamicDocumentUseCase}
 *   VẪN dùng chung {@link AddDynamicDocumentRequest} với các module khác.</li>
 * </ol>
 *
 * <p><b>Cập nhật 16/09/2026 (2) — yêu cầu người dùng "đã bổ sung vào các endpoint liên quan tới
 * nghiệp vụ chưa? như chuyển để biết, chuyển xử lý... tất cả modules"</b>: đã bổ sung {@link
 * #applyOnUpdate} — CÙNG thuật toán 3 nguồn, áp dụng khi HIỆU CHỈNH document ĐÃ TỒN TẠI (mỗi module
 * có 1 use case {@code Luu*UseCase}/{@code ChuyenXuLy*UseCase} riêng, không đi qua {@code Add*}) —
 * khớp đúng Domino thật (author cộng dồn ở CẢ tạo mới lẫn MỌI lần sửa sau, không chỉ lúc tạo). Khác
 * {@link #applyOnCreate} 1 điểm kỹ thuật: field value ở use case hiệu chỉnh đã qua
 * {@code parseDynamicFields} (kiểu {@code Object}, có thể là {@code List<String>} thay vì
 * {@code String} — field {@code _tlist}/{@code _aut}/{@code _rdr}, xem
 * {@code DynamicDocumentFieldsService}/{@code FieldTypeInferrer}) — {@link #collectSplitValues} đã
 * tổng quát hoá để nhận cả 2 dạng.</p>
 *
 * <p><b>Bugfix 16/09/2026 (3) — bug thật người dùng phát hiện: "author tạm đã thấy đủ rồi, nhưng chưa
 * có người tạo (create by)" khi soạn mới văn bản đi</b>: đọc lại {@code TDDocument.java} dòng 551-571
 * xác nhận Domino KHÔNG bao giờ tin field {@code user_creator} do client gửi — với document MỚI
 * ({@code isNewNote()}), LUÔN ghi đè bằng {@code connection.user_token.username} (bất kể client gửi
 * gì); với document ĐÃ TỒN TẠI, chỉ backfill khi field ĐANG RỖNG trên document. Trước bugfix này,
 * {@link #applyOnCreate} chỉ đọc thụ động {@code fields.get("user_creator")} — với FE thật
 * ({@code ChiTietModal.jsx} nhánh "Thêm mới" chuẩn, KHÔNG phải nhánh "Sao chép") KHÔNG hề gửi field
 * này (chỉ set {@code creator} — tên hiển thị, khác {@code user_creator} — username) nên {@code author}
 * mất hẳn định danh người tạo. Đã thêm bước ghi đè/backfill {@code user_creator} qua
 * {@link CurrentUserProvider} NGAY ĐẦU {@link #applyOnCreate}/{@link #applyOnUpdate} (trước khi gom
 * giá trị) — khớp ĐÚNG tiền lệ SERVER-AUTHORITATIVE đã có riêng ở {@code TaoDuThaoUseCase}/
 * {@code LuuDuThaoUseCase} (duthao), nay tổng quát hoá cho MỌI module dùng chung service này. Tác
 * dụng phụ CÓ LỢI: {@code user_creator} — 1 field nghiệp vụ độc lập với {@code author} — cũng được
 * đảm bảo đúng ở MỌI module từ đây (trước đó chỉ duthao có backfill riêng).</p>
 */
@Service
@RequiredArgsConstructor
public class AuthorAclFieldsService {

    public static final String AUTHOR_FIELD = "author";
    private static final String USER_CREATOR_FIELD = "user_creator";
    private static final String USER_PREFIX = "user_";
    private static final String HIDE_SUFFIX = "hide";

    // Domino TDKeySettings.SET_AUTHOR_FIELDS - hang so CO DINH, TRON LAN username va identifier.
    private static final List<String> SET_AUTHOR_FIELDS = List.of(
        "coordinator_hide_rtext", "identifier", "user_creator", "unit_publish_identifier",
        "unit_creator_identifier", "user_draft", "submission_dossier_identity", "unit_in_charge_identifier");

    // databasetype KHONG duoc ap dung (Domino: "vbcddh"/"chiasetep" trong denylist cua
    // CreateOrUpdateDocument - vbcddh o Java van dung chung AddDynamicDocumentRequest nen can loai
    // truong minh; chiasetep khong co Add*DynamicDocumentUseCase nao goi lop nay nen khong can liet ke).
    private static final Set<String> EXCLUDED_DOCUMENT_TYPES = Set.of("vbcddh");

    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    /**
     * Gọi TRƯỚC khi build {@code dynamic_fields} lúc TẠO MỚI document — ghi đè
     * {@code fields.get(AUTHOR_FIELD)} bằng union đã dedup của mọi nguồn (xem javadoc lớp). No-op nếu
     * {@code fields} null hoặc {@code documentType} nằm trong {@link #EXCLUDED_DOCUMENT_TYPES}.
     */
    public void applyOnCreate(String documentType, Map<String, String> fields) {
        if (fields == null || EXCLUDED_DOCUMENT_TYPES.contains(normalize(documentType))) {
            return;
        }
        // Domino TDDocument.java#CreateOrUpdateDocument() dong 551-559: docDocument.isNewNote() ->
        // LUON ghi de user_creator bang connection.user_token.username, KHONG dieu kien theo client
        // co gui field nay hay khong (va bat ke gia tri client gui la gi - da co tien le
        // SERVER-AUTHORITATIVE giong het o TaoDuThaoUseCase, ly do: chan client mao danh nguoi tao).
        // Neu khong resolve duoc current user (vd tao tu he thong) thi giu nguyen gia tri client gui.
        currentUserProvider.currentUsername().ifPresent(username -> fields.put(USER_CREATOR_FIELD, username));
        apply(fields, fields.keySet());
    }

    /**
     * Gọi TRƯỚC khi {@code replaceDynamicFields(toJson(fields))}/{@code save(...)} lúc HIỆU CHỈNH
     * document ĐÃ TỒN TẠI — cùng thuật toán {@link #applyOnCreate}, ghi đè {@code fields.get(
     * AUTHOR_FIELD)} bằng union đã dedup (không mất giá trị {@code author} cũ, vì nó luôn được gom
     * lại là 1 trong các nguồn — xem javadoc lớp mục "CỘNG DỒN").
     *
     * @param fields TOÀN BỘ field hiện tại của document (đã merge thay đổi mới vào, TRƯỚC khi
     *   serialize) — dùng để đọc giá trị HIỆN TẠI của {@link #SET_AUTHOR_FIELDS} (khớp Domino: đọc
     *   giá trị sau khi field khác đã ghi) và {@code author} cũ (nếu có).
     * @param touchedFieldNames tên các field mà THAO TÁC HIỆN TẠI thực sự ghi/gửi lên (vd
     *   {@code request.getFields().keySet()} ở use case "Lưu" chung, hoặc tập field cụ thể use case
     *   "Chuyển xử lý" vừa mutate) — dùng cho nguồn {@code user_fields} động (mục 1, javadoc lớp).
     *   {@code null} nếu use case không phân biệt được (bỏ qua nguồn 1, vẫn áp dụng nguồn 2/3).
     */
    public void applyOnUpdate(String documentType, Map<String, Object> fields, Set<String> touchedFieldNames) {
        if (fields == null || EXCLUDED_DOCUMENT_TYPES.contains(normalize(documentType))) {
            return;
        }
        backfillUserCreatorIfBlank(fields);
        apply(fields, touchedFieldNames);
    }

    // Domino TDDocument.java dong 560-571 (nhanh "else", document DA TON TAI): CHI backfill
    // user_creator bang connection.user_token.username khi field DANG RONG tren document - KHONG
    // ghi de gia tri da co (khac applyOnCreate - nguoi sua sau KHONG duoc doi danh tinh nguoi tao goc).
    // Xu ly ho MOI use case Luu*/ChuyenXuLy* thay vi bat tung noi tu lam rieng (khop tien le
    // LuuDuThaoUseCase#backfillUserCreatorIfBlank, tong quat hoa ra toan bo module).
    private void backfillUserCreatorIfBlank(Map<String, Object> fields) {
        Object existing = fields.get(USER_CREATOR_FIELD);
        boolean blank = existing == null || String.valueOf(existing).isBlank();
        if (blank) {
            currentUserProvider.currentUsername().ifPresent(username -> fields.put(USER_CREATOR_FIELD, username));
        }
    }

    private void apply(Map<String, ?> fields, Set<String> touchedFieldNames) {
        Set<String> values = new LinkedHashSet<>();
        collectSplitValues(values, fields.get(AUTHOR_FIELD));
        collectSplitValues(values, fields.get(USER_CREATOR_FIELD));

        if (touchedFieldNames != null) {
            for (String fieldName : touchedFieldNames) {
                if (fieldName != null && (fieldName.startsWith(USER_PREFIX) || fieldName.endsWith(HIDE_SUFFIX))) {
                    collectSplitValues(values, fields.get(fieldName));
                }
            }
        }

        for (String fieldName : SET_AUTHOR_FIELDS) {
            collectSplitValues(values, fields.get(fieldName));
        }

        collectSplitValues(values, resolveCurrentUserIdentifiers());

        if (!values.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mutableFields = (Map<String, Object>) fields;
            mutableFields.put(AUTHOR_FIELD, String.join(";", values));
        }
    }

    // Domino connection.user_token.identifiers - chuoi to tien LA->GOC cua nguoi dang thao tac.
    private String resolveCurrentUserIdentifiers() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(getUserOrganizationProfileUseCase::buildProfileForUser)
            .map(profile -> profile.getIdentifiers())
            .orElse(null);
    }

    // Tong quat hoa (16/09/2026 (2)): field da qua parseDynamicFields co the la List<String> (hau to
    // _tlist/_aut/_rdr) thay vi String scalar - nhan Object, xu ly ca 2 dang, cung quy uoc
    // "toStringList" da dung khap noi trong codebase (vd TheoDoiVanBanDenUseCase).
    private void collectSplitValues(Set<String> target, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        if (rawValue instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String trimmed = item.toString().trim();
                    if (!trimmed.isEmpty()) {
                        target.add(trimmed);
                    }
                }
            }
            return;
        }
        for (String part : rawValue.toString().split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
