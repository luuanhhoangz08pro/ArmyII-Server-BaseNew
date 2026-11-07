/**
 * @author Conzy
 **/
package conzy.server.network;

public final class Packet {
    public enum Frame {
        PLAIN,
        NORMAL,
        RAW_BODY
    }

    public final int cmd;
    public final byte[] body;
    public final Frame frame;

    public Packet(int cmd, byte[] body, Frame frame) {
        this.cmd = cmd & 0xFF;
        this.body = body == null ? new byte[0] : body;
        this.frame = frame;
    }
}
