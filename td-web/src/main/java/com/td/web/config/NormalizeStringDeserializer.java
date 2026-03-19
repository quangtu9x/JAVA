package com.td.web.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.td.application.common.TextNormalizer;

import java.io.IOException;

/**
 * Custom Jackson deserializer áp dụng chuẩn hóa NFC + sanitize XSS
 * cho mọi String field trong {@code @RequestBody} JSON.
 *
 * <p>Được đăng ký tự động qua {@link StringNormalizationConfig#stringNormalizationModule()}.</p>
 */
class NormalizeStringDeserializer extends StringDeserializer {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String raw = super.deserialize(p, ctxt);
        return TextNormalizer.normalizeAndSanitize(raw);
    }
}
