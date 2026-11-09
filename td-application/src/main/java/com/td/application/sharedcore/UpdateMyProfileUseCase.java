package com.td.application.sharedcore;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 30/10/2026: màn "Thông tin tài khoản" (qlvb/thongtincanhan) — người đăng nhập tự sửa hồ sơ CỦA CHÍNH
 * MÌNH, thay Domino {@code qlvb/add?databasetype=qlvb&id=...} (404 trên td-web). Không nhận id từ client;
 * chỉ cho đổi các field hồ sơ cá nhân (không đổi tên đăng nhập/tổ chức/quyền/trạng thái). Field null = giữ
 * nguyên (AppUser.updateProfile null-safe).
 */
@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
public class UpdateMyProfileUseCase {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AppUserRepository appUserRepository;
    private final CurrentUserProvider currentUserProvider;

    @Data
    public static class Request {
        private String fullName;
        private String phoneNumber;
        private String gender;
        // "dd/MM/yyyy" hoặc "yyyy-MM-dd"; rỗng = giữ nguyên.
        private String birthdayDate;
        private String address;
        private String userIdNo;
    }

    public Result<UUID> execute(Request request) {
        var me = currentUserProvider.currentUsername().flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull);
        if (me.isEmpty()) {
            return Result.failure("Không xác định được người dùng đăng nhập");
        }
        if (request == null) {
            return Result.failure("Dữ liệu không hợp lệ");
        }
        String fullName = TextNormalizer.normalizeAndSanitize(request.getFullName());
        if (fullName != null && fullName.isBlank()) {
            return Result.failure("Họ và tên không được để trống");
        }
        if (fullName != null && fullName.length() > 300) {
            return Result.failure("Họ và tên tối đa 300 ký tự");
        }
        LocalDate birthday;
        try {
            birthday = parseDate(request.getBirthdayDate());
        } catch (RuntimeException ex) {
            return Result.failure("Ngày sinh không hợp lệ");
        }
        var current = me.get();
        current.updateProfile(null, fullName, null, null, null, null, null, null,
            TextNormalizer.normalize(request.getPhoneNumber()),
            TextNormalizer.normalize(request.getGender()),
            birthday,
            TextNormalizer.normalizeAndSanitize(request.getAddress()),
            TextNormalizer.normalize(request.getUserIdNo()),
            null, null, null);
        return Result.success(appUserRepository.save(current).getId());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.length() > 10 && v.charAt(4) == '-') {
            v = v.substring(0, 10);
        }
        return v.contains("/") ? LocalDate.parse(v, DMY) : LocalDate.parse(v);
    }
}
