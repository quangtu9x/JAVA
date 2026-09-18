package com.td.application.hosocv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosocv.HoSoCVDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3 ({@code docs/analysis/08-...md} mục 2.2, rủi ro #6;
 * {@code docs/analysis/13-...md} Phần 3 "Đồng bộ hosocv → hosoluutru"): "Chuyển lưu trữ"
 * — {@code storaged: ""/"0" → "2"} (đã chuyển, chờ kho tiếp nhận).
 *
 * <p><b>Mở rộng 08/09/2026 — bỏ giới hạn cũ, nối với module {@code hosoluutru} đã port</b> (xem
 * {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md} mục 5.1/5.6/8.4): đã xác nhận
 * chính xác (đọc trực tiếp {@code XuLyHoSoLuuTru.java}) cơ chế đồng bộ THẬT là 3 lệnh POST tường minh
 * từ FE, KHÔNG có trigger/agent Domino ẩn — bước 1 chính là API
 * {@code qlvb/hosoluutru/chuyenluutru?id=<hosocvId>&dmhsid=<dmhsId>}, tạo bản ghi {@code hosoluutru}
 * mới (copy field + văn bản liên quan) TRƯỚC KHI set {@code storaged="2"}. Nay gọi
 * {@link TaoHoSoLuuTruTuHoSoCVUseCase} (nằm ở {@code td-application/hosoluutru}, package khác nhưng
 * cùng tầng use-case — dependency injection trực tiếp, KHÔNG qua HTTP nội bộ) trong CÙNG giao dịch
 * {@code @Transactional} này, đúng thứ tự thật (tạo hosoluutru trước, set storaged sau).</p>
 *
 * <p>Guard đúng điều kiện nút "Chuyển lưu trữ" hiển thị ở FE thật ({@code ButtonActions.jsx}):
 * {@code status="1"} (đã kết thúc) && {@code trinhlanhdaoduyet="1"} (đã duyệt) && {@code category}
 * đã chọn && {@code storaged} chưa set (rỗng hoặc {@code "0"}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChuyenLuuTruHoSoUseCase {

    private static final String DOCUMENT_TYPE = TaoHoSoCVUseCase.DOCUMENT_TYPE;
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ công việc";
    private static final String GUARD_MESSAGE =
        "Hồ sơ chưa đủ điều kiện chuyển lưu trữ (phải đã kết thúc, đã được duyệt, đã chọn danh mục hồ sơ, chưa chuyển lưu trữ)";
    private static final String PROCESS_TYPE_CHUYEN_LUU_TRU = "chuyenluutru";
    private static final String FALLBACK_USERNAME = "system";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AddHoSoCVProcessDocumentUseCase addHoSoCVProcessDocumentUseCase;
    private final com.td.application.hosoluutru.TaoHoSoLuuTruTuHoSoCVUseCase taoHoSoLuuTruTuHoSoCVUseCase;

    public Result<UUID> execute(ChuyenLuuTruHoSoRequest request) {
        if (request == null || request.getHosocvId() == null || request.getDmhsId() == null) {
            return Result.failure("Thiếu id hồ sơ công việc hoặc id danh mục hồ sơ lưu trữ");
        }
        var documentOptional = hoSoCVDocumentRepository.findById(request.getHosocvId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(documentOptional.get().getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        boolean isEnded = HoSoCVStatus.DA_KET_THUC.equals(document.getStatus());
        boolean isApproved = HoSoCVLifecycleStatus.TRINH_DA_DUYET.equals(
            stringField(fields, HoSoCVLifecycleStatus.FIELD_TRINHLANHDAODUYET));
        boolean hasCategory = !stringField(fields, HoSoCVLifecycleStatus.FIELD_CATEGORY).isBlank();
        String currentStoraged = stringField(fields, HoSoCVLifecycleStatus.FIELD_STORAGED);
        boolean notYetStoraged = currentStoraged.isBlank()
            || HoSoCVLifecycleStatus.STORAGED_CHUA_CHUYEN.equals(currentStoraged);

        if (!isEnded || !isApproved || !hasCategory || !notYetStoraged) {
            return Result.failure(GUARD_MESSAGE);
        }

        // Buoc 1 (dung thu tu that): tao ban ghi hosoluutru TRUOC KHI set storaged="2" ben hosocv -
        // that bai o day (vd. khong tim thay danh muc) thi return ngay, CHUA save gi ben hosocv (van
        // trong cung 1 giao dich @Transactional, khong can rollback thu cong).
        var taoHoSoLuuTruRequest = new com.td.application.hosoluutru.TaoHoSoLuuTruTuHoSoCVRequest();
        taoHoSoLuuTruRequest.setHosocvId(request.getHosocvId());
        taoHoSoLuuTruRequest.setDmhsId(request.getDmhsId());
        var taoHoSoLuuTruResult = taoHoSoLuuTruTuHoSoCVUseCase.execute(taoHoSoLuuTruRequest);
        if (!taoHoSoLuuTruResult.isSuccess()) {
            return Result.failure(taoHoSoLuuTruResult.getError());
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        var before = buildSnapshot(document);

        fields.put(HoSoCVLifecycleStatus.FIELD_STORAGED, HoSoCVLifecycleStatus.STORAGED_DA_CHUYEN_CHO_TIEP_NHAN);
        fields.put(HoSoCVLifecycleStatus.FIELD_STORAGED_DATE, DATE_FORMATTER.format(LocalDate.now()));

        document.replaceDynamicFields(toJson(fields));
        var saved = hoSoCVDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        appendProcessRecord(saved.getId(), actingUsername);

        return Result.success(saved.getId());
    }

    private void appendProcessRecord(UUID hosocvId, String actingUsername) {
        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle("Chuyển lưu trữ hồ sơ công việc");
            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put("hosocv_id", hosocvId.toString());
            processFields.put("process_type", PROCESS_TYPE_CHUYEN_LUU_TRU);
            processFields.put("new_value", HoSoCVLifecycleStatus.STORAGED_DA_CHUYEN_CHO_TIEP_NHAN);
            processFields.put("creator", actingUsername);
            processRequest.setFields(processFields);
            addHoSoCVProcessDocumentUseCase.execute(processRequest);
        } catch (Exception ignored) {
            // Vet xu ly la audit phu - loi ghi vet KHONG duoc lam hong thao tac chinh.
        }
    }

    private String stringField(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? "" : value.toString();
    }

    private Map<String, Object> buildSnapshot(HoSoCVDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
