package com.td.web.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.td.application.common.TextNormalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;

/**
 * Cấu hình tự động chuẩn hóa NFC + sanitize XSS cho toàn bộ dữ liệu đầu vào HTTP:
 *
 * <ul>
 *   <li><b>@RequestBody JSON</b>: thông qua Jackson Module đăng ký custom StringDeserializer</li>
 *   <li><b>@RequestParam / @ModelAttribute / form fields</b>: thông qua WebDataBinder</li>
 * </ul>
 *
 * <p>Không cần thay đổi gì ở controller hay request class — áp dụng tự động cho toàn bộ API.</p>
 */
@Configuration
public class StringNormalizationConfig {

    /**
     * Jackson Module đăng ký {@link NormalizeStringDeserializer} để tự động xử lý
     * tất cả String values trong JSON request body.
     *
     * <p>Spring Boot auto-configuration phát hiện {@code Module} bean và thêm vào
     * {@code ObjectMapper} HTTP (không ảnh hưởng đến ObjectMapper của Redis).</p>
     */
    @Bean
    public SimpleModule stringNormalizationModule() {
        SimpleModule module = new SimpleModule("VietnameseNormalizationModule");
        module.addDeserializer(String.class, new NormalizeStringDeserializer());
        return module;
    }

    /**
     * ControllerAdvice áp dụng chuẩn hóa NFC + sanitize XSS cho @RequestParam,
     * @ModelAttribute và mọi form field đến controller dưới dạng String.
     */
    @ControllerAdvice
    static class StringNormalizationAdvice {

        @InitBinder
        public void registerStringNormalizer(WebDataBinder binder) {
            binder.registerCustomEditor(String.class, new PropertyEditorSupport() {
                @Override
                public void setAsText(String text) {
                    setValue(TextNormalizer.normalizeAndSanitize(text));
                }

                @Override
                public String getAsText() {
                    Object value = getValue();
                    return value != null ? value.toString() : "";
                }
            });
        }
    }
}
