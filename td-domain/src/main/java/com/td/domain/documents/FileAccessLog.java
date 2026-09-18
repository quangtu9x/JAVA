package com.td.domain.documents;

import com.td.domain.common.contracts.BaseEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 09/09/2026 (yêu cầu người dùng "log xem tệp đính kèm cần thiết cho các module khác nữa... tối ưu
 * lưu trữ, tránh tạo nhiều bản ghi"): log xem/tải tệp đính kèm — GENERIC cho MỌI {@code module}
 * (vbdi/vbden/duthao/hosocv/...), bảng vật lý riêng {@code file_access_logs} (migration
 * {@code V1.0.56} tạo bảng với cột {@code action} đơn, {@code V1.0.58} gộp lại thành mảng
 * {@code actions} riêng, {@code V1.0.59} chuyển {@code actions} vào cột chung {@code dynamic_fields}
 * — {@code V1.0.56}/{@code V1.0.58} đã áp dụng thật trước khi đổi ý nên KHÔNG được sửa lại nội dung,
 * mọi lần đổi ý sau đều PHẢI là migration mới, xem javadoc {@code V1.0.59}).
 *
 * <p><b>Model UPSERT, KHÔNG append-only</b> (khác {@code VbdiUserActionLog}/{@code
 * VbdiSendLogDocument}): unique key {@code (module, documentId, fileId, username)} — 1 dòng DUY
 * NHẤT/tổ hợp (tệp, người dùng) BẤT KỂ loại thao tác, {@code firstAccessedOn} bất biến,
 * {@code lastAccessedOn}/{@code accessCount} cập nhật MỖI LẦN xem/tải lại. Không có setter công khai
 * — giá trị THẬT luôn ghi qua UPSERT native query ({@code FileAccessLogRepository#recordAccess},
 * {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}), KHÔNG qua JPA save thông thường — đúng
 * tiền lệ {@link com.td.domain.numbering.NumberingCounter} (cùng kỹ thuật atomic UPSERT, tránh
 * lost-update dưới concurrent access, vd 2 tab cùng xem 1 tệp đồng thời). Constructor đầy đủ tham số
 * bên dưới CHỈ phục vụ test/mapping thủ công (vd fixture cho {@code ListFileAccessLogUseCaseTest}),
 * KHÔNG dùng trong luồng ghi thật.</p>
 *
 * <p><b>Vì sao KHÔNG dùng model append-only</b>: xem/tải là hành động TẦN SUẤT CAO (1 người có thể
 * mở lại cùng 1 tệp nhiều lần) — nếu 1 dòng/1 sự kiện (như {@code VbdiUserActionLog}), bảng sẽ phình
 * theo SỐ LƯỢT XEM (không giới hạn), khác hẳn nhu cầu thực tế chỉ cần biết "ai đã xem tệp nào, lần
 * đầu/lần cuối khi nào" — KHÔNG cần biết TỪNG lượt xem riêng lẻ. Model UPSERT giữ số dòng bị CHẶN
 * TRÊN bởi (số tệp × số người dùng) của MỌI văn bản — tối ưu cả lưu trữ (không phình vô hạn) lẫn
 * hiệu năng ghi (1 câu SQL nguyên tử/lần, dùng đúng unique index, không cần SELECT-rồi-UPDATE 2 bước
 * tốn thêm 1 round-trip + nguy cơ race condition).</p>
 *
 * <p><b>{@code dynamicFieldsJson} — DÙNG LẠI ĐÚNG kiến trúc {@code dynamic_fields} chung của mọi
 * bảng document khác</b> (BL-101 Dynamic Data Engine, xem {@code VbdiDocument#dynamicFieldsJson},
 * {@code VbdenResDocument}, ...) THAY VÌ tự chế 1 cột JSONB riêng tên "actions" — tránh lặp lại kiến
 * trúc mỗi bảng mới (xem quy ước chung ARCHITECTURE.md § Best Practices "Dynamic fields / JSONB").
 * Field nghiệp vụ biến thiên DUY NHẤT hiện có trong đây là khoá {@code "actions"}: mảng các loại
 * thao tác đã xảy ra trên tổ hợp (module, documentId, fileId, username), vd
 * {@code {"actions":["view","download"]}} — APPEND loại mới vào mảng nếu chưa có (xem
 * {@code PostgresFileAccessLogRepository} — merge bằng {@code jsonb_set}/{@code @>}/{@code ||} ngay
 * trong câu UPSERT, không SELECT-rồi-parse). Parse qua {@code DynamicDocumentFieldsService} ở
 * application layer — entity KHÔNG tự parse (đúng tiền lệ mọi entity có {@code dynamic_fields}
 * khác). {@code accessCount} đếm TỔNG mọi loại thao tác cộng dồn (không tách riêng theo action) —
 * FE nhóm 1 dòng/người dùng, hiển thị các phần tử {@code actions} dạng nhiều tag thay vì 1 dòng/action.</p>
 */
@Entity
@Table(name = "file_access_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileAccessLog extends BaseEntity implements IAggregateRoot {

    /** Module sở hữu {@code documentId} (vbdi/vbden/duthao/hosocv/...) — tránh nhầm lẫn hiển thị FE,
     * xem javadoc lớp. Client-supplied (KHÔNG security-sensitive — chỉ để phân loại hiển thị). */
    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    /** Tên gốc tệp — LÀM MỚI mỗi lần ghi (server-authoritative qua GetFileUseCase, không tin client). */
    @Column(name = "file_name", length = 500)
    private String fileName;

    /** Field nghiệp vụ biến thiên (BL-101 Dynamic Data Engine) — hiện chỉ chứa khoá {@code "actions"}
     * (mảng loại thao tác, vd {@code {"actions":["view","download"]}}), xem javadoc lớp. Raw JSON
     * string — parse qua {@code DynamicDocumentFieldsService} ở application layer, entity KHÔNG tự
     * parse (đúng tiền lệ {@code VbdiDocument#dynamicFieldsJson}). */
    @Column(name = "dynamic_fields", columnDefinition = "jsonb", nullable = false)
    private String dynamicFieldsJson = "{}";

    /** Username người thực hiện — SERVER-AUTHORITATIVE qua CurrentUserProvider. */
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /** Tên đầy đủ — server-authoritative qua UserDisplayInfoService, LÀM MỚI mỗi lần ghi. */
    @Column(name = "fullname", length = 300)
    private String fullname;

    /** Cơ quan/đơn vị/phòng ban (nối " / ") — server-authoritative qua UserDisplayInfoService, LÀM MỚI mỗi lần ghi. */
    @Column(name = "unit", length = 500)
    private String unit;

    @Column(name = "first_accessed_on", nullable = false)
    private LocalDateTime firstAccessedOn;

    @Column(name = "last_accessed_on", nullable = false)
    private LocalDateTime lastAccessedOn;

    @Column(name = "access_count", nullable = false)
    private int accessCount;

    /** CHỈ dùng cho test/mapping thủ công — xem javadoc lớp (luồng ghi thật đi qua UPSERT native query).
     * {@code dynamicFieldsJson} truyền vào phải LÀ JSON object hợp lệ (vd
     * {@code "{\"actions\":[\"view\"]}"}), không tự bọc. */
    public FileAccessLog(String module, UUID documentId, UUID fileId, String fileName, String dynamicFieldsJson,
            String username, String fullname, String unit,
            LocalDateTime firstAccessedOn, LocalDateTime lastAccessedOn, int accessCount) {
        this.module = module;
        this.documentId = documentId;
        this.fileId = fileId;
        this.fileName = fileName;
        this.dynamicFieldsJson = dynamicFieldsJson;
        this.username = username;
        this.fullname = fullname;
        this.unit = unit;
        this.firstAccessedOn = firstAccessedOn;
        this.lastAccessedOn = lastAccessedOn;
        this.accessCount = accessCount;
    }
}
