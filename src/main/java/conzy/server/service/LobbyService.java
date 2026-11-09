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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class LobbyService {
    private static final Logger LOG = Logger.getLogger("army2");
    private final CombatService combat;
    private final Board board = new Board(0, 0);
    private static final String[] BOT_NAMES = {
        "Kien", "Minh", "Tuan", "Huy", "Long", "Nam", "Quan", "Dung",
        "Phong", "Bao", "Khoa", "Dat", "Hung", "Viet", "Son", "Tai",
        "Lam", "Duc", "An", "Cuong", "Hoang", "Trung", "Thang", "Linh",
        "Hieu", "Phuc", "Khang", "Nhat", "Quoc", "Tien"
    };
    private static final String[] BOT_TAGS = {
        "Ace", "Wolf", "Hawk", "Fire", "Ice", "Gun", "Bom", "Shot",
        "Sky", "Fox", "Rock", "Storm", "Kid", "Pro", "VN", "Max"
    };
    private int nextBotId = -2;
    private int botSerial = 1;
    public volatile boolean maintenance;
    private long maintenanceUntil;
    private boolean autoMaintenance;
    private long repeatEveryMs;
    private long repeatHoldMs;
    private long nextMaintenanceAt;

    public LobbyService(CombatService combat) {
        this.combat = combat;
    }

    public void tick() {
        combat.tick(board);
        long now = System.currentTimeMillis();
        if (maintenance && maintenanceUntil > 0 && now >= maintenanceUntil) {
            maintenance = false;
            maintenanceUntil = 0;
            if (autoMaintenance && repeatEveryMs > 0) {
                nextMaintenanceAt = now + repeatEveryMs;
            }
        }
        if (!maintenance && autoMaintenance && nextMaintenanceAt > 0 && now >= nextMaintenanceAt) {
            maintenance = true;
            maintenanceUntil = now + Math.max(repeatHoldMs, 60_000L);
            nextMaintenanceAt = 0;
        }
    }

    public void beginMaintenance(long durationMs) {
        if (durationMs < 60_000L) {
            durationMs = 60_000L;
        }
        maintenance = true;
        maintenanceUntil = System.currentTimeMillis() + durationMs;
    }

    public void stopMaintenance() {
        maintenance = false;
        maintenanceUntil = 0;
    }

    public void setAutoMaintenance(boolean on, long everyMs, long holdMs) {
        autoMaintenance = on;
        repeatEveryMs = Math.max(everyMs, 3_600_000L);
        repeatHoldMs = Math.max(holdMs, 60_000L);
        if (!on) {
            nextMaintenanceAt = 0;
            return;
        }
        if (!maintenance) {
            nextMaintenanceAt = System.currentTimeMillis() + repeatEveryMs;
        }
    }

    public String addBots(String prefix, int count) {
        if (board.fighting) {
            return "Đang trong trận, chưa thêm bot";
        }
        String base = prefix == null ? "" : prefix.trim();
        if (base.isEmpty()) {
            base = "Bot";
        }
        if (base.length() > 12) {
            base = base.substring(0, 12);
        }
        if (count < 1) {
            count = 1;
        }
        int added = 0;
        for (int i = 0; i < count; i++) {
            int seat = freeSeat();
            if (seat < 0) {
                break;
            }
            seatBot(base + " " + botSerial++);
            added++;
        }
        if (added == 0) {
            return "Hết chỗ";
        }
        return "Đã thêm " + added + " bot";
    }

    public String buffBots(int count) {
        if (board.fighting) {
            return "Đang trong trận, chưa thêm bot";
        }
        int free = 0;
        for (Player player : board.seats) {
            if (player == null) {
                free++;
            }
        }
        int reserve = hasHuman(board) ? 0 : 1;
        int room = free - reserve;
        if (room < 1) {
            return reserve > 0 ? "Giữ 1 ghế cho người" : "Hết chỗ";
        }
        if (count < 1) {
            count = 1;
        }
        if (count > room) {
            count = room;
        }
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Player player : board.seats) {
            if (player != null) {
                used.add(player.name);
            }
        }
        int added = 0;
        for (int i = 0; i < count; i++) {
            String name = randomBotName(used);
            used.add(name);
            seatBot(name);
            added++;
        }
        return "Đã buff " + added + " bot" + (reserve > 0 ? ", giữ 1 ghế" : "");
    }

    private void seatBot(String name) {
        int seat = freeSeat();
        if (seat < 0) {
            return;
        }
        Player bot = new Player(nextBotId--, name, 0, 0, Database.BASE_HP);
        bot.bot = true;
        bot.gun = 0;
        bot.level = 1 + ThreadLocalRandom.current().nextInt(12);
        bot.wearDefaults();
        bot.ready = true;
        bot.seat = seat;
        bot.board = board;
        board.seats[seat] = bot;
        board.each(target -> {
            PacketSender.someoneJoin(target, bot);
            PacketSender.ready(target, bot.id, true);
        });
    }

    private String randomBotName(java.util.Set<String> used) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 48; i++) {
            String name = BOT_NAMES[random.nextInt(BOT_NAMES.length)] + BOT_TAGS[random.nextInt(BOT_TAGS.length)];
            if (!used.contains(name)) {
                return name;
            }
        }
        return "Bot" + botSerial++;
    }

    public void removeBot(int id) {
        Player found = find(id);
        if (found != null && found.bot) {
            leave(found);
        }
    }

    public void removeBots() {
        java.util.List<Player> bots = new java.util.ArrayList<>();
        for (Player player : board.seats) {
            if (player != null && player.bot) {
                bots.add(player);
            }
        }
        for (Player bot : bots) {
            leave(bot);
        }
    }

    public Snapshot snapshot() {
        java.util.List<PlayerView> players = new java.util.ArrayList<>();
        int humans = 0;
        for (Player player : PlayerDB.get().copyOnline()) {
            players.add(view(player));
            humans++;
        }
        int bots = 0;
        for (Player player : board.seats) {
            if (player != null && player.bot) {
                players.add(view(player));
                bots++;
            }
        }
        return new Snapshot(
            maintenance,
            maintenanceUntil,
            autoMaintenance,
            repeatEveryMs,
            repeatHoldMs,
            nextMaintenanceAt,
            board.fighting,
            humans,
            bots,
            players
        );
    }

    private static PlayerView view(Player player) {
        return new PlayerView(player.id, player.name, player.bot, player.seat, player.gun, player.level, player.ready);
    }

    private static boolean hasHuman(Board current) {
        for (Player player : current.seats) {
            if (player != null && !player.bot) {
                return true;
            }
        }
        return false;
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
            case Cmd.CHAT_TO -> chatTo(session, in);
            case Cmd.HIDE_MAIL -> hideMail(session, in);
            case Cmd.CHAT_TEAM -> chatTeam(session, in);
            case Cmd.SET_MONEY -> setMoney(session, in);
            case Cmd.SET_PASS -> {
                if (session.player != null && session.player.board != null) {
                    session.player.board.password = in.readUTF();
                }
            }
            case Cmd.CHANGE_PASS -> changePass(session, in);
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
            case Cmd.FIRE, Cmd.FIRE_TRAINING -> combat.fire(session, in);
            case Cmd.MOVE -> combat.move(session, in);
            case Cmd.UPDATE_XY -> combat.xy(session, in);
            case Cmd.USE_ITEM -> combat.useItem(session, in);
            case Cmd.SKIP -> combat.skip(session);
            case Cmd.FILE -> file(session, in);
            case Cmd.MATERIAL -> material(session, in);
            case Cmd.TRAINING_MAP -> trainingMap(session);
            case Cmd.TRAINING -> training(session, in);
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
            default -> unavailable(session, packet.cmd);
        }
    }

    private void login(Session session, DataInputStream in) throws IOException {
        String name = in.readUTF();
        String pass = in.readUTF();
        String version = in.readUTF();
        if (maintenance) {
            PacketSender.loginFail(session, "Server đang bảo trì");
            return;
        }
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
        deliverMail(session, player);
        PacketSender.loginOk(session, player);
    }

    private void deliverMail(Session session, Player player) {
        List<PlayerDB.Inbox> mail;
        try {
            mail = PlayerDB.get().unpaidMail(player.id);
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "mail " + player.name, e);
            return;
        }
        int xu = 0;
        int luong = 0;
        for (PlayerDB.Inbox row : mail) {
            xu += row.xu;
            luong += row.luong;
            try {
                PlayerDB.get().markGift(row.id);
            } catch (SQLException e) {
                LOG.log(java.util.logging.Level.WARNING, "mail gift mark " + player.name, e);
            }
        }
        if (xu > 0) {
            player.addXu(xu);
        }
        if (luong > 0) {
            player.addLuong(luong);
        }
        if (xu > 0 || luong > 0) {
            try {
                PlayerDB.get().save(player);
            } catch (SQLException e) {
                LOG.log(java.util.logging.Level.WARNING, "mail gift " + player.name, e);
            }
        }
        try {
            boolean paid = false;
            for (PlayerDB.Notice notice : PlayerDB.get().dueNotices(player.id)) {
                boolean show = notice.forever || !notice.seen;
                boolean pay = !notice.gifted && (notice.xu > 0 || notice.luong > 0);
                if (pay) {
                    if (notice.xu > 0) {
                        player.addXu(notice.xu);
                    }
                    if (notice.luong > 0) {
                        player.addLuong(notice.luong);
                    }
                    paid = true;
                }
                if (show) {
                    PacketSender.chatTo(session, 0, "Admin", notice.body);
                }
                if (pay || (show && !notice.forever)) {
                    PlayerDB.get().markNotice(notice.id, player.id, show && !notice.forever, pay || notice.gifted);
                }
            }
            if (paid) {
                PlayerDB.get().save(player);
            }
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "notice " + player.name, e);
        }
        try {
            for (PlayerDB.Inbox row : PlayerDB.get().savedMail(player.id)) {
                PacketSender.chatTo(session, row.fromId, row.fromName, row.body);
            }
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "mail show " + player.name, e);
        }
    }

    private void changePass(Session session, DataInputStream in) throws IOException {
        String oldPass = in.readUTF();
        String next = in.readUTF();
        Player player = session.player;
        if (player == null) {
            return;
        }
        PlayerDB.PassResult result;
        try {
            result = PlayerDB.get().changePass(player.id, oldPass, next);
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "change pass " + player.name, e);
            PacketSender.message(session, "database");
            return;
        }
        String text = switch (result) {
            case OK -> "Đổi mật khẩu thành công";
            case WRONG -> "Nhập sai mật khẩu cũ";
            case OLD_EMPTY -> "Bạn phải nhập mật khẩu cũ.";
            case NEW_EMPTY -> "Bạn phải nhập mật khẩu mới.";
            case BAD_LENGTH -> "Mật khẩu mới phải từ 5 đến 12 ký tự";
        };
        PacketSender.message(session, text);
    }

    private void unavailable(Session session, int cmd) {
        LOG.info("unhandled cmd " + cmd);
        if (session.player == null || quiet(cmd)) {
            return;
        }
        PacketSender.message(session, "Chức năng đang phát triển (" + cmd + ")");
    }

    private static boolean quiet(int cmd) {
        return cmd == 23 || cmd == 58 || cmd == 79 || cmd == 80 || cmd == 114 || cmd == 127 || cmd == 164;
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
            player.training = false;
            openMap(board);
            PacketSender.join(session, board);
            PacketSender.mapSelected(session, board.mapId);
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
        player.training = false;
        board.seats[seat] = player;
        if (!player.bot && board.ownerId == 0) {
            board.ownerId = player.id;
            player.ready = true;
        }
        openMap(board);
        PacketSender.join(session, board);
        PacketSender.mapSelected(session, board.mapId);
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
                if (other != null && !other.bot) {
                    current.ownerId = other.id;
                    other.ready = true;
                    break;
                }
            }
        }
        if (!hasHuman(current)) {
            combat.stop(current);
        }
        if (current.occupants() == 0) {
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
        if (text.isEmpty()) {
            return;
        }
        remember("phòng", player, null, player.board.roomId + "/" + player.board.boardId, text);
        player.board.each(target -> PacketSender.chatBoard(target, player.id, text));
    }

    private void hideMail(Session session, DataInputStream in) throws IOException {
        Player player = session.player;
        if (player == null) {
            return;
        }
        int mode = in.readUnsignedByte();
        try {
            if (mode == 2) {
                PlayerDB.get().hideAllMail(player.id);
                return;
            }
            in.readInt();
            String name = in.readUTF();
            String body = in.readUTF();
            PlayerDB.get().hideMail(player.id, name, body);
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "hide mail " + player.name, e);
        }
    }

    private void chatTo(Session session, DataInputStream in) throws IOException {
        Player from = session.player;
        if (from == null) {
            return;
        }
        int id = in.readInt();
        String text = in.readUTF();
        if (text.isEmpty()) {
            return;
        }
        Player target = online(id);
        if (target == null || target.session == null) {
            remember("riêng", from, id, null, text);
            try {
                if (PlayerDB.get().playerExists(id)) {
                    PlayerDB.get().queueMail(id, from.id, from.name, text, 0, 0);
                }
            } catch (SQLException e) {
                LOG.log(java.util.logging.Level.WARNING, "mail queue " + from.name, e);
            }
            PacketSender.message(session, "Người này không online");
            return;
        }
        remember("riêng", from, target.id, target.name, text);
        try {
            PlayerDB.get().queueMail(target.id, from.id, from.name, text, 0, 0, true);
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "mail keep " + from.name, e);
        }
        PacketSender.chatTo(target.session, from.id, from.name, text);
    }

    private void chatTeam(Session session, DataInputStream in) throws IOException {
        Player from = session.player;
        if (from == null) {
            return;
        }
        String text = in.readUTF();
        if (text.isEmpty()) {
            return;
        }
        remember("đội", from, null, null, text);
        for (Player target : PlayerDB.get().copyOnline()) {
            if (target.session != null) {
                PacketSender.chatTeam(target.session, from.name, text);
            }
        }
    }

    public int announce(String text, int xu, int luong, boolean onlyOnline, boolean forever) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String body = text.trim();
        int sent = 0;
        long noticeId;
        List<Integer> ids;
        try {
            noticeId = PlayerDB.get().createNotice(body, xu, luong, onlyOnline, forever);
            ids = PlayerDB.get().playerIds();
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "announce", e);
            return 0;
        }
        for (int id : ids) {
            Player target = online(id);
            boolean live = target != null && target.session != null;
            if (onlyOnline && !live) {
                continue;
            }
            boolean pay = live && (xu > 0 || luong > 0);
            if (pay) {
                if (xu > 0) {
                    target.addXu(xu);
                }
                if (luong > 0) {
                    target.addLuong(luong);
                }
                try {
                    PlayerDB.get().save(target);
                    PacketSender.balance(target.session, target.xu, target.luong);
                } catch (SQLException e) {
                    LOG.log(java.util.logging.Level.WARNING, "gift " + target.name, e);
                }
            }
            if (onlyOnline || !forever || pay) {
                try {
                    PlayerDB.get().markNotice(noticeId, id, live && !forever, pay || (xu <= 0 && luong <= 0));
                } catch (SQLException e) {
                    LOG.log(java.util.logging.Level.WARNING, "notice mark " + id, e);
                }
            }
            if (live) {
                PacketSender.chatTo(target.session, 0, "Admin", body);
                sent++;
            }
        }
        String logged = body;
        if (xu > 0 || luong > 0) {
            logged = body + " | xu " + xu + " luong " + luong;
        }
        try {
            PlayerDB.get().chatLog("báo", 0, "admin", null, null, logged);
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "announce log", e);
        }
        return sent;
    }

    private void remember(String kind, Player from, Integer toId, String toName, String text) {
        try {
            PlayerDB.get().chatLog(kind, from.id, from.name, toId, toName, text);
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "chat log " + from.name, e);
        }
    }

    private Player online(int id) {
        for (Player player : PlayerDB.get().copyOnline()) {
            if (player.id == id) {
                return player;
            }
        }
        return null;
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

    private void openMap(LobbyService.Board board) {
        if (board.mapId == Database.RANDOM_MAP) {
            board.mapId = 0;
        }
    }

    private void material(Session session, DataInputStream in) throws IOException {
        int action = in.readUnsignedByte();
        int id = in.readUnsignedByte();
        if (action == 2) {
            PacketSender.mapIcon(session, id);
        }
    }

    private void trainingMap(Session session) {
        if (session.player == null) {
            return;
        }
        PacketSender.trainingMap(session, 0);
    }

    private void training(Session session, DataInputStream in) throws IOException {
        if (session.player == null) {
            return;
        }
        if (in.available() > 0) {
            in.readByte();
        }
        session.player.training = true;
        PacketSender.trainingStart(session, session.player);
        PacketSender.wind(session, 0, 0);
        PacketSender.nextTurn(session, 0);
    }

    private void file(Session session, DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        int version = in.readUnsignedByte();
        LOG.info("file " + type + " clientVersion " + version);
        if (type == 6) {
            if (!session.loginReady) {
                PacketSender.bigImage(session, 9);
            }
            return;
        }
        PacketSender.filePack(session, type, version);
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

    public static final class PlayerView {
        public final int id;
        public final String name;
        public final boolean bot;
        public final int seat;
        public final int gun;
        public final int level;
        public final boolean ready;

        public PlayerView(int id, String name, boolean bot, int seat, int gun, int level, boolean ready) {
            this.id = id;
            this.name = name;
            this.bot = bot;
            this.seat = seat;
            this.gun = gun;
            this.level = level;
            this.ready = ready;
        }
    }

    public static final class Snapshot {
        public final boolean maintenance;
        public final long maintenanceUntil;
        public final boolean auto;
        public final long repeatEveryMs;
        public final long repeatHoldMs;
        public final long nextMaintenanceAt;
        public final boolean fighting;
        public final int humans;
        public final int bots;
        public final java.util.List<PlayerView> players;

        public Snapshot(
            boolean maintenance,
            long maintenanceUntil,
            boolean auto,
            long repeatEveryMs,
            long repeatHoldMs,
            long nextMaintenanceAt,
            boolean fighting,
            int humans,
            int bots,
            java.util.List<PlayerView> players
        ) {
            this.maintenance = maintenance;
            this.maintenanceUntil = maintenanceUntil;
            this.auto = auto;
            this.repeatEveryMs = repeatEveryMs;
            this.repeatHoldMs = repeatHoldMs;
            this.nextMaintenanceAt = nextMaintenanceAt;
            this.fighting = fighting;
            this.humans = humans;
            this.bots = bots;
            this.players = players;
        }
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
