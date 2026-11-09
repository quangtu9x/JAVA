package com.td.application.edithistory;

import com.td.application.common.models.PaginationResponse;
import com.td.application.sharedcore.UserFullNameResolver;
import com.td.domain.edithistory.EditHistoryDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 30/10/2026: đọc vết hiệu chỉnh dùng chung theo bản ghi cha — thay {@code qlvb/danhmuc/GetRes} (Domino)
 * ở nhánh mặc định của VetHieuChinh.jsx. Item giữ khuôn legacy như {@code ListVbdenEditHistoryUseCase}
 * ({@code id,parentid,form=log,form_edit,edit_obj,creator,user_creator,created}), mới nhất trước.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListEditHistoryUseCase {

    private static final DateTimeFormatter CREATED_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final UserFullNameResolver userFullNameResolver;
    private final EditHistoryDocumentRepository editHistoryDocumentRepository;

    public PaginationResponse<Map<String, Object>> execute(ListEditHistoryRequest request) {
        int pageNumber = request == null || request.getPageNumber() == null
            ? 1 : Math.max(request.getPageNumber(), 1);
        int pageSize = request == null || request.getPageSize() == null
            ? DEFAULT_PAGE_SIZE : Math.min(Math.max(request.getPageSize(), 1), MAX_PAGE_SIZE);

        String parentId = request == null || request.getParentId() == null ? "" : request.getParentId().trim();
        Page<EditHistoryDocument> page = parentId.isEmpty()
            ? Page.empty()
            : editHistoryDocumentRepository.findByParentIdNewestFirst(parentId, PageRequest.of(pageNumber - 1, pageSize));

        var nameLookup = userFullNameResolver.displayNameLookup();
        var items = page.getContent().stream().map(doc -> toLegacyFields(doc, nameLookup)).toList();
        return new PaginationResponse<>(items, pageNumber, pageSize,
            page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private Map<String, Object> toLegacyFields(EditHistoryDocument document,
            Function<UserFullNameResolver.RowName, String> nameLookup) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", document.getId());
        item.put("parentid", document.getParentId());
        item.put("form", "log");
        item.put("form_edit", document.getFormEdit());
        item.put("edit_obj", document.getEditObjJson() == null ? "{}" : document.getEditObjJson());
        item.put("creator", nameLookup.apply(
            new UserFullNameResolver.RowName(document.getCreatorName(), document.getUserCreator())));
        item.put("user_creator", document.getUserCreator());
        item.put("created", formatCreated(document.getCreatedOn()));
        return item;
    }

    private String formatCreated(LocalDateTime createdOn) {
        return createdOn == null ? "" : CREATED_FORMATTER.format(createdOn);
    }
}
