/**
 * @author Conzy
 **/
package conzy.server;

import conzy.server.database.PlayerDB;
import conzy.server.network.Crypto;
import conzy.server.server.GameServer;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class LoginWireTest {
    private static GameServer server;

    @BeforeAll
    static void up() throws InterruptedException {
        server = GameServer.start(0);
    }

    @org.junit.jupiter.api.AfterEach
    void passwordsStayOne() throws Exception {
        restorePasswords();
    }

    private static void restorePasswords() throws Exception {
        try (java.sql.Connection db = java.sql.DriverManager.getConnection(
            "jdbc:mariadb://127.0.0.1:3306/army2?allowPublicKeyRetrieval=true", "army2", "army2"
        );
            java.sql.PreparedStatement reset = db.prepareStatement("UPDATE player SET pass=?")) {
            reset.setString(1, conzy.server.database.PasswordHash.hash("1"));
            reset.executeUpdate();
        }
    }

    @AfterAll
    static void down() {
        server.shutdown();
    }

    @Test
    void loginAndMapPack() throws Exception {
        try (java.sql.Connection db = java.sql.DriverManager.getConnection(
            "jdbc:mariadb://127.0.0.1:3306/army2?allowPublicKeyRetrieval=true", "army2", "army2"
        );
            java.sql.PreparedStatement reset = db.prepareStatement(
                "UPDATE player SET pass=?, locked=0 WHERE name='tester'"
            )) {
            reset.setString(1, conzy.server.database.PasswordHash.hash("1"));
            reset.executeUpdate();
        }
        try (Wire wire = new Wire(server.port())) {
            wire.sendPlain(229, new byte[0]);
            Frame key = wire.read();
            Assertions.assertEquals(229, key.cmd);
            int size = key.body[0] & 255;
            byte[] raw = new byte[size];
            System.arraycopy(key.body, 1, raw, 0, size);
            wire.crypto.acceptRaw(raw);
            wire.ready = true;

            ByteArrayOutputStream rawLogin = new ByteArrayOutputStream();
            DataOutputStream login = new DataOutputStream(rawLogin);
            login.writeUTF("tester");
            login.writeUTF("1");
            login.writeUTF("2.4.1");
            wire.send(1, rawLogin.toByteArray());

            Frame ok = wire.readUntil(3);
            DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(ok.body));
            Assertions.assertEquals("tester", in.readUTF());
            Assertions.assertTrue(in.readInt() > 0);

            Frame tables = wire.readUntil(64);
            DataInputStream table = new DataInputStream(new java.io.ByteArrayInputStream(tables.body));
            Assertions.assertEquals(10, table.readUnsignedByte());
            Assertions.assertEquals(80, table.readUnsignedByte());
            table.skipBytes(9);
            Assertions.assertEquals(10, table.readUnsignedByte());
            Assertions.assertEquals(-45, table.readShort());
            table.skipBytes(18);
            Assertions.assertEquals(10, table.readUnsignedByte());
            table.skipBytes(10);
            Assertions.assertEquals(10, table.readUnsignedByte());
            table.skipBytes(10);
            Assertions.assertEquals(8, table.readUnsignedByte());
            Assertions.assertEquals(0, table.readUnsignedByte());
            Assertions.assertEquals(8, table.readUnsignedByte());

            Frame tiles = wire.readUntil(92);
            DataInputStream tile = new DataInputStream(new java.io.ByteArrayInputStream(tiles.body));
            Assertions.assertEquals(12, tile.readUnsignedShort());
            Assertions.assertEquals(8, tile.readUnsignedShort());

            wire.send(90, new byte[] {2, 0});
            Frame pack = wire.readUntil(90);
            DataInputStream file = new DataInputStream(new java.io.ByteArrayInputStream(pack.body));
            Assertions.assertEquals(2, file.readUnsignedByte());
            Assertions.assertEquals(19, file.readUnsignedByte());
            int len = file.readUnsignedShort();
            Assertions.assertEquals(13412, len);
            Assertions.assertEquals(40, file.readUnsignedByte());

            Assertions.assertEquals("Nhập sai mật khẩu cũ", wire.change("nope", "secret2"));
            Assertions.assertEquals("Nhập sai mật khẩu cũ", wire.change("Secret", "secret2"));
            Assertions.assertEquals("Nhập sai mật khẩu cũ", wire.change("secret ", "secret2"));
            Assertions.assertEquals("Bạn phải nhập mật khẩu cũ.", wire.change("", "secret2"));
            Assertions.assertEquals("Bạn phải nhập mật khẩu mới.", wire.change("1", ""));
            Assertions.assertEquals("Mật khẩu mới phải từ 5 đến 12 ký tự", wire.change("1", "1234"));
            Assertions.assertEquals("Mật khẩu mới phải từ 5 đến 12 ký tự", wire.change("1", "x".repeat(13)));
            Assertions.assertEquals("Đổi mật khẩu thành công", wire.change("1", "secret2"));
            try (java.sql.Connection db = java.sql.DriverManager.getConnection(
                "jdbc:mariadb://127.0.0.1:3306/army2?allowPublicKeyRetrieval=true", "army2", "army2"
            )) {
                java.sql.ResultSet rows = db.createStatement().executeQuery("SELECT pass FROM player WHERE name='tester'");
                Assertions.assertTrue(rows.next());
                String stored = rows.getString(1);
                Assertions.assertEquals(conzy.server.database.PasswordHash.hash("secret2"), stored);
            }
            restorePasswords();
            wire.send(79, new byte[] {0});
            wire.send(31, new byte[0]);
            Frame missing = wire.readUntil(45);
            Assertions.assertEquals(
                "Chức năng đang phát triển (31)",
                new DataInputStream(new java.io.ByteArrayInputStream(missing.body)).readUTF()
            );
            ByteArrayOutputStream joinRaw = new ByteArrayOutputStream();
            DataOutputStream joinBody = new DataOutputStream(joinRaw);
            joinBody.writeByte(0);
            joinBody.writeByte(0);
            joinBody.writeUTF("");
            wire.send(8, joinRaw.toByteArray());
            wire.readUntil(8);
            server.game().submit(() -> server.lobby().addBots("Bot", 1)).get();
            Frame joined = wire.readUntil(12);
            DataInputStream bot = new DataInputStream(new java.io.ByteArrayInputStream(joined.body));
            bot.readByte();
            int botId = bot.readInt();
            Assertions.assertTrue(botId < -1);
            bot.readShort();
            Assertions.assertTrue(bot.readUTF().startsWith("Bot"));
            Frame ready = wire.readUntil(16);
            DataInputStream flag = new DataInputStream(new java.io.ByteArrayInputStream(ready.body));
            Assertions.assertEquals(botId, flag.readInt());
            Assertions.assertTrue(flag.readBoolean());
            server.game().submit(() -> server.lobby().removeBots()).get();

            try (java.sql.Connection db = java.sql.DriverManager.getConnection(
                "jdbc:mariadb://127.0.0.1:3306/army2?allowPublicKeyRetrieval=true", "army2", "army2"
            );
                java.sql.PreparedStatement reset = db.prepareStatement(
                    "UPDATE player SET pass=?, locked=0 WHERE name='1'"
                )) {
                reset.setString(1, conzy.server.database.PasswordHash.hash("1"));
                reset.executeUpdate();
            }
            try (Wire other = Wire.open(server.port())) {
                other.login("1", "1");
                Frame who = other.readUntil(3);
                DataInputStream whoIn = new DataInputStream(new java.io.ByteArrayInputStream(who.body));
                whoIn.readUTF();
                int otherId = whoIn.readInt();
                ByteArrayOutputStream rawChat = new ByteArrayOutputStream();
                DataOutputStream chat = new DataOutputStream(rawChat);
                chat.writeInt(otherId);
                chat.writeUTF("chao");
                wire.send(5, rawChat.toByteArray());
                Frame letter = other.readUntil(5);
                DataInputStream letterIn = new DataInputStream(new java.io.ByteArrayInputStream(letter.body));
                Assertions.assertTrue(letterIn.readInt() > 0);
                Assertions.assertEquals("tester", letterIn.readUTF());
                Assertions.assertEquals("chao", letterIn.readUTF());
            }
            try (java.sql.Connection db = java.sql.DriverManager.getConnection(
                "jdbc:mariadb://127.0.0.1:3306/army2?allowPublicKeyRetrieval=true", "army2", "army2"
            )) {
                java.sql.ResultSet rows = db.createStatement().executeQuery(
                    "SELECT kind, from_name, to_name, body FROM chat_log WHERE body='chao' ORDER BY id DESC LIMIT 1"
                );
                Assertions.assertTrue(rows.next());
                Assertions.assertEquals("riêng", rows.getString(1));
                Assertions.assertEquals("tester", rows.getString(2));
                Assertions.assertEquals("1", rows.getString(3));
                Assertions.assertEquals("chao", rows.getString(4));
            }
            ByteArrayOutputStream missingChat = new ByteArrayOutputStream();
            DataOutputStream missingOut = new DataOutputStream(missingChat);
            missingOut.writeInt(999999);
            missingOut.writeUTF("chao");
            wire.send(5, missingChat.toByteArray());
            Frame offline = wire.readUntil(45);
            Assertions.assertEquals(
                "Người này không online",
                new DataInputStream(new java.io.ByteArrayInputStream(offline.body)).readUTF()
            );
            server.game().submit(() -> server.lobby().announce("bao tri nhe", 0, 0, false, false)).get();
            Frame note = wire.readUntil(5);
            DataInputStream noteIn = new DataInputStream(new java.io.ByteArrayInputStream(note.body));
            Assertions.assertEquals(0, noteIn.readInt());
            Assertions.assertEquals("Admin", noteIn.readUTF());
            Assertions.assertEquals("bao tri nhe", noteIn.readUTF());

            try (Wire bad = Wire.open(server.port())) {
                Assertions.assertEquals("Vui lòng điền tài khoản mật khẩu", bad.fail("", ""));
                Assertions.assertEquals("Nhập sai tài khoản mật khẩu", bad.fail("tester", "nope"));
            }
            try (java.sql.Connection db = java.sql.DriverManager.getConnection(
                "jdbc:mariadb://127.0.0.1:3306/army2?allowPublicKeyRetrieval=true", "army2", "army2"
            )) {
                db.createStatement().executeUpdate("UPDATE player SET locked=1 WHERE name='tester'");
                try (Wire locked = Wire.open(server.port())) {
                    Assertions.assertEquals(
                        "Tài khoản của bạn đã bị khoá, vui lòng liên hệ admin",
                        locked.fail("tester", "1")
                    );
                }
                db.createStatement().executeUpdate("UPDATE player SET locked=0 WHERE name='tester'");
            }
            try (java.sql.Connection db = java.sql.DriverManager.getConnection(
                "jdbc:mariadb://127.0.0.1:3306/army2?allowPublicKeyRetrieval=true", "army2", "army2"
            )) {
                java.sql.ResultSet who = db.createStatement().executeQuery("SELECT id FROM player WHERE name='tester'");
                Assertions.assertTrue(who.next());
                int testerId = who.getInt(1);
                server.game().submit(() -> {
                    try {
                        PlayerDB.get().queueMail(testerId, 0, "Admin", "cho vao", 0, 0);
                    } catch (java.sql.SQLException e) {
                        throw new RuntimeException(e);
                    }
                }).get();
            }
            try (Wire other = Wire.open(server.port())) {
                other.login("tester", "1");
                boolean saw = false;
                Frame again = null;
                for (int i = 0; i < 60; i++) {
                    Frame frame = other.read();
                    if (frame.cmd == 5) {
                        DataInputStream waitingIn = new DataInputStream(new java.io.ByteArrayInputStream(frame.body));
                        waitingIn.readInt();
                        waitingIn.readUTF();
                        if ("cho vao".equals(waitingIn.readUTF())) {
                            saw = true;
                        }
                        continue;
                    }
                    if (frame.cmd == 3) {
                        again = frame;
                        break;
                    }
                }
                Assertions.assertTrue(saw);
                Assertions.assertNotNull(again);
                Assertions.assertEquals("tester", new DataInputStream(new java.io.ByteArrayInputStream(again.body)).readUTF());
                Frame kicked = wire.readUntil(4);
                Assertions.assertEquals(
                    "Tài khoản đã đăng nhập ở máy khác",
                    new DataInputStream(new java.io.ByteArrayInputStream(kicked.body)).readUTF()
                );
            }
        }
    }

    private static final class Frame {
        final int cmd;
        final byte[] body;

        Frame(int cmd, byte[] body) {
            this.cmd = cmd;
            this.body = body;
        }
    }

    private static final class Wire implements AutoCloseable {
        final Socket socket = new Socket();
        final DataInputStream in;
        final DataOutputStream out;
        final Crypto crypto = new Crypto();
        boolean ready;

        Wire(int port) throws IOException {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            socket.setSoTimeout(5000);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        }

        static Wire open(int port) throws IOException {
            Wire wire = new Wire(port);
            wire.sendPlain(229, new byte[0]);
            Frame key = wire.read();
            byte[] raw = new byte[key.body[0] & 255];
            System.arraycopy(key.body, 1, raw, 0, raw.length);
            wire.crypto.acceptRaw(raw);
            wire.ready = true;
            return wire;
        }

        void login(String name, String pass) throws IOException {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream login = new DataOutputStream(raw);
            login.writeUTF(name);
            login.writeUTF(pass);
            login.writeUTF("2.4.1");
            send(1, raw.toByteArray());
        }

        String change(String oldPass, String next) throws IOException {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(raw);
            body.writeUTF(oldPass);
            body.writeUTF(next);
            send(81, raw.toByteArray());
            Frame frame = readUntil(45);
            return new DataInputStream(new java.io.ByteArrayInputStream(frame.body)).readUTF();
        }

        String fail(String name, String pass) throws IOException {
            login(name, pass);
            Frame frame = readUntil(4);
            return new DataInputStream(new java.io.ByteArrayInputStream(frame.body)).readUTF();
        }

        void sendPlain(int cmd, byte[] body) throws IOException {
            out.writeByte(cmd);
            out.writeShort(body.length);
            out.write(body);
            out.flush();
        }

        void send(int cmd, byte[] body) throws IOException {
            out.writeByte(crypto.cryptWrite((byte) cmd));
            out.writeByte(crypto.cryptWrite((byte) (body.length >> 8)));
            out.writeByte(crypto.cryptWrite((byte) (body.length & 255)));
            for (byte value : body) {
                out.writeByte(crypto.cryptWrite(value));
            }
            out.flush();
        }

        Frame readUntil(int cmd) throws IOException {
            while (true) {
                Frame frame = read();
                if (frame.cmd == cmd) {
                    return frame;
                }
            }
        }

        Frame read() throws IOException {
            if (!ready) {
                int cmd = in.readUnsignedByte();
                int len = in.readUnsignedShort();
                return new Frame(cmd, in.readNBytes(len));
            }
            int cmd = crypto.cryptRead(in.readByte()) & 255;
            if (cmd == 90 || cmd == (-120 & 0xFF)) {
                int len = in.readInt();
                return new Frame(cmd, in.readNBytes(len));
            }
            int len = ((crypto.cryptRead(in.readByte()) & 255) << 8) | (crypto.cryptRead(in.readByte()) & 255);
            byte[] body = new byte[len];
            for (int i = 0; i < len; i++) {
                body[i] = crypto.cryptRead(in.readByte());
            }
            return new Frame(cmd, body);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
