package com.td.application.chiasetep;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 30/10/2026 (port chiasetep): các request của {@code /api/v1/chiasetep/*}. */
public final class ChiaSeTepRequests {

    private ChiaSeTepRequests() {
    }

    /** Cây thư mục - thay qlvb/chiasetep/danhsachthumuc (type: canhan|dachiase|dachiase_theonguoi|duocchiase|duocchiase_theonguoi). */
    @Data
    public static class Tree {
        private String type;
        private String key2;
    }

    /** Người dùng đã/được chia sẻ - thay qlvb/chiasetep/danhsachnguoidung. */
    @Data
    public static class Users {
        private String type;
    }

    /** Tài liệu trong thư mục - thay qlvb/view/query viewname=vwTai_Lieu_By_ParentID. */
    @Data
    public static class ListDocuments {
        private String parentId;
        private String keyword;
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

    /** Thêm/sửa thư mục hoặc tài liệu - thay qlvb/add?databasetype=chiasetep. {@code id} rỗng = thêm mới. */
    @Data
    public static class Save {
        private UUID id;
        private String form;
        private String parentId;
        private Map<String, String> fields = new LinkedHashMap<>();
    }

    /** Sao chép thư mục {@code id} vào thư mục {@code pastedId} - thay qlvb/chiasetep/danthumuc. */
    @Data
    public static class Paste {
        private UUID id;
        private UUID pastedId;
    }

    /** Bỏ chia sẻ hàng loạt cho 1 người - thay qlvb/chiasetep/bochiasethumuc. */
    @Data
    public static class Unshare {
        private List<UUID> ids = new ArrayList<>();
        private String username;
        private String name;
    }

    /**
     * Gắn văn bản (vbden/vbdi/phieutrinhtd) vào thư mục: tạo tài liệu + sao chép tệp của văn bản nguồn -
     * thay qlvb/chiasetep/ganvanban. {@code sourceIds}: id ứng viên giữ tệp (thử lần lượt).
     */
    @Data
    public static class Attach {
        private UUID parentId;
        private String databasesearch;
        private List<UUID> sourceIds = new ArrayList<>();
        private Map<String, String> fields = new LinkedHashMap<>();
    }
}
