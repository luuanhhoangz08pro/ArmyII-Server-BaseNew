/**
 * @author Conzy
 **/
package conzy.server.network;

import conzy.server.database.Config;
import conzy.server.database.Database;
import conzy.server.item.Item;
import conzy.server.player.Player;
import conzy.server.service.LobbyService;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PacketSender {
    private PacketSender() {
    }

    public static void loginOk(Session session, Player player) {
        session.send(Cmd.LOGIN_OK, Bytes.out(out -> writeLogin(out, player)));
        session.send(Cmd.TABLES, Bytes.out(PacketSender::writeTables));
        session.send(Cmd.TILE, Bytes.out(PacketSender::writeTiles));
    }

    public static void loginFail(Session session, String reason) {
        session.send(Cmd.LOGIN_FAIL, Bytes.out(out -> out.writeUTF(reason)));
    }

    public static void message(Session session, String text) {
        session.send(Cmd.SERVER_MESSAGE, Bytes.out(out -> out.writeUTF(text)));
    }

    public static void info(Session session, String text) {
        session.send(Cmd.SERVER_INFO, Bytes.out(out -> out.writeUTF(text)));
    }

    public static void balance(Session session, int xu, int luong) {
        session.send(Cmd.UPDATE_MONEY, Bytes.out(out -> {
            out.writeInt(xu);
            out.writeInt(luong);
        }));
    }

    public static void ping(Session session) {
        session.send(Cmd.PING, new byte[0]);
    }

    public static void agent(Session session) {
        Config config = Config.get();
        session.send(Cmd.AGENT, Bytes.out(out -> {
            out.writeUTF(config.agent);
            out.writeByte(config.provider);
        }));
    }

    public static void rankingMenus(Session session) {
        session.send(Cmd.RANKING, Bytes.out(out -> {
            out.writeByte(-1);
            out.writeByte(0);
        }));
    }

    public static void roomNames(Session session) {
        session.send(Cmd.ROOM_NAMES, Bytes.out(out -> out.writeByte(0)));
    }

    public static void friends(Session session) {
        session.send(Cmd.FRIENDS, new byte[0]);
    }

    public static void strongest(Session session) {
        session.send(Cmd.STRONGEST, new byte[0]);
    }

    public static void zones(Session session) {
        session.send(Cmd.EMPTY_ROOM, Bytes.out(out -> {
            out.writeByte(0);
            out.writeByte(-1);
            out.writeUTF("Khu vực");
        }));
    }

    public static void boards(Session session, int roomId, int boardId, int players, int money) {
        session.send(Cmd.EMPTY_ROOM, Bytes.out(out -> {
            out.writeByte(0);
            out.writeByte(roomId);
            out.writeByte(boardId);
            out.writeByte(Database.RANDOM_MAP);
            out.writeByte(players);
            out.writeByte(Database.SEATS);
            out.writeInt(money);
        }));
    }

    public static void classicRooms(Session session) {
        session.send(Cmd.ROOM_LIST, Bytes.out(out -> {
            out.writeByte(0);
            out.writeByte(1);
            out.writeByte(0);
            out.writeByte(0);
        }));
    }

    public static void classicBoards(Session session, int roomId, int boardId, int players, int money, int mode) {
        session.send(Cmd.BOARD_LIST, Bytes.out(out -> {
            out.writeByte(roomId);
            out.writeByte(boardId);
            out.writeByte(players);
            out.writeByte(Database.SEATS);
            out.writeBoolean(false);
            out.writeInt(money);
            out.writeBoolean(false);
            out.writeUTF("Ngẫu nhiên");
            out.writeByte(mode);
        }));
    }

    public static void join(Session session, LobbyService.Board board) {
        session.send(Cmd.JOIN, Bytes.out(out -> writeJoin(out, board)));
    }

    public static void someoneJoin(Session session, Player player) {
        session.send(Cmd.SOMEONE_JOIN, Bytes.out(out -> {
            out.writeByte(player.seat);
            out.writeInt(player.id);
            out.writeShort(-1);
            out.writeUTF(player.name);
            out.writeByte(player.level);
            out.writeByte(player.gun);
            for (int i = 0; i < 5; i++) {
                out.writeShort(player.equip[player.gun][i]);
            }
        }));
    }

    public static void leave(Session session, int id, int ownerId) {
        session.send(Cmd.LEAVE, Bytes.out(out -> {
            out.writeInt(id);
            out.writeInt(ownerId);
        }));
    }

    public static void ready(Session session, int id, boolean ready) {
        session.send(Cmd.READY, Bytes.out(out -> {
            out.writeInt(id);
            out.writeBoolean(ready);
        }));
    }

    public static void money(Session session, int value) {
        session.send(Cmd.SET_MONEY, Bytes.out(out -> {
            out.writeShort(0);
            out.writeInt(value);
        }));
    }

    public static void moneyError(Session session, String text) {
        session.send(Cmd.SET_MONEY_ERROR, Bytes.out(out -> out.writeUTF(text)));
    }

    public static void kick(Session session, int id, String reason) {
        session.send(Cmd.KICK, Bytes.out(out -> {
            out.writeShort(0);
            out.writeInt(id);
            out.writeUTF(reason);
        }));
    }

    public static void chatBoard(Session session, int fromId, String text) {
        session.send(Cmd.CHAT_BOARD, Bytes.out(out -> {
            out.writeInt(fromId);
            out.writeUTF(text);
        }));
    }

    public static void chatTo(Session session, int fromId, String name, String text) {
        session.send(Cmd.CHAT_TO, Bytes.out(out -> {
            out.writeInt(fromId);
            out.writeUTF(name);
            out.writeUTF(text);
        }));
    }

    public static void chatTeam(Session session, String name, String text) {
        session.send(Cmd.CHAT_TEAM, Bytes.out(out -> {
            out.writeUTF(name);
            out.writeUTF(text);
        }));
    }

    public static void gun(Session session, int id, int gun) {
        session.send(Cmd.CHOOSE_GUN, Bytes.out(out -> {
            out.writeInt(id);
            out.writeByte(gun);
        }));
    }

    public static void team(Session session, int id, int seat) {
        session.send(Cmd.CHANGE_TEAM, Bytes.out(out -> {
            out.writeInt(id);
            out.writeByte(seat);
        }));
    }

    public static void mapSelected(Session session, int mapId) {
        session.send(Cmd.MAP_SELECT, Bytes.out(out -> out.writeByte(mapId)));
    }

    public static void buyResult(Session session, Player player) {
        session.send(Cmd.BUY_ITEM, Bytes.out(out -> {
            out.writeByte(0);
            out.writeInt(player.xu);
            out.writeInt(player.luong);
        }));
    }

    public static void detail(Session session, Player player, String rank) {
        session.send(Cmd.DETAIL, Bytes.out(out -> {
            out.writeInt(player.id);
            out.writeUTF(player.name);
            out.writeInt(player.xu);
            out.writeByte(player.level);
            out.writeByte(player.levelPercent);
            out.writeInt(player.luong);
            out.writeInt(player.exp);
            out.writeInt(player.nextExp);
            out.writeInt(player.cup);
            out.writeUTF(rank);
        }));
    }

    public static void charInfo(Session session, Player player) {
        session.send(Cmd.CHAR_INFO, Bytes.out(out -> {
            out.writeByte(player.level);
            out.writeByte(player.levelPercent);
            out.writeShort(player.point);
            for (int value : player.ability) {
                out.writeShort(value);
            }
            out.writeInt(player.exp);
            out.writeInt(player.nextExp);
            out.writeInt(player.cup);
        }));
    }

    public static void shopEquip(Session session) {
        session.send(Cmd.SHOP_EQUIP, Bytes.out(out -> out.writeShort(0)));
    }

    public static void shopEmpty(Session session, int cmd) {
        session.send(cmd, new byte[0]);
    }

    public static void missionEmpty(Session session) {
        session.send(Cmd.MISSION, new byte[0]);
    }

    public static void filePack(Session session, int type, int clientVersion) {
        int version = 0;
        byte[] data = null;
        boolean wide = false;
        if (type == 1) {
            version = 18;
            data = cache("icondata2");
        } else if (type == 2) {
            version = 19;
            data = cache("valuesdata2");
        } else if (type == 3) {
            version = 15;
            data = cache("playerdata2");
        } else if (type == 4) {
            version = 22;
            data = cache("equipdata2");
            wide = true;
        } else if (type == 5) {
            version = 17;
            data = cache("levelCData2");
        }
        if (data == null) {
            session.sendRawBody(Cmd.FILE, Bytes.out(out -> {
                out.writeByte(type);
                out.writeByte(0);
            }));
            return;
        }
        if (type != 1 && type != 5 && clientVersion == version) {
            int same = version;
            session.sendRawBody(Cmd.FILE, Bytes.out(out -> {
                out.writeByte(type);
                out.writeByte(same);
            }));
            return;
        }
        session.sendRawBody(Cmd.FILE, fileBody(type, version, data, wide));
    }

    public static void mapIcon(Session session, int id) {
        byte[] png = mapPng(id);
        if (png == null) {
            return;
        }
        session.send(Cmd.MATERIAL, Bytes.out(out -> {
            out.writeByte(2);
            out.writeByte(id);
            out.writeShort(png.length);
            out.write(png);
        }));
    }

    public static void trainingMap(Session session, int mapId) {
        session.send(Cmd.TRAINING_MAP, Bytes.out(out -> out.writeByte(mapId)));
    }

    public static void trainingStart(Session session, Player player) {
        session.send(Cmd.START, Bytes.out(out -> {
            int gun = player.gun;
            if (gun < 0 || gun > 9) {
                gun = 0;
            }
            for (int i = 0; i < 5; i++) {
                out.writeShort(player.equip[gun][i]);
            }
            out.writeByte(0);
            out.writeByte(Database.TURN_SECONDS);
            out.writeShort(0);
            out.writeShort(230);
            out.writeShort(200);
            out.writeShort(player.maxHp);
            out.writeShort(550);
            out.writeShort(200);
            out.writeShort(player.maxHp);
            for (int i = 2; i < Database.SEATS; i++) {
                out.writeShort(-1);
            }
        }));
    }

    public static void bigImage(Session session, int id) {
        if (id == 9) {
            session.loginReady = true;
        }
        byte[] png = loadBigImage(id);
        session.sendRawBody(Cmd.BIG_IMAGE_HD, Bytes.out(out -> {
            out.writeByte(id);
            out.writeInt(png.length);
            out.write(png);
        }));
    }

    public static void start(Session session, LobbyService.Board board) {
        session.send(Cmd.START, Bytes.out(out -> {
            out.writeByte(board.mapId);
            out.writeByte(Database.TURN_SECONDS);
            out.writeShort(0);
            for (int i = 0; i < Database.SEATS; i++) {
                Player player = board.seats[i];
                if (player == null) {
                    out.writeShort(-1);
                } else {
                    out.writeShort(80 + i * 50);
                    out.writeShort(200);
                    out.writeShort(player.maxHp);
                }
            }
        }));
    }

    public static void wind(Session session, int x, int y) {
        session.send(Cmd.WIND, Bytes.out(out -> {
            out.writeByte(x);
            out.writeByte(y);
        }));
    }

    public static void nextTurn(Session session, int seat) {
        session.send(Cmd.NEXT_TURN, Bytes.out(out -> out.writeByte(seat)));
    }

    public static void move(Session session, int seat, int x, int y) {
        session.send(Cmd.MOVE, Bytes.out(out -> {
            out.writeByte(seat);
            out.writeShort(x);
            out.writeShort(y);
        }));
    }

    public static void xy(Session session, int seat, int x, int y) {
        session.send(Cmd.UPDATE_XY, Bytes.out(out -> {
            out.writeByte(seat);
            out.writeShort(x);
            out.writeShort(y);
        }));
    }

    public static void useItem(Session session, int seat, int item) {
        session.send(Cmd.USE_ITEM, Bytes.out(out -> {
            out.writeByte(seat);
            out.writeByte(item);
        }));
    }

    public static void fire(Session session, int seat, int type, int x, int y, int angle, int nShoot) {
        session.send(Cmd.FIRE, Bytes.out(out -> {
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(seat);
            out.writeByte(type);
            out.writeShort(x);
            out.writeShort(y);
            out.writeShort(angle);
            if (type == 17 || type == 19) {
                out.writeByte(0);
            }
            if (type == 14 || type == 40) {
                out.writeByte(0);
                out.writeByte(0);
            }
            if (type == 44 || type == 45 || type == 47) {
                out.writeByte(0);
            }
            out.writeByte(nShoot);
            int paths = bulletPaths(type) * Math.max(nShoot, 1);
            if (paths > 127) {
                paths = 127;
            }
            out.writeByte(paths);
            for (int i = 0; i < paths; i++) {
                out.writeShort(1);
                out.writeShort(x);
                out.writeShort(y);
            }
            if (type == 48) {
                out.writeByte(0);
            }
            out.writeByte(0);
        }));
    }

    private static int bulletPaths(int type) {
        // Slots BM.createBullet reads when critical is 0. Same point, not a trajectory.
        if (type == 1) {
            return 2;
        }
        if (type == 2) {
            return 3;
        }
        if (type == 9) {
            return 4;
        }
        if (type == 10) {
            return 3;
        }
        if (type == 11) {
            return 5;
        }
        return 1;
    }

    private static void writeLogin(DataOutputStream out, Player player) throws IOException {
        out.writeUTF(player.name);
        out.writeInt(player.id);
        out.writeInt(player.xu);
        out.writeInt(player.luong);
        out.writeByte(player.gun);
        out.writeShort(-1);
        out.writeByte(0);
        for (int gun = 0; gun < 10; gun++) {
            out.writeBoolean(false);
            for (int part = 0; part < 5; part++) {
                out.writeShort(player.equip[gun][part]);
            }
        }
        for (int i = 0; i < 36; i++) {
            Item item = Database.get().item(i);
            out.writeByte(item != null && item.free ? 99 : 0);
            out.writeInt(-1);
            out.writeInt(-1);
        }
        for (int gun = 3; gun < 10; gun++) {
            out.writeByte(1);
            out.writeShort(0);
            out.writeShort(0);
        }
        out.writeUTF("");
        out.writeUTF("");
        out.writeUTF("");
    }

    private static void writeTables(DataOutputStream out) throws IOException {
        // Wind, aim, bar and bullet count from the reference cache/msg/64.
        // That file also says max 100, 10 boss maps and numb 12. This server has 8 seats, so those stay 8 and 0.
        byte[] wind = {80, 50, 80, 40, 50, 30, 20, 10, 30, 40};
        short[] angle = {-45, -45, -45, 30, 20, 30, -45, 1, -45, 20};
        byte[] power = {28, 14, 10, 10, 12, 7, 23, 30, 11, 35};
        byte[] number = {1, 2, 3, 4, 3, 5, 2, 1, 4, 1};
        out.writeByte(wind.length);
        out.write(wind);
        out.writeByte(angle.length);
        for (short value : angle) {
            out.writeShort(value);
        }
        out.writeByte(power.length);
        out.write(power);
        out.writeByte(number.length);
        out.write(number);
        out.writeByte(Database.SEATS);
        out.writeByte(0);
        out.writeByte(Database.SEATS);
    }

    private static void writeJoin(DataOutputStream out, LobbyService.Board board) throws IOException {
        out.writeInt(board.ownerId);
        out.writeInt(board.money);
        out.writeByte(board.mapId);
        out.writeByte(board.mode);
        for (int i = 0; i < Database.SEATS; i++) {
            Player player = board.seats[i];
            if (player == null) {
                out.writeInt(-1);
            } else {
                writeSeatPlayer(out, player);
                out.writeBoolean(player.ready || player.id == board.ownerId);
            }
        }
    }

    private static void writeSeatPlayer(DataOutputStream out, Player player) throws IOException {
        out.writeInt(player.id);
        out.writeShort(-1);
        out.writeUTF(player.name);
        out.writeInt(player.xu);
        out.writeByte(player.level);
        out.writeByte(player.gun);
        for (int i = 0; i < 5; i++) {
            out.writeShort(player.equip[player.gun][i]);
        }
    }

    private static byte[] fileBody(int type, int version, byte[] data, boolean intLength) {
        return Bytes.out(out -> {
            out.writeByte(type);
            out.writeByte(version);
            if (intLength) {
                out.writeInt(data.length);
            } else {
                out.writeShort(data.length);
            }
            out.write(data);
        });
    }

    private static byte[] equipPng() {
        Path[] paths = new Path[] {
            Path.of("res", "equip", "01.png"),
            Path.of("..", "army2-gdx", "core", "src", "main", "resources", "res", "equip", "01.png")
        };
        for (Path path : paths) {
            if (Files.exists(path)) {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException ignored) {
                    break;
                }
            }
        }
        throw new IllegalStateException("missing res/equip/01.png");
    }

    private static void writeTiles(DataOutputStream out) throws IOException {
        short[] open = {8, 7, 15, 70, 71, 73, 74, 75, 77, 78, 79, 97};
        out.writeShort(open.length);
        for (short id : open) {
            out.writeShort(id);
        }
    }

    private static byte[] cache(String name) {
        Path path = Path.of("res", "cache", name);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("missing " + path.toAbsolutePath(), e);
        }
    }

    private static byte[] mapPng(int id) {
        Path path = Path.of("res", "icon", "map", id + ".png");
        if (!Files.exists(path)) {
            return null;
        }
        try {
            byte[] png = Files.readAllBytes(path);
            if (png.length < 8 || png.length > 32767) {
                return null;
            }
            if ((png[0] & 255) != 137 || png[1] != 80 || png[2] != 78 || png[3] != 71) {
                return null;
            }
            return png;
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] loadBigImage(int id) {
        String name = "bigImage" + id + ".png";
        Path configured = Config.get() == null ? Path.of("res", "BigImage") : Config.get().bigImage;
        Path[] paths = new Path[] {
            configured.resolve(name),
            Path.of("army2-gdx", "core", "src", "main", "resources", "res", "BigImage", name),
            Path.of("..", "army2-gdx", "core", "src", "main", "resources", "res", "BigImage", name)
        };
        for (Path path : paths) {
            if (Files.exists(path)) {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException ignored) {
                    break;
                }
            }
        }
        return equipPng();
    }
}
