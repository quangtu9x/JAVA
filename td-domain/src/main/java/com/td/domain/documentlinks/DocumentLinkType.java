package com.td.domain.documentlinks;

import java.util.Arrays;
import java.util.Optional;

/**
 * 06/10/2026: các loại liên kết văn bản — {@code code} là giá trị lưu ở cột {@code document_links.link_type}
 * (KHÔNG đổi sau khi đã có dữ liệu). Loại liên kết quyết định luôn loại văn bản nguồn/đích.
 * Xem {@code docs/architecture/document-links.md}.
 */
public enum DocumentLinkType {

    /** Hồ sơ công việc → dự thảo (tab "Văn bản dự thảo"; actionName {@code gankemduthao}). */
    HOSO_DUTHAO("hoso_duthao", "hosocv", "duthao"),
    /** Hồ sơ → văn bản đi liên quan (actionName {@code ganvanbandi}). */
    HOSO_VBDI("hoso_vbdi", "hosocv", "vbdi"),
    /** Hồ sơ → văn bản đi phát hành (tab "Văn bản phát hành"; actionName {@code ganvanbanphathanh}). */
    HOSO_VBDI_PHATHANH("hoso_vbdi_phathanh", "hosocv", "vbdi"),
    /** Hồ sơ → văn bản đến liên quan (actionName {@code ganvanbanden}). */
    HOSO_VBDEN("hoso_vbden", "hosocv", "vbden"),
    /** Hồ sơ → văn bản đến cần xử lý (actionName {@code ganvanbandenxuly}). */
    HOSO_VBDEN_XULY("hoso_vbden_xuly", "hosocv", "vbden"),
    /** Hồ sơ → phiếu trình (actionName {@code gankemphieutrinh}; module phiếu trình chưa port). */
    HOSO_PHIEUTRINH("hoso_phieutrinh", "hosocv", "phieutrinh"),
    /** Dự thảo → văn bản đi được phát hành từ dự thảo đó (ChuyenThanhVanBanDi). */
    DUTHAO_VBDI("duthao_vbdi", "duthao", "vbdi"),

    // Giai đoạn 2 (V1.0.70): thay documentin_obj / vanban_lienquan_obj — link_data giữ bản chụp phần tử.
    /** Văn bản đi trả lời văn bản đến ("Trả lời cho văn bản đến", documentin_obj). */
    VBDI_TRALOI_VBDEN("vbdi_traloi_vbden", "vbdi", "vbden"),
    /** Dự thảo trả lời văn bản đến (documentin_obj của dự thảo). */
    DUTHAO_TRALOI_VBDEN("duthao_traloi_vbden", "duthao", "vbden"),
    /** "Văn bản kèm theo" (vanban_lienquan_obj) của văn bản đi -> văn bản đến / văn bản đi. */
    VBDI_KEMTHEO_VBDEN("vbdi_kemtheo_vbden", "vbdi", "vbden"),
    VBDI_KEMTHEO_VBDI("vbdi_kemtheo_vbdi", "vbdi", "vbdi"),
    /** "Văn bản kèm theo" của dự thảo -> văn bản đến / văn bản đi. */
    DUTHAO_KEMTHEO_VBDEN("duthao_kemtheo_vbden", "duthao", "vbden"),
    DUTHAO_KEMTHEO_VBDI("duthao_kemtheo_vbdi", "duthao", "vbdi");

    public static final String HOSOCV = "hosocv";

    private final String code;
    private final String sourceType;
    private final String targetType;

    DocumentLinkType(String code, String sourceType, String targetType) {
        this.code = code;
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    public String code() {
        return code;
    }

    public String sourceType() {
        return sourceType;
    }

    public String targetType() {
        return targetType;
    }

    public static Optional<DocumentLinkType> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase();
        return Arrays.stream(values()).filter(type -> type.code.equals(normalized)).findFirst();
    }
}
