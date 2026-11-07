/**
 * @author Conzy
 **/
package conzy.server.network;

import conzy.server.server.GameServer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PacketHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger LOG = Logger.getLogger("army2");
    private final GameServer server;

    public PacketHandler(GameServer server) {
        this.server = server;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Session session = new Session(ctx.channel());
        ctx.channel().attr(Session.KEY).set(session);
        LOG.info("connect " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        Session session = Session.get(ctx);
        server.game().execute(() -> {
            try {
                if (packet.cmd == Cmd.KEY) {
                    server.lobby().handshake(session);
                    return;
                }
                server.lobby().onPacket(session, packet);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "packet " + packet.cmd, e);
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Session session = Session.get(ctx);
        server.game().execute(() -> server.lobby().disconnect(session));
        LOG.info("disconnect " + ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.log(Level.WARNING, "channel", cause);
        ctx.close();
    }
}
