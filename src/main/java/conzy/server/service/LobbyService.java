/**
 * @author Conzy
 **/
package conzy.server.service;

import conzy.server.database.Database;
import conzy.server.database.PlayerDB;
import conzy.server.network.Bytes;
import conzy.server.network.Cmd;
import conzy.server.network.Packet;
import conzy.server.network.PacketSender;
import conzy.server.network.Session;
import conzy.server.player.Player;
import java.io.DataInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class LobbyService {
    private static final Logger LOG = Logger.getLogger("army2");
    private final CombatService combat;
    private final Board board = new Board(0, 0);

    public LobbyService(CombatService combat) {
        this.combat = combat;
    }

    public void tick() {
        combat.tick(board);
    }

    public void handshake(Session session) {
        if (session.ready) {
            return;
        }
        byte[] raw = new byte[16];
        ThreadLocalRandom.current().nextBytes(raw);
        byte[] body = new byte[raw.length + 1];
        body[0] = (byte) raw.length;
        System.arraycopy(raw, 0, body, 1, raw.length);
        session.sendPlain(Cmd.KEY, body);
        session.crypto.acceptRaw(raw);
        session.ready = true;
        LOG.info("key exchanged");
    }

    public void disconnect(Session session) {
        if (session == null || session.player == null) {
            return;
        }
        Player player = session.player;
        if (player.session != session) {
            session.player = null;
            PlayerDB.get().markLeft(player.name);
            return;
        }
        leave(player);
        try {
            PlayerDB.get().save(player);
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "save " + player.name, e);
        }
        PlayerDB.get().offline(player);
        PlayerDB.get().markLeft(player.name);
        player.session = null;
        session.player = null;
    }

    public void onPacket(Session session, Packet packet) throws IOException {
        DataInputStream in = Bytes.in(packet.body);
        switch (packet.cmd) {
            case Cmd.LOGIN -> login(session, in);
            case Cmd.AGENT -> PacketSender.agent(session);
            case Cmd.LOGOUT, Cmd.SIGN_OUT -> session.channel.close();
            case Cmd.PING -> ping(session, in);
            case Cmd.RANKING -> ranking(session, in);
            case Cmd.EMPTY_ROOM -> emptyRoom(session, in);
            case Cmd.ROOM_LIST -> PacketSender.classicRooms(session);
            case Cmd.BOARD_LIST -> classicBoards(session, in);
            case Cmd.JOIN, Cmd.JOIN_ANY -> join(session, in, packet.cmd == Cmd.JOIN_ANY);
            case Cmd.LEAVE -> {
                if (session.player != null) {
                    leave(session.player);
                }
            }
            case Cmd.READY -> ready(session, in);
            case Cmd.START -> start(session);
            case Cmd.CHAT_BOARD -> chat(session, in);
            case Cmd.SET_MONEY -> setMoney(session, in);
            case Cmd.SET_PASS -> {
                if (session.player != null && session.player.board != null) {
                    session.player.board.password = in.readUTF();
                }
            }
            case Cmd.KICK -> kick(session, in);
            case Cmd.CHOOSE_GUN -> chooseGun(session, in);
            case Cmd.CHANGE_TEAM -> changeTeam(session);
            case Cmd.MAP_SELECT, Cmd.CHOOSE_MAP -> chooseMap(session, in);
            case Cmd.CHANGE_MODE -> {
                if (session.player != null && session.player.board != null) {
                    session.player.board.mode = in.readUnsignedByte();
                }
            }
            case Cmd.MAX_PLAYER -> {
                if (session.player != null && session.player.board != null) {
                    in.readUnsignedByte();
                }
            }
            case Cmd.FIRE -> combat.fire(session, in);
            case Cmd.MOVE -> combat.move(session, in);
            case Cmd.UPDATE_XY -> combat.xy(session, in);
            case Cmd.USE_ITEM -> combat.useItem(session, in);
            case Cmd.SKIP -> combat.skip(session);
            case Cmd.FILE -> file(session, in);
            case Cmd.BIG_IMAGE -> PacketSender.bigImage(session, in.readUnsignedByte());
            case Cmd.FRIENDS -> PacketSender.friends(session);
            case Cmd.STRONGEST -> {
                if (in.available() >= 4) {
                    in.readInt();
                }
                PacketSender.strongest(session);
            }
            case Cmd.DETAIL -> detail(session, in);
            case Cmd.CHAR_INFO -> {
                if (session.player != null) {
                    PacketSender.charInfo(session, session.player);
                }
            }
            case Cmd.BUY_ITEM -> buy(session, in);
            case Cmd.SHOP_EQUIP -> PacketSender.shopEquip(session);
            case Cmd.SHOP_LINH_TINH, Cmd.SHOP_CLAN -> shop(session, in, packet.cmd);
            case Cmd.MISSION -> {
                if (in.readUnsignedByte() == 1 && in.available() > 0) {
                    in.readUnsignedByte();
                }
                PacketSender.missionEmpty(session);
            }
            case Cmd.ROOM_NAMES -> PacketSender.roomNames(session);
            case Cmd.FIND_PLAYER -> find(session, in);
            default -> LOG.info("unhandled cmd " + packet.cmd);
        }
    }

    private void login(Session session, DataInputStream in) throws IOException {
        String name = in.readUTF();
        String pass = in.readUTF();
        String version = in.readUTF();
        if (name.isBlank() || pass.isBlank()) {
            PacketSender.loginFail(session, "Vui lòng điền tài khoản mật khẩu");
            return;
        }
        long wait = PlayerDB.get().remainingWait(name);
        if (wait > 0) {
            long seconds = (wait + 999) / 1000;
            PacketSender.loginFail(session, "Vui lòng chờ " + seconds + "s");
            return;
        }
        PlayerDB.Login account;
        try {
            account = PlayerDB.get().open(name, pass);
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "login " + name, e);
            PacketSender.loginFail(session, "database");
            return;
        }
        if (account.locked) {
            PacketSender.loginFail(session, "Tài khoản của bạn đã bị khoá, vui lòng liên hệ admin");
            return;
        }
        if (!account.ok) {
            PacketSender.loginFail(session, "Nhập sai tài khoản mật khẩu");
            return;
        }
        Player player = account.player;
        Session previous = player.session;
        if (previous != null && previous != session) {
            leave(player);
            PacketSender.loginFail(previous, "Tài khoản đã đăng nhập ở máy khác");
            player.session = session;
            session.player = player;
            previous.channel.close();
        } else {
            player.session = session;
            session.player = player;
        }
        LOG.info("login " + name + " id " + player.id + " version " + version);
        PacketSender.loginOk(session, player);
    }

    private void ping(Session session, DataInputStream in) throws IOException {
        if (in.available() >= 4) {
            in.readInt();
        }
        PacketSender.ping(session);
    }

    private void ranking(Session session, DataInputStream in) throws IOException {
        int type = in.readByte();
        in.readByte();
        if (type == -1) {
            PacketSender.rankingMenus(session);
        }
    }

    private void emptyRoom(Session session, DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        if (type == 1 && in.available() > 0) {
            in.readByte();
        }
        if (type == 2 && in.available() > 0) {
            in.readUTF();
        }
        if (type == 0) {
            PacketSender.zones(session);
        } else {
            PacketSender.boards(session, board.roomId, board.boardId, board.occupants(), board.money);
        }
    }

    private void classicBoards(Session session, DataInputStream in) throws IOException {
        int room = in.readUnsignedByte();
        PacketSender.classicBoards(session, room, board.boardId, board.occupants(), board.money, board.mode);
    }

    private void join(Session session, DataInputStream in, boolean any) throws IOException {
        Player player = session.player;
        if (player == null) {
            return;
        }
        if (!any) {
            in.readByte();
            in.readByte();
            String pass = in.readUTF();
            if (!board.password.isEmpty() && !board.password.equals(pass)) {
                PacketSender.message(session, "wrong pass");
                return;
            }
        } else {
            in.readByte();
        }
        if (board.fighting) {
            PacketSender.message(session, "playing");
            return;
        }
        if (player.board == board) {
            PacketSender.join(session, board);
            return;
        }
        int seat = freeSeat();
        if (seat < 0) {
            PacketSender.message(session, "full");
            return;
        }
        player.seat = seat;
        player.board = board;
        player.ready = false;
        board.seats[seat] = player;
        if (board.ownerId == 0) {
            board.ownerId = player.id;
            player.ready = true;
        }
        PacketSender.join(session, board);
        board.each(other -> {
            if (other != session) {
                PacketSender.someoneJoin(other, player);
            }
        });
        LOG.info("join " + player.name + " seat " + seat);
    }

    private void leave(Player player) {
        Board current = player.board;
        if (current == null) {
            return;
        }
        int seat = player.seat;
        if (seat >= 0 && seat < current.seats.length && current.seats[seat] == player) {
            current.seats[seat] = null;
        }
        player.board = null;
        player.seat = -1;
        player.ready = false;
        if (current.ownerId == player.id) {
            current.ownerId = 0;
            for (Player other : current.seats) {
                if (other != null) {
                    current.ownerId = other.id;
                    other.ready = true;
                    break;
                }
            }
        }
        if (current.occupants() == 0) {
            combat.stop(current);
            current.ownerId = 0;
            current.money = 0;
        }
        int owner = current.ownerId;
        current.each(other -> PacketSender.leave(other, player.id, owner));
        if (player.session != null && player.session.channel.isActive()) {
            PacketSender.leave(player.session, player.id, owner);
        }
    }

    private void ready(Session session, DataInputStream in) throws IOException {
        Player player = session.player;
        if (player == null || player.board == null) {
            return;
        }
        boolean ready = in.readBoolean();
        if (player.id == player.board.ownerId) {
            ready = true;
        }
        player.ready = ready;
        player.board.each(target -> PacketSender.ready(target, player.id, player.ready));
    }

    private void start(Session session) {
        Player player = session.player;
        if (player == null || player.board == null || player.id != player.board.ownerId) {
            return;
        }
        LOG.info("start board " + player.board.boardId);
        combat.start(player.board);
    }

    private void chat(Session session, DataInputStream in) throws IOException {
        Player player = session.player;
        if (player == null || player.board == null) {
            return;
        }
        String text = in.readUTF();
        player.board.each(target -> PacketSender.chatBoard(target, player.id, text));
    }

    private void setMoney(Session session, DataInputStream in) throws IOException {
        Player player = session.player;
        if (player == null || player.board == null || player.id != player.board.ownerId) {
            return;
        }
        int money = in.readInt();
        if (money < 0) {
            PacketSender.moneyError(session, "money");
            return;
        }
        player.board.money = money;
        player.board.each(target -> PacketSender.money(target, money));
    }

    private void kick(Session session, DataInputStream in) throws IOException {
        Player player = session.player;
        if (player == null || player.board == null || player.id != player.board.ownerId) {
            return;
        }
        int id = in.readInt();
        Player target = find(id);
        if (target == null || target.board != player.board || target.ready) {
            return;
        }
        player.board.each(other -> PacketSender.kick(other, id, "kick"));
        leave(target);
    }

    private void chooseGun(Session session, DataInputStream in) throws IOException {
        Player player = session.player;
        if (player == null || player.board == null) {
            return;
        }
        int gun = in.readUnsignedByte();
        if (gun > 10) {
            return;
        }
        player.gun = gun;
        player.board.each(target -> PacketSender.gun(target, player.id, gun));
    }

    private void changeTeam(Session session) {
        Player player = session.player;
        if (player == null || player.board == null) {
            return;
        }
        int next = freeSeat();
        if (next < 0) {
            return;
        }
        player.board.seats[player.seat] = null;
        player.seat = next;
        player.board.seats[next] = player;
        player.board.each(target -> PacketSender.team(target, player.id, next));
    }

    private void chooseMap(Session session, DataInputStream in) throws IOException {
        Player player = session.player;
        if (player == null || player.board == null) {
            return;
        }
        int map = in.readUnsignedByte();
        player.board.mapId = map;
        player.board.each(target -> PacketSender.mapSelected(target, map));
    }

    private void buy(Session session, DataInputStream in) throws IOException {
        if (session.player == null) {
            return;
        }
        in.readUnsignedByte();
        in.readUnsignedByte();
        in.readUnsignedByte();
        PacketSender.message(session, "no price");
        PacketSender.buyResult(session, session.player);
    }

    private void shop(Session session, DataInputStream in, int cmd) throws IOException {
        int action = in.readUnsignedByte();
        if (action == 1) {
            if (in.available() > 0) {
                in.readUnsignedByte();
                in.readUnsignedByte();
            }
            if (cmd == Cmd.SHOP_LINH_TINH && in.available() > 0) {
                in.readUnsignedByte();
            }
            PacketSender.message(session, "no price");
            return;
        }
        if (cmd == Cmd.SHOP_CLAN) {
            session.send(Cmd.SHOP_CLAN, Bytes.out(out -> out.writeByte(0)));
        } else {
            PacketSender.shopEmpty(session, cmd);
        }
    }

    private void file(Session session, DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        int version = in.readUnsignedByte();
        LOG.info("file " + type + " clientVersion " + version);
        PacketSender.filePack(session, type);
    }

    private void detail(Session session, DataInputStream in) throws IOException {
        int id = in.readInt();
        Player found = find(id);
        if (found == null) {
            session.send(Cmd.DETAIL, Bytes.out(out -> out.writeInt(-1)));
            return;
        }
        String rank = Database.get().ranks().isEmpty() ? "" : Database.get().ranks().get(0);
        PacketSender.detail(session, found, rank);
    }

    private void find(Session session, DataInputStream in) throws IOException {
        boolean list = in.readBoolean();
        in.readInt();
        if (!list) {
            return;
        }
        session.send(Cmd.FIND_PLAYER, Bytes.out(out -> {
            out.writeBoolean(true);
            out.writeByte(0);
        }));
    }

    private int freeSeat() {
        for (int i = 0; i < board.seats.length; i++) {
            if (board.seats[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private Player find(int id) {
        for (Player player : board.seats) {
            if (player != null && player.id == id) {
                return player;
            }
        }
        return null;
    }

    public static final class Board {
        public final int roomId;
        public final int boardId;
        public int mapId = Database.RANDOM_MAP;
        public int money;
        public int mode;
        public String password = "";
        public final Player[] seats = new Player[Database.SEATS];
        public int ownerId;
        public int turnSeat = -1;
        public long turnAt;
        public boolean fighting;

        public Board(int roomId, int boardId) {
            this.roomId = roomId;
            this.boardId = boardId;
        }

        public int occupants() {
            int count = 0;
            for (Player player : seats) {
                if (player != null) {
                    count++;
                }
            }
            return count;
        }

        public void each(Consumer<Session> consumer) {
            for (Player player : seats) {
                if (player != null && player.session != null && player.session.channel.isActive()) {
                    consumer.accept(player.session);
                }
            }
        }
    }
}
