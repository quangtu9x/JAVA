package com.td.application.sharedcore;

import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gap #3 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026) — số lượng
 * văn bản cần xử lý theo TỪNG username kiêm nhiệm, dùng để hiển thị badge số ở chuông thông báo khi
 * người dùng đang xem qua vai kiêm nhiệm. Port từ Domino {@code SoLuongCanXuLyKiemNhiem.java}
 * ({@code MapsSoLuongCanXuLy.VBDEN_KHAC}/{@code DUTHAO_KHAC}/{@code PHIEUTRINH_KHAC}).
 *
 * <p><b>Phạm vi port — 7/12 field gốc (cập nhật 09/09/2026, sau khi port module phieutrinhtd)</b>:
 * <ul>
 *   <li>{@code VBDEN_KHAC} (1 field): view {@code "chutri"} (dbType {@code vbden}) → key
 *       {@code "user_process"} — view này ĐÃ CÓ sẵn ở {@link ViewQueryService} (dùng chung với
 *       {@code VIEW_DOCUMENT_CHO_XU_LY}).</li>
 *   <li>{@code DUTHAO_KHAC} (3 field): view {@code "vwsigner_submit"} → {@code "drafting_user_signer_waiting"},
 *       {@code "vwuser_process_handler_submit"} → {@code "drafting_user_process_handler_submit"},
 *       {@code "vwsigner_documentout_return"} → {@code "draft_signer_documentout_return"} — cả 3 view
 *       (dbType {@code duthao}) ĐỀU ĐÃ CÓ sẵn ở {@link ViewQueryService}.</li>
 *   <li>{@code PHIEUTRINH_KHAC} (8 field gốc) — <b>KHÔNG port đúng nguyên trạng được, có lý do kỹ
 *       thuật cứng</b> (xem {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md}
 *       mục 1/8): 8 field này trỏ tới view {@code dossier_by_*} của database Domino
 *       <b>{@code phieutrinh}</b> (KHÔNG có hậu tố "TD") — 1 module Domino KHÁC module thật đang chạy
 *       ({@code phieutrinhtd}), không có menu/FE/DXL nào để đối chiếu công thức view thật — không có
 *       gì để port đúng nghĩa. Đã port THAY THẾ 3/8 field bằng view THẬT của {@code phieutrinhtd} có
 *       ý nghĩa gần nhất (ánh xạ theo tên, KHÔNG suy đoán field/view không có bằng chứng):
 *       <ul>
 *         <li>{@code pt_chopheduyet} ("chờ phê duyệt") ← {@code VIEW_PHIEUTRINHTD_CHODUYET}
 *             (chờ duyệt cấp 1/2, {@code lanhdao1}/{@code lanhdao2}).</li>
 *         <li>{@code pt_chochoykien} ("chờ cho ý kiến") ← {@code VIEW_PHIEUTRINHTD_THAMKHAO}
 *             (được xin ý kiến, chưa phản hồi).</li>
 *         <li>{@code pt_tralai} ("bị trả lại") ← tổng {@code VIEW_PHIEUTRINHTD_KHONGDUYET} +
 *             {@code VIEW_PHIEUTRINHTD_KHONGTHONGQUA} (2 trạng thái "bị từ chối/trả lại" của
 *             {@code phieutrinhtd}).</li>
 *       </ul>
 *       <b>5 field còn lại VẮNG MẶT có chủ đích</b> ({@code pt_choduyettrinhtiep}/{@code pt_chorasoat}/
 *       {@code pt_chodvdongtrinh}/{@code pt_chodvchoykien}/{@code pt_chokytat}) — KHÔNG có khái niệm
 *       tương đương trong {@code phieutrinhtd} (không có "rà soát"/"đơn vị đồng trình"/"đơn vị cho ý
 *       kiến"; "ký tắt" tồn tại ở field {@code nguoikynhayhide} nhưng route/UI FE gốc đang tắt hoàn
 *       toàn, không có nghiệp vụ thật để đếm) — KHÔNG suy đoán/giả lập, không trả 0 giả (tránh FE hiểu
 *       nhầm "đã xử lý xong"). Việc ánh xạ thay thế 3 field trên cần XÁC NHẬN NGHIỆP VỤ trước khi coi
 *       là chính thức (checklist 21 Phần 2 Nhóm 2).</li>
 * </ul>
 *
 * <p>Không dùng Redis cache như Domino gốc ({@code TDRedisConnection.setCacheTimeToLive}, TTL 60
 * phút) — quy mô nhỏ (7 query COUNT, pageSize=1, JPA vẫn tính COUNT riêng), chưa có bằng chứng cần
 * cache ngay ở lượt port đầu tiên; bổ sung sau nếu đo được cần thiết.</p>
 */
