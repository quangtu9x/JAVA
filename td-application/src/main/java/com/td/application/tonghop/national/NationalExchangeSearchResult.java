package com.td.application.tonghop.national;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NationalExchangeSearchResult {

    private List<NationalExchangeItem> items;
    private long totalItems;
    private int pageNumber;
    private int pageSize;
    /** Khoảng thời gian thực sự áp dụng (sau khi gán mặc định/giới hạn). */
    private LocalDate fromDate;
    private LocalDate toDate;
}
