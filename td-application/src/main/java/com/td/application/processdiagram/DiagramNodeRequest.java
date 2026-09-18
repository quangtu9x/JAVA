package com.td.application.processdiagram;

import lombok.Data;

/**
 * R8 (tài liệu phân tích 03) — 1 node được thêm vào sơ đồ xử lý ({@link ProcessDiagramService#addNode}).
 * Tương đương tham số của {@code ClassTreeSoDo.createNewNode(Map<String,Object>)} phía Domino.
 */
@Data
public class DiagramNodeRequest {

    /** Người nhận (bắt buộc). */
    private String username;

    /** Tên hiển thị (bắt buộc — hiện chưa có nguồn tra tên hiển thị từ username, xem javadoc service). */
    private String name;

    private String chucvu;

    /** Org identifier của người gửi/đơn vị liên quan. */
    private String identifier;

    /** Thời điểm gửi, định dạng {@code dd/MM/yyyy HH:mm:ss} (khớp quy ước hiển thị Domino). */
    private String ngaygui;

    /** Id bản ghi quá trình xử lý (vbden_res) sinh ra hành động này, nếu có. */
    private String quytrinhid;

    /** Ý kiến / tiêu đề hành động. */
    private String noidung;

    private String hanxuly;

    private String nguoidaidien;

    /** Username người thực hiện chuyển. */
    private String usergui;

    /** Tên hiển thị người thực hiện chuyển. */
    private String nguoigui;
}
