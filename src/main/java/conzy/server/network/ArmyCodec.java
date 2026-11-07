/**
 * @author Conzy
 **/
package conzy.server.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.List;

public final class ArmyCodec {
    public static final int MAX_BODY = 2 * 1024 * 1024;

    private ArmyCodec() {
    }

    public static final class Decoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            Session session = Session.get(ctx);
            if (session == null || !session.ready) {
                decodePlain(ctx, in, out);
                return;
            }
            decodeNormal(ctx, session, in, out);
        }

        private void decodePlain(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            while (in.readableBytes() >= 3) {
                int mark = in.readerIndex();
                int cmd = in.readUnsignedByte();
                int len = in.readUnsignedShort();
                if (len > MAX_BODY) {
                    ctx.close();
                    return;
                }
                if (in.readableBytes() < len) {
                    in.readerIndex(mark);
                    return;
                }
                byte[] body = new byte[len];
                in.readBytes(body);
                out.add(new Packet(cmd, body, Packet.Frame.PLAIN));
            }
        }

        private void decodeNormal(ChannelHandlerContext ctx, Session session, ByteBuf in, List<Object> out) {
            while (in.readableBytes() >= 3) {
                int mark = in.readerIndex();
                int cryptoMark = session.crypto.readIndex();
                int cmd = session.crypto.cryptRead(in.readByte()) & 255;
                int hi = session.crypto.cryptRead(in.readByte()) & 255;
                int lo = session.crypto.cryptRead(in.readByte()) & 255;
                int len = (hi << 8) | lo;
                if (len > MAX_BODY) {
                    ctx.close();
                    return;
                }
                if (in.readableBytes() < len) {
                    in.readerIndex(mark);
                    session.crypto.readIndex(cryptoMark);
                    return;
                }
                byte[] body = new byte[len];
                for (int i = 0; i < len; i++) {
                    body[i] = session.crypto.cryptRead(in.readByte());
                }
                out.add(new Packet(cmd, body, Packet.Frame.NORMAL));
            }
        }
    }

    public static final class Encoder extends MessageToByteEncoder<Packet> {
        @Override
        protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) {
            Session session = Session.get(ctx);
            if (packet.frame == Packet.Frame.PLAIN || session == null || !session.ready) {
                out.writeByte(packet.cmd);
                out.writeShort(packet.body.length);
                out.writeBytes(packet.body);
                return;
            }
            if (packet.frame == Packet.Frame.RAW_BODY) {
                out.writeByte(session.crypto.cryptWrite((byte) packet.cmd));
                out.writeInt(packet.body.length);
                out.writeBytes(packet.body);
                return;
            }
            out.writeByte(session.crypto.cryptWrite((byte) packet.cmd));
            int len = packet.body.length;
            out.writeByte(session.crypto.cryptWrite((byte) (len >> 8)));
            out.writeByte(session.crypto.cryptWrite((byte) (len & 255)));
            for (byte value : packet.body) {
                out.writeByte(session.crypto.cryptWrite(value));
            }
        }
    }
}
