package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbden.VbdenResDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Sửa ý kiến" ({@code Components/SuaYkien.jsx}, mở từ popover "Quá trình xử lý"
 * ({@code QuatrinhxulyVBden.jsx}) và {@code lichsuxuly/components/ItemsList.jsx}) — yêu cầu người
 * dùng 15/09/2026 ("kiểm tra lại nghiệp vụ văn bản đến xem còn nghiệp vụ nào chưa port thì port sang
 * java và cutoverfe"). Tương đương 1 PHẦN {@code CapNhatVanBanDen.java} thật:
 * {@code ?databasetype=vbden_res&action=suanoidung&id=...&parentid=...}.
 *
 * <p><b>Đối chiếu trực tiếp Domino gốc</b>: {@code action="suanoidung"} tự nó là NO-OP tuyệt đối
 * (thân {@code case "suanoidung": break;} rỗng — comment sẵn gọi {@code capnhatykien()} nhưng bị
 * comment out). Việc SỬA THẬT xảy ra ở PREAMBLE dùng CHUNG {@code CreateOrUpdateDocument()} (chạy
 * TRƯỚC switch-case cho MỌI action, xem {@link CapNhatPhoiHopUseCase}): vì FE gửi kèm {@code id}
 * (khác mọi action khác trong module chỉ gửi {@code parentid}), hàm này KHÔNG tạo document con mới mà
 * MỞ LẠI chính bản ghi {@code vbden_res} có UNID={@code id} (databasetype của request =
 * {@code "vbden_res"} nên {@code dbDocument} trỏ thẳng database {@code vbden_res}), rồi ghi ĐÈ từng
 * field trong {@code formData} (diff qua {@code getChangedFieldDetailsNotSaveLog}) trực tiếp lên bản
 * ghi đó và lưu — KHÔNG tạo bản ghi mới, KHÔNG đụng văn bản cha.</p>
 *
 * <p><b>Thu hẹp phạm vi CÓ CHỦ ĐÍCH</b>: Domino gốc generic (chấp nhận diff BẤT KỲ field nào form gửi
 * lên), nhưng FE {@code SuaYkien.jsx} CHỈ có đúng 1 field chỉnh sửa được (TextArea {@code opinion}) —
 * port chỉ cho phép sửa field này, KHÔNG mở rộng thành generic-field-update (tránh 1 endpoint có thể
 * sửa bất kỳ field nào của bất kỳ bản ghi vbden_res nào — thu hẹp bề mặt tấn công/lỗi so với bản gốc,
 * không mất tính năng nào FE thực sự dùng).</p>
 *
 * <p><b>NGOẠI LỆ có chủ đích với thiết kế "append-only"</b> của {@link VbdenResDocument} (javadoc lớp
 * đó ghi rõ "không use case nào đọc lại 1 bản ghi hiện có để sửa, chỉ tạo mới") — đây là use case ĐẦU
 * TIÊN phá vỡ giả định đó, vì đúng theo nghiệp vụ Domino thật (đối chiếu trực tiếp, không suy đoán).
 * KHÔNG ghi {@code documentLogService.logUpdate} theo khuôn {@code before/after snapshot} như
 * {@code BusinessDocument} (không có tiền lệ nào làm vậy cho {@code VbdenResDocument} — chỉ có
 * {@code logCreate} ở {@link AddVbdenResDocumentUseCase}) — dùng {@code logUpdate} tương tự nhưng với
 * snapshot tối giản (chỉ field {@code opinion}), đủ để có audit trail mà không cần thiết kế lại.</p>
 *
 * <p><b>KHÔNG port</b> (Domino gốc có nhưng KHÔNG có tác dụng quan sát được với Postgres/JPA):
 * {@code docQTXL.replaceItemValue("parentid_send"/"identifiers"/"identifier")}/
 * {@code removeItem("reader")} — bookkeeping ACL/routing Domino-only, không có cột/field tương đương
 * cần đồng bộ ở Java (bản ghi vbden_res Java không có khái niệm reader-list).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SuaYKienVbdenUseCase {

    public static final String DOCUMENT_TYPE = "vbden_res";
    private static final String NOT_FOUND_MESSAGE = "Khong tim thay y kien";
    private static final String OPINION_FIELD = "opinion";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdenResDocumentRepository vbdenResDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(SuaYKienVbdenRequest request) {
        var recordOptional = vbdenResDocumentRepository.findById(request.getId());
        if (recordOptional.isEmpty() || recordOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var record = recordOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(record.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(record.getDynamicFieldsJson()));
        String before = asString(fields.get(OPINION_FIELD));
        fields.put(OPINION_FIELD, request.getOpinion() == null ? "" : request.getOpinion());
        record.replaceDynamicFields(toJson(fields));

        var saved = vbdenResDocumentRepository.save(record);
        documentLogService.logUpdate(saved.getId(),
            Map.of(OPINION_FIELD, before),
            Map.of(OPINION_FIELD, request.getOpinion() == null ? "" : request.getOpinion()));

        return Result.success(saved.getId());
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
