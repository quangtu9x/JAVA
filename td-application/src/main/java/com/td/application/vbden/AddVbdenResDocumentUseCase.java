package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbden.VbdenResDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-210 (VBDen Core) — DoD (4), yêu cầu người dùng 05/09/2026 ("tách vbden_res sang bảng riêng"):
 * tương đương {@code AddDynamicDocumentUseCase} (BL-101 chung) nhưng lưu vào bảng vật lý ĐỘC LẬP
 * {@code vbden_res_documents} (xem {@link VbdenResDocument}) — writer DUY NHẤT của bản ghi "quá
 * trình xử lý" vbden, dùng bởi {@code ChuyenXuLyVbdenUseCase}/{@code TaoDuThaoVBdenUseCase}/
 * {@code KetThucXuLyUseCase}/{@code CapNhatKetQuaXuLyUseCase}/{@code XuLyLaiUseCase}/
 * {@code ThuHoiVanBanUseCase}/{@code LayLaiVanBanUseCase} — TẤT CẢ trước đây đều gọi
 * {@code addDynamicDocumentUseCase.execute(childRequest)} với {@code childRequest.documentType =
 * "vbden_res"}, giờ đổi sang gọi use case này (chữ ký {@link AddDynamicDocumentRequest} giữ NGUYÊN,
 * chỉ đổi use case gọi — không đổi field nào FE/caller đang gửi).
 *
 * <p>Ép cứng {@code document_type='vbden_res'} ở TẦNG NÀY (caller không tự đặt được, kể cả khi FE/
 * request cũ có gửi giá trị khác) — bảng này chỉ chứa đúng 1 loại document, đúng nguyên tắc đã áp
 * dụng ở {@code AddDraftProcessDocumentUseCase}.</p>
 *
 * <p>Khác {@code AddDynamicDocumentUseCase} 2 điểm có chủ đích (cùng lý do đã ghi ở javadoc
 * {@code AddDraftProcessDocumentUseCase}): (1) KHÔNG đụng {@code DocumentCacheService} — vết xử lý
 * là append-only, chưa có view cache danh sách nào đọc nó (mọi nơi đọc {@code vbden_res} —
 * {@code ViewQueryService}/{@code LichSuXuLyUseCase}/{@code ThuHoiVanBanUseCase} — đều query trực
 * tiếp qua {@link VbdenResDocumentRepository}, không qua cache); (2) KHÔNG tích hợp numbering (chưa
 * từng có cấu hình numbering cho {@code document_type="vbden_res"}, gọi cũng luôn no-op).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddVbdenResDocumentUseCase {

    /** Loại document DUY NHẤT được phép tồn tại trong bảng vbden_res_documents. */
    public static final String DOCUMENT_TYPE = "vbden_res";

    private static final String DEFAULT_TITLE = "Quá trình xử lý văn bản đến";
    // Giu nguyen dung default cua AddDynamicDocumentUseCase (khong doi hanh vi cu) - khong co use case
    // nao trong 7 use case goi truoc day tung setStatus() rieng cho childRequest, nen moi ban ghi
    // vbden_res tu truoc toi nay deu mang status mac dinh nay.
    private static final String DEFAULT_STATUS = "DRAFT";

    private final VbdenResDocumentRepository vbdenResDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(AddDynamicDocumentRequest request) {
        try {
            String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? DEFAULT_TITLE
                : request.getTitle().trim();
            String status = (request.getStatus() == null || request.getStatus().isBlank())
                ? DEFAULT_STATUS
                : request.getStatus().trim();

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new VbdenResDocument(title, DOCUMENT_TYPE, status, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = vbdenResDocumentRepository.save(document);
            documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo bản ghi quá trình xử lý văn bản đến (vbden_res) thất bại", ex);
            return Result.failure("Tạo bản ghi quá trình xử lý thất bại: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildSnapshot(VbdenResDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
