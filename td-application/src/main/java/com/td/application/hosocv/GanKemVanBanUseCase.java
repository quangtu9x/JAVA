package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.documentlinks.DocumentLinkService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.duthao.DuThaoDocumentRepository;
import com.td.application.phieutrinhtd.PhieuTrinhTdDocumentRepository;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.documentlinks.DocumentLinkType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * BL-240 (HoSoCV Core) — gắn / gỡ văn bản (dự thảo, văn bản đi, văn bản đến, phiếu trình) vào hồ sơ công
 * việc, tương thích {@code GanKemVanBan.XuLyGanKemVanBanVaoHoSoCV_Chung()} của Domino theo
 * {@code actionName}.
 *
 * <p><b>Từ 06/10/2026</b> liên kết lưu ở bảng {@code document_links} qua {@link DocumentLinkService}
 * (xem {@code docs/architecture/document-links.md}) — KHÔNG còn ghi id nối {@code ";"} vào
 * {@code dynamic_fields} của cả 2 phía ({@code document_profiles_id}, {@code *_attached_id},
 * {@code hoso_attached_id}, {@code document_profiles_from_draft_id}...). Dữ liệu cũ của các field đó đã
 * được migration {@code V1.0.69} chuyển sang bảng; field cũ không còn được đọc ở đâu.
 *
 * <p>Vẫn kiểm tra văn bản tồn tại ĐÚNG bảng vật lý theo {@code actionName}
 * ({@link #ACTION_DOCUMENT_TYPE_MAP}): {@code duthao_documents} / {@code vbdi_documents} /
 * {@code phieutrinhtd_documents} / {@code documents} (vbden) — id không tồn tại hoặc đã xoá thì bỏ qua
 * (giữ hành vi cũ). Không validate loại của id có khớp {@code actionName} hay không (Domino cũng không).
 *
 * <p>{@code gankemduthao}: văn bản đi đã phát hành từ dự thảo cũng được gắn/gỡ vào tab "Văn bản phát
 * hành" của hồ sơ ({@code hoso_vbdi_phathanh}) — tương đương {@code document_profiles_from_draft_id} cũ.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GanKemVanBanUseCase {

    public static final String DOCUMENT_TYPE = "hosocv";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ công việc";
    private static final String INVALID_ACTION_NAME_MESSAGE = "Không xác định được loại gắn kèm (actionName)";
    private static final String REMOVE_ACTION = "remove";
    private static final String DUTHAO_ACTION_NAME = "gankemduthao";
    // Field nghiệp vụ của dự thảo (ChuyenThanhVanBanDi) - fallback khi dự thảo phát hành trước 06/10/2026
    // mà migration chưa có liên kết duthao_vbdi (vd văn bản đi đã bị xoá rồi khôi phục).
    private static final String DUTHAO_DOCUMENT_OUT_ID_FIELD = "document_out_id";
    private static final String TYPE_DUTHAO = "duthao";
    private static final String TYPE_VBDI = "vbdi";
    private static final String TYPE_PHIEUTRINH = "phieutrinh";

    // actionName -> loại liên kết.
    private static final Map<String, DocumentLinkType> ACTION_LINK_TYPE_MAP = Map.of(
        DUTHAO_ACTION_NAME, DocumentLinkType.HOSO_DUTHAO,
        "ganvanbandi", DocumentLinkType.HOSO_VBDI,
        "ganvanbanphathanh", DocumentLinkType.HOSO_VBDI_PHATHANH,
        "ganvanbanden", DocumentLinkType.HOSO_VBDEN,
        "ganvanbandenxuly", DocumentLinkType.HOSO_VBDEN_XULY,
        "gankemphieutrinh", DocumentLinkType.HOSO_PHIEUTRINH
    );

    // actionName -> loại văn bản (quyết định tra tồn tại ở bảng vật lý nào).
    private static final Map<String, String> ACTION_DOCUMENT_TYPE_MAP = Map.of(
        DUTHAO_ACTION_NAME, TYPE_DUTHAO,
        "ganvanbandi", TYPE_VBDI,
        "ganvanbanphathanh", TYPE_VBDI,
        "ganvanbanden", "vbden",
        "ganvanbandenxuly", "vbden",
        "gankemphieutrinh", TYPE_PHIEUTRINH
    );

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DocumentLinkService documentLinkService;
    private final DocumentRepository documentRepository;
    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(GanKemVanBanRequest request) {
        String normalizedAction = normalize(request.getActionName());
        DocumentLinkType linkType = ACTION_LINK_TYPE_MAP.get(normalizedAction);
        if (linkType == null) {
            return Result.failure(INVALID_ACTION_NAME_MESSAGE);
        }

        var hosocvOptional = hoSoCVDocumentRepository.findById(request.getHosocvId());
        if (hosocvOptional.isEmpty() || hosocvOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(hosocvOptional.get().getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        UUID hosocvId = hosocvOptional.get().getId();

        boolean isRemove = REMOVE_ACTION.equalsIgnoreCase(request.getAction());
        boolean isDuThao = DUTHAO_ACTION_NAME.equals(normalizedAction);
        List<UUID> vanBanIds = request.getVanBanIds() == null ? List.of() : request.getVanBanIds();

        for (UUID vanBanId : vanBanIds) {
            if (vanBanId == null || !exists(normalizedAction, vanBanId)) {
                continue;
            }
            apply(linkType, hosocvId, vanBanId, isRemove);
            if (isDuThao) {
                for (UUID vbdiId : issuedDocumentsOfDraft(vanBanId)) {
                    apply(DocumentLinkType.HOSO_VBDI_PHATHANH, hosocvId, vbdiId, isRemove);
                }
            }
        }
        return Result.success(hosocvId);
    }

    private void apply(DocumentLinkType type, UUID hosocvId, UUID vanBanId, boolean remove) {
        if (remove) {
            documentLinkService.unlink(type, hosocvId, vanBanId);
        } else {
            documentLinkService.link(type, hosocvId, vanBanId);
        }
    }

    private boolean exists(String actionName, UUID vanBanId) {
        return switch (ACTION_DOCUMENT_TYPE_MAP.getOrDefault(actionName, "")) {
            case TYPE_DUTHAO -> duThaoDocumentRepository.findById(vanBanId).filter(d -> !d.isDeleted()).isPresent();
            case TYPE_VBDI -> vbdiDocumentRepository.findById(vanBanId).filter(d -> !d.isDeleted()).isPresent();
            // 06/10/2026: phiếu trình (TD) có bảng riêng phieutrinhtd_documents - trước đây tra nhầm bảng
            // documents. FE phiếu trình hiện vẫn chạy Domino (chưa cutover) nên nhánh này chưa có dữ liệu.
            case TYPE_PHIEUTRINH -> phieuTrinhTdDocumentRepository.findById(vanBanId).filter(d -> !d.isDeleted()).isPresent();
            default -> documentRepository.findById(vanBanId).filter(d -> !d.isDeleted()).isPresent();
        };
    }

    // Văn bản đi đã phát hành từ dự thảo: liên kết duthao_vbdi + fallback field document_out_id.
    private Set<UUID> issuedDocumentsOfDraft(UUID duThaoId) {
        Set<UUID> result = new LinkedHashSet<>(
            documentLinkService.findTargetIds(duThaoId, List.of(DocumentLinkType.DUTHAO_VBDI)));
        duThaoDocumentRepository.findById(duThaoId)
            .map(document -> dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson())
                .get(DUTHAO_DOCUMENT_OUT_ID_FIELD))
            .flatMap(GanKemVanBanUseCase::parseUuid)
            .filter(vbdiId -> vbdiDocumentRepository.findById(vbdiId).filter(d -> !d.isDeleted()).isPresent())
            .ifPresent(result::add);
        return result;
    }

    private static Optional<UUID> parseUuid(Object value) {
        if (value == null || value.toString().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.toString().trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
