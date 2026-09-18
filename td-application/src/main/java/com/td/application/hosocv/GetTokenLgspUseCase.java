package com.td.application.hosocv;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G8 ({@code docs/analysis/08-...md} mục 4.1/rủi ro #7): lấy token
 * xác thực LGSP (tích hợp một cửa/dịch vụ ngoài, {@code DownloadFileHSCV.java#gettoken()} thật gọi
 * {@code api.monre.gov.vn/token} với Basic Auth HARD-CODE trong source Domino).
 *
 * <p><b>KHÔNG hard-code secret</b> (đúng rủi ro #7 tài liệu 08 + quy tắc bảo mật
 * {@code docs/architecture/03} mục 5) — uỷ quyền cho {@link LgspTokenClient} (implementation thật ở
 * {@code td-infrastructure}, đọc cấu hình từ env/{@code application.yml} — xem javadoc ở đó). Đúng
 * tiền lệ {@code GuiEmailVbdiUseCase} (BL-221, {@code MAIL_SENDING_ENABLED=false}): nếu chưa cấu
 * hình, {@link LgspTokenClient} tự trả lỗi nghiệp vụ rõ ràng thay vì kết nối thất bại/NPE.
 *
 * <p>Ghi chú đúng nhận xét gốc (mục "Không làm" BL-240): route này vốn thuộc phạm vi tích hợp một
 * cửa ({@code TichHop}/BL-280) chứ không phải nghiệp vụ lõi hồ sơ công việc, đặt tạm ở đây theo
 * đúng vị trí route Domino thật ({@code HoSoCVController.gettokenlgsp}).
 */
@Service
@RequiredArgsConstructor
public class GetTokenLgspUseCase {

    private final LgspTokenClient lgspTokenClient;

    public Result<String> execute() {
        return lgspTokenClient.fetchToken();
    }
}
