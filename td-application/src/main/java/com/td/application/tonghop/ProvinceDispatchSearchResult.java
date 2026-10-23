package com.td.application.tonghop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDispatchSearchResult {

    private List<ProvinceDispatchDocumentItem> items;
    private long totalItems;
    private int pageNumber;
    private int pageSize;
    private int lateArrivalMinutes;
    private int lateAcceptHours;
}
