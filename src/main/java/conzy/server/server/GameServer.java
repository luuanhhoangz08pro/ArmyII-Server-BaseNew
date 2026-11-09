/**
 * @author Conzy
 **/
package conzy.server.server;

import conzy.server.admin.AdminFrame;
import conzy.server.database.Config;
import conzy.server.database.Database;
import conzy.server.database.PlayerDB;
import conzy.server.network.ArmyCodec;
import conzy.server.network.PacketHandler;
import conzy.server.service.CombatService;
import conzy.server.service.LobbyService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class GameServer {
    private static final Logger LOG = Logger.getLogger("army2");
    private final ScheduledExecutorService game = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "game");
        thread.setDaemon(true);
        return thread;
    });
    private final CombatService combat = new CombatService();
    private final LobbyService lobby = new LobbyService(combat);
    private NioEventLoopGroup boss;
    private NioEventLoopGroup worker;
    private Channel channel;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Config config = Config.load(Path.of("config.ini"));
        int port = args.length > 0 ? Integer.parseInt(args[0]) : config.port;
        GameServer server = start(port);
        SwingUtilities.invokeLater(() -> new AdminFrame(server).setVisible(true));
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        server.await();
    }
    public static GameServer start(int port) throws InterruptedException {
        if (Config.get() == null) {
            Config.load(Path.of("config.ini"));
        }
        try {
            PlayerDB.get().open(Config.get());
        } catch (SQLException e) {
            throw new IllegalStateException("MariaDB " + Config.get().dbHost + ":" + Config.get().dbPort, e);
        }
        Database.get().load();
        LOG.info("database " + Config.get().dbName);
        GameServer server = new GameServer();
        server.boss = new NioEventLoopGroup(1);
        server.worker = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(server.boss, server.worker)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new ArmyCodec.Decoder(), new ArmyCodec.Encoder(), new PacketHandler(server));
                }
            });
        server.channel = bootstrap.bind(port).sync().channel();
        server.game.scheduleAtFixedRate(() -> {
            try {
                server.lobby.tick();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "tick", e);
            }
        }, 200, 200, TimeUnit.MILLISECONDS);
        LOG.info("listen " + server.channel.localAddress());
        return server;
    }

    public ScheduledExecutorService game() {
        return game;
    }

    public LobbyService lobby() {
        return lobby;
    }

    public int port() {
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    public void await() throws InterruptedException {
        channel.closeFuture().sync();
    }

    public void shutdown() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
        if (boss != null) {
            boss.shutdownGracefully();
        }
        if (worker != null) {
            worker.shutdownGracefully();
        }
        game.shutdownNow();
    }
}
