package com.td.application.vbden;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.PaginationResponse;
import com.td.domain.vbden.VbdenUpdateHistoryDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Yêu cầu người dùng 24/09/2026 ("lưu lại vết ngày giờ cập nhật, có liên kết tới văn bản đã cập nhật,
 * thể hiện những tệp cũ đã cập nhật nhưng không cho tải về, chỉ hiển thị tên"): đọc lịch sử "Cập nhật
 * VB" của 1 văn bản đến — {@code LichSuCapNhatVanBan.jsx} (FE) render dạng dòng thời gian.
 *
 * <p>Mỗi item: {@code id}, {@code parent_id}, {@code source_document_id} (văn bản gửi lại dùng làm
 * nguồn — FE mở xem chỉ đọc từ snapshot {@code update_obj.source}/{@code source_files}, KHÔNG gọi
 * chi tiết văn bản vì bản nguồn đã tự huỷ), {@code action}, {@code update_obj} (Map đã parse, xem
 * COMMENT cột ở migration {@code V1.0.64}), {@code creator} (tên hiển thị, fallback username),
 * {@code user_creator}, {@code created} ("dd/MM/yyyy HH:mm:ss"). MỚI NHẤT TRƯỚC. Trang từ 1, size mặc
 * định 20/tối đa 200.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ListVbdenUpdateHistoryUseCase {

    private static final DateTimeFormatter CREATED_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdenUpdateHistoryDocumentRepository vbdenUpdateHistoryDocumentRepository;

    public PaginationResponse<Map<String, Object>> execute(ListVbdenUpdateHistoryRequest request) {
        int pageNumber = request == null || request.getPageNumber() == null
            ? 1 : Math.max(request.getPageNumber(), 1);
        int pageSize = request == null || request.getPageSize() == null
            ? DEFAULT_PAGE_SIZE : Math.min(Math.max(request.getPageSize(), 1), MAX_PAGE_SIZE);

        UUID parentId = request == null ? null : request.getParentId();
        Page<VbdenUpdateHistoryDocument> page = parentId == null
            ? Page.empty()
            : vbdenUpdateHistoryDocumentRepository.findByParentIdDesc(parentId, PageRequest.of(pageNumber - 1, pageSize));

        var items = page.getContent().stream().map(this::toItem).toList();
        return new PaginationResponse<>(items, pageNumber, pageSize,
            page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private Map<String, Object> toItem(VbdenUpdateHistoryDocument document) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", document.getId());
        item.put("parent_id", document.getParentId());
        item.put("source_document_id", document.getSourceDocumentId());
        item.put("action", document.getAction());
        item.put("update_obj", parseObject(document.getUpdateObjJson()));
        String displayName = document.getCreatorName() != null && !document.getCreatorName().isBlank()
            ? document.getCreatorName() : document.getUserCreator();
        item.put("creator", displayName);
        item.put("user_creator", document.getUserCreator());
        item.put("created", formatCreated(document.getCreatedOn()));
        return item;
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception ex) {
            log.warn("Khong doc duoc update_obj lich su cap nhat van ban den: {}", ex.getMessage());
            return Map.of();
        }
    }

    private String formatCreated(LocalDateTime createdOn) {
        return createdOn == null ? "" : CREATED_FORMATTER.format(createdOn);
    }
}
