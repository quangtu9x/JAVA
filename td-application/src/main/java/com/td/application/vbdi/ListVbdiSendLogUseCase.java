package com.td.application.vbdi;

import com.td.application.common.models.PaginationResponse;
import com.td.domain.vbdi.VbdiSendLogDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026: đọc lịch sử "Nhật ký gửi nhận" theo văn bản đi cha từ bảng
 * {@code vbdi_send_log_documents} — phần ĐỌC LẠI tương ứng với {@link SendVbdiDocumentUseCase} (ghi),
 * tương đương "danh sách nơi đã gửi" mà FE cần hiển thị (xem
 * {@code docs/analysis/07-phan-tich-port-senddocument-vbdi-qlvb.md} §6 "ListVbdiSendLogUseCase").
 *
 * <p>MỚI NHẤT TRƯỚC ({@code created_on DESC} — xem javadoc
 * {@link VbdiSendLogDocumentRepository#findByParentId}). Page nhỏ nhất 1, size mặc định 20/giới hạn
 * 200 (cùng convention {@link ListDuThaoEditHistoryUseCase}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListVbdiSendLogUseCase {

    private static final DateTimeFormatter CREATED_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final VbdiSendLogDocumentRepository vbdiSendLogDocumentRepository;

    public PaginationResponse<Map<String, Object>> execute(ListVbdiSendLogRequest request) {
        int pageNumber = request == null || request.getPageNumber() == null
            ? 1 : Math.max(request.getPageNumber(), 1);
        int pageSize = request == null || request.getPageSize() == null
            ? DEFAULT_PAGE_SIZE : Math.min(Math.max(request.getPageSize(), 1), MAX_PAGE_SIZE);

        UUID documentId = request == null ? null : request.getDocumentId();
        Page<VbdiSendLogDocument> page = documentId == null
            ? Page.empty()
            : vbdiSendLogDocumentRepository.findByParentId(documentId, PageRequest.of(pageNumber - 1, pageSize));

        var items = page.getContent().stream().map(this::toFields).toList();
        return new PaginationResponse<>(items, pageNumber, pageSize,
            page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private Map<String, Object> toFields(VbdiSendLogDocument document) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", document.getId());
        item.put("parentid", document.getParentId() == null ? "" : document.getParentId().toString());
        item.put("send_type", document.getSendType());
        item.put("recipient_group", document.getRecipientGroup());
        item.put("recipient_identifier", document.getRecipientIdentifier());
        item.put("recipient_name", document.getRecipientName());
        item.put("mailin", document.getMailin());
        item.put("status", document.getStatus());
        item.put("content", document.getContent());
        item.put("user_creator", document.getUserCreator());
        item.put("created", formatCreated(document.getCreatedOn()));
        return item;
    }

    private String formatCreated(LocalDateTime createdOn) {
        return createdOn == null ? "" : CREATED_FORMATTER.format(createdOn);
    }
}
