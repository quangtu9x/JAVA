package com.td.application.documents;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * N03/N13 (VBDen — hàng đợi/hạn xử lý theo tổ chức): tổ hợp điều kiện dùng chung cho 11 view Domino
 * đọc trực tiếp từ {@code docs/views_domino/view_vanbanden.dxl} (export thiết kế View thật, gồm
 * $Formula + công thức cột phân loại) — {@code captrengiao}, {@code dennoibo}, {@code quamangnoibo},
 * {@code lienthong}, {@code dendientu}, {@code phaixuly_cuatochuc}, {@code quahan_cuatochuc},
 * {@code saptoihan_cuatochuc}, {@code denhan_cuatochuc}, {@code daxuly_quahan_cuatochuc},
 * {@code daxuly_dunghan_cuatochuc}. Thay vì 1 method repository riêng cho mỗi tổ hợp (cách Slice 2/3
 * đã làm — không mở rộng được khi có thêm điều kiện), gom thành 1 filter object.
 *
 * <p>{@code status} luôn xử lý qua cột tĩnh {@code documents.status} (không phải {@code dynamic_fields}
 * JSONB) — nếu {@code includeFieldValues}/{@code excludeFieldValues} có key {@code "status"}, tầng
 * repository tự biết đọc cột tĩnh; mọi field khác (vd. {@code internal}, {@code document_internet},
 * {@code Reply}, {@code document_type}) được coi là field động, so khớp qua
 * {@code dynamic_fields->>'fieldName'}.</p>
 */
@Data
@Builder(toBuilder = true)
public class VbdenOrgViewFilter {

    /** Field(s) JSON-array containment cho khoá tổ chức (OR — tái dùng {@code appendAnyFieldContainsPredicate}). */
    private List<String> orgFieldNames;
    private String orgValue;

    /**
     * DT-002 (25/08/2026): field động có giá trị là DANH SÁCH username nối bằng {@code ";"} lưu dạng
     * scalar (vd. {@code user_other_processed_handler = "a;b"} — lịch sử người đã chuyển xử lý,
     * {@code XuLyDuThaoUseCase} ghi mirror {@code DraftProcesses}). So khớp THEO THÀNH VIÊN
     * ({@code orgValue} trùng 1 phần tử phân cách bởi {@code ";"}, case-insensitive — tương đương
     * {@code @Explode(field; ";")} + {@code sortnocase='true'} của cột Domino), KHÁC
     * {@link #orgFieldNames} (containment JSON-array / equality TOÀN chuỗi — sẽ KHÔNG khớp khi dữ
     * liệu là {@code "a;b"} mà {@code orgValue}={@code "a"}). OR giữa các field; đi kèm
     * {@link #orgValue}. Cần tầng repository hỗ trợ ({@code appendAnyListFieldMemberPredicate})
     * — không phải mọi implementor của filter này đều xử lý.
     */
    private List<String> orgListFieldNames;

    /** field → giá trị phải nằm TRONG danh sách này (IN). */
    private Map<String, List<String>> includeFieldValues;
    /** field → giá trị KHÔNG được nằm trong danh sách này (NOT IN). */
    private Map<String, List<String>> excludeFieldValues;

    /**
     * 01/09/2026 ("tìm kiếm nâng cao" — {@code CollapseVBden.jsx}, xem
     * docs/analysis/02-checklist-port-vbden-con-lai.md mục 1.3/Phần 4): field → danh sách giá trị,
     * field đó PHẢI chứa ÍT NHẤT MỘT giá trị trong danh sách (OR — tái dùng công thức containment
     * JSON-array-HOẶC-scalar-equality của {@code appendAnyFieldContainsPredicate}, KHÁC
     * {@link #includeFieldValues} vốn chỉ so khớp SCALAR qua {@code ->>'field'} — sẽ không khớp nếu
     * field là mảng, vd. {@code unit_process}/{@code user_process}). Nhiều field khác nhau trong Map
     * này kết hợp AND với nhau (mỗi field 1 điều kiện riêng) — KHÁC {@link #orgFieldNames}/
     * {@link #orgValue} (OR nhiều field CHO CÙNG 1 giá trị khoá tổ chức/cá nhân, dùng cho khoá CHÍNH
     * của view — field này dùng cho các điều kiện LỌC THÊM, độc lập với khoá chính).
     */
    private Map<String, List<String>> containsAnyFieldValues;


    /**
     * 19/10/2026: field → chuỗi con, khớp ILIKE {@code %giá trị%} (không phân biệt hoa thường) — cho ô
     * nhập TỰ DO của bộ lọc nâng cao (vd "Cơ quan ban hành", "Số, ký hiệu", "Ký hiệu/Tiêu đề hồ sơ") để
     * không phải gõ đúng nguyên giá trị như {@link #containsAnyFieldValues}. Nhiều field kết hợp AND.
     */
    private Map<String, String> textContainsFieldValues;

