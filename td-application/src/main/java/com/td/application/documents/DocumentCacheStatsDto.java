package com.td.application.documents;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCacheStatsDto {

    private long documentByIdHits;
    private long documentByIdMisses;
    private long documentByIdPuts;
    private long documentByIdEvictions;
    private long documentListHits;
    private long documentListMisses;
    private long documentListPuts;
    private long documentListEvictions;
}