package com.td.infrastructure.simpki;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.simpki.LocateTextSlotsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/** Xem javadoc {@link LocateTextSlotsUseCase}. */
@Slf4j
@Service
public class LocateTextSlotsUseCaseImpl implements LocateTextSlotsUseCase {

    /** Khớp giới hạn số regex 1 lượt của TDSimPki ({@code MAX_LOCATOR_PATTERNS}). */
    static final int MAX_SLOTS = 10;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TdSimPkiClient client;

    public LocateTextSlotsUseCaseImpl(TdSimPkiClient client) {
        this.client = client;
    }

    @Override
    public Result<Map<String, Object>> execute(byte[] pdfBytes, String fileName, String slotsJson) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return Result.failure("Thieu noi dung PDF de dinh vi");
        }
        JsonNode slots;
        try {
            slots = slotsJson == null || slotsJson.isBlank() ? null : OBJECT_MAPPER.readTree(slotsJson);
        } catch (IOException ex) {
            return Result.failure("slots phai la chuoi JSON mang");
        }
        if (slots == null || !slots.isArray() || slots.isEmpty()) {
            return Result.failure("Thieu slots can dinh vi");
        }
        if (slots.size() > MAX_SLOTS) {
            return Result.failure("Toi da " + MAX_SLOTS + " slots moi lan");
        }
        try {
            JsonNode json = client.locateTextSlots(pdfBytes, fileName, slots);
            return Result.success(OBJECT_MAPPER.convertValue(json, new TypeReference<Map<String, Object>>() { }));
        } catch (TdSimPkiException ex) {
            log.warn("Dinh vi o trong qua TDSimPki that bai: {}", ex.getMessage());
            return Result.failure(ex.getMessage());
        }
    }
}
