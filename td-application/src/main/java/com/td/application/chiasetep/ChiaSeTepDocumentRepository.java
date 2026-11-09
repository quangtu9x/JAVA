package com.td.application.chiasetep;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.chiasetep.ChiaSeTepDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

/**
 * 30/10/2026 (port chiasetep): truy cập {@code chiasetep_documents}. Mọi method chỉ trả bản ghi CHƯA xoá.
 * "Chia sẻ cho X" = X nằm trong {@code nguoichinhsuahide} hoặc {@code nguoixemhide} (chuỗi username nối ";",
 * không phân biệt hoa thường) - khớp các view Domino vwThu_Muc_*_Chia_Se*.
 */
public interface ChiaSeTepDocumentRepository extends IRepository<ChiaSeTepDocument> {

    /** Thư mục do {@code owner} tạo (vwThu_Muc_Ca_Nhan). */
    List<ChiaSeTepDocument> findFoldersOwnedBy(String owner);

    /**
     * Thư mục của {@code owner} đã chia sẻ (vwThu_Muc_Ca_Nhan_Da_Chia_Se); {@code recipient} khác rỗng = chỉ
     * thư mục chia sẻ cho người đó (..._Theo_Nguoi_Duoc_Chia_Se).
     */
    List<ChiaSeTepDocument> findFoldersSharedBy(String owner, String recipient);

    /**
     * Thư mục người khác chia sẻ cho {@code recipient} (vwThu_Muc_Duoc_Chia_Se); {@code owner} khác rỗng = chỉ
     * thư mục của người đó (..._Theo_Nguoi_Chia_Se).
     */
    List<ChiaSeTepDocument> findFoldersSharedWith(String recipient, String owner);

    /** Bản ghi con trực tiếp (thư mục + tài liệu) của các thư mục cho trước. */
    List<ChiaSeTepDocument> findChildren(Collection<String> parentIds);

    /**
     * Tài liệu trong 1 thư mục (vwTai_Lieu_By_ParentID) - mới tạo nhất trước; {@code keyword} tìm không dấu
     * trên số ký hiệu/trích yếu/cơ quan ban hành/ghi chú.
     */
    Page<ChiaSeTepDocument> findDocumentsInFolder(String parentId, String keyword, Pageable pageable);

    /**
     * Không chọn thư mục: tài liệu {@code username} tạo hoặc nằm trong thư mục người đó sở hữu / được chia sẻ.
     */
    Page<ChiaSeTepDocument> findDocumentsAccessibleTo(String username, String keyword, Pageable pageable);
}
