package com.td.application.vbden;

import java.math.BigDecimal;

/**
 * 05/10/2026: giá trị field động vbden đọc từ JSONB có thể là số (vd {@code document_number} = 92 lưu
 * thành Double -> {@code String.valueOf} ra "92.0"). Chuẩn hoá về text để hiển thị VÀ so khớp
 * (KiemTrungSoDen từng so "92" với "92.0" nên không bao giờ báo trùng số đến).
 */
final class VbdenFieldText {

    private VbdenFieldText() {
    }

    /** Số -> dạng thập phân gọn ("92.0" -> "92"); null -> ""; còn lại {@code String.valueOf}. */
    static String asPlainText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ex) {
                return number.toString(); // NaN/Infinity
            }
        }
        return String.valueOf(value);
    }
}
