package com.td.application.phieutrinhtd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.phieutrinhtd.PhieuTrinhTdProcessDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chuyển trạng thái ({@code status}) của 1 phiếu trình thẩm định — tương đương
 * {@code ChiTietPage.jsx#handleTrinh(type)} phía FE (KHÔNG có servlet Domino riêng nào, toàn bộ logic
 * chỉ tồn tại phía FE — xem {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md} mục
 * 2.1, 3, 6.2). Dùng CHUNG 1 use case cho MỌI target status ("2" trình thông qua, "5"/"25" trình duyệt
 * cấp 1/2, và cả các trạng thái "kết quả" 3/4/6/7/26/27 + "0" thu hồi) — cơ chế PUSH (ghi log
 * {@code quatrinh} + set {@code status} + xoá {@code trinh<N>_date}/{@code ykientrinh<N>} "downstream")
 * đã xác nhận CHÍNH XÁC cho "2"/"5"/"25"/"0" bằng code thật; cho 6 trạng thái "kết quả" (chưa đọc được
 * handler chi tiết — ⚠️ rủi ro #2 tài liệu 20) áp dụng ĐÚNG cơ chế chung này (ghi log + set status,
 * KHÔNG reset downstream vì không có bằng chứng cụ thể) — an toàn hơn suy đoán 6 use case riêng với
 * logic không kiểm chứng được.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChuyenTrangThaiPhieuTrinhTdUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy phiếu trình";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter CREATE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Xoa trinh<N>_date/ykientrinh<N> "downstream" khi trinh lai - dung khuon handleTrinh (ChiTietPage.jsx:710-737).
    private static final Map<String, List<Integer>> DOWNSTREAM_RESET = Map.of(
        PhieuTrinhTdDocument.STATUS_TRINH_THONG_QUA, List.of(3, 4, 5, 6, 7, 25, 26, 27),
        PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP1, List.of(6, 7),
        PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP2, List.of(5, 6, 7, 26, 27),
        PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO, List.of(2, 3, 4, 5, 6, 7, 25, 26, 27)
    );

    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final PhieuTrinhTdProcessDocumentRepository phieuTrinhTdProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    // 06/10/2026 (cutover FE): field FE KHONG duoc ghi de qua "fields" (server-authoritative).
    private static final java.util.Set<String> PROTECTED_FIELDS =
        java.util.Set.of("status", "form", "user_creator", "creator", "id", "parentid");

    public Result<ChuyenTrangThaiPhieuTrinhTdResult> execute(ChuyenTrangThaiPhieuTrinhTdRequest request) {
        if (request == null || request.getId() == null || request.getTargetStatus() == null
                || request.getTargetStatus().isBlank()) {
            return Result.failure("Thiếu id hoặc targetStatus");
        }

        PhieuTrinhTdDocument document = phieuTrinhTdDocumentRepository.findById(request.getId()).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String targetStatus = request.getTargetStatus().trim();
        Map<String, Object> updatedFields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        // 06/10/2026: gop field FE gui kem TRUOC khi xac dinh nguoi nhan (vd "Trinh thong qua" chon nguoi
        // thong qua ngay trong cung thao tac).
        if (request.getFields() != null) {
            request.getFields().forEach((key, value) -> {
                if (key != null && !PROTECTED_FIELDS.contains(key.toLowerCase())) {
                    updatedFields.put(key, value == null ? "" : value);
                }
            });
        }

        String username = currentUserProvider.currentUsername().orElse("");
        String fullName = appUserRepository.findByUsernameAndDeletedOnIsNull(username)
            .map(AppUser::getFullName).orElse(username);

        var receiver = hasText(request.getUserReceiver()) || hasText(request.getReceiver())
            ? new Receiver(nullToEmpty(request.getReceiver()), nullToEmpty(request.getUserReceiver()),
                nullToEmpty(request.getReceiverObj()))
            : resolveReceiver(targetStatus, updatedFields);
        String opinion = request.getOpinion() == null ? "" : request.getOpinion();

        // 1. Ghi log "quatrinh" (append-only, form=quatrinh, action_type=targetStatus). "subject" = ten
        // field y kien FE (QuaTrinhItemsList) doc - giu ca "opinion" cho tuong thich.
        Map<String, Object> logFields = new LinkedHashMap<>();
        logFields.put("form", "quatrinh");
        logFields.put("action_type", targetStatus);
        logFields.put("creator", fullName);
        logFields.put("user_creator", username);
        logFields.put("receiver", receiver.name());
        logFields.put("user_receiver", receiver.username());
        logFields.put("receiver_obj", receiver.obj());
        logFields.put("opinion", opinion);
        logFields.put("subject", opinion);
        String createDate = java.time.LocalDateTime.now().format(CREATE_DATE_FORMAT);
        logFields.put("create_date", createDate);
        logFields.put("parentid", document.getId().toString());

        var processDocument = new PhieuTrinhTdProcessDocument("Quá trình phiếu trình " + document.getId())
            .replaceDynamicFields(toJson(logFields));
        var savedProcess = phieuTrinhTdProcessDocumentRepository.save(processDocument);

        // 2. Cap nhat status + trinh<N>_date/ykientrinh<N> tren ban ghi cha.
        updatedFields.put("trinh" + targetStatus + "_date", createDate);
        if (!opinion.isBlank()) {
            updatedFields.put("ykientrinh" + targetStatus, opinion);
        }
        for (Integer downstream : DOWNSTREAM_RESET.getOrDefault(targetStatus, List.of())) {
            updatedFields.put("trinh" + downstream + "_date", "");
            updatedFields.put("ykientrinh" + downstream, "");
        }
        document.replaceDynamicFields(toJson(updatedFields));
        document.changeStatus(targetStatus);

        var saved = phieuTrinhTdDocumentRepository.save(document);
        return Result.success(new ChuyenTrangThaiPhieuTrinhTdResult(saved.getId(), savedProcess.getId()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Receiver(String name, String username, String obj) {
    }

    private Receiver resolveReceiver(String targetStatus, Map<String, Object> parentFields) {
        String nameField;
        String usernameField;
        String objField;
        if (PhieuTrinhTdDocument.STATUS_TRINH_THONG_QUA.equals(targetStatus)) {
            nameField = "lanhdaovanphong";
            usernameField = "user_lanhdaovanphong";
            objField = "lanhdaovanphong_obj";
        } else if (PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP1.equals(targetStatus)) {
            nameField = "lanhdao1";
            usernameField = "user_lanhdao1";
            objField = "lanhdao1_obj";
        } else if (PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP2.equals(targetStatus)) {
            nameField = "lanhdao2";
            usernameField = "user_lanhdao2";
            objField = "lanhdao2_obj";
        } else {
            return new Receiver("", "", "");
        }
        return new Receiver(
            asString(parentFields.get(nameField)),
            asString(parentFields.get(usernameField)),
            asString(parentFields.get(objField)));
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
