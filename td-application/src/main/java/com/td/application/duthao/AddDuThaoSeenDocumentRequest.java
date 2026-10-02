package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 25/09/2026: request cho {@code POST /api/v1/duthao/SeenDocument} — đánh dấu CHÍNH người gọi đã
 * xem dự thảo {@code documentId} (xem {@link AddDuThaoSeenDocumentUseCase}). Không nhận username từ
 * client — server-authoritative.
 */
@Data
public class AddDuThaoSeenDocumentRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;
}
