# Cutover thống kê văn bản đến theo chủ trì

Ngày cập nhật: 12/10/2026.

## Phạm vi

- `Văn bản đến → Tra cứu văn bản → Theo người chủ trì`.
- `Văn bản đến → Tra cứu văn bản → Theo đơn vị chủ trì`.
- FE chỉ cutover hai view `theonguoichutri` và `theodonvichutri`; các màn dùng
  `GroupTableVbden` khác vẫn giữ API hiện tại.

## Nguồn đối chiếu Domino

- `docs/views_domino/view_vanbanden_theonguoichutri.dxl`.
- `docs/views_domino/view_vanbanden_theodonvichutri.dxl`.
- `BE_THANHHOA/.../vbden/GetViewGroups.java`.
- `BE_THANHHOA/.../vbden/GetViewGroupsByCategory.java`.

## Ánh xạ nghiệp vụ

| Nội dung | Domino | Java |
| --- | --- | --- |
| Điều kiện chọn | `Form="DocumentIn" & identifier_creator!=""` | `document_type="vbden"`, chưa xóa và `dynamic_fields.identifier_creator` khác rỗng |
| Người chủ trì | `@Unique(@Explode(user_process; user_receiver; user_processing; user_processed))` | Hợp, tách `;` và loại trùng bốn field tương ứng |
| Đơn vị chủ trì | `@Unique(@Explode(unit_process; unit_process_registered))` | Hợp, tách `;` và loại trùng hai field tương ứng |
| Trạng thái | `status=3 hoặc 4 → 04`, còn lại `02` | Giữ nguyên công thức |
| Thời gian | Năm, tháng của `promulgation_date` | Giữ nguyên; không nhóm theo ngày |
| Sắp xếp | Chủ trì/trạng thái tăng; năm/tháng giảm | Giữ nguyên |

Một văn bản có nhiều người hoặc đơn vị trong công thức `@Explode` được tính vào từng nhánh tương
ứng; giá trị trùng trong cùng văn bản chỉ được tính một lần, đúng `@Unique`.

## API Java và FE

- Endpoint: `POST /api/v1/vbden/GetViewGroups`.
- Request giữ các field mà `GroupTableVbden` dùng: `viewname`, `node`, `getallchild`, `items`,
  `pageNumber`, `pageSize`.
- Response giữ envelope `ApiResponse` và metadata tương thích: `properties.type`,
  `properties.total_group`, `total`.
- `GroupTableVbden.jsx` tự chọn endpoint Java chỉ với hai view trên; không đổi luồng các view khác.

## Kiểm thử

- `ViewQueryServiceTest` kiểm tra hợp/loại trùng người chủ trì, hợp đơn vị, quy đổi trạng thái và
  phân nhóm theo tháng ban hành.
- Build Java `td-application`, `td-infrastructure`, `td-web` thành công.
