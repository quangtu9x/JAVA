package com.td.application.vbcddh;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BL-250 (VBCDDH Core) — DoD (5): nhắc việc tự động cho người dùng hiện tại, đúng cấu trúc/ngưỡng
 * thời gian thật ({@code NhacViec.java}): "Sắp đến hạn" = {@code hanxuly_date} trong khoảng
 * [hôm nay, ngày mai], nhóm theo 2 trạng thái tổng quát ("đang thực hiện"/"đã thực hiện").
 *
 * <p><b>Đơn giản hoá so với Domino thật</b>: Domino xác định "khoá tra cứu" bằng cách duyệt mảng
 * {@code currentUser.identifiers} theo {@code currentUser.level} ({@code GetKeyValue()}) — mô hình
 * dữ liệu Spring Boot hiện tại ({@link com.td.domain.sharedcore.AppUser}) không có các field tương
 * đương ({@code identifiers}/{@code level} dạng chuỗi phân cấp); dùng {@code organizationId} của
 * người dùng hiện tại làm khoá tra cứu (tương đương "đơn vị trực tiếp"), đơn giản hơn cơ chế phân
 * cấp đầy đủ thật — cần xác nhận nghiệp vụ nếu cần khớp chính xác nhiều cấp tổ chức.
 */
@Service
@RequiredArgsConstructor
public class NhacViecUseCase {

    private static final String DOCUMENT_TYPE = "vbcddh";
    private static final String FORM_NHIEMVU = "nhiemvu";
    private static final String DON_VI_FIELD = "donvithuchienhide";
    private static final int MAX_CANDIDATES = 5000;

    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<NhacViecResponseDto> execute() {
        String key = resolveKey();
        if (key == null) {
            return Result.failure("Không xác định được đơn vị của người dùng hiện tại");
        }

        List<Map<String, Object>> candidates = fetchNhiemVuFields(key);
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        List<NhacViecItemDto> dangThucHien = new ArrayList<>();
        dangThucHien.add(new NhacViecItemDto("Chưa hoàn thành", countByTinhTrang(candidates, "chưa")));
        dangThucHien.add(new NhacViecItemDto("Toàn bộ", countByTinhTrang(candidates, "đang")));
        dangThucHien.add(new NhacViecItemDto("Trả lại", countByFlag(candidates, "tralai")));
        dangThucHien.add(new NhacViecItemDto("Xử lý lại", countByFlag(candidates, "xulylai")));
        dangThucHien.add(new NhacViecItemDto("Trong ngày",
            countByTinhTrangAndDate(candidates, "đang", "create_date", today, today)));
        dangThucHien.add(new NhacViecItemDto("Sắp đến hạn",
            countByTinhTrangAndDate(candidates, "đang", "hanxuly_date", today, tomorrow)));
        dangThucHien.add(new NhacViecItemDto("Trọng tâm", countByFlag(candidates, "trongtam")));

        List<NhacViecItemDto> daHoanThanh = new ArrayList<>();
        daHoanThanh.add(new NhacViecItemDto("Trong ngày",
            countByTinhTrangAndDate(candidates, "đã", "create_date", today, today)));
        daHoanThanh.add(new NhacViecItemDto("Không ấn định hạn", countByTinhTrangAndBlankDate(candidates, "đã")));
        daHoanThanh.add(new NhacViecItemDto("Toàn bộ", countByTinhTrang(candidates, "đã")));
        daHoanThanh.add(new NhacViecItemDto("Đúng hạn", countByTinhTrangAndQuaHan(candidates, "đã", false)));
        daHoanThanh.add(new NhacViecItemDto("Quá hạn", countByTinhTrangAndQuaHan(candidates, "đã", true)));

        return Result.success(new NhacViecResponseDto(dangThucHien, daHoanThanh));
    }

    private String resolveKey() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(user -> user.getOrganizationId() != null ? user.getOrganizationId().toString() : null)
            .orElse(null);
    }

    private List<Map<String, Object>> fetchNhiemVuFields(String key) {
        List<VbcddhDocument> candidates = vbcddhDocumentRepository
            .findByFieldValue(DOCUMENT_TYPE, DON_VI_FIELD, key, PageRequest.of(0, MAX_CANDIDATES))
            .getContent();

        List<Map<String, Object>> result = new ArrayList<>();
        for (VbcddhDocument document : candidates) {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
            if (FORM_NHIEMVU.equalsIgnoreCase(asString(fields.get("form")))) {
                result.add(fields);
            }
        }
        return result;
    }

    private long countByTinhTrang(List<Map<String, Object>> candidates, String tinhTrangChua) {
        long count = 0;
        for (Map<String, Object> fields : candidates) {
            if (tinhTrangContains(fields, tinhTrangChua)) {
                count++;
            }
        }
        return count;
    }

    private long countByFlag(List<Map<String, Object>> candidates, String flagField) {
        long count = 0;
        for (Map<String, Object> fields : candidates) {
            String value = asString(fields.get(flagField));
            if (value != null && !value.isBlank() && !"0".equals(value)) {
                count++;
            }
        }
        return count;
    }

    private long countByTinhTrangAndDate(
            List<Map<String, Object>> candidates, String tinhTrangChua, String dateField, LocalDate from, LocalDate to) {
        long count = 0;
        for (Map<String, Object> fields : candidates) {
            if (!tinhTrangContains(fields, tinhTrangChua)) {
                continue;
            }
            LocalDate parsed = parseDate(asString(fields.get(dateField)));
            if (parsed == null) {
                continue;
            }
            if (!parsed.isBefore(from) && !parsed.isAfter(to)) {
                count++;
            }
        }
        return count;
    }

    private long countByTinhTrangAndBlankDate(List<Map<String, Object>> candidates, String tinhTrangChua) {
        long count = 0;
        for (Map<String, Object> fields : candidates) {
            if (!tinhTrangContains(fields, tinhTrangChua)) {
                continue;
            }
            String hanXuLy = asString(fields.get("hanxuly_date"));
            if (hanXuLy == null || hanXuLy.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private long countByTinhTrangAndQuaHan(List<Map<String, Object>> candidates, String tinhTrangChua, boolean quaHan) {
        long count = 0;
        String expected = quaHan ? "1" : "0";
        for (Map<String, Object> fields : candidates) {
            if (!tinhTrangContains(fields, tinhTrangChua)) {
                continue;
            }
            if (expected.equals(asString(fields.get("quahan")))) {
                count++;
            }
        }
        return count;
    }

    private boolean tinhTrangContains(Map<String, Object> fields, String needle) {
        String tinhtrang = asString(fields.get("tinhtrang"));
        return tinhtrang != null && tinhtrang.toLowerCase().contains(needle);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
