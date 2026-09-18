# certs/simpki — chứng thư kết nối MSSP (module `simpki.sign`)

Xem đầy đủ bối cảnh: `docs/17-checklist-tich-hop-kyso-simpki.md`.

## `truststore-bcy.jks` (đã có, dùng được ngay)

Copy nguyên từ `docs/TDSimSign/sdk-mobilepki/java/samplemssp/etc/truststore-bcy.jks` (mật khẩu
`changeit`, `PKCS12`/`JKS` chuẩn Java). Đây là **truststore CA công khai** (root CA + sub CA của Ban
Cơ yếu Chính phủ ký chứng thư server MSSP) — KHÔNG gắn với 1 AP_ID cụ thể nào, nên **dùng chung được
cho mọi đơn vị** (không cần xin riêng cho Thanh Hóa).

**Đã xác minh THẬT** (17/09/2026, `openssl s_client -connect mpki1.ca.gov.vn:18083 -showcerts`): chuỗi
chứng thư server LIVE hiện tại —

```
leaf   CN=mpki1.ca.gov.vn                                    (cấp 13/11/2024, hết hạn 12/11/2029)
  ↑ issued by subca  fingerprint 75:E9:69:C0:7B:A3:5C:29:C7:2B:BC:1B:FB:88:0F:20:F3:89:1A:61:9B:00:B7:7E:B0:2C:A6:16:07:36:A1:FE
      ↑ issued by rootca fingerprint F7:BC:89:05:4C:EC:7F:12:73:0D:7B:8C:D3:EB:79:55:B1:63:83:50:87:E5:EB:87:3E:DF:B5:84:76:3B:3F:90
```

2 fingerprint `subca`/`rootca` này **KHỚP CHÍNH XÁC** với 2 entry `subca`/`rootca` đã có sẵn trong
`truststore-bcy.jks` (dù file này từ 2018 và leaf cert server đã được cấp lại 2024 — trust vẫn hợp lệ
vì anchor ở tầng sub/root CA, không phải leaf). Kết luận: **file này CÒN DÙNG ĐƯỢC**, không cần xin
lại. Đã xác nhận thêm: TCP/TLS reachable từ môi trường build (curl kết nối thành công tới cổng 18083)
— tường lửa không chặn ở tầng TCP, hạn chế theo IP (nếu có) chỉ áp dụng ở tầng ứng dụng AP.

## `3108937_VGCA_Application_Provider_certificate.p12` (đã có, ĐÃ cấu hình + bật `enabled=true`)

Cập nhật 17/09/2026 (đợt 2): người dùng cung cấp file này từ
`docs/TDSimSign/sdk-mobilepki/java/p12key/` và xác nhận **CHÍNH LÀ chứng thư đang cấu hình/chạy thật**
ở phía tích hợp .NET cùng dự án (KHÔNG chỉ để demo) — dù trùng byte-for-byte với file mẫu ở
`samplemssp/etc/` (SHA-256 giống hệt) và CN chứng thư là `VGCA Application Provider` (tên chung,
KHÔNG gắn theo tên với AP_ID `thanhhoa.qlvb` — việc AP_ID nào được dùng với chứng thư client nào do
MSSP đối chiếu ở phía server, không thể hiện qua CN chứng thư).

- Mật khẩu: `123456` (người dùng xác nhận trực tiếp — khớp `KEYSTORE_PASSWORD` mẫu trong
  `Samplemssp.java`).
- Còn hạn tới **26/02/2028** (`keytool -list -v -storetype PKCS12`, JDK đọc trực tiếp không cần cờ
  `-legacy` — khác OpenSSL 3.x cần `-legacy` do thuật toán mã hoá cũ RC2-40/3DES trong PKCS12 2018).
- File này KHÔNG commit vào git — khớp `.gitignore` (`certs/simpki/*.p12`), đã xác nhận bằng
  `git check-ignore -v`.
- Đã cấu hình `WORKER_TASKS_SIMPKI_KEYSTOREPATH`/`_KEYSTOREPASSWORD` trong `.env` (không commit) và
  **BẬT `WORKER_TASKS_SIMPKI_ENABLED=true`**.

### Đã thực nghiệm xác nhận (probe thủ công 1 lần, xoá test ngay sau khi chạy)

Gọi thật `VgcaMsspSignatureClient.sign()` với cấu hình trên từ máy build (KHÔNG nằm trong 2 IP đã
đăng ký, xem dưới) — **TLS mutual-auth THÀNH CÔNG** (bắt tay xong ~1.3s), request được MSSP CHẤP
NHẬN VÀ XỬ LÝ, trả về đúng 1 SOAP Fault chuẩn giao thức: `AxisFault: UNAUTHORIZED_ACCESS` (mã lỗi
`104`, đúng bảng mã lỗi chính thức `MSSP-Tai lieu dac ta tich hop AE-VNP.pdf`) — ĐÚNG hành vi kỳ vọng
khi gọi từ IP CHƯA đăng ký. Kết luận: cấu hình phần mềm (keystore/truststore/AP_ID/AP_PASSWORD) **đã
đúng và hoạt động** — rào cản CÒN LẠI DUY NHẤT là mạng/IP nguồn.

### Yêu cầu IP nguồn (rào cản còn lại)

Theo `docs/TDSimSign/sdk-mobilepki/HuongDanTichHop-SIM-PKI.pdf` mục I.4: Cục CTS-BMTT chỉ chấp nhận
kết nối từ (các) **IP Public đã đăng ký** cho AP. Theo
`docs/TDSimSign/sdk-mobilepki/java/thanhhoa.txt`, 2 IP đã đăng ký cho AP này:
`116.97.118.219` (= `117.6.43.97`, Sở TT&TT) và `222.255.21.194` (UBND tỉnh). **Máy chủ Worker chạy
`simpki.sign` thật PHẢI NAT ra Internet bằng 1 trong 2 IP này** (hoặc đăng ký thêm IP mới với Cục
CTS-BMTT nếu hạ tầng thay đổi) thì mới ký thành công thật — nếu không sẽ luôn nhận
`UNAUTHORIZED_ACCESS` như đã thực nghiệm ở trên.
