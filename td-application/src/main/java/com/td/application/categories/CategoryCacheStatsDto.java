package com.td.application.categories;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryCacheStatsDto {
    private long categoryByIdHits;
    private long categoryByIdMisses;
    private long categoryByIdPuts;
    private long categoryByIdEvictions;
    private long categoryListHits;
    private long categoryListMisses;
    private long categoryListPuts;
    private long categoryListEvictions;
}
