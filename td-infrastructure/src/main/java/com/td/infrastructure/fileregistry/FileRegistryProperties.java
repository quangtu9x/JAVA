package com.td.infrastructure.fileregistry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình sổ đăng ký tệp đính kèm dùng chung — database RIÊNG (mặc định {@code tdfileregistry}), không
 * phải control-plane, không phải DB nghiệp vụ. Xem {@link SharedFileRegistryClient}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.file-registry")
public class FileRegistryProperties {

    /**
     * Tắt thì: không migrate DB sổ đăng ký, {@code DeleteFileUseCaseImpl} xóa object như trước (không tra
     * tham chiếu), xóa vĩnh viễn văn bản không nhả tham chiếu. Dòng {@code is_shared=TRUE} VẪN bị chặn xóa.
     */
    private boolean enabled = true;

    private Datasource datasource = new Datasource();

    @Data
    public static class Datasource {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "org.postgresql.Driver";
        private int maximumPoolSize = 4;
    }
}
