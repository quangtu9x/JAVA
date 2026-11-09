package com.td.application.vanbanphapquy;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 30/10/2026 (port vanbanphapquy): request của {@code /api/v1/vanbanphapquy/*}. */
public final class VanBanPhapQuyRequests {

    private VanBanPhapQuyRequests() {
    }

    /**
     * Danh sách - thay qlvb/view/query + qlvb/search?databasetype=vanbanphapquy. {@code viewname}:
     * vwVanBan_By_All|vwVanBan_By_Nam|vwVanBan_By_LinhVuc|vwVanBan_By_LoaiVanBan; {@code keys} =
     * [identifier cơ quan, khoá menu ("all"/năm/lĩnh vực/loại văn bản)]; {@code search} = bộ lọc nâng cao.
     */
    @Data
    public static class GetView {
        private String viewname;
        private List<String> keys = new ArrayList<>();
        private String keyword;
        private Map<String, Object> search = new LinkedHashMap<>();
        private int pageNumber = 1;
        private int pageSize = 20;
    }

    @Data
    public static class Id {
        private UUID id;
    }

    @Data
    public static class Ids {
        private List<UUID> ids = new ArrayList<>();
    }

    /** Thêm/sửa (thay qlvb/add?databasetype=vanbanphapquy) - {@code id} rỗng = thêm mới. */
    @Data
    public static class Save {
        private UUID id;
        private Map<String, String> fields = new LinkedHashMap<>();
    }
}
