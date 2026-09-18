package com.td.domain.documentregister;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// BL-116: "Sổ văn bản" (dungrieng/sovanban) — Danh mục riêng, entity RIÊNG (không tái dùng
// NumberingConfig/BL-104: đã đối chiếu, NumberingConfig là 1 định dạng số duy nhất/loại văn bản,
// update-only, không có khái niệm nhiều sổ/năm/đơn vị hay kiểm tra trùng — quyết định của người
// dùng khi thiết kế BL-116). `identifier`: xem PrivateCategory — cơ chế tách Sổ văn bản GIỮA CÁC
// PHÒNG/BAN trong cùng 1 tenant (hiện chỉ có đúng 1 tenant thật, cả tỉnh).
@Entity
@Table(name = "document_registers")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentRegister extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 300)
    private String name;

    // "Văn bản đi" | "Văn bản đến"
    @Column(name = "document_register_type", nullable = false, length = 50)
    private String documentRegisterType;

    @Column(nullable = false)
    private int year;

    @Column(length = 200)
    private String identifier;

    @Column(length = 300)
    private String agency;

    @Column(name = "agency_id", length = 100)
    private String agencyId;

    // Chỉ dùng khi documentRegisterType = "Văn bản đi"
    @Column(name = "document_type", length = 300)
    private String documentType;

    @Column(name = "code_notation", length = 100)
    private String codeNotation;

    @Column(name = "unit_publish", length = 300)
    private String unitPublish;

    @Column(name = "unit_publish_identifier", length = 200)
    private String unitPublishIdentifier;

    @Column(name = "unit_publish_id", length = 100)
    private String unitPublishId;

    // Chỉ dùng khi documentRegisterType = "Văn bản đến"
    @Column(length = 300)
    private String assigner;

    // Bổ sung 04/09/2026 (BL-116 tiếp, yêu cầu người dùng: "Lãnh đạo phân công tự điền mặc định khi
    // Nhập mới" — Domino thật DanhSachSo.java mỗi sổ mang theo 1 lãnh đạo mặc định, port BL-116 lần
    // đầu chỉ giữ "assigner" tên tự do). userAssignerId: tham chiếu MỀM tới app_users.id — khoá cho ô
    // chọn "Lãnh đạo" ở màn quản trị (TDSelect khớp theo id, không phải theo tên — cùng lý do
    // Organization.receiverId). userAssignerUsername: dùng TRỰC TIẾP khi tự điền form Nhập mới (khớp
    // đúng field user_assigner Domino gốc). assignerPosition: snapshot chức vụ hiển thị.
    @Column(name = "user_assigner_id")
    private UUID userAssignerId;

    @Column(name = "user_assigner_username", length = 100)
    private String userAssignerUsername;

    @Column(name = "assigner_position", length = 300)
    private String assignerPosition;

    @Column(name = "code_start_number", nullable = false)
    private int codeStartNumber = 1;

    @Column(name = "auto_increment", nullable = false)
    private boolean autoIncrement = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "code_start_numbers", columnDefinition = "TEXT")
    private String codeStartNumbers;

    @Column(name = "is_locked", nullable = false)
    private boolean isLocked = false;

    public DocumentRegister(String name, String documentRegisterType, int year, String identifier,
                             String agency, String agencyId, String documentType, String codeNotation,
                             String unitPublish, String unitPublishIdentifier, String unitPublishId,
                             String assigner, Integer codeStartNumber, Boolean autoIncrement,
                             Boolean isDefault, String codeStartNumbers) {
        this(name, documentRegisterType, year, identifier, agency, agencyId, documentType, codeNotation,
            unitPublish, unitPublishIdentifier, unitPublishId, assigner, codeStartNumber, autoIncrement,
            isDefault, codeStartNumbers, null, null, null);
    }

    // Bổ sung 04/09/2026 (BL-116 tiếp — xem javadoc field userAssignerId/userAssignerUsername/
    // assignerPosition): overload đầy đủ, nối THÊM 3 tham số ở CUỐI (không chen giữa) để KHÔNG phải
    // sửa lại thứ tự tham số ở các call site cũ — constructor 16-tham số ở trên giữ nguyên, gọi
    // xuống đây với 3 giá trị null.
    public DocumentRegister(String name, String documentRegisterType, int year, String identifier,
                             String agency, String agencyId, String documentType, String codeNotation,
                             String unitPublish, String unitPublishIdentifier, String unitPublishId,
                             String assigner, Integer codeStartNumber, Boolean autoIncrement,
                             Boolean isDefault, String codeStartNumbers, UUID userAssignerId,
                             String userAssignerUsername, String assignerPosition) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.documentRegisterType = documentRegisterType;
        this.year = year;
        this.identifier = identifier;
        this.agency = agency;
        this.agencyId = agencyId;
        this.documentType = documentType;
        this.codeNotation = codeNotation;
        this.unitPublish = unitPublish;
        this.unitPublishIdentifier = unitPublishIdentifier;
        this.unitPublishId = unitPublishId;
        this.assigner = assigner;
        this.userAssignerId = userAssignerId;
        this.userAssignerUsername = userAssignerUsername;
        this.assignerPosition = assignerPosition;
        this.codeStartNumber = (codeStartNumber != null) ? codeStartNumber : 1;
        this.autoIncrement = (autoIncrement != null) ? autoIncrement : true;
        this.isDefault = (isDefault != null) && isDefault;
        this.codeStartNumbers = codeStartNumbers;
        this.isLocked = false;
    }

    // Bổ sung 04/09/2026 - xem javadoc constructor đầy đủ ở trên. KHÔNG có overload 16-tham số cũ
    // gọi xuống đây với null — sẽ XOÁ MẤT userAssignerId/userAssignerUsername/assignerPosition mỗi
    // lần cập nhật, vì 3 field này (giống documentType/unitPublish/assigner ngay dưới) dùng quy ước
    // GHI ĐÈ VÔ ĐIỀU KIỆN (không null-check) — cho phép xoá về rỗng/null có chủ đích. Caller DUY NHẤT
    // (UpdateDocumentRegisterUseCase) phải luôn truyền đủ 19 tham số.
    public DocumentRegister update(String name, String documentRegisterType, Integer year, String identifier,
                                    String agency, String agencyId, String documentType, String codeNotation,
                                    String unitPublish, String unitPublishIdentifier, String unitPublishId,
                                    String assigner, Integer codeStartNumber, Boolean autoIncrement,
                                    Boolean isDefault, String codeStartNumbers, UUID userAssignerId,
                                    String userAssignerUsername, String assignerPosition) {
        if (name != null)                 this.name = name;
        if (documentRegisterType != null) this.documentRegisterType = documentRegisterType;
        if (year != null)                 this.year = year;
        if (identifier != null)           this.identifier = identifier;
        if (agency != null)               this.agency = agency;
        if (agencyId != null)             this.agencyId = agencyId;
        this.documentType           = documentType;
        this.codeNotation           = codeNotation;
        this.unitPublish            = unitPublish;
        this.userAssignerId         = userAssignerId;
        this.userAssignerUsername   = userAssignerUsername;
        this.assignerPosition       = assignerPosition;
        this.unitPublishIdentifier  = unitPublishIdentifier;
        this.unitPublishId          = unitPublishId;
        this.assigner               = assigner;
        if (codeStartNumber != null)      this.codeStartNumber = codeStartNumber;
        if (autoIncrement != null)        this.autoIncrement = autoIncrement;
        if (isDefault != null)            this.isDefault = isDefault;
        if (codeStartNumbers != null)     this.codeStartNumbers = codeStartNumbers;
        return this;
    }

    public void setLocked(boolean locked) {
        this.isLocked = locked;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    // BL-210/211 (Nhập mới VBDen): số văn bản đến scope theo SỔ ĐĂNG KÝ (không phải counter phẳng
    // theo documentType — đúng phát hiện đã ghi ở docs/migration-backlog/05-review-p0-p1-bl210-vs-
    // domino.md mục 4 #1). Mirror đúng thuật toán Domino thật (LuuVanBanDen.java `tang_so_cho_so_van_ban`
    // / danhmuc/DanhSachSo.java `findMaxNumber`): số tiếp theo = max(codeStartNumbers đã phát) + 1 nếu
    // sổ đã từng phát số, ngược lại dùng codeStartNumber hiện tại; giữ tối đa 500 số gần nhất (giống
    // Domino) để hỗ trợ kiểm tra trùng số về sau.
    private static final int MAX_ISSUED_NUMBERS_HISTORY = 500;

    /** Chỉ TÍNH số kế tiếp, KHÔNG ghi nhận/thay đổi trạng thái sổ — dùng để gợi ý số cho FE. */
    public int peekNextNumber() {
        return computeNextNumber();
    }

    /**
     * Ghi nhận 1 số vừa được phát cho 1 văn bản đến mới — nâng {@code codeStartNumber} và thêm vào
     * lịch sử {@code codeStartNumbers}. Gọi đúng 1 lần khi tạo mới thành công (mirror lời gọi vô điều
     * kiện {@code tang_so_cho_so_van_ban} phía Domino — kể cả sổ tắt tự động đánh số vẫn ghi nhận số đã
     * nhập thủ công vào lịch sử để phục vụ kiểm tra trùng).
     */
    public void recordIssuedNumber(int number) {
        if (isLocked) {
            throw new IllegalStateException("Sổ văn bản '" + name + "' đã khoá, không thể ghi nhận số mới");
        }
        appendIssuedNumber(number);
        this.codeStartNumber = number + 1;
    }

    private int computeNextNumber() {
        if (codeStartNumbers == null || codeStartNumbers.isBlank()) {
            return codeStartNumber;
        }
        int max = 0;
        boolean found = false;
        for (String part : codeStartNumbers.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                int value = Integer.parseInt(part.trim());
                if (!found || value > max) {
                    max = value;
                    found = true;
                }
            } catch (NumberFormatException ignored) {
                // Bỏ qua phần tử không phải số — khớp findMaxNumber(...) phía Domino (nuốt lỗi parse)
            }
        }
        return found ? max + 1 : codeStartNumber;
    }

    private void appendIssuedNumber(int issued) {
        List<String> history = new ArrayList<>();
        if (codeStartNumbers != null && !codeStartNumbers.isBlank()) {
            for (String part : codeStartNumbers.split(";")) {
                if (!part.isBlank()) {
                    history.add(part.trim());
                }
            }
        }
        history.add(String.valueOf(issued));
        while (history.size() > MAX_ISSUED_NUMBERS_HISTORY) {
            history.remove(0);
        }
        this.codeStartNumbers = String.join(";", history);
    }
}
