package com.td.application.tonghop;

import lombok.Data;

/** Khoảng thời gian (theo ngày đến/ngày ban hành) cho "Tổng hợp chung". Bỏ trống = toàn bộ. */
@Data
public class ProvinceStatisticsRequest {

    private String fromDate;

    private String toDate;
}
