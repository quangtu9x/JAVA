package com.td.application.dbserver;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// BL-117 (bổ sung 19/08/2026): kiểm tra kết nối TRƯỚC khi lưu credential thật — mở thử 1 connection
// tới database "postgres" mặc định (xem DbServerConnectionTester), không lưu/thay đổi gì.
@Service
@RequiredArgsConstructor
@Slf4j
public class TestDbServerConnectionUseCase {

    private final DbServerConnectionTester dbServerConnectionTester;

    public Result<Void> execute(TestDbServerConnectionRequest request) {
        try {
            dbServerConnectionTester.testConnection(
                request.getHost(), request.getPort(), request.getUsername(), request.getPassword());
            return Result.success(null);
        } catch (Exception ex) {
            log.warn("Kiem tra ket noi may chu CSDL that bai: {}", ex.getMessage());
            return Result.failure("Kết nối thất bại: " + ex.getMessage());
        }
    }
}
