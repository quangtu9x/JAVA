package com.td.application.sharedcore;

import com.td.application.common.security.CurrentUserProvider;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * BL-118/BL-2xx — Bugfix 21/08/2026: tương đương {@code getIdentifier_by_level()} (FE,
 * {@code utils/global.jsx}) / {@code get_Identifier_user_by_level()} (Domino, {@code TDUlties.java})
 * — chọn ĐÚNG 1 identifier trong chuỗi phân cấp tổ chức ({@code identifiers}, LÁ → GỐC) của user
 * hiện tại theo {@code level} (đếm từ GỐC = 1).
 *
 * <p><b>Bối cảnh phát hiện</b>: {@code ViewQueryService}/{@code GetSoVanBanDenUseCase} (N03/N05/N12/
 * N13/N22, Slice 9) trước đó tự resolve identifier qua {@link AppUser#getIdentifier()} — field PHẲNG
 * đơn lẻ, ĐÃ ghi nhận là "chưa có ancestor chain" từ các slice trước. Người dùng báo
 * {@code GetSoVanBanDen} không có dữ liệu dù văn bản đã lưu — điều tra xác nhận: {@code document_register_id}/
 * {@code identifier_creator} khi tạo mới được gán theo {@code DocumentRegister} chọn bằng
 * {@code getIdentifier_by_level(currentUser)} (FE) — GIÁ TRỊ NÀY khác {@link AppUser#getIdentifier()}
 * (field phẳng) với user có cây tổ chức nhiều cấp — 2 nguồn identifier không khớp nhau, dù cùng 1
 * user. {@link GetUserOrganizationProfileUseCase} ĐÃ có sẵn cơ chế tính đúng {@code identifiers}
 * (chuỗi phân cấp, xây riêng cho BL-2xx — xem javadoc {@link UserOrganizationProfileDto#getIdentifiers()})
 * nhưng trước đó chỉ dùng để trả về FE (qua {@code qlvb/profile}), CHƯA được tái dùng ở tầng resolve
 * identifier server-side của các use case đọc dữ liệu vbden — service này lấp khoảng trống đó.
 *
 * <p>KHÔNG fallback về {@link AppUser#getIdentifier()} khi resolve theo cấp thất bại (thiếu
 * {@code level}/{@code identifiers} rỗng) — trả {@code Optional.empty()}, ĐÚNG hành vi FE (trả `""`)
 * — an toàn hơn fallback sang 1 giá trị identifier KHÁC (tránh false-positive hiện sai dữ liệu tổ
 * chức khác), dù nghĩa là 1 số tài khoản thiếu dữ liệu tổ chức sẽ thấy trang trống thay vì lỗi rõ.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResolveIdentifierByLevelUseCase {

    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    public Optional<String> resolveForCurrentUser() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .flatMap(this::resolveForUser);
    }

    public Optional<String> resolveForUser(AppUser user) {
        UserOrganizationProfileDto profile = getUserOrganizationProfileUseCase.buildProfileForUser(user);
        return pickByLevel(profile.getIdentifiers(), profile.getLevel());
    }

    // Dung dung thuat toan getIdentifier_by_level() (ThanhHoaDEV/src/utils/global.jsx): tach
    // "identifiers" (LA->GOC) thanh mang, duyet TU CUOI (goc) VE DAU (la), dem tu 1 - tra ve phan tu
    // co so thu tu (tinh tu goc) == level. KHONG parse duoc level hoac identifiers rong -> rong.
    private Optional<String> pickByLevel(String identifiers, String levelText) {
        if (identifiers == null || identifiers.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifiers.endsWith(";") ? identifiers.substring(0, identifiers.length() - 1) : identifiers;
        String[] parts = trimmed.split(";");

        int level;
        try {
            level = Integer.parseInt(levelText == null ? "" : levelText.trim());
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }

        int count = 0;
        for (int i = parts.length - 1; i >= 0; i--) {
            count++;
            if (count == level) {
                String value = parts[i];
                return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }
}
