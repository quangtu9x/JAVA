package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cổng "Cấp ban hành" (dropdown chọn cấp/cơ quan phát hành văn bản) — tương thích
 * {@code qlvb/danhmuc/publish?get=capbanhanh} thật ({@code publish.java#capbanhanh}/
 * {@code #handleDuthao}), dùng bởi {@code ChiTietPage.jsx} (module dự thảo, 24/08/2026).
 *
 * <p><b>Thuật toán Domino gốc</b> (đã đọc lại {@code publish.java} thật): lấy {@code parentids_obj}
 * (JSON array các tổ chức tổ tiên của user hiện tại, thứ tự LÁ→GỐC — org của chính user trước, tỉnh
 * sau cùng) từ view {@code vwuserbyusername}, {@code reverse()} thành GỐC→LÁ, rồi với mỗi phần tử
 * {@code identifier} không rỗng: thêm vào danh sách kết quả — TRỪ phần tử {@code agency_level} ĐẦU
 * TIÊN (bị hoãn lại), chỉ chèn vào đầu danh sách nếu {@code dbpath} của nó KHÔNG khác {@code dbpath}
 * của bất kỳ tổ tiên {@code agency} nào trên đường đi xuống (kiểm tra "tỉnh có cùng CSDL Notes với
 * cơ quan này không" — hạ tầng multi-database-per-agency của Domino).</p>
 *
 * <p><b>Đơn giản hoá CÓ CHỦ ĐÍCH ở Java (24/08/2026, đã hỏi xác nhận người dùng qua
 * AskUserQuestion)</b>: KHÔNG port check {@code dbpath} nói trên — 2 lý do đã xác minh: (1) kiến
 * trúc Java mới cách ly dữ liệu theo tenant ở tầng request/schema ({@code TenantResolver}), không
 * còn khái niệm "mỗi cơ quan 1 CSDL Notes riêng" mà check này bảo vệ; (2) đã audit thực tế —
 * {@code organizations.dbpath} trong Postgres CHƯA từng được backfill từ Domino (BL-305 "Domino
 * Data Migration Script" chưa bắt đầu, xem {@code docs/migration-backlog/04-migration-status.md}),
 * nên hầu hết rỗng — nếu port y nguyên, biểu thức {@code !dbPathChild.equals(tempDbPathAgency)} so
 * {@code "" != ""} luôn là {@code false}, tức {@code agency_level} LUÔN được thêm vào — HÀNH VI THỰC
 * TẾ đã tương đương "luôn thêm agency_level" ngay cả khi port y nguyên. Vì vậy Java LUÔN thêm tổ tiên
 * {@code agency_level} (Tỉnh) vào đầu danh sách kết quả nếu tồn tại, không cần state
 * {@code tempAgencyLevelInDb}.</p>
 *
 * <p>Hệ quả: nhánh {@code handleOtherTypes} (dùng cho {@code databasetype != "duthao"}, chỉ thêm
 * MỌI tổ tiên không phân biệt gì) và {@code handleDuthao} (thêm mọi tổ tiên + xử lý riêng
 * {@code agency_level}) trở thành TƯƠNG ĐƯƠNG sau khi bỏ check {@code dbpath} — {@code agency_level}
 * vốn đã đứng ĐẦU danh sách GỐC→LÁ nên "hoãn lại rồi chèn vào đầu" cho cùng kết quả với "thêm trực
 * tiếp theo thứ tự lặp". Vì vậy use case này không cần tham số {@code databasetype} để đổi hành vi —
 * chỉ dùng để validate phạm vi (CHỈ hỗ trợ {@code databasetype=duthao}, xem {@link #execute}).</p>
 *
 * <p><b>CHƯA port</b> (ngoài phạm vi lần này): {@code get=kythualenh}/{@code kyuyquyen}/
 * {@code nguoiduyet}/{@code capbanhanhhdndtinh}, và nhánh "K56" (HĐND tỉnh, merge thêm danh sách từ
 * {@code vwcocautochuc}) — {@code ChiTietPage.jsx} vẫn tự gọi
 * {@code LayDanhSachDungChung}/lọc phía FE cho nhánh này, không cần BE hỗ trợ thêm.</p>
 *
 * <p><b>Bổ sung 28/08/2026</b>: mở thêm {@code databasetype=vbdi} (dropdown "Cấp ban hành" ở
 * {@code SubFormThongTinCoBan.jsx}, module văn bản đi) — thuật toán/dữ liệu trả về Y HỆT
 * {@code duthao} (đã chứng minh ở trên: {@code handleDuthao}≡{@code handleOtherTypes} sau khi bỏ
 * check {@code dbpath}, chỉ khác nhánh {@code databasetype} nào được publish.java gọi tới, không
 * đổi dữ liệu) nên không cần nhánh xử lý riêng, chỉ nới allowlist.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class GetCapBanHanhOptionsUseCase {

    private static final String DATABASE_TYPE_DUTHAO = "duthao";
    private static final String DATABASE_TYPE_VBDI = "vbdi";
    private static final String IDENTIFIER_OTHER_FIELD = "identifier_other";
    private static final String MAILIN_FIELD = "mailin";
    private static final String FORM_AGENCY = "agency";

    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<List<Map<String, Object>>> execute(String databasetype) {
        if (databasetype != null && !databasetype.isBlank()
                && !DATABASE_TYPE_DUTHAO.equalsIgnoreCase(databasetype)
                && !DATABASE_TYPE_VBDI.equalsIgnoreCase(databasetype)) {
            return Result.failure(
                "Cấp ban hành (Java) chỉ hỗ trợ databasetype=duthao|vbdi, chưa hỗ trợ: " + databasetype);
        }

        // Bugfix 03/09/2026 (báo lỗi người dùng: tài khoản THẬT đã dùng được trước đây, tự nhiên
        // "capbanhanh" trả về data:[] rỗng — đã xác nhận KHÔNG liên quan bugfix "level=0->1" cùng
        // ngày (use case này không đọc Organization.level ở đâu cả, cây tổ chức vẫn hiển thị đúng)
        // nhưng không có DB truy cập trực tiếp để xác định CHÍNH XÁC 1 trong 3 nhánh rỗng dưới đây đang
        // xảy ra. Thêm log cảnh báo ở TỪNG nhánh rỗng để lần tái hiện sau chỉ cần đọc log là biết ngay,
        // không phải suy đoán qua nhiều vòng hỏi lại người dùng như lần này.
        Optional<String> usernameOpt = currentUserProvider.currentUsername();
        if (usernameOpt.isEmpty()) {
            log.warn("Cap ban hanh (databasetype={}): khong xac dinh duoc username hien tai tu token", databasetype);
            return Result.success(List.of());
        }
        String username = usernameOpt.get();
        Optional<AppUser> userOpt = appUserRepository.findByUsernameAndDeletedOnIsNull(username);
        if (userOpt.isEmpty()) {
            log.warn("Cap ban hanh (databasetype={}): khong tim thay AppUser voi username={}", databasetype, username);
            return Result.success(List.of());
        }
        AppUser user = userOpt.get();
        if (user.getOrganizationId() == null) {
            log.warn("Cap ban hanh (databasetype={}): user {} khong co organization_id (chua duoc gan vao to chuc nao)",
                databasetype, username);
            return Result.success(List.of());
        }
        // Bugfix 03/09/2026 (tiep, phat hien qua tai hien voi anh chup man hinh that: dang nhap
        // skhcn nhung "Cap ban hanh" van hien nhanh cua snnmt - KET QUA KHONG RONG nen 5 log WARN o
        // tren KHONG BAO GIO ban, khien lan tai hien truoc do khong thay log nao ca du bug van xay
        // ra): log KHONG DIEU KIEN (moi lan goi, ca thanh cong lan that bai) username + organizationId
        // vua doc duoc TU DB - neu dong log nay hien sai user/org ngay tu day, chac chan loi nam o
        // currentUserProvider.currentUsername()/AppUserRepository (BE); neu dong nay hien DUNG
        // (organization_id la cua skhcn) ma FE van hien snnmt, loi nam o FE (TDSelect cache/component
        // khong remount), khong phai o use case nay nua.
        log.info("Cap ban hanh (databasetype={}): resolved username={} -> organization_id={}",
            databasetype, username, user.getOrganizationId());

        List<Organization> ancestryLeafToRoot = walkAncestry(user.getOrganizationId());
        if (ancestryLeafToRoot.isEmpty()) {
            log.warn("Cap ban hanh (databasetype={}): user {} co organization_id={} nhung khong tim thay to chuc "
                    + "nao (co the la ID mo coi/da bi xoa mem)",
                databasetype, username, user.getOrganizationId());
            return Result.success(List.of());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        // Domino reverse() dataList (LÁ->GỐC thành GỐC->LÁ) trước khi lặp — ở đây đơn giản hơn: lặp
        // ngược danh sách LÁ->GỐC đã có sẵn, cho ĐÚNG thứ tự GỐC->LÁ (agency_level trước, don_vi
        // cua chinh user sau cung) ma khong can allocate list moi.
        List<Organization> options = new ArrayList<>();
        for (int i = ancestryLeafToRoot.size() - 1; i >= 0; i--) {
            Organization org = ancestryLeafToRoot.get(i);
            if (org.getIdentifier() == null || org.getIdentifier().isBlank()) {
                continue; // Domino: bo qua to tien khong co identifier
            }
            options.add(org);
        }
        // 01/10/2026 (thay nhanh HĐND gan cung o FE): co quan cau hinh "bo sung don vi con" -> them cac
        // don vi con TRUC TIEP cua co quan; to chuc cau hinh "an khoi cap ban hanh" -> bo ra. Xem
        // OrganizationConfigKeys.
        ancestryLeafToRoot.stream()
            .filter(org -> FORM_AGENCY.equals(org.getNodeType()))
            .findFirst()
            .filter(agency -> isFlagOn(agency, OrganizationConfigKeys.CAP_BAN_HANH_INCLUDE_CHILDREN))
            .ifPresent(agency -> organizationRepository.findByParentIdAndDeletedOnIsNull(agency.getId()).stream()
                .filter(child -> child.getIdentifier() != null && !child.getIdentifier().isBlank())
                .filter(child -> options.stream().noneMatch(existing -> existing.getId().equals(child.getId())))
                .sorted(java.util.Comparator.comparingInt(Organization::getSortOrder)
                    .thenComparing(child -> child.getName() == null ? "" : child.getName()))
                .forEach(options::add));
        for (Organization org : options) {
            if (!isFlagOn(org, OrganizationConfigKeys.HIDE_FROM_CAP_BAN_HANH)) {
                result.add(toLegacyNode(org));
            }
        }
        // 01/10/2026 (yêu cầu người dùng "dự thảo -> tạo mới, chọn cấp ban hành chỉ lấy từ phòng, đơn
        // vị, cơ quan của người dùng thay vì hiển thị cả cấp 1"): databasetype=duthao bỏ cấp 1
        // (agency_level/Tỉnh). Ngoại lệ: user gắn thẳng vào cấp 1 (không có cấp thấp hơn) thì giữ lại
        // để dropdown không trống. Cùng ngày mở rộng cho vbdi (yêu cầu "làm luôn với văn bản đi").
        if (DATABASE_TYPE_DUTHAO.equalsIgnoreCase(databasetype) || DATABASE_TYPE_VBDI.equalsIgnoreCase(databasetype)) {
            List<Map<String, Object>> withoutLevel1 = result.stream()
                .filter(node -> !OrganizationHierarchyRules.AGENCY_LEVEL.equals(node.get("form")))
                .toList();
            if (!withoutLevel1.isEmpty()) {
                result = new ArrayList<>(withoutLevel1);
            }
        }
        if (result.isEmpty()) {
            log.warn("Cap ban hanh (databasetype={}): user {} co {} to chuc to tien (tu {} den goc) nhung TAT CA "
                    + "deu thieu identifier - khong con phan tu nao duoc tra ve. To chuc goc/la: {}",
                databasetype, username, ancestryLeafToRoot.size(), user.getOrganizationId(),
                ancestryLeafToRoot.stream().map(Organization::getId).toList());
        }
        return Result.success(result);
    }

    // 01/10/2026: co cau hinh "1" trong dynamic_fields cua to chuc (xem OrganizationConfigKeys).
    private boolean isFlagOn(Organization org, String key) {
        Object value = dynamicDocumentFieldsService.parseDynamicFields(org.getDynamicFieldsJson()).get(key);
        return value != null && "1".equals(String.valueOf(value).trim());
    }

    // Tuong duong doc "parentids_obj" cua user hien tai (GetUserOrganizationProfileUseCase da co
    // thuat toan nay nhung chi giu 4 field ten theo form - o day can GIU CA NODE, khong chi ten,
    // moi tra du field FE can: id/identifier/form/dbpath). Tra ve thu tu LA->GOC (chinh user truoc).
    private List<Organization> walkAncestry(UUID organizationId) {
        List<Organization> chain = new ArrayList<>();
        UUID currentId = organizationId;
        Set<UUID> visited = new HashSet<>();
        while (currentId != null && visited.add(currentId)) {
            Optional<Organization> orgOpt = organizationRepository.findByIdAndDeletedOnIsNull(currentId);
            if (orgOpt.isEmpty()) {
                break;
            }
            Organization org = orgOpt.get();
            chain.add(org);
            currentId = org.getParentId();
        }
        return chain;
    }

    // Hinh dang FE dang doc that (xem ChiTietPage.jsx TDSelect fetchOptions "Cap ban hanh"): id,
    // name (-> label), identifier, identifier_other, form, dbpath. "identifier_other"/"mailin" doc
    // tu Organization.dynamicFieldsJson (dung dung muc dich da ghi trong javadoc Organization.java:
    // "cho phep luu field kieu Domino cu (mailin, clerical, identifier_other...)").
    private Map<String, Object> toLegacyNode(Organization org) {
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(org.getDynamicFieldsJson());
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", org.getId());
        node.put("name", org.getName());
        node.put("identifier", org.getIdentifier());
        node.put(IDENTIFIER_OTHER_FIELD, dynamicFields.getOrDefault(IDENTIFIER_OTHER_FIELD, ""));
        node.put("form", org.getNodeType());
        node.put("dbpath", org.getDbpath() == null ? "" : org.getDbpath());
        node.put(MAILIN_FIELD, dynamicFields.getOrDefault(MAILIN_FIELD, ""));
        return node;
    }
}
