package com.td.application.duthao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Xem {@link ChuyenTepVetDuThaoUseCase}. */
@Data
public class ChuyenTepVetDuThaoRequest {

    /** Dòng "Quá trình dự thảo" (draft_process) đang chứa tệp. */
    @NotNull(message = "processId khong duoc de trong")
    private UUID processId;

    /** Tệp của dòng quá trình cần chuyển. */
    @NotNull(message = "fileId khong duoc de trong")
    private UUID fileId;

    /** Trường đích trên dự thảo: tepbanhanh | tepkemtheo | tepphieutrinh. */
    @NotBlank(message = "fieldname khong duoc de trong")
    private String fieldname;
}
