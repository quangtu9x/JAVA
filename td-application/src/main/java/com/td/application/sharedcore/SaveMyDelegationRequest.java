package com.td.application.sharedcore;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 09/10/2026 — lưu ủy quyền của người đang đăng nhập (màn "Ủy quyền"). Ngày dạng dd/MM/yyyy. */
@Data
public class SaveMyDelegationRequest {

    /** Tài khoản người nhận ủy quyền. */
    private String delegateUsername;

    private String startDate;

    private String endDate;

    /** {@code vbden}, {@code duthao}. */
    private List<String> scopes = new ArrayList<>();

    private String subject;
}
