/**
 * @author Conzy
 **/
package conzy.server;

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

    @AfterAll
    static void down() {
        server.shutdown();
    }

    @Test
    void loginAndMapPack() throws Exception {
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
            login.writeUTF("secret");
            login.writeUTF("2.4.1");
            wire.send(1, rawLogin.toByteArray());

            Frame ok = wire.readUntil(3);
            DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(ok.body));
            Assertions.assertEquals("tester", in.readUTF());
            Assertions.assertTrue(in.readInt() > 0);

            wire.send(90, new byte[] {2, 0});
            Frame pack = wire.readUntil(90);
            DataInputStream file = new DataInputStream(new java.io.ByteArrayInputStream(pack.body));
            Assertions.assertEquals(2, file.readUnsignedByte());
            Assertions.assertEquals(1, file.readUnsignedByte());
            int len = file.readUnsignedShort();
            Assertions.assertEquals(1, len);
            Assertions.assertEquals(0, file.readUnsignedByte());

            try (Wire bad = Wire.open(server.port())) {
                Assertions.assertEquals("Vui lòng điền tài khoản mật khẩu", bad.fail("", ""));
                Assertions.assertEquals("Nhập sai tài khoản mật khẩu", bad.fail("tester", "nope"));
            }
            try (java.sql.Connection db = java.sql.DriverManager.getConnection(
                "jdbc:mariadb://127.0.0.1:3306/army2", "army2", "army2"
            )) {
                db.createStatement().executeUpdate("UPDATE player SET locked=1 WHERE name='tester'");
                try (Wire locked = Wire.open(server.port())) {
                    Assertions.assertEquals(
                        "Tài khoản của bạn đã bị khoá, vui lòng liên hệ admin",
                        locked.fail("tester", "secret")
                    );
                }
                db.createStatement().executeUpdate("UPDATE player SET locked=0 WHERE name='tester'");
            }
            try (Wire other = Wire.open(server.port())) {
                other.login("tester", "secret");
                Frame again = other.readUntil(3);
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
