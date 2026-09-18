package com.td.web.controllers.v1;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Cấu hình Spring Boot tối giản chỉ dùng cho {@code @WebMvcTest} slice trong package này.
 * Được đặt cùng package với các test controller để Spring Boot Test tìm thấy class này
 * (gần nhất theo package) thay vì {@code TdWebApiApplication} — vốn khai báo
 * {@code @EnableJpaRepositories}/{@code @EntityScan} và cần DataSource thật, không phù hợp
 * cho slice test chỉ kiểm tra tầng web (controller + ApiResponse envelope).
 */
@SpringBootApplication
class WebSliceTestApplication {
}
