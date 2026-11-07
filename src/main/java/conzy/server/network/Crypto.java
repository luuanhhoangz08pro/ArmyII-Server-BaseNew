/**
 * @author Conzy
 **/
package conzy.server.network;

public final class Crypto {
    private byte[] key = new byte[0];
    private int readIndex;
    private int writeIndex;

    public void acceptRaw(byte[] raw) {
        byte[] next = raw.clone();
        for (int i = 0; i < next.length - 1; i++) {
            next[i + 1] = (byte) (next[i + 1] ^ next[i]);
        }
        key = next;
        readIndex = 0;
        writeIndex = 0;
    }

    public int readIndex() {
        return readIndex;
    }

    public void readIndex(int index) {
        readIndex = index;
    }

    public byte cryptRead(byte value) {
        return crypt(value, true);
    }

    public byte cryptWrite(byte value) {
        return crypt(value, false);
    }

    private byte crypt(byte value, boolean read) {
        int index = read ? readIndex : writeIndex;
        byte out = (byte) ((key[index] & 255) ^ (value & 255));
        index++;
        if (index >= key.length) {
            index = index % key.length;
        }
        if (read) {
            readIndex = index;
        } else {
            writeIndex = index;
        }
        return out;
    }
}
