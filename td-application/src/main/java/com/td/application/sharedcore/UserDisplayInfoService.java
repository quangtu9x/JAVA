package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 09/09/2026 (yêu cầu người dùng "chưa thấy có thông tin đơn vị của người dùng khi xem (tài khoản
 * thuộc cơ quan, đơn vị, phòng ban nào)"): tra tên hiển thị + tổ chức ĐẦY ĐỦ của 1 username, dùng
 * chung cho mọi nơi cần "server-authoritative snapshot" của người thao tác (log seen/xem tệp/...) —
 * tách ra khỏi {@link AddVbdiSeenDocumentUseCase} (nơi phát sinh nhu cầu đầu tiên) để tái dùng ở
 * {@code RecordFileAccessUseCase} mà không lặp code.
 *
 * <p><b>Vì sao KHÔNG dùng thẳng {@link UserOrganizationProfileDto#getUnitName()}</b> (quy ước
 * {@code currentUser.unit} toàn hệ thống, xem {@code QLVBCompatAuthController#buildProfile}):
 * {@code unitName} CHỈ có giá trị khi có 1 tổ tiên trong cây tổ chức khớp ĐÚNG form {@code "unit"}
 * (xem {@link GetUserOrganizationProfileUseCase#resolveOrganizationAncestry}) — nhiều tài khoản
 * KHÔNG có tổ tiên dạng "unit" (chỉ có "agency"/"department") nên {@code unitName} rỗng dù tài
 * khoản RÕ RÀNG thuộc 1 cơ quan/phòng ban nào đó — hiển thị "để trống" gây hiểu lầm "không xác định
 * được đơn vị". Ở đây gộp CẢ 3 cấp {@code agencyName}/{@code unitName}/{@code departmentName} (đúng
 * 3 khái niệm "cơ quan/đơn vị/phòng ban" người dùng nêu) thành 1 chuỗi hiển thị, nối {@code " / "},
 * bỏ qua cấp nào rỗng — luôn có nội dung hiển thị nếu tài khoản có organizationId hợp lệ ở BẤT KỲ
 * cấp nào.</p>
 */
@Service
@RequiredArgsConstructor
public class UserDisplayInfoService {

    private final AppUserRepository appUserRepository;
    private final GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    /** Snapshot hiển thị của 1 username — {@code unit} là chuỗi tổ chức ĐẦY ĐỦ (cơ quan/đơn vị/phòng ban). */
    public record UserDisplayInfo(String fullName, String unit) {
    }

    public UserDisplayInfo resolve(String username) {
        if (username == null || username.isBlank()) {
            return new UserDisplayInfo(username, "");
        }
        AppUser appUser = appUserRepository.findByUsernameAndDeletedOnIsNull(username).orElse(null);
        if (appUser == null) {
            return new UserDisplayInfo(username, "");
        }
        UserOrganizationProfileDto profile = getUserOrganizationProfileUseCase.buildProfileForUser(appUser);
        String fullName = profile.getFullName() != null && !profile.getFullName().isBlank()
            ? profile.getFullName() : username;
        String unit = composeOrganizationDisplay(profile);
        return new UserDisplayInfo(fullName, unit);
    }

    private String composeOrganizationDisplay(UserOrganizationProfileDto profile) {
        return Stream.of(profile.getAgencyName(), profile.getUnitName(), profile.getDepartmentName())
            .filter(name -> name != null && !name.isBlank())
            .collect(Collectors.joining(" / "));
    }
}
