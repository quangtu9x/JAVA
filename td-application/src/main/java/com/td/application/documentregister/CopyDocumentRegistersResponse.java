package com.td.application.documentregister;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CopyDocumentRegistersResponse {
    private List<UUID> createdIds = new ArrayList<>();
    // "<tên sổ gốc>: <lý do>" — cho phép FE báo lỗi từng sổ, giống hành vi toast-per-item bản gốc.
    private List<String> errors = new ArrayList<>();
}
