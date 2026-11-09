package com.td.application.nhatkyhethong;

import com.td.domain.edithistory.EditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * 30/10/2026 (port "Nhật ký quản trị" - nhatkyhethong/nhatkyhieuchinh/*): ĐỌC vết hiệu chỉnh dùng chung
 * {@code edit_history_documents} theo loại bản ghi ({@code form_edit} - user/group_roles/config_sys...),
 * thay Domino qlvb/search form=log. Chỉ đọc, mới nhất trước.
 */
public interface NhatKyQuanTriRepository {

    Page<EditHistoryDocument> search(String formEdit, String keyword, LocalDate from, LocalDate to, Pageable pageable);
}