    /** Khoảng ngày cho {@code deadline_date} (field động) — {@code null} ở 1 đầu = không giới hạn đầu đó. */
    private LocalDate minDeadline;
    private LocalDate maxDeadline;

    /**
     * 31/08/2026 (port "toanbovanban" — màn "Toàn bộ văn bản" mặc định, xem
     * docs/analysis/02-checklist-port-vbden-con-lai.md): khoảng ngày cho {@code received_date} (field
     * động — RangePicker "khoảng ngày nhận văn bản" ở FE, hiện chỉ dùng bởi view {@code toanbovanban}).
     * TÁCH RIÊNG khỏi {@link #minDeadline}/{@link #maxDeadline} dù cùng công thức xử lý định dạng
     * {@code dd/MM/yyyy} (xem {@code appendReceivedDateInRangePredicate}) — 2 field Domino khác nhau,
     * không dùng chung 1 cặp min/max để tránh nhầm lẫn ngữ nghĩa giữa 2 view khác nhau.
     */
    private LocalDate minReceivedDate;
    private LocalDate maxReceivedDate;

    /**
     * Riêng cho {@code phaixuly_cuatochuc}: {@code (deadline_date!="" | documentreply="1")} — không
     * gộp chung được vào {@code includeFieldValues} vì là OR giữa "field khác rỗng" và "field khác
     * bằng 1 giá trị cụ thể", không phải IN-list trên cùng 1 field.
     */
    @Builder.Default
    private boolean requireDeadlineOrDocumentReply = false;

    /**
     * Riêng cho {@code chophanxuly} (N05, dxl 253-278): {@code user_process=""} — field động PHẢI
     * rỗng/vắng mặt (mảng JSON rỗng hoặc không tồn tại key). Khác {@code excludeFieldValues} (vốn so
     * khớp "khác 1 danh sách giá trị cụ thể") vì đây là kiểm tra "không có bất kỳ giá trị nào" trên
     * field kiểu mảng — {@code ChuyenXuLyVbdenUseCase} luôn ghi field này dạng {@code List<String>}
     * (rỗng khi hết người xử lý), không bao giờ xoá hẳn key, nên "rỗng" và "vắng mặt" đều phải coi
     * là khớp điều kiện Domino {@code @Field=""}.
     */
    private String requireEmptyFieldName;

    /**
     * Slice 7 (tìm kiếm/lọc): {@code keyword} tìm trên {@code keywordColumns} (field động scalar, vd.
     * {@code document_number}/{@code code}/{@code subject}) — xem {@code ViewQueryRequest.keyword}.
     * Rỗng/null ở keyword HOẶC keywordColumns = không lọc, giữ nguyên hành vi trước Slice 7.
     */
    private String keyword;
    private List<String> keywordColumns;

    /**
     * Slice 9 ({@code sovanban} — "Vào sổ văn bản đến", đọc từ {@code docs/views_domino/
     * view_vanbanden_so.dxl}): field động PHẢI khác rỗng/vắng mặt — ngược lại {@link #requireEmptyFieldName}.
     * Dùng cho {@code document_register_id!=""} (tương đương "đã vào sổ" trong kiến trúc Java — khác
     * Domino có hẳn 1 database {@code vbden_so.nsf} riêng chỉ chứa bản ghi đã vào sổ, Java không tách
     * bảng nên phải lọc field này thay thế).
     */
    private String requireNonEmptyFieldName;

    /** Slice 9: khoảng ngày cho {@code documents.created_on} (cột tĩnh, KHÁC {@code minDeadline}/
     * {@code maxDeadline} là field động) — dùng cho {@code homqua}/{@code trongngay}/{@code trongthang}/
     * {@code trongtuan} ({@code @Date(@Created)} trong DXL, tính động tại thời điểm query thay vì
     * hard-code như formula Domino tự sinh lại hàng ngày). */
    private LocalDate minCreatedOn;
    private LocalDate maxCreatedOn;

    /**
     * 09/09/2026 ("tìm kiếm nâng cao" hồ sơ công việc — cutover {@code qlvb/search?databasetype=hosocv},
     * {@code CollapseHoSoCV.jsx}): khoảng ngày cho {@code end_date} (field động — "Ngày xử lý"/ngày
     * kết thúc xử lý hồ sơ) — TÁCH RIÊNG khỏi {@link #minDeadline}/{@link #minReceivedDate} dù cùng
     * công thức xử lý {@code dd/MM/yyyy} (đúng tiền lệ mỗi field ngày động 1 cặp min/max riêng, xem
     * javadoc {@link #minReceivedDate}).
     */
    private LocalDate minEndDate;
    private LocalDate maxEndDate;

