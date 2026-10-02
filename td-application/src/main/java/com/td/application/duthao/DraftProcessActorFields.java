package com.td.application.duthao;

import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Yêu cầu người dùng 25/09/2026 ("ở lưu vết quá trình dự thảo -> lúc hiện tên đầy đủ, lúc hiện tài khoản
 * -> chỉnh lại cho thống nhất là hiện TÊN ĐẦY ĐỦ, tài khoản chỉ lưu ở trong thuộc tính không hiển thị ra
 * ngoài"): quy ước DUY NHẤT cho người thực hiện của 1 dòng {@code draft_process} (tab "Quá trình xử lý"):
 * <ul>
 *   <li>{@code creator} = TÊN ĐẦY ĐỦ ({@code AppUser.fullName}) — cột "Người thực hiện" FE hiển thị field
 *   này (Files/duthaofile.jsx), cùng kiểu với cột "Người nhận" vốn đã là tên đầy đủ.</li>
 *   <li>{@code user_creator} = TÀI KHOẢN (username) — chỉ dùng nội bộ (vd FE kiểm tra "vết của chính mình"
 *   để cho xoá), không hiển thị.</li>
 * </ul>
 * Cả 2 do SERVER tự xác định theo tài khoản đang đăng nhập (không nhận tên từ FE) — giữ nguyên nguyên tắc
 * server-authoritative trước đây, chỉ đổi GIÁ TRỊ hiển thị từ username sang tên đầy đủ tra từ hồ sơ.
 * Không tìm được hồ sơ (vd tài khoản hệ thống) thì tên hiển thị = username.
 */
@Component
@RequiredArgsConstructor
public class DraftProcessActorFields {

    public static final String CREATOR_FIELD = "creator";
    public static final String USER_CREATOR_FIELD = "user_creator";

    private final AppUserRepository appUserRepository;

    /** {@code fields} có thể là Map&lt;String,String&gt; hoặc Map&lt;String,Object&gt; (tuỳ use case). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void put(Map<String, ?> fields, String username) {
        Map raw = fields;
        raw.put(USER_CREATOR_FIELD, username);
        raw.put(CREATOR_FIELD, displayName(username));
    }

    public String displayName(String username) {
        if (username == null || username.isBlank()) {
            return username;
        }
        try {
            return appUserRepository.findByUsernameAndDeletedOnIsNull(username)
                .map(AppUser::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(username);
        } catch (RuntimeException ex) {
            // Tra hồ sơ lỗi không được làm hỏng nghiệp vụ ghi vết — rơi về username như trước.
            return username;
        }
    }
}
