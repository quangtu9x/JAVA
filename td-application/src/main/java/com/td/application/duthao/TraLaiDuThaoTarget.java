package com.td.application.duthao;

/**
 * Yêu cầu người dùng 28/08/2026: 2 đích "trả lại" của {@code XuLyDuThao.java} — chỉ khác nhau ở dữ
 * liệu (mã {@code return_document_out_for}, trạng thái đích của dự thảo, tên {@code action_type},
 * field nguồn tra người nhận trên chính dự thảo), KHÔNG khác thuật toán — dùng CHUNG
 * {@link TraLaiDuThaoSupport} thay vì viết lặp lại logic ở 2 use case.
 */
enum TraLaiDuThaoTarget {

    /** {@code TraLaiDuThaoChoLanhDaoKyDuyet()} — trả về cho lãnh đạo ký duyệt (dự thảo → PENDING_SIGNATURE). */
    LANH_DAO("1", DuThaoStatus.PENDING_SIGNATURE, "vttralanhdao", "signer", "user_signer"),

    /** {@code TraLaiDuThaoChoNguoiSoanThao()} — trả về cho người soạn thảo sửa lại (dự thảo → RETURNED). */
    NGUOI_SOAN_THAO("2", DuThaoStatus.RETURNED, "vttranguoisoan", "creator", "user_creator");

    final String returnDocumentOutForValue;
    final String targetDraftStatus;
    final String actionType;
    final String receiverField;
    final String receiverUsernameField;

    TraLaiDuThaoTarget(String returnDocumentOutForValue, String targetDraftStatus, String actionType,
                        String receiverField, String receiverUsernameField) {
        this.returnDocumentOutForValue = returnDocumentOutForValue;
        this.targetDraftStatus = targetDraftStatus;
        this.actionType = actionType;
        this.receiverField = receiverField;
        this.receiverUsernameField = receiverUsernameField;
    }
}
