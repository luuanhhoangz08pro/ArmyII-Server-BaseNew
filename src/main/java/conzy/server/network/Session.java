/**
 * @author Conzy
 **/
package conzy.server.network;

import conzy.server.player.Player;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

public final class Session {
    public static final AttributeKey<Session> KEY = AttributeKey.valueOf("army.session");

    public final Channel channel;
    public final Crypto crypto = new Crypto();
    public volatile boolean ready;
    public boolean loginReady;
    public Player player;

    public Session(Channel channel) {
        this.channel = channel;
    }

    public static Session get(ChannelHandlerContext ctx) {
        return ctx.channel().attr(KEY).get();
    }

    public void sendPlain(int cmd, byte[] body) {
        channel.writeAndFlush(new Packet(cmd, body, Packet.Frame.PLAIN));
    }

    public void send(int cmd, byte[] body) {
        channel.writeAndFlush(new Packet(cmd, body, Packet.Frame.NORMAL));
    }

    public void sendRawBody(int cmd, byte[] body) {
        channel.writeAndFlush(new Packet(cmd, body, Packet.Frame.RAW_BODY));
    }
}
