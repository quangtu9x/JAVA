package com.td.application.documents;

import com.td.domain.documents.DocumentLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * BL-103 (Audit Log): cổng repository cố tình KHÔNG kế thừa {@code IRepository<T>} — chỉ có
 * {@code save} và truy vấn theo {@code documentId}, không có {@code delete}/{@code update}, để
 * ràng buộc "log không thể sửa/xoá" ngay ở tầng application, không chỉ là quy ước.
 */
public interface DocumentLogRepository {

    DocumentLog save(DocumentLog documentLog);

    Page<DocumentLog> findByDocumentId(UUID documentId, Pageable pageable);
}
