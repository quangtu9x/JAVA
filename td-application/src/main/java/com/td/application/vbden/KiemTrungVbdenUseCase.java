package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BL-210/211 (Nhập mới VBDen): tương thích {@code qlvb/vbden/KiemTrung} (Domino thật,
 * {@code KiemTrung.java}) — kiểm tra văn bản đến khác đã tồn tại cùng (identifier, promulgation_date,
 * code) hay chưa, trước khi lưu (cảnh báo trùng ký hiệu/ngày ban hành). Domino thật đọc từ 1 view
 * riêng trên database "vbden_so" (bản ghi companion mỗi lần vào sổ) — kiến trúc mới KHÔNG có database
 * companion đó, nên truy vấn thẳng bảng {@code documents} (document_type=vbden) qua Dynamic Data
 * Engine (BL-101), đúng field {@code code} sẵn có bởi field này chọn lọc tốt nhất trong 3 field khoá.
 *
 * <p><b>Bugfix 07/09/2026 (báo lỗi người dùng: "snnmt" — đơn vị CHƯA từng có văn bản đến nào — vẫn
 * bị cảnh báo trùng MỖI LẦN vào sổ)</b>: đọc lại {@code KiemTrung.java}/{@code LuuVanBanDen.java}
 * (Domino thật, {@code BE_THANHHOA}) xác nhận: {@code original_doc_id} (Domino) KHÔNG phải id văn
 * bản gửi gốc như đoán ban đầu — nó là 1 CHUỖI KHOÁ GHÉP
 * {@code identifier_user + "," + promulgation_date + "," + code}, chỉ được GHI vào bản ghi companion
 * {@code vbden_so} SAU KHI "vào sổ" thành công ({@code LuuVanBanDen.java}, dòng
 * {@code docSoVB.replaceItemValue("original_doc_id", ...)}) — {@code KiemTrung.java} chỉ tra cứu
 * trong database {@code vbden_so} này, tức CHỈ so khớp với văn bản ĐÃ vào sổ trước đó, KHÔNG BAO GIỜ
 * so khớp với văn bản mới đến/chưa vào sổ (kể cả các bản ghi khác cùng {@code code} đến cùng lúc
 * nhưng chưa ai vào sổ). Bản port Java TRƯỚC ĐÂY so khớp thẳng trên bảng {@code documents} — bao
 * gồm CẢ văn bản CHƯA vào sổ — nên khi tồn tại &gt;1 bản ghi cùng (code, promulgation_date) mà CHƯA
 * cái nào vào sổ (vd. do redelivery/nhiều kênh phân phối cùng tenant), lần vào sổ ĐẦU TIÊN đã bị báo
 * trùng nhầm, dù đơn vị "chưa có văn bản nào cả" theo đúng nghĩa "đã vào sổ". Sửa bằng cách CHỈ coi
 * 1 candidate là trùng khi nó đã có {@code document_register_id} (đã vào sổ xong — xem
 * {@code LuuVanBanDenUseCase#DOCUMENT_REGISTER_ID_FIELD}), tương đương "đã có mặt trong vbden_so"
 * của Domino.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class KiemTrungVbdenUseCase {

    private static final String DOCUMENT_TYPE = "vbden";
    private static final String CODE_FIELD = "code";
    private static final String SUBJECT_FIELD = "subject";
    private static final String UNIT_IN_CHARGE_FIELD = "unit_in_charge";
    private static final String DOCUMENT_NUMBER_FIELD = "document_number";
    private static final String PROMULGATION_DATE_FIELD = "promulgation_date";
    private static final String IDENTIFIER_CREATOR_FIELD = "identifier_creator";
    // Bugfix 07/09/2026 (xem javadoc lớp): chỉ văn bản ĐÃ "vào sổ" (đã ghi field này —
    // LuuVanBanDenUseCase#DOCUMENT_REGISTER_ID_FIELD) mới được coi là candidate trùng, đúng ngữ
    // nghĩa "đã có mặt trong vbden_so" của Domino.
    private static final String DOCUMENT_REGISTER_ID_FIELD = "document_register_id";
    // Giới hạn hợp lý cho 1 lượt kiểm tra trùng (khác danh sách phân trang thông thường) — cùng 1
    // ký hiệu văn bản trùng lặp hàng trăm bản ghi là bất thường, không cần phân trang thêm.
    private static final int MAX_CANDIDATES = 200;

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<List<VbdenDuplicateDto>> execute(KiemTrungVbdenRequest request) {
        try {
            String code = request.getCode();
            String promulgationDate = request.getPromulgationDate();
            if (code == null || code.isBlank() || promulgationDate == null || promulgationDate.isBlank()) {
                return Result.success(List.of());
            }

            var candidates = documentRepository.findByFieldValue(
                DOCUMENT_TYPE, CODE_FIELD, code.trim(), PageRequest.of(0, MAX_CANDIDATES));

            List<VbdenDuplicateDto> duplicates = new ArrayList<>();
            for (BusinessDocument doc : candidates) {
                if (request.getId() != null && request.getId().equals(doc.getId())) {
                    continue;
                }
                Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(doc.getDynamicFieldsJson());
                if (asString(fields.get(DOCUMENT_REGISTER_ID_FIELD)).isBlank()) {
                    // Bugfix 07/09/2026 (xem javadoc lớp): chưa vào sổ -> không phải candidate trùng.
                    continue;
                }
                if (!promulgationDate.trim().equals(asString(fields.get(PROMULGATION_DATE_FIELD)))) {
                    continue;
                }
                String identifierCreator = asString(fields.get(IDENTIFIER_CREATOR_FIELD));
                if (request.getIdentifier() != null && !request.getIdentifier().isBlank()
                        && !request.getIdentifier().trim().equals(identifierCreator)) {
                    continue;
                }
                var dto = new VbdenDuplicateDto();
                dto.setId(doc.getId());
                dto.setCode(asString(fields.get(CODE_FIELD)));
                dto.setSubject(asString(fields.get(SUBJECT_FIELD)));
                dto.setUnitInCharge(asString(fields.get(UNIT_IN_CHARGE_FIELD)));
                dto.setDocumentNumber(asString(fields.get(DOCUMENT_NUMBER_FIELD)));
                dto.setPromulgationDate(asString(fields.get(PROMULGATION_DATE_FIELD)));
                dto.setIdentifierCreator(identifierCreator);
                duplicates.add(dto);
            }
            return Result.success(duplicates);
        } catch (Exception ex) {
            log.error("Kiểm tra trùng văn bản đến thất bại", ex);
            return Result.failure("Kiểm tra trùng văn bản đến thất bại: " + ex.getMessage());
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