    /** 09/09/2026 (cùng đợt): khoảng ngày cho {@code storaged_date} (field động — "Ngày chuyển lưu
     * trữ" hồ sơ). */
    private LocalDate minStoragedDate;
    private LocalDate maxStoragedDate;

    /**
     * Slice 9: sắp xếp theo field động (thay {@code ORDER BY last_modified_on DESC} mặc định) —
     * {@code sortType}: {@code "integer"} (vd. {@code document_number}) hoặc {@code "date"} (vd.
     * {@code received_date}, chuỗi ISO); {@code null}/để trống = giữ text thô. {@code sortDescending}
     * áp dụng cho CẢ 2 field sort (primary/secondary) — {@code sovanban} (màn "Vào sổ văn bản đến")
     * là view DUY NHẤT hiện có UI đảo chiều thật (nút sort tăng/giảm ở FE), các view khác trong DXL
     * đã đọc đều cố định giảm dần.
     */
    private String primarySortField;
    private String primarySortType;
    private String secondarySortField;
    private String secondarySortType;
    @Builder.Default
    private boolean sortDescending = true;

    /**
     * 02/10/2026 ({@code theodoi_sort_thoihan}: hạn xử lý TĂNG, ngày đến GIẢM): chiều riêng cho
     * {@code secondarySortField}. {@code null} = cùng chiều {@link #sortDescending}.
     */
    private Boolean secondarySortDescending;

    /**
     * 01/10/2026 (yêu cầu người dùng "vbden/xuly/quamangnoibo — sắp xếp mới nhất lên trên"): {@code true}
     * = {@code ORDER BY created_on DESC} (mới ĐẾN nhất trước) thay vì {@code last_modified_on} mặc định
     * (văn bản cũ chỉ cần bị sửa là nhảy lên đầu). Dùng cho 5 view màn "Chờ vào sổ" — văn bản nhận qua
     * mạng có {@code created_on} = lúc đến ({@code received_date} lẫn 2 dạng có/không giờ nên không
     * sort theo field đó được, xem {@code dynamicSortExpression}). Bị bỏ qua khi có {@code primarySortField}.
     */
    @Builder.Default
    private boolean newestCreatedFirst = false;

    /**
     * 01/10/2026 (hồ sơ "Tìm kiếm nâng cao" → Trạng thái phối hợp = "Chưa phối hợp", Domino
     * {@code fieldnot_X}: {@code !@Contains(X; value)}): field → giá trị mà field KHÔNG được chứa
     * (không là phần tử mảng JSON, không bằng scalar, không là thành viên chuỗi {@code "a;b"}). Hiện
     * chỉ {@code PostgresHoSoCVDocumentRepository} áp dụng.
     */
    private Map<String, List<String>> excludeMemberFieldValues;

    /**
     * Bổ sung 14/09/2026 (port route {@code vbden/thungrac/thungrac} — chuyển {@code qlvb/vbden/
     * GetViewColl} về dùng chung {@code GetView}, xem javadoc {@code ViewQueryService#VIEW_VBDEN_TRASH}):
     * {@code true} = đảo ngược điều kiện mặc định — chỉ lấy bản ghi ĐÃ soft-delete
     * ({@code deleted_on IS NOT NULL}) thay vì {@code IS NULL}. Mặc định {@code false} giữ NGUYÊN
     * hành vi mọi filter hiện có (không callsite nào khác cần thay đổi).
     */
    @Builder.Default
    private boolean deletedOnly = false;

    /**
     * Bổ sung 30/09/2026 (báo lỗi người dùng: "Lấy lại văn bản" — văn bản đến đã mất khỏi danh sách
     * văn thư nhưng vẫn hiện ở {@code vbden/xuly/dencoquan}): mặc định {@code false} = LOẠI văn bản
     * đã thu hồi ({@code is_revoke="1"}) — tương đương Domino đổi {@code Form} sang
     * {@code DocumentIn_Deleted} khi thu hồi/lấy lại, khiến văn bản rơi khỏi MỌI view
     * {@code Form="DocumentIn"}. {@code true} chỉ dùng cho tra cứu KHÔNG phải danh sách (vd mở chi
     * tiết văn bản đã bị thu hồi). Filter có {@code includeFieldValues} chứa {@code is_revoke} (view
     * "Bị thu hồi") tự động không bị loại.
     */
    @Builder.Default
    private boolean includeRevoked = false;
}
