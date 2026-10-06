package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documentlinks.DocumentLinkService;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.documentlinks.DocumentLinkType;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import com.td.domain.hosoluutru.HoSoLuuTruVanBanDocument;
import com.td.domain.recordsstorage.RecordsStorageCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" (hosoluutru) — thay {@code XuLyHoSoLuuTru.XuLyChuyenLuuTru()}, xem
 * {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md} mục 5.1/5.6/8.2/8.4. Đây là
 * bước 1/3 của cơ chế đồng bộ {@code hosocv} ↔ {@code hosoluutru} — được gọi TỪ
 * {@code ChuyenLuuTruHoSoUseCase} (hosocv) trong cùng giao dịch nghiệp vụ, KHÔNG gọi qua HTTP nội bộ
 * (đúng nguyên tắc Clean Architecture use-case-gọi-use-case).
 *
 * <p><b>Phát hiện quan trọng khi code</b>: {@code docs/analysis/16-...md} ghi "CSDL danh mục hồ sơ
 * (tudien) chưa port ở Java" — SAI, đã kiểm tra lại: đây CHÍNH LÀ {@link RecordsStorageCatalog}
 * ({@code records_storage_catalogs}, BL-116, đã port đầy đủ CRUD) — field khớp 1-1 với
 * {@code docDMHS.copyAllItems()} phía Domino ({@code category}/{@code code}/{@code phong}/
 * {@code firstTopic}/{@code secondTopic}/{@code storagePeriod}/{@code numberOfDocumentNumber}/
 * {@code identifier}...). Sửa lại trong tài liệu 16/17 ở Phase 8.</p>
 *
 * <p>Copy văn bản liên quan: đúng bằng chứng BE thật (chỉ 2 nguồn, KHÔNG có {@code duthao} — xem tài
 * liệu 16 mục 5.1) — đọc field {@code document_in_attached_id} (vbden, bảng {@code documents} dùng
 * chung — vbden chưa tách bảng riêng) và {@code document_out_attached_id} (vbdi,
 * {@link VbdiDocumentRepository}) trên chính hồ sơ {@code hosocv} nguồn (đúng field
 * {@code GanKemVanBanUseCase} ghi khi gắn văn bản — đơn giản/đáng tin hơn cách Domino dò ngược qua
 * view {@code vwDocument_Profiles_ID}). "Văn bản khác" ({@code other_related_document}) KHÔNG copy —
 * chưa port ở Java (đúng checklist hosocv, {@code ganvanbankhac} "cần xác nhận nghiệp vụ").</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TaoHoSoLuuTruTuHoSoCVUseCase {

    public static final String DOCUMENT_TYPE = "hosoluutru";
    public static final String VAN_BAN_DOCUMENT_TYPE = "vanban";

    private static final String HOSOCV_DOCUMENT_TYPE = "hosocv";
    private static final String FALLBACK_USERNAME = "system";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final com.td.application.hosocv.HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final com.td.application.recordsstorage.RecordsStorageCatalogRepository recordsStorageCatalogRepository;
    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;
    private final DocumentRepository documentRepository;
    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AuthorAclFieldsService authorAclFieldsService;
    private final DocumentLinkService documentLinkService;

    public Result<UUID> execute(TaoHoSoLuuTruTuHoSoCVRequest request) {
        if (request == null || request.getHosocvId() == null || request.getDmhsId() == null) {
            return Result.failure("Thiếu id hồ sơ công việc hoặc id danh mục hồ sơ lưu trữ");
        }
        var hosocvOptional = hoSoCVDocumentRepository.findById(request.getHosocvId());
        if (hosocvOptional.isEmpty() || hosocvOptional.get().isDeleted()
                || !HOSOCV_DOCUMENT_TYPE.equalsIgnoreCase(hosocvOptional.get().getDocumentType())) {
            return Result.failure("Không tìm thấy hồ sơ công việc");
        }
        var catalogOptional = recordsStorageCatalogRepository.findByIdAndDeletedOnIsNull(request.getDmhsId());
        if (catalogOptional.isEmpty()) {
            return Result.failure("Không tìm thấy danh mục hồ sơ lưu trữ");
        }
        var hosocv = hosocvOptional.get();
        var catalog = catalogOptional.get();
        Map<String, Object> hosocvFields = dynamicDocumentFieldsService.parseDynamicFields(hosocv.getDynamicFieldsJson());
        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);

        Map<String, String> fields = buildHoSoLuuTruFields(hosocv.getId(), catalog, hosocvFields, actingUsername);
        authorAclFieldsService.applyOnCreate(DOCUMENT_TYPE, fields);
        String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(fields, null, null, null);

        var hoSoLuuTru = new HoSoLuuTruDocument(
            hosocv.getTitle(), DOCUMENT_TYPE, HoSoLuuTruLifecycleStatus.FORM_CHO_TIEP_NHAN, null, null, null, null)
            .replaceDynamicFields(dynamicFieldsJson);
        var saved = hoSoLuuTruDocumentRepository.save(hoSoLuuTru);

        copyVanBanTuVbden(saved.getId(), hosocv.getId(), actingUsername);
        copyVanBanTuVbdi(saved.getId(), hosocv.getId(), actingUsername);

        return Result.success(saved.getId());
    }

    private Map<String, String> buildHoSoLuuTruFields(
            UUID hosocvId, RecordsStorageCatalog catalog, Map<String, Object> hosocvFields, String actingUsername) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_DOCUMENT_PROFILES_ID, hosocvId.toString());
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_CATEGORY_ID, catalog.getId().toString());
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_CATEGORY, nullToEmpty(catalog.getCategory()));
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_CHUYENLUUTRU, HoSoLuuTruLifecycleStatus.CHUYENLUUTRU_CHUA);
        fields.put("code", nullToEmpty(catalog.getCode()));
        fields.put("phong", nullToEmpty(catalog.getPhong()));
        fields.put("phong_id", catalog.getPhongId() == null ? "" : catalog.getPhongId().toString());
        fields.put("first_topic", nullToEmpty(catalog.getFirstTopic()));
        fields.put("first_topic_id", catalog.getFirstTopicId() == null ? "" : catalog.getFirstTopicId().toString());
        fields.put("second_topic", nullToEmpty(catalog.getSecondTopic()));
        fields.put("second_topic_id", catalog.getSecondTopicId() == null ? "" : catalog.getSecondTopicId().toString());
        fields.put("storage_period", nullToEmpty(catalog.getStoragePeriod()));
        fields.put("number_of_document_number",
            catalog.getNumberOfDocumentNumber() == null ? "" : catalog.getNumberOfDocumentNumber().toString());
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_IDENTIFIER, nullToEmpty(catalog.getIdentifier()));
        fields.put("creator", actingUsername);
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_USER_CREATOR, actingUsername);
        // Dung khuon Domino that (XuLyHoSoLuuTru.java:197-202): copy unit_creator cua hosocv sang CA
        // unit_creator LAN unit_process_handler ben hosoluutru (khong phai bug - hanh vi that).
        fields.put("unit_creator", stringField(hosocvFields, "unit_creator"));
        fields.put("unit_creator_id", stringField(hosocvFields, "unit_creator_id"));
        fields.put("unit_process_handler", stringField(hosocvFields, "unit_creator"));
        fields.put("unit_process_handler_id", stringField(hosocvFields, "unit_creator_id"));
        fields.put("process_handler", stringField(hosocvFields, "process_handler"));
        fields.put("user_process_handler", stringField(hosocvFields, "user_process_handler"));
        fields.put("process_handler_obj", stringField(hosocvFields, "process_handler_obj"));
        fields.put("start_date", stringField(hosocvFields, "start_date"));
        fields.put("finish_date", stringField(hosocvFields, "finish_date"));
        fields.put("create_date", DATE_FORMATTER.format(LocalDate.now()));
        return fields;
    }

    // 06/10/2026: danh sach van ban doc bang document_links (docs/architecture/document-links.md) thay
    // document_in_attached_id/document_out_attached_id tren ho so.
    private void copyVanBanTuVbden(UUID hosoId, UUID hosocvId, String actingUsername) {
        for (UUID vanBanId : documentLinkService.findTargetIds(hosocvId,
                List.of(DocumentLinkType.HOSO_VBDEN, DocumentLinkType.HOSO_VBDEN_XULY))) {
            documentRepository.findById(vanBanId)
                .filter(document -> !document.isDeleted())
                .ifPresent(document -> saveVanBan(hosoId, "vbden", "document_in_id", vanBanId,
                    documentFields(document.getDynamicFieldsJson()), actingUsername, false));
        }
    }

    private void copyVanBanTuVbdi(UUID hosoId, UUID hosocvId, String actingUsername) {
        for (UUID vanBanId : documentLinkService.findTargetIds(hosocvId,
                List.of(DocumentLinkType.HOSO_VBDI, DocumentLinkType.HOSO_VBDI_PHATHANH))) {
            vbdiDocumentRepository.findById(vanBanId)
                .filter(document -> !document.isDeleted())
                .ifPresent(document -> saveVanBan(hosoId, "vbdi", "document_out_id", vanBanId,
                    documentFields(document.getDynamicFieldsJson()), actingUsername, true));
        }
    }

    // isVbdi=true -> doc code_full/unit_publish(_identifier) (dung ten field cua vbdi, xem tai lieu
    // 16 muc 5.1); false -> doc code/unit_in_charge(_identifier) (vbden) + received_date rieng.
    private void saveVanBan(
            UUID hosoId, String source, String linkFieldName, UUID linkedId,
            Map<String, Object> sourceFields, String actingUsername, boolean isVbdi) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_HOSO_ID, hosoId.toString());
        fields.put(linkFieldName, linkedId.toString());
        fields.put("source", source);
        fields.put("code", isVbdi ? stringField(sourceFields, "code_full") : stringField(sourceFields, "code"));
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_IDENTIFIER, stringField(sourceFields, "identifier"));
        fields.put("promulgation_date", stringField(sourceFields, "promulgation_date"));
        if (!isVbdi) {
            fields.put("received_date", stringField(sourceFields, "received_date"));
        }
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_SUBJECT, stringField(sourceFields, "subject"));
        fields.put("unit_in_charge", isVbdi
            ? stringField(sourceFields, "unit_publish") : stringField(sourceFields, "unit_in_charge"));
        fields.put("unit_in_charge_identifier", isVbdi
            ? stringField(sourceFields, "unit_publish_identifier") : stringField(sourceFields, "unit_in_charge_identifier"));
        fields.put("signer", stringField(sourceFields, "signer"));
        fields.put("signer_position", stringField(sourceFields, "signer_position"));
        fields.put("document_type", stringField(sourceFields, "document_type"));
        fields.put("sector", stringField(sourceFields, "sector"));
        fields.put("creator", actingUsername);
        fields.put(HoSoLuuTruLifecycleStatus.FIELD_USER_CREATOR, actingUsername);
        fields.put("create_date", DATE_FORMATTER.format(LocalDate.now()));

        authorAclFieldsService.applyOnCreate(VAN_BAN_DOCUMENT_TYPE, fields);
        String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(fields, null, null, null);
        String title = stringField(sourceFields, "subject");
        var vanBan = new HoSoLuuTruVanBanDocument(
            title.isBlank() ? "Văn bản liên quan" : title, VAN_BAN_DOCUMENT_TYPE, null, null, null, null, null)
            .replaceDynamicFields(dynamicFieldsJson);
        hoSoLuuTruVanBanDocumentRepository.save(vanBan);
    }

    private Map<String, Object> documentFields(String dynamicFieldsJson) {
        return dynamicDocumentFieldsService.parseDynamicFields(dynamicFieldsJson);
    }

    private String stringField(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? "" : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
