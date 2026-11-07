/**
 * @author Conzy
 **/
package conzy.server.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class Bytes {
    private Bytes() {
    }

    public static DataInputStream in(byte[] body) {
        return new DataInputStream(new ByteArrayInputStream(body));
    }

    public static byte[] out(Writer writer) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            writer.write(out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return raw.toByteArray();
    }

    public interface Writer {
        void write(DataOutputStream out) throws IOException;
    }
}
