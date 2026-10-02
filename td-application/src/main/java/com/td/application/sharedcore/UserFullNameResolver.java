package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 25/09/2026 (yêu cầu người dùng "kiểm tra phần hiển thị nhật ký hiệu chỉnh với các tài khoản, làm
 * tổng thể, không làm riêng 1 tài khoản"): tra TÊN ĐẦY ĐỦ của 1 username từ hồ sơ {@link AppUser} —
 * dùng chung cho "Lịch sử hiệu chỉnh" của dự thảo/vbdi/vbden.
 *
 * <p>Nguyên nhân gốc: {@code creator_name} của vết hiệu chỉnh trước đây lấy NGUYÊN từ client
 * ({@code currentUser?.name}) — nhiều đường ghi vết phụ ở FE (gắn tệp, ký số...) không gửi field
 * này, nên cùng 1 bảng có dòng hiện tên đầy đủ, có dòng chỉ hiện tài khoản (vd 17 dòng vbdi của
 * {@code skhcn}). Giờ server tự tra tên theo tài khoản đăng nhập lúc GHI, và tra bù lúc ĐỌC cho dữ
 * liệu cũ thiếu tên — không phụ thuộc client gửi gì.</p>
 */
@Service
@RequiredArgsConstructor
public class UserFullNameResolver {

    private final AppUserRepository appUserRepository;

    /** Tên đầy đủ của {@code username}; không tra được (không có hồ sơ/lỗi) thì trả {@code null}. */
    public String fullNameOrNull(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return appUserRepository.findByUsernameAndDeletedOnIsNull(username)
                .map(AppUser::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);
        } catch (RuntimeException ex) {
            // Tra hồ sơ lỗi không được làm hỏng nghiệp vụ ghi/đọc vết.
            return null;
        }
    }

    /**
     * Tên hiển thị khi GHI vết: ưu tiên hồ sơ server, rồi tên client gửi lên, cuối cùng là username.
     */
    public String resolveForWrite(String username, String clientName) {
        String fromProfile = fullNameOrNull(username);
        if (fromProfile != null) {
            return fromProfile;
        }
        return clientName != null && !clientName.isBlank() ? clientName : username;
    }

    /**
     * Bộ tra tên khi ĐỌC danh sách vết — ghi nhớ theo username trong phạm vi 1 lần đọc để không tra
     * lặp lại cho từng dòng. Dòng đã có tên (khác username) giữ nguyên tên đã lưu.
     */
    public Function<RowName, String> displayNameLookup() {
        Map<String, String> cache = new HashMap<>();
        return row -> {
            String stored = row.storedName();
            String username = row.username();
            boolean hasRealName = stored != null && !stored.isBlank() && !stored.equalsIgnoreCase(username);
            if (hasRealName) {
                return stored;
            }
            if (username == null || username.isBlank()) {
                return stored;
            }
            String resolved = cache.computeIfAbsent(username, key -> {
                String name = fullNameOrNull(key);
                return name == null ? "" : name;
            });
            return resolved.isEmpty() ? (stored != null && !stored.isBlank() ? stored : username) : resolved;
        };
    }

    /** Cặp (tên đã lưu, username) của 1 dòng vết. */
    public record RowName(String storedName, String username) {
    }
}
