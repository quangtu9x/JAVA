package com.td.application.tonghop;

import com.td.application.common.models.Result;
import com.td.application.vbdi.SendVbdiDocumentRequest;
import com.td.application.vbdi.SendVbdiDocumentUseCase;
import com.td.application.vbdi.VbdiSendRecipientGroup;
import com.td.application.vbdi.VbdiSendRecipientRequest;
import com.td.application.vbdi.VbdiSendType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Gửi lại" từ màn giám sát tập trung: quản trị tỉnh gửi lại văn bản đi của 1 đơn vị tới các nơi nhận
 * được chọn. Không tự chế luồng gửi riêng — chạy CHÍNH {@link SendVbdiDocumentUseCase}
 * ({@code sendType=resenddocument}) trong tenant sở hữu văn bản, nên nhật ký gửi nhận, outbox
 * {@code vbdi.delivery}, tạo văn bản đến phía nhận... giống hệt khi văn thư đơn vị bấm "Gửi lại".
 *
 * <p>Không gắn {@code @Transactional}: phải đổi tenant TRƯỚC khi transaction của use case gửi mở kết nối.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResendProvinceDispatchUseCase {

    private final ProvinceQueryRepository provinceQueryRepository;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final SendVbdiDocumentUseCase sendVbdiDocumentUseCase;
    private final ProvinceSyncService provinceSyncService;
    private final ProvinceScopePolicy provinceScopePolicy;

    public Result<Integer> execute(ResendProvinceDispatchRequest request) {
        if (request == null || request.getTenantId() == null || request.getDocumentId() == null) {
            return Result.failure("Thiếu tenantId/documentId");
        }
        ProvinceScope scope = provinceScopePolicy.current();
        if (!scope.isResolved()) {
            return Result.failure(SearchProvinceDocumentsUseCase.SCOPE_UNRESOLVED);
        }
        if (!scope.covers(request.getTenantId())) {
            // 22/10/2026: admin đơn vị chỉ gửi lại văn bản đi của tenant mình (trước đây mọi ADMIN gửi lại được của mọi đơn vị).
            return Result.failure("Bạn chỉ được gửi lại văn bản của đơn vị mình");
        }
        if (request.getRecipientKeys() == null || request.getRecipientKeys().isEmpty()) {
            return Result.failure("Chưa chọn nơi nhận để gửi lại");
        }
        if (!tenantScopedExecutor.isKnownTenant(request.getTenantId())) {
            return Result.failure("Đơn vị sở hữu văn bản không còn hoạt động trên hệ thống");
        }
        Set<String> wanted = new HashSet<>(request.getRecipientKeys());
        List<ProvinceDispatchRecipientItem> recipients = provinceQueryRepository
            .dispatchRecipients(request.getTenantId(), request.getDocumentId()).stream()
            .filter(r -> wanted.contains(r.getRecipientKey()))
            .toList();
        if (recipients.isEmpty()) {
            return Result.failure("Không tìm thấy nơi nhận đã chọn của văn bản này");
        }

        SendVbdiDocumentRequest send = new SendVbdiDocumentRequest();
        send.setDocumentId(request.getDocumentId());
        send.setSendType(VbdiSendType.RESEND_DOCUMENT);
        for (ProvinceDispatchRecipientItem r : recipients) {
            VbdiSendRecipientRequest item = new VbdiSendRecipientRequest();
            item.setIdentifier(r.getRecipientIdentifier());
            item.setName(r.getRecipientName());
            switch (r.getRecipientGroup()) {
                case VbdiSendRecipientGroup.INTERNAL_UNIT -> send.getInternalUnit().add(item);
                case VbdiSendRecipientGroup.EXTERNAL_UNIT -> send.getExternalUnit().add(item);
                default -> send.getContactUnit().add(item);
            }
        }

        Result<Integer> result = tenantScopedExecutor.runInTenant(request.getTenantId(),
            () -> sendVbdiDocumentUseCase.execute(send));
        if (result.isSuccess()) {
            try {
                // Cập nhật ngay kho tổng hợp để màn giám sát thấy lượt gửi lại (không đợi chu kỳ định kỳ).
                provinceSyncService.syncNow(false);
            } catch (RuntimeException ex) {
                log.warn("Gui lai thanh cong nhung dong bo kho tong hop that bai - se cap nhat o chu ky ke tiep", ex);
            }
        }
        return result;
    }
}
