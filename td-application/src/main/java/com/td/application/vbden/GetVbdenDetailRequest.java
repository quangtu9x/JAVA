package com.td.application.vbden;

import lombok.Data;

/**
 * BL-210 (VBDen Core) — request cho {@code POST /api/v1/vbden/XemChiTiet}, tương thích payload
 * legacy {@code qlvb/vbden/XemChiTiet} ({@code {idvbden, idso, edit}}).
 *
 * <p>Bugfix 21/08/2026 (Slice 9, phát hiện qua test thực tế): trước bản này, endpoint chỉ nhận 1
 * {@code id} phẳng ({@link com.td.application.documents.GetDynamicDocumentRequest}) — không xem
 * được chi tiết 1 dòng trong "Vào sổ văn bản đến" (route {@code vbden/vaosovanban}), vì kiến trúc
 * 2-document (xem javadoc {@link TiepNhanVbdenUseCase}, {@link GetSoVanBanDenUseCase}) khiến
 * {@code id} hiển thị trên danh sách là id của bản ghi "sổ" ({@code documentType=vbden_so}), không
 * phải id văn bản gốc — gọi thẳng {@code GetDynamicDocumentUseCase} với id đó sẽ báo "Không tìm
 * thấy văn bản đến" (vì {@code document_type != "vbden"}).</p>
 *
 * <p>{@code idso}: id bản ghi "sổ" (có giá trị khi xem chi tiết từ danh sách "Vào sổ văn bản đến").
 * {@code idvbden}: id văn bản gốc — LUÔN cần khi {@code idso} có giá trị (FE luôn gửi kèm, đọc từ
 * field động {@code parentid} của bản ghi sổ khi hiển thị danh sách); dùng làm id chính khi xem chi
 * tiết văn bản đến THÔNG THƯỜNG (chưa vào sổ / các luồng xử lý khác), lúc đó {@code idso} rỗng.</p>
 *
 * <p>Cả 2 field kiểu {@code String} (không phải {@code UUID}) — vì FE (component dùng chung cho
 * ~18 màn) luôn gửi CẢ 2 khoá, để rỗng {@code ""} khi không áp dụng (không omit khoá) — Jackson mặc
 * định KHÔNG coi {@code ""} là {@code null} khi deserialize vào {@code UUID} (sẽ throw lỗi 400),
 * nên phải nhận {@code String} rồi tự parse "an toàn với chuỗi rỗng" ở use case.</p>
 *
 * <p>Bugfix 21/08/2026 (lần 2): thêm {@code vt} — cùng {@code edit}, khi 1 trong 2 là "true"/true
 * thì server tự tra "bản ghi sổ của tổ chức người xem" theo {@code (idvbden, identifier_creator)}
 * (đúng {@code XemChiTiet.java} thật, dòng 107-127) — quyết định có hiển thị document_number/sổ
 * hay không khi {@code idso} KHÔNG được client gửi kèm (đa số các màn danh sách vbden khác, chỉ gửi
 * {@code idvbden}). FE luôn gửi {@code vt} dạng boolean JSON thật (không phải chuỗi như
 * {@code edit}) — xem các call site {@code vt: currentUser?.roles.includes("QCapNhatVBDen")}.</p>
 */
@Data
public class GetVbdenDetailRequest {

    private String idvbden;

    private String idso;

    private String items = "getall";

    // Khong dung server-side (FE dung de quyet dinh hien thi form hieu chinh) - giu lai field de
    // nhan nguyen payload legacy, tranh loi "unrecognized field" neu co cau hinh strict.
    private String edit;

    private Boolean vt;
}
