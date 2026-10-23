package com.td.application.sharedcore;

import com.td.domain.sharedcore.Organization;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 12/10/2026 (báo lỗi: snnmt gửi VB cho "Trung tâm CNTT-TT" (H05.01.05, đơn vị con của Sở KHCN) — bên gửi
 * báo "Đã đến" nhưng văn thư trung tâm không thấy ở vbden/xuly/quamangnoibo): {@code linked_tenant_id}
 * chỉ khai báo ở cấp cơ quan (Sở = tenant), đơn vị/phòng con để trống. Các nơi tra tenant đích trước đây
 * chỉ đọc ĐÚNG tổ chức nhận → đơn vị con không có giá trị → rơi về tenant BÊN GỬI (văn bản đến được tạo
 * nhầm trong DB của snnmt). Tenant của 1 tổ chức = {@code linked_tenant_id} của CHÍNH nó, nếu trống thì
 * của tổ tiên gần nhất có khai báo (đi lên {@code parentId}).
 *
 * <p>Helper tĩnh, nhận {@link OrganizationRepository} của nơi gọi (không thêm bean/không đổi constructor).
 * Dùng ở: AddVbdiSendLogUseCase (gửi VB đi), ChuyenXuLyVbdenUseCase (chuyển xử lý sang đơn vị khác
 * tenant), PublishVbdiReplyStatusUseCase (phản hồi trạng thái về bên gửi). Xem
 * docs/architecture/11 §5.2 (bổ sung 12/10/2026).</p>
 */
public final class OrganizationTenantLookup {

    private static final int MAX_DEPTH = 20;

    private OrganizationTenantLookup() {
    }

    /** Tenant của tổ chức có mã định danh {@code identifier}; rỗng nếu không tìm thấy hoặc cả chuỗi tổ tiên không khai báo. */
    public static Optional<String> resolveLinkedTenantId(OrganizationRepository repository, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdentifierAndDeletedOnIsNull(identifier.trim())
            .flatMap(org -> resolveLinkedTenantId(repository, org));
    }

    public static Optional<String> resolveLinkedTenantId(OrganizationRepository repository, Organization organization) {
        Organization current = organization;
        Set<UUID> visited = new HashSet<>();
        int depth = 0;
        while (current != null && visited.add(current.getId()) && depth++ < MAX_DEPTH) {
            String linked = current.getLinkedTenantId();
            if (linked != null && !linked.isBlank()) {
                return Optional.of(linked.trim());
            }
            UUID parentId = current.getParentId();
            current = parentId == null ? null : repository.findByIdAndDeletedOnIsNull(parentId).orElse(null);
        }
        return Optional.empty();
    }
}
