package com.td.application.vbdi;

import com.td.application.common.models.PaginationResponse;
import com.td.domain.vbdi.VbdiEditHistoryDocument;
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
 * Yêu cầu người dùng 28/08/2026: đọc lịch sử hiệu chỉnh theo văn bản đi cha từ bảng
 * {@code vbdi_edit_history_documents} — mirror {@code ListDuThaoEditHistoryUseCase} — tương đương
 * {@code qlvb/danhmuc/GetRes?databasetype=vbdi} phía Domino mà {@code VetHieuChinh.jsx} đang gọi
 * ({@code {parentid, items:"getall"}}).
 *
 * <p>Mỗi item giữ ĐÚNG tên field legacy để component render không phải đổi: {@code id},
 * {@code parentid} (chuỗi UUID), {@code form="log"}, {@code form_edit}, {@code edit_obj} (JSON
 * string), {@code creator} (tên hiển thị, fallback username), {@code user_creator}, {@code created}
 * ("dd/MM/yyyy HH:mm:ss"). Thứ tự TĂNG DẦN theo tạo ra (khớp nguồn Domino trước khi FE reverse).
 * Page nhỏ nhất 1, size mặc định 20/giới hạn 200.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListVbdiEditHistoryUseCase {

    static final String FORM_VALUE = "log";
    private static final DateTimeFormatter CREATED_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final VbdiEditHistoryDocumentRepository vbdiEditHistoryDocumentRepository;

    public PaginationResponse<Map<String, Object>> execute(ListVbdiEditHistoryRequest request) {
        int pageNumber = request == null || request.getPageNumber() == null
            ? 1 : Math.max(request.getPageNumber(), 1);
        int pageSize = request == null || request.getPageSize() == null
            ? DEFAULT_PAGE_SIZE : Math.min(Math.max(request.getPageSize(), 1), MAX_PAGE_SIZE);

        UUID parentId = request == null ? null : request.getParentId();
        Page<VbdiEditHistoryDocument> page = parentId == null
            ? Page.empty()
            : vbdiEditHistoryDocumentRepository.findByParentIdAsc(parentId, PageRequest.of(pageNumber - 1, pageSize));

        var items = page.getContent().stream().map(this::toLegacyFields).toList();
        return new PaginationResponse<>(items, pageNumber, pageSize,
            page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private Map<String, Object> toLegacyFields(VbdiEditHistoryDocument document) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", document.getId());
        item.put("parentid", document.getParentId() == null ? "" : document.getParentId().toString());
        item.put("form", FORM_VALUE);
        item.put("form_edit", document.getFormEdit());
        item.put("edit_obj", document.getEditObjJson() == null ? "{}" : document.getEditObjJson());
        String displayName = document.getCreatorName() != null && !document.getCreatorName().isBlank()
            ? document.getCreatorName() : document.getUserCreator();
        item.put("creator", displayName);
        item.put("user_creator", document.getUserCreator());
        item.put("created", formatCreated(document.getCreatedOn()));
        return item;
    }

    private String formatCreated(LocalDateTime createdOn) {
        return createdOn == null ? "" : CREATED_FORMATTER.format(createdOn);
    }
}
