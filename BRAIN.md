# Bộ não — Army II Netty server

Đọc file này trước khi sửa `ArmyII-Server`. Làm tiếp đúng dự án này. Không dựng server mới, không bịa opcode, không bịa map, trang bị, dame, HP gốc, giá shop.

## Việc của bạn

Server Java Netty khớp client LibGDX `army2-gdx` bản 2.4.1 (`versionByte` 241). Client PC đang chạy là `Army2_v241_PC\Army2_v241_PC\Army2_PC\Army2.exe`. Source đối chiếu là `army2-gdx\core\src\main\java`. Khi source workspace và class trong `Army2-all.jar` khác nhau, tin file jar đang chạy.

Package `conzy.server`. Header mỗi file Java:

```java
/**
 * @author Conzy
 **/
```

Config sửa ở `ArmyII-Server\config.ini`. Không hard-code host, cổng, user database.

## Chạy

MariaDB 13 đang nghe `127.0.0.1:3306`, database `army2`, user `army2`, mật khẩu `army2`. Schema và 2 tài khoản: `ArmyII-Server\army2.sql`.

```powershell
$env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.3\jbr"
cd C:\Users\Administrator\Downloads\ArmyII-Full\ArmyII-Server
.\gradlew.bat run
```

Cổng game **19150**. Client trỏ `127.0.0.1:19150`. JDK để build là JBR ở đường dẫn trên. Test: `.\gradlew.bat test --offline`. Test dùng tài khoản `tester` / `secret` trong MariaDB.

Sau khi sửa server, bảo người dùng tắt process Gradle cũ rồi chạy lại. Process cũ vẫn nói protocol cũ.

## Login — câu chữ đúng từng ký tự

Gói thất bại là lệnh `4`, một `writeUTF`.

| Việc | Câu |
|---|---|
| Tên hoặc mật khẩu trống | Vui lòng điền tài khoản mật khẩu |
| Không có tên, hoặc sai mật khẩu | Nhập sai tài khoản mật khẩu |
| `player.locked <> 0` | Tài khoản của bạn đã bị khoá, vui lòng liên hệ admin |
| Vừa thoát hoặc vừa bị đá, còn N giây | Vui lòng chờ Ns |
| Máy khác đăng nhập cùng tài khoản | Tài khoản đã đăng nhập ở máy khác |

Không tự tạo tài khoản khi login. Muốn có acc thì `INSERT` vào `player`.

Một tài khoản một session. Login mới đúng mật khẩu thì `leave` bàn cũ, gửi câu đá cho session cũ, gán `player.session` sang session mới, rồi `close` session cũ. Trong `disconnect`, nếu `player.session` không còn là session vừa chết thì không xóa session mới. Session bị đá và session tự thoát đều chờ 10 giây (`PlayerDB.WAIT_MS`) mới login lại. Session mới vào ngay, không bị 10 giây đó chặn.

## Protocol đã kiểm trên client thật

TCP. Client gửi lệnh `-27` (byte `229`), độ dài short `0`. Server trả key 16 byte: `[keySize][key...]`, chưa XOR. Cả hai bên làm `key[i+1] ^= key[i]`. Sau đó mỗi byte XOR, con trỏ đọc và ghi riêng, quay vòng theo độ dài key.

Frame thường: 1 byte lệnh + 2 byte độ dài unsigned + body. Lệnh `90` và `-120` **server gửi đi** chỉ XOR byte lệnh; 4 byte độ dài và body không XOR. Client gửi lệnh `90` vẫn là frame short đã XOR.

`versionByte >= 240` nên login success (lệnh `3`) bắt đầu bằng `writeUTF(tên)` rồi mới `writeInt(id)`.

Chuỗi vào menu, client xin lần lượt lệnh `90`:

| type | Việc | Bản server đang gửi |
|---|---|---|
| 2 | map | version 1, số map = 0 |
| 1 | icon | version 0, không body |
| 3 | sprite người | version 0, không body |
| 4 | trang bị | version 4, có body, độ dài **int** |
| 5 | quân hàm | version 1, 16 tên, mốc level byte `0` vì client không có mốc |

Byte đầu body trang bị là **số bộ** (`10`), rồi mỗi bộ mới là `id`, `maxDame` short, `ntype`. `ntype = 0` thì không có item bên trong. Thiếu byte đếm này client cắt nhầm ảnh và tắt game. Sau các bộ là `short` độ dài icon (đang 0) rồi đúng 10 ảnh đạn. Ảnh đạn phải là PNG client giải được. Dùng `res/equip/01.png`. PNG 1×1 tự vẽ bị `Pixmap` báo corrupt và `forceExit` đóng cửa sổ.

Lệnh `64` sau login: 4 độ dài bảng = 0, `MAX_PLAYER`, số map boss = 0, rồi **thêm một byte** `NUMB_PLAYER`. Thiếu byte cuối client ném `EOFException`.

Lệnh `120` xin ảnh lớn. Server trả `-120`: `byte id`, `int len`, PNG. Ảnh nằm ở `res/BigImage/bigImage0.png` … `bigImage9.png`. Client chỉ gọi login thành công khi nhận id `9`, hoặc khi `nBigImage` lấy từ số bộ trang bị đủ để xin tới id 9.

Lệnh `230` (`-26`): trả `writeUTF(agent)` + `writeByte(provider)`. Config đang là `abc` và `1`.

Lệnh `58`, `127`, `114` client gửi lúc connect và không đọc trả lời. Cứ bỏ qua.

## Việc chưa có trong client — không bịa

Không có file gốc của: địa hình map (`valuesdata`), sprite nhân vật (`playerdata`), chỉ số trang bị, bảng gió/góc/lực lệnh `64`, mốc quân hàm, giá shop, HP gốc, dame quái. `res/pack` chỉ là file pack hình client đã có trong jar, không phải ba gói mạng type 2, 3, 4.

Vì không có đất, vào trận và đi được là chưa xong. `isLand` cần pixel map. Bắn hiện chỉ echo một điểm. HP 1000 và 20 giây một lượt là cấu hình server trong `Database.BASE_HP` và `TURN_SECONDS`, không phải số lấy từ client.

## Cách sửa

Đối từng `readByte/readShort/readInt/readUTF` trong `MessageHandler.java` và `CCanvas.readMess` trước khi thêm field. Log server dừng sớm thường là client chết ở frame sau, không phải server ngừng gửi. Crash pixmap nằm ở `CLib.Image` runnable, không có try/catch, desktop `forceExit=true`.

Cache client: `Army2_PC\rms`. `equipdata2` hỏng thì xóa cùng `equipVersion2` trước lần login sau, nếu không `sendMapData` parse cache cũ và tắt game trước khi tải bản mới.

SQL chỉ trong `PlayerDB`. Handler Netty không query. Login chạy trên thread game, không trên thread Netty. Xu, lượng, HP nằm trên `Player`. Không tạo class Currency.

Đừng đổi `Army2.exe` / `Army2-all.jar` trừ khi người dùng yêu cầu. Jar đang chạy mới là client thật.
