package com.td.application.numbering;

/**
 * BL-104: cổng repository cố tình chỉ có 1 thao tác nguyên tử duy nhất — không có {@code save}
 * thông thường, để buộc mọi lần tăng số phải đi qua UPSERT nguyên tử (an toàn dưới concurrent
 * access), không thể vô tình "load rồi ghi lại" (read-modify-write không an toàn).
 */
public interface NumberingCounterRepository {

    /**
     * Tăng bộ đếm cho {@code (documentType, year, scopeKey)} và trả về giá trị MỚI (nguyên tử).
     * Nếu chưa tồn tại, tạo mới bắt đầu từ 1.
     */
    long incrementAndGet(String documentType, int year, String scopeKey);
}
