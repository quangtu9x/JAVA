package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.PrepareVbdiDeliveryFilesRequest;
import com.td.application.documents.PrepareVbdiDeliveryFilesUseCase;
import com.td.application.documents.VbdiDeliveryFileRef;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Bổ sung 07/09/2026 (yêu cầu người dùng — xem javadoc {@link PrepareVbdiDeliveryFilesUseCase}):
 * copy server-side (MinIO {@code CopyObjectArgs}, KHÔNG tải byte về/tải lên lại — cùng kỹ thuật
 * {@code CopyDocumentFilesUseCaseImpl} đã dùng cho luồng "Chuyển văn thư") mọi tệp của
 * {@code request.getDocumentId()} sang 1 namespace RIÊNG, TÁCH HẲN khỏi đường dẫn gốc của văn bản đi:
 *
 * <pre>vbdi-delivery/{senderIdentifier}/{yyyy}/{MM}/{dd}/{documentId}/{uuid-mới}.{ext}</pre>
 *
 * <p><b>Vì sao KHÔNG tái dùng {@code CopyDocumentFilesUseCaseImpl}</b>: use case đó gắn bản copy vào
 * 1 {@code targetDocumentId} CỤ THỂ (1 document sở hữu 1 bản copy) — không khớp nhu cầu ở đây: 1 lượt
 * "gửi văn bản" có thể có NHIỀU nơi nhận (N tenant khác nhau), TẤT CẢ phải cùng trỏ về 1 bản copy DUY
 * NHẤT (không phải N bản copy, N-1 bản thừa) — nên bản copy này KHÔNG thuộc về 1 document nào cả lúc
 * tạo ra (mỗi tenant nhận sẽ tự tạo 1 dòng {@code file_metadata} RIÊNG trỏ về CÙNG object này khi tạo
 * văn bản đến — xem {@code VbdenIncomingDocumentWriter}, td-woker).</p>
 *
 * <p>Vẫn ghi 1 dòng {@code file_metadata} "kiểm toán" ở tenant BÊN GỬI cho bản copy này —
 * {@code document_id=NULL} (không thuộc về 1 document cụ thể, dùng chung nhiều nơi nhận),
 * {@code file_category="VBDI_DELIVERY"} (nhãn riêng, dễ truy vấn dọn dẹp sau này),
 * {@code description} ghi rõ nguồn gốc — GIÚP TRUY VẾT/dọn dẹp, KHÔNG BẮT BUỘC cho việc tải tệp phía
 * nơi nhận hoạt động (nơi nhận đọc thẳng theo {@code objectKey} trả về, không phụ thuộc dòng này).
 * Ghi dòng kiểm toán này lỗi (vd DB tạm thời lỗi) KHÔNG làm hỏng việc trả về tệp đã copy thành công —
 * chỉ log cảnh báo (best-effort, giống các side-effect kiểm toán khác trong module này).</p>
 *
 * <p><b>Bổ sung 21/09/2026 (yêu cầu người dùng "Phát hành kèm văn bản đến")</b>:
 * {@link PrepareVbdiDeliveryFilesRequest#getAdditionalSourceDocumentIds()} — copy THÊM tệp của các
 * văn bản đến (module {@code vbden}) liên quan, CÙNG namespace/thư mục với tệp của chính văn bản đi
 * (chỉ khác {@code sourceDocumentId} trong đường dẫn/dòng kiểm toán — không có sự khác biệt cấu trúc
 * nào khác, đúng tinh thần Domino gốc copy thẳng item file vào document đích, không phân biệt nguồn ở
 * phía nơi nhận).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepareVbdiDeliveryFilesUseCaseImpl implements PrepareVbdiDeliveryFilesUseCase {

    private static final String NAMESPACE = "vbdi-delivery";
    private static final String FILE_CATEGORY = "VBDI_DELIVERY";
    private static final DateTimeFormatter DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioService minioService;
    private final FileMetadataJpaRepository fileMetadataRepo;

    @Override
    public Result<List<VbdiDeliveryFileRef>> execute(PrepareVbdiDeliveryFilesRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return Result.success(List.of());
        }
        String senderSegment = safeSegment(request.getSenderIdentifier());
        String datePath = LocalDateTime.now().format(DATE_PATH_FORMAT);

        List<VbdiDeliveryFileRef> refs = new ArrayList<>();
        Set<String> attachedNames = normalizeNames(request.getAttachedFilenames());
        copyAllFilesOf(request.getDocumentId(), senderSegment, datePath, refs, attachedNames);

        // Bo sung 21/09/2026 (yeu cau nguoi dung "Phat hanh kem van ban den"): tep dinh kem cua CAC
        // van ban den lien quan - dung CHUNG namespace/thu muc voi tep cua chinh van ban di (khong
        // phan biet nguon o phia nhan, dung tinh than Domino goc copy THANG item vao document dich).
        if (request.getAdditionalSourceDocumentIds() != null) {
            for (UUID vbdenId : request.getAdditionalSourceDocumentIds()) {
                if (vbdenId != null) {
                    // Bo sung 30/09/2026: tep "Phat hanh kem van ban den" LUON vao "Tep kem theo" o noi nhan.
                    copyAllFilesOf(vbdenId, senderSegment, datePath, refs, null);
                }
            }
        }
        return Result.success(refs);
    }

    /**
     * {@code attachedNames}: {@code null} = mọi tệp của nguồn này là "Tệp kèm theo" (văn bản đến kèm
     * theo); khác {@code null} = tệp có tên trong tập là "Tệp kèm theo", còn lại "Tệp ban hành" (văn bản
     * đi — khớp {@code filesVBdi.jsx} chia nhóm theo {@code tepkemtheo}).
     */
    private void copyAllFilesOf(UUID sourceDocumentId, String senderSegment, String datePath,
            List<VbdiDeliveryFileRef> refs, Set<String> attachedNames) {
        List<FileMetadataEntity> sourceFiles = fileMetadataRepo.findAllByDocumentId(sourceDocumentId);
        for (FileMetadataEntity source : sourceFiles) {
            try {
                VbdiDeliveryFileRef ref = copyOne(source, sourceDocumentId, senderSegment, datePath);
                boolean attached = attachedNames == null
                    || attachedNames.contains(normalizeName(source.getOriginalFilename()));
                ref.setAttachmentGroup(attached
                    ? VbdiDeliveryFileRef.GROUP_TEP_KEM_THEO : VbdiDeliveryFileRef.GROUP_TEP_BAN_HANH);
                refs.add(ref);
            } catch (Exception ex) {
                // 1 tep loi (vd MinIO tam thoi khong san sang) KHONG duoc lam hong ca lot gui hoac
                // cac tep khac - bo qua tep nay, ghi log de phat hien, tiep tuc voi cac tep con lai
                // (xem javadoc interface).
                log.error("Khong the copy tep dinh kem {} (document {}) sang vi tri luu tru rieng cho "
                        + "lot gui vbdi - BO QUA tep nay, van ban den se thieu dung tep nay",
                    source.getOriginalFilename(), sourceDocumentId, ex);
            }
        }
    }

    private VbdiDeliveryFileRef copyOne(FileMetadataEntity source, UUID documentId, String senderSegment, String datePath) {
        UUID newFileId = UUID.randomUUID();
        String extension = source.getFileExtension();
        String storedName = newFileId + (extension == null || extension.isEmpty() ? "" : "." + extension);
        String objectPath = NAMESPACE + "/" + senderSegment + "/" + datePath + "/" + documentId + "/" + storedName;

        minioService.copyObject(source.getFilePath(), objectPath);

        try {
            FileMetadataEntity audit = FileMetadataEntity.builder()
                .id(newFileId)
                .documentId(null) // Dung chung nhieu noi nhan - khong thuoc ve 1 document cu the.
                .processId(null)
                .originalFilename(source.getOriginalFilename())
                .storedFilename(storedName)
                .filePath(objectPath)
                .fileSize(source.getFileSize())
                .contentType(source.getContentType())
                .fileExtension(source.getFileExtension())
                .fileCategory(FILE_CATEGORY)
                .bucketName(minioService.getBucketName())
                .uploadedBy(source.getUploadedBy())
                .uploadedAt(LocalDateTime.now())
                .isPublic(Boolean.FALSE)
                // Bugfix 09/09/2026 (xem javadoc FileMetadataEntity.isShared): dong audit nay
                // KHONG tro chung object voi nguon (chi de ghi vet, MinIO object that su gui di o
                // publish RabbitMQ - xem javadoc lop), nen khong phai "doc chung".
                .isShared(Boolean.FALSE)
                .description("vbdi-delivery-copy source_document_id=" + documentId)
                .build();
            fileMetadataRepo.save(audit);
        } catch (Exception ex) {
            // Dong kiem toan chi phuc vu truy vet/don dep sau nay - KHONG bat buoc de nguoi nhan tai
            // duoc tep (ho tu tao dong file_metadata RIENG cua chinh ho tro ve objectPath nay). Loi
            // o day khong duoc lam mat tep da copy thanh cong.
            log.warn("Copy tep {} thanh cong nhung ghi dong kiem toan file_metadata that bai (khong "
                    + "anh huong toi kha nang tai tep phia noi nhan): {}",
                objectPath, ex.getMessage());
        }

        return VbdiDeliveryFileRef.builder()
            .objectKey(objectPath)
            .bucketName(minioService.getBucketName())
            .originalFilename(source.getOriginalFilename())
            .size(source.getFileSize())
            .contentType(source.getContentType())
            .fileExtension(source.getFileExtension())
            .build();
    }

    private static Set<String> normalizeNames(List<String> names) {
        Set<String> result = new HashSet<>();
        if (names != null) {
            for (String name : names) {
                String normalized = normalizeName(name);
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            }
        }
        return result;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String safeSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown-sender";
        }
        // Tranh ky tu de gay nham lan cau truc thu muc MinIO (object key dung "/" phan cach segment).
        return value.trim().replace("/", "_");
    }
}
