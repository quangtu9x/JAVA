package com.td.application.tonghop;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc ngày từ dữ liệu động/request: {@code dd/MM/yyyy[ HH:mm[:ss]]} (dạng Domino, phổ biến nhất trong
 * {@code received_date}/{@code promulgation_date}) hoặc ISO {@code yyyy-MM-dd[THH:mm...]}. Không đọc được → null.
 */
public final class ProvinceDates {

    private static final Pattern DMY = Pattern.compile("^\\s*(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})");
    private static final Pattern ISO = Pattern.compile("^\\s*(\\d{4})-(\\d{2})-(\\d{2})");

    private ProvinceDates() {
    }

    public static LocalDate parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Matcher iso = ISO.matcher(value);
            if (iso.find()) {
                return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                    Integer.parseInt(iso.group(3)));
            }
            Matcher dmy = DMY.matcher(value);
            if (dmy.find()) {
                return LocalDate.of(Integer.parseInt(dmy.group(3)), Integer.parseInt(dmy.group(2)),
                    Integer.parseInt(dmy.group(1)));
            }
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeException | NumberFormatException ex) {
            return null;
        }
    }
}
