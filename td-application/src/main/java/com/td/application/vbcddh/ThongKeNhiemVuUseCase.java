package com.td.application.vbcddh;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BL-250 (VBCDDH Core) — DoD (4): thống kê nhiệm vụ theo đơn vị/người chủ trì, đúng logic đếm thật
 * ({@code ThongKeNhiemVu.java}): 6 tổ hợp cố định (tinhtrang × quahan), so khớp {@code tinhtrang}
 * kiểu chứa-chuỗi-con (không phân biệt hoa/thường — {@code "Chưa"}/{@code "Đang"}/{@code "Đã"}),
 * {@code quahan} so khớp tuyệt đối {@code "0"}/{@code "1"}. Lọc theo khoảng ngày (nếu có) trên field
 * {@code create_date} — Domino thật lọc bằng {@code FTSearch} trên chính field này.
 */
@Service
@RequiredArgsConstructor
public class ThongKeNhiemVuUseCase {

    private static final String DOCUMENT_TYPE = "vbcddh";
    private static final String FORM_NHIEMVU = "nhiemvu";
    private static final String DON_VI_FIELD = "donvithuchienhide";
    private static final String NGUOI_FIELD = "chuyenvienthuchienhide";
    private static final int MAX_CANDIDATES = 5000;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<Map<String, List<NhiemVuThongKeItemDto>>> execute(ThongKeNhiemVuRequest request) {
        String field;
        List<String> keys;
        if (request.getNguoiList() != null && !request.getNguoiList().isEmpty()) {
            field = NGUOI_FIELD;
            keys = request.getNguoiList();
        } else if (request.getIdentifiers() != null && !request.getIdentifiers().isEmpty()) {
            field = DON_VI_FIELD;
            keys = request.getIdentifiers();
        } else {
            return Result.failure("Cần truyền identifiers hoặc nguoiList");
        }

        LocalDate tuNgay = parseDate(request.getTuNgay());
        LocalDate denNgay = parseDate(request.getDenNgay());

        Map<String, List<NhiemVuThongKeItemDto>> response = new LinkedHashMap<>();
        for (String key : keys) {
            response.put(key, thongKeMotKey(field, key, tuNgay, denNgay));
        }
        return Result.success(response);
    }

    private List<NhiemVuThongKeItemDto> thongKeMotKey(String field, String key, LocalDate tuNgay, LocalDate denNgay) {
        var candidates = vbcddhDocumentRepository
            .findByFieldValue(DOCUMENT_TYPE, field, key, PageRequest.of(0, MAX_CANDIDATES))
            .getContent();

        long chuaThucHien = 0;
        long chuaThucHienQh = 0;
        long dangThucHien = 0;
        long dangThucHienQh = 0;
        long daThucHien = 0;
        long daThucHienQh = 0;

        for (VbcddhDocument document : candidates) {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
            if (!FORM_NHIEMVU.equalsIgnoreCase(asString(fields.get("form")))) {
                continue;
            }
            if (!inDateRange(asString(fields.get("create_date")), tuNgay, denNgay)) {
                continue;
            }

            String tinhtrang = asString(fields.get("tinhtrang"));
            String lower = tinhtrang == null ? "" : tinhtrang.toLowerCase();
            boolean quaHan = "1".equals(asString(fields.get("quahan")));

            if (lower.contains("chưa")) {
                if (quaHan) {
                    chuaThucHienQh++;
                } else {
                    chuaThucHien++;
                }
            } else if (lower.contains("đang")) {
                if (quaHan) {
                    dangThucHienQh++;
                } else {
                    dangThucHien++;
                }
            } else if (lower.contains("đã")) {
                if (quaHan) {
                    daThucHienQh++;
                } else {
                    daThucHien++;
                }
            }
        }

        return List.of(
            new NhiemVuThongKeItemDto(chuaThucHien, "Chưa thực hiện", "green"),
            new NhiemVuThongKeItemDto(chuaThucHienQh, "Chưa thực hiện (quá hạn)", "yellow"),
            new NhiemVuThongKeItemDto(dangThucHien, "Đang thực hiện", "pink"),
            new NhiemVuThongKeItemDto(dangThucHienQh, "Đang thực hiện (quá hạn)", "orange"),
            new NhiemVuThongKeItemDto(daThucHien, "Đã thực hiện", "grey"),
            new NhiemVuThongKeItemDto(daThucHienQh, "Đã thực hiện (quá hạn)", "red"));
    }

    private boolean inDateRange(String createDate, LocalDate tuNgay, LocalDate denNgay) {
        if (tuNgay == null && denNgay == null) {
            return true;
        }
        LocalDate parsed = parseDate(createDate);
        if (parsed == null) {
            return false;
        }
        if (tuNgay != null && parsed.isBefore(tuNgay)) {
            return false;
        }
        return denNgay == null || !parsed.isAfter(denNgay);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), ISO_DATE);
        } catch (Exception ex) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
