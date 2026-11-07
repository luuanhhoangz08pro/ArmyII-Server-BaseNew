# Army II

Mình giữ bản client PC này vì còn muốn vào lại cái game cũ, không phải để khoe kiến trúc. Client là `army2-gdx`, server mình dựng cạnh nó là `ArmyII-Server`. Hai đứa nói chuyện được với nhau tới màn hình chờ sau login. Vào trận rồi đi trên đất thì chưa, vì đất không nằm trong client.

## Trong này có gì

`army2-gdx` là client LibGDX 1.10, Java 8, chạy desktop bằng LWJGL. Gradle. Ảnh, font, tiếng nằm trong `core/src/main/resources/res`. Phần mạng nằm ở `core/src/main/java/network`. Bản mình đang bấm là `Army2.exe` 2.4.1.

`ArmyII-Server` là server Java 17, Gradle Kotlin, Netty 4.1. Tài khoản để trong MariaDB, cấu hình ở `config.ini`, ảnh lớn và icon trang bị để trong `res/`. Test login nằm ở `LoginWireTest`.

Socket là kiểu Teamobi ngày xưa. Nối TCP, client xin khóa bằng lệnh `-27`, hai bên XOR từng byte. Lệnh `90` và `-120` server gửi đi thì chỉ XOR mỗi byte lệnh, phần còn lại để trần. Mình học cái này bằng cách đọc client, không chép protocol từ trí nhớ.

## Đã chạy được

Login thật, không còn tạo acc trong RAM rồi tắt máy là mất.

- Sai tên hoặc sai mật khẩu: *Nhập sai tài khoản mật khẩu*
- Bỏ trống: *Vui lòng điền tài khoản mật khẩu*
- Acc bị khóa (`locked = 1`): *Tài khoản của bạn đã bị khoá, vui lòng liên hệ admin*
- Vừa thoát hoặc vừa bị đá: phải chờ 10 giây, client nhận *Vui lòng chờ Ns*
- Một acc một máy. Máy kia đăng nhập đúng thì máy đang chơi bị đá, câu hiện ra là *Tài khoản đã đăng nhập ở máy khác*

Sau login, client xin lần lượt gói map, icon, sprite, trang bị, quân hàm. Server trả đủ để nó không đứng hình giữa đường. Ảnh lớn `bigImage0` tới `bigImage9` lấy từ thư mục `res` của chính client. Gói trang bị có byte đếm số bộ đứng trước, ảnh đạn là `res/equip/01.png` thật. Thiếu hai cái đó là cửa sổ tắt luôn, `forceExit` không thương tiếc. Mình đã mất vài lần login chỉ để hiểu chuyện này.

Catalog item, tên súng, loại đạn, tên quân hàm, tên file trong các gói hình thì kéo từ source client ra `catalog.json`.

Lobby có khu, bàn, vào phòng, sẵn sàng, chat, đổi súng. Bắn và đi thì server chỉ gửi lại tọa độ cho khớp gói, chưa tính đạn chạm đất.

File `army2.sql` để mang database sang máy khác. Trong đó có sẵn acc `tester` / `secret` và `1` / `1`.

## Chưa có, và mình không bịa

Client không giữ địa hình map, sprite người chơi, chỉ số trang bị, bảng gió, mốc level quân hàm, giá shop. Mấy thứ đó ngày xưa server gốc gửi xuống. Trong repo không có file gốc, nên server đang gửi map rỗng và trang bị không có món nào bên trong.

Vì vậy menu thì tới được, còn đứng trên map và bắn một phát cho đã thì chưa. HP 1000 và 20 giây một lượt chỉ là số mình đặt tạm để gói vào trận không vỡ.

## Chạy server

MariaDB nghe `127.0.0.1:3306`. Database `army2`, user `army2`, mật khẩu `army2`. Sửa trong `ArmyII-Server/config.ini` nếu máy bạn khác.

```powershell
cd ArmyII-Server
.\gradlew.bat run
```

Game nghe cổng `19150`. Trỏ client vào `127.0.0.1:19150`. Sửa xong nhớ tắt bản server cũ đang chạy, không thì bạn vẫn đang nói chuyện với bản hôm qua.

```powershell
.\gradlew.bat test
```
