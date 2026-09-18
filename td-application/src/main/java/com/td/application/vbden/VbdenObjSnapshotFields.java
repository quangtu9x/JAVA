package com.td.application.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Bugfix 14/09/2026 (báo lỗi người dùng: modal "Trả lại văn bản" trả về trống cho tài khoản demo2 dù
 * dữ liệu thật có bản ghi khớp — văn bản {@code 2b834a46-e700-4264-af54-5f619b62d766}) — TỔNG QUÁT,
 * không vá riêng {@link GetNguoiTraLaiUseCase} (theo đúng chỉ đạo người dùng trước đó "lỗi chung thì
 * fix tổng thể, không làm riêng trường hợp cụ thể").
 *
 * <p><b>Nguyên nhân gốc</b>: MỌI field động có tên kết thúc {@code "_obj"} (đúng
 * {@code user_process_obj}/{@code user_assigner_obj}/{@code user_coordinate_obj}/
 * {@code user_to_read_obj}) được {@link com.td.application.common.dynamicfields.FieldTypeInferrer}
 * suy luận là {@code FieldType.JSON}, khiến
 * {@link com.td.application.common.dynamicfields.DynamicFieldValueConverter#convert} PARSE chuỗi
 * JSON thô thành {@code List}/{@code Map} lồng NGAY LÚC GHI ({@code AddVbdenResDocumentUseCase} qua
 * {@code DynamicDocumentFieldsService#buildDynamicFieldsJson}) — đối chiếu dữ liệu thật (tenant
 * H05_01/h05_01_sokhcn) xác nhận {@code jsonb_typeof(dynamic_fields->'user_process_obj') = 'array'},
 * KHÔNG PHẢI {@code 'string'}. Khi đọc lại qua {@code DynamicDocumentFieldsService#parseDynamicFields},
 * giá trị field đã LÀ {@code List<Map<String,Object>>} (Java native), không còn là chuỗi JSON —
 * {@code Object#toString()} trên cấu trúc đó (vd. {@code [{username=demo2}]}, dùng {@code =} thay vì
 * {@code :}, không có dấu nháy) KHÔNG PHẢI JSON hợp lệ, khiến {@code ObjectMapper#readTree} luôn ném
 * lỗi và bị bắt/bỏ qua ÂM THẦM (không exception nào lộ ra ngoài) — mọi bản ghi snapshot bị coi như
 * "không khớp" dù dữ liệu thật khớp hoàn toàn.</p>
 *
 * <p><b>Phạm vi lỗi</b>: ẢNH HƯỞNG CẢ {@link GetNguoiTraLaiUseCase} (phát hiện qua báo lỗi này) VÀ
 * {@link ThuHoiVanBanUseCase#revokeFromSnapshot} (đã ship 01/09/2026, cùng pattern
 * {@code asString(...)} rồi {@code readTree}) — {@code ThuHoiVanBanUseCaseTest}/
 * {@code GetNguoiTraLaiUseCaseTest} trước bản fix này đều KHÔNG bắt được lỗi vì tự tạo fixture snapshot
 * dạng CHUỖI JSON thô (khớp giả định sai của code, không khớp hình dạng dữ liệu thật) — 2 test đã bổ
 * sung thêm ca dữ liệu dạng {@code List}/{@code Map} lồng (đúng thực tế) để không tái diễn.</p>
 *
 * <p>Helper này CHỊU ĐƯỢC CẢ 2 hình dạng — chuỗi JSON thô (fixture test cũ, hoặc field không kết thúc
 * {@code "_obj"}) VÀ cấu trúc {@code List}/{@code Map} đã parse sẵn (thực tế production) — quy về
 * {@link JsonNode} thống nhất để code gọi không cần biết/đoán hình dạng lưu trữ thật.</p>
 */
final class VbdenObjSnapshotFields {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VbdenObjSnapshotFields() {
    }

    /**
     * Trả {@link JsonNode} mảng của 1 field snapshot {@code _obj} — RỖNG (không throw) nếu giá trị
     * null, chuỗi rỗng, chuỗi không parse được, hoặc không phải mảng.
     */
    static JsonNode readJsonArray(Object rawValue) {
        JsonNode node = toJsonNode(rawValue);
        return (node != null && node.isArray()) ? node : OBJECT_MAPPER.createArrayNode();
    }

    /** true nếu field snapshot rỗng/không có phần tử nào (tương đương Domino so {@code "[]"}) . */
    static boolean isEmptyArray(Object rawValue) {
        return readJsonArray(rawValue).isEmpty();
    }

    private static JsonNode toJsonNode(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readTree(text);
            } catch (Exception ex) {
                return null;
            }
        }
        // Da la List/Map (thuc te production - xem javadoc lop) - chuyen truc tiep sang JsonNode,
        // KHONG di qua String#toString() (khong phai JSON hop le).
        return OBJECT_MAPPER.valueToTree(rawValue);
    }
}
