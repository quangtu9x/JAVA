package com.td.application.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbden.VbdenResDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modal "Trả lại văn bản" ({@code ModalTraLaiVanBan.jsx}) — yêu cầu người dùng 14/09/2026 ("ở chi
 * tiết văn bản đến, chức năng Trả lại ... đang gọi qlvb/vbden/GetNguoiTraLai?databasetype=vbden ->
 * port sang java"). Tương đương {@code GetNguoiTraLai.java} thật (KHÔNG phải
 * {@code GetNguoiTraLai_1.java} — biến thể song song đọc {@code diagram_obj}/{@code ClassTreeNodeSoDo}
 * qua database {@code vbden}/{@code vbden_res}; đã xác nhận qua {@code ServletFactory#getServletMatch}
 * (naming-convention routing theo path, không qua bảng map tường minh) rằng path
 * {@code GetNguoiTraLai} — đúng path FE gọi, không hậu tố — CHỈ khớp class gốc; {@code _1} không được
 * bất kỳ path/FE nào gọi tới, là bản song song chưa/không còn dùng).
 *
 * <p><b>Thuật toán (đúng {@code GetNguoiTraLai.java})</b>: quét MỌI bản ghi {@code vbden_res} có
 * {@code parentid} = văn bản này (view Domino {@code QTXL_by_parentid}, tương đương
 * {@link VbdenResDocumentRepository#findByParentId}), chỉ giữ bản ghi {@code action="chuyenxuly"}
 * ({@code "vanthuchuyenxuly"} giữ lại trong điều kiện cho ĐÚNG Domino gốc, dù grep toàn bộ 2 repo xác
 * nhận KHÔNG action nào — Java lẫn Domino — từng ghi giá trị này, nên nhánh đó luôn chết trong thực
 * tế, giống tiền lệ {@code "vanthuchuyenvanban"} ở {@link ThuHoiVanBanUseCase#REVOCABLE_ACTIONS}). Với
 * mỗi bản ghi khớp, đọc snapshot {@code user_process_obj} (fallback {@code user_assigner_obj} khi
 * rỗng/{@code "[]"}, đúng thứ tự ưu tiên Domino gốc) — mảng {@code {username|user_receiver, name,
 * identifier}}; nếu người dùng ĐANG ĐĂNG NHẬP xuất hiện trong đó (khớp {@code username} HOẶC
 * {@code user_receiver}, không phân biệt hoa/thường) thì thêm NGƯỜI ĐÃ THỰC HIỆN bản ghi đó
 * ({@code nguoi_thuc_hien}/{@code creator} — tương ứng {@code user_creator}/{@code creator} phía
 * Domino, xem {@code ChuyenXuLyVbdenUseCase#childFields.put("creator", ...)}) vào danh sách "người trả
 * lại" (người đã chuyển văn bản này cho tôi — tôi có thể trả lại cho ĐÚNG người đó).</p>
 *
 * <p><b>Quirk Domino giữ nguyên có chủ đích</b>: {@code id} rỗng/null → Domino set nội bộ
 * {@code status=false} nhưng bị {@code doService()} ghi đè thành {@code true} ngay sau đó (không có
 * exception nào được ném) — trả {@link Result#success} với danh sách RỖNG thay vì lỗi, đúng tiền lệ
 * quirk đã ghi ở {@code XoaVanBanDenUseCase}.</p>
 */
@Service
@RequiredArgsConstructor
public class GetNguoiTraLaiUseCase {

    private static final Set<String> MATCHING_ACTIONS = Set.of(
        ChuyenXuLyVbdenUseCase.ACTION_CHUYEN_XU_LY, "vanthuchuyenxuly");
    private static final String ACTION_FIELD = "action";
    private static final String NGUOI_THUC_HIEN_FIELD = "nguoi_thuc_hien";
    private static final String CREATOR_FIELD = "creator";
    // "Toan bo" ban ghi vbden_res cua 1 van ban thuc te khong nhieu (vai chuc lan xu ly la cung) -
    // dung 1 trang lon duy nhat, cung tien le ThuHoiVanBanUseCase (pageSize:1000).
    private static final int MAX_HISTORY_RECORDS = 1000;

    private final VbdenResDocumentRepository vbdenResDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;

    public Result<List<Map<String, String>>> execute(GetNguoiTraLaiRequest request) {
        List<Map<String, String>> result = new ArrayList<>();
        if (request == null || request.getId() == null) {
            return Result.success(result);
        }
        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (isBlank(actingUsername)) {
            return Result.success(result);
        }

        var historyPage = vbdenResDocumentRepository.findByParentId(
            request.getId().toString(), PageRequest.of(0, MAX_HISTORY_RECORDS));

        for (VbdenResDocument record : historyPage.getContent()) {
            Map<String, Object> fields =
                dynamicDocumentFieldsService.parseDynamicFields(record.getDynamicFieldsJson());
            if (!MATCHING_ACTIONS.contains(asString(fields.get(ACTION_FIELD)))) {
                continue;
            }
            if (!isCurrentUserInSnapshot(fields, actingUsername)) {
                continue;
            }
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("username", asString(fields.get(NGUOI_THUC_HIEN_FIELD)));
            entry.put("name", asString(fields.get(CREATOR_FIELD)));
            result.add(entry);
        }
        return Result.success(result);
    }

    // Domino: user_process_obj, fallback user_assigner_obj neu rong/"[]" - dung DUNG thu tu uu tien.
    // Doc qua VbdenObjSnapshotFields (bugfix 14/09/2026 - xem javadoc lop do): CHIU DUOC ca 2 hinh
    // dang luu tru (chuoi JSON tho HOAC List/Map da parse san - dung THUC TE production).
    private boolean isCurrentUserInSnapshot(Map<String, Object> fields, String actingUsername) {
        Object rawValue = fields.get(ChuyenXuLyVbdenUseCase.USER_PROCESS_OBJ_FIELD_RAW);
        if (VbdenObjSnapshotFields.isEmptyArray(rawValue)) {
            rawValue = fields.get(ChuyenXuLyVbdenUseCase.USER_ASSIGNER_OBJ_FIELD_RAW);
        }
        JsonNode parsed = VbdenObjSnapshotFields.readJsonArray(rawValue);
        for (JsonNode node : parsed) {
            if (node == null || node.isNull()) {
                continue;
            }
            String userReceiver = node.path("user_receiver").asText("").trim();
            String username = node.path("username").asText("").trim();
            if (actingUsername.equalsIgnoreCase(userReceiver) || actingUsername.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
