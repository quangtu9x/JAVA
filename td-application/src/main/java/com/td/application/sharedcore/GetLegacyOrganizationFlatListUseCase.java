package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port danhmuc (09/09/2026) — {@code qlvb/danhmuc/DanhSachCoCauToChuc?databasetype=vbden} (Domino
 * {@code DanhSachCoCauToChuc.java}, view {@code cocautochuc_by_identifier}) — DANH SÁCH PHẲNG mọi
 * node cơ cấu tổ chức (agency_level/agency/unit/department), KHÁC {@link GetLegacyOrganizationTreeUseCase}
 * (cây, có lọc phạm vi theo cơ quan của user). Domino gốc duyệt NGUYÊN view, KHÔNG lọc theo
 * ACL/user/is_active — giữ đúng hành vi đó ở đây (không set {@code isActive} khi search, khác
 * {@link GetLegacyOrganizationTreeUseCase#loadOrganizationsForTree}).
 *
 * <p>FE dùng CHUNG kết quả này cho nhiều màn hình vbden (qua {@code getCoCauToChuc()} ở
 * {@code global.jsx}) để tra {@code identifier -> name}/{@code user_receiver} — xác nhận bằng khảo
 * sát toàn bộ 16 call site (16 file FE, 09/09/2026): field THỰC SỰ được đọc là {@code identifier},
 * {@code name}, {@code user_receiver} (rộng khắp vbden) và {@code identifier_other}/{@code clerical}/
 * {@code ipserver}/{@code dbpath} (chỉ 2 service báo cáo tonghop). {@code identifiers} (số nhiều) và
 * {@code user_manage} — có trong {@code FILTER_ITEMS} Domino nhưng KHÔNG file FE nào đọc — vẫn được
 * trả về cho ĐỦ hình dạng hợp đồng cũ (phòng trường hợp có nơi gọi khác ngoài phạm vi khảo sát), theo
 * đúng tiền lệ {@code layDanhSachDungChung}/{@code getNhomXemLichTongHop}.
 *
 * <p>{@code identifiers}: chuỗi identifier của CHÍNH node và mọi tổ tiên, nối {@code ";"}, thứ tự
 * LÁ → GỐC — cùng thuật toán/thứ tự đã xác nhận từ Domino thật (xem
 * {@code LuuDanhMuc.java#XoaCache}/dòng gán {@code doc.replaceItemValue("identifiers", identifiers)})
 * và đã dùng lại ở {@link GetUserOrganizationProfileUseCase#resolveOrganizationAncestry} — KHÁC ở
 * đây là đi trong bộ nhớ (map id→Organization đã nạp sẵn) thay vì query DB từng bước, vì cần tính
 * cho MỌI node (tránh N+1 lặp lại của cách làm bên kia).
 *
 * <p>{@code user_manage}: Domino có field này trên doc {@code cocautochuc} nhưng KHÔNG servlet nào
 * trong {@code BE_THANHHOA} từng đọc/ghi nó từ context "cơ cấu tổ chức" (chỉ thấy nghĩa khác hẳn trên
 * doc vbden — "người phụ trách văn bản"), và {@code Organization} (Java) chưa có cột/dynamic field
 * tương ứng — trả "" cố định, ghi nhận là hạn chế thay vì suy đoán bừa.
 */
@Service
@RequiredArgsConstructor
@Transactional(value = "identityTransactionManager", readOnly = true)
public class GetLegacyOrganizationFlatListUseCase {

    private final OrganizationRepository organizationRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final AppUserRepository appUserRepository;

    public List<Map<String, Object>> execute() {
        List<Organization> organizations = loadAllOrganizations();

        Map<UUID, Organization> byId = new HashMap<>();
        for (Organization organization : organizations) {
            byId.put(organization.getId(), organization);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Organization organization : organizations) {
            result.add(toFlatRow(organization, byId));
        }
        return result;
    }

    // KHÔNG set isActive (null = không lọc, xem PostgresOrganizationRepository#buildIsActiveSpec) —
    // Domino duyệt nguyên view cocautochuc_by_identifier, không loại trừ node đã khoá/inactive.
    private List<Organization> loadAllOrganizations() {
        List<Organization> organizations = new ArrayList<>();

        int pageNumber = 0;
        int pageSize = 200;
        while (true) {
            var request = new SearchOrganizationsRequest();
            request.setPageNumber(pageNumber);
            request.setPageSize(pageSize);

            var pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Order.asc("level"), Sort.Order.asc("sortOrder"), Sort.Order.asc("name"))
            );

            var page = organizationRepository.search(request, pageable);
            organizations.addAll(page.getContent());

            if (page.isLast()) {
                break;
            }
            pageNumber++;
        }

        return organizations;
    }

    private Map<String, Object> toFlatRow(Organization organization, Map<UUID, Organization> byId) {
        Map<String, Object> dynamicFields =
            dynamicDocumentFieldsService.parseDynamicFields(organization.getDynamicFieldsJson());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", organization.getId().toString());
        row.put("name", safe(organization.getName()));
        row.put("identifiers", buildIdentifierChain(organization, byId));
        row.put("identifier", safe(organization.getIdentifier()));
        row.put("identifier_other", asString(dynamicFields.get("identifier_other")));
        row.put("clerical", asString(dynamicFields.get("clerical")));
        row.put("user_receiver", resolveReceiverUsername(organization.getReceiverId()));
        row.put("dbpath", safe(organization.getDbpath()));
        row.put("ipserver", safe(organization.getIpserver()));
        row.put("user_manage", "");
        return row;
    }

    private String buildIdentifierChain(Organization organization, Map<UUID, Organization> byId) {
        StringBuilder identifiers = new StringBuilder();
        Set<UUID> visited = new HashSet<>();
        Organization current = organization;
        while (current != null && visited.add(current.getId())) {
            if (current.getIdentifier() != null) {
                identifiers.append(current.getIdentifier()).append(";");
            }
            current = current.getParentId() == null ? null : byId.get(current.getParentId());
        }
        return identifiers.toString();
    }

    // Cùng thuật toán resolveReceiverUsername ở GetLegacyOrganizationTreeUseCase — receiverId lưu
    // UUID (dạng String) của AppUser đại diện.
    private String resolveReceiverUsername(String receiverId) {
        if (receiverId == null || receiverId.isBlank()) {
            return "";
        }
        try {
            UUID id = UUID.fromString(receiverId.trim());
            return appUserRepository.findByIdAndDeletedOnIsNull(id)
                .map(com.td.domain.sharedcore.AppUser::getUsername)
                .orElse("");
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
