package com.td.application.vanbanphapquy;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.vanbanphapquy.VbpqDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/** 30/10/2026 (port vanbanphapquy): truy cập {@code vbpq_documents} (chỉ bản ghi chưa xoá khi liệt kê). */
public interface VbpqDocumentRepository extends IRepository<VbpqDocument> {

    /**
     * Danh sách mới tạo nhất trước. {@code coquanIdentifier} rỗng = mọi cơ quan; {@code year} = năm của
     * ngayky_date (dd/MM/yyyy); {@code equalsFields} = field động so khớp không phân biệt hoa thường (linhvuc,
     * loaivanban); {@code containsFields} = chứa (không dấu); {@code keyword} tìm trên các field chính.
     */
    Page<VbpqDocument> search(String coquanIdentifier, String year, Map<String, String> equalsFields,
                              Map<String, String> containsFields, String keyword, Pageable pageable);
}
