package com.td.application.vbdi;

import com.td.application.common.models.PaginationResponse;
import com.td.application.sharedcore.UserFullNameResolver;
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
import java.util.function.Function;

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
    private final UserFullNameResolver userFullNameResolver;

    public PaginationResponse<Map<String, Object>> execute(ListVbdiSendLogRequest request) {
        int pageNumber = request == null || request.getPageNumber() == null
            ? 1 : Math.max(request.getPageNumber(), 1);
        int pageSize = request == null || request.getPageSize() == null
            ? DEFAULT_PAGE_SIZE : Math.min(Math.max(request.getPageSize(), 1), MAX_PAGE_SIZE);

        UUID documentId = request == null ? null : request.getDocumentId();
        Page<VbdiSendLogDocument> page = documentId == null
            ? Page.empty()
            : vbdiSendLogDocumentRepository.findByParentId(documentId, PageRequest.of(pageNumber - 1, pageSize));

        Function<UserFullNameResolver.RowName, String> displayName = userFullNameResolver.displayNameLookup();
        var items = page.getContent().stream().map(document -> toFields(document, displayName)).toList();
        return new PaginationResponse<>(items, pageNumber, pageSize,
            page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    // 28/09/2026 (yêu cầu người dùng "Nhật ký gửi nhận -> chi tiết phản hồi: cột Người thực hiện lúc
    // tên đầy đủ, lúc tên đăng nhập -> hiển thị tên đầy đủ, username lưu ẩn để tra cứu"): cột
    // user_creator của bảng lưu TÀI KHOẢN (dòng gửi: currentUsername; dòng phản hồi: actorDisplayName mà
    // mọi caller vbden đều truyền username) — dữ liệu cũ có thể là tên hiển thị/"Hệ thống tiếp nhận".
    // Trả "user_creator" = TÊN ĐẦY ĐỦ tra từ hồ sơ (không tra được thì giữ giá trị đã lưu) + field ẩn
    // "user_creator_username" = giá trị gốc đã lưu — cùng pattern UserFullNameResolver của "Lịch sử
    // hiệu chỉnh". Không ai so sánh user_creator của API này với tài khoản (đã rà FE).
    private Map<String, Object> toFields(
            VbdiSendLogDocument document, Function<UserFullNameResolver.RowName, String> displayName) {
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
        String storedCreator = document.getUserCreator();
        item.put("user_creator", displayName.apply(new UserFullNameResolver.RowName(null, storedCreator)));
        item.put("user_creator_username", storedCreator);
        item.put("created", formatCreated(document.getCreatedOn()));
        return item;
    }

    private String formatCreated(LocalDateTime createdOn) {
        return createdOn == null ? "" : CREATED_FORMATTER.format(createdOn);
    }
}