@Service
@RequiredArgsConstructor
public class GetKiemNhiemPendingCountUseCase {

    private static final String DB_VBDEN = "vbden";
    private static final String DB_DUTHAO = "duthao";
    private static final String DB_PHIEUTRINHTD = "phieutrinhtd";

    static final String KEY_USER_PROCESS = "user_process";
    static final String KEY_DRAFTING_USER_SIGNER_WAITING = "drafting_user_signer_waiting";
    static final String KEY_DRAFTING_USER_PROCESS_HANDLER_SUBMIT = "drafting_user_process_handler_submit";
    static final String KEY_DRAFT_SIGNER_DOCUMENTOUT_RETURN = "draft_signer_documentout_return";
    static final String KEY_PT_CHOPHEDUYET = "pt_chopheduyet";
    static final String KEY_PT_CHOCHOYKIEN = "pt_chochoykien";
    static final String KEY_PT_TRALAI = "pt_tralai";

    private final ViewQueryService viewQueryService;

    public Map<String, Object> execute(String username) {
        Map<String, Object> counts = new LinkedHashMap<>();
        if (username == null || username.isBlank()) {
            counts.put(KEY_USER_PROCESS, 0L);
            counts.put(KEY_DRAFTING_USER_SIGNER_WAITING, 0L);
            counts.put(KEY_DRAFTING_USER_PROCESS_HANDLER_SUBMIT, 0L);
            counts.put(KEY_DRAFT_SIGNER_DOCUMENTOUT_RETURN, 0L);
            counts.put(KEY_PT_CHOPHEDUYET, 0L);
            counts.put(KEY_PT_CHOCHOYKIEN, 0L);
            counts.put(KEY_PT_TRALAI, 0L);
            return counts;
        }

        counts.put(KEY_USER_PROCESS, countByView(DB_VBDEN, "chutri", username));
        counts.put(KEY_DRAFTING_USER_SIGNER_WAITING, countByView(DB_DUTHAO, "vwsigner_submit", username));
        counts.put(KEY_DRAFTING_USER_PROCESS_HANDLER_SUBMIT,
            countByView(DB_DUTHAO, "vwuser_process_handler_submit", username));
        counts.put(KEY_DRAFT_SIGNER_DOCUMENTOUT_RETURN,
            countByView(DB_DUTHAO, "vwsigner_documentout_return", username));
        counts.put(KEY_PT_CHOPHEDUYET, countByView(DB_PHIEUTRINHTD, "phieutrinhtd_choduyet", username));
        counts.put(KEY_PT_CHOCHOYKIEN, countByView(DB_PHIEUTRINHTD, "phieutrinhtd_thamkhao", username));
        counts.put(KEY_PT_TRALAI,
            countByView(DB_PHIEUTRINHTD, "phieutrinhtd_khongduyet", username)
                + countByView(DB_PHIEUTRINHTD, "phieutrinhtd_khongthongqua", username));
        return counts;
    }

    private long countByView(String dbType, String viewname, String username) {
        var request = new ViewQueryRequest();
        request.setViewname(viewname);
        request.setKeys(List.of(username));
        request.setPageNumber(1);
        request.setPageSize(1);
        return viewQueryService.execute(dbType, request).getTotalItems();
    }
}
