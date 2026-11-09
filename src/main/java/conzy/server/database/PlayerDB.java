/**
 * @author Conzy
 **/
package conzy.server.database;

import conzy.server.item.Item;
import conzy.server.monster.Monster;
import conzy.server.player.Player;
import java.sql.Connection;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDB {
    public static final long WAIT_MS = 10_000;
    private static final PlayerDB INSTANCE = new PlayerDB();
    private final Map<String, Player> online = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldownUntil = new ConcurrentHashMap<>();
    private Connection connection;

    private PlayerDB() {
    }

    public static PlayerDB get() {
        return INSTANCE;
    }

    public void open(Config config) throws SQLException {
        String server = "jdbc:mariadb://" + config.dbHost + ":" + config.dbPort + "/";
        String auth = "?allowPublicKeyRetrieval=true";
        try (Connection setup = DriverManager.getConnection(server + auth, config.dbUser, config.dbPassword);
            Statement statement = setup.createStatement()) {
            statement.executeUpdate(
                "CREATE DATABASE IF NOT EXISTS `" + config.dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
        } catch (SQLException ignored) {
        }
        connection = DriverManager.getConnection(server + config.dbName + auth, config.dbUser, config.dbPassword);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS player (
                  id INT NOT NULL AUTO_INCREMENT,
                  name VARCHAR(32) NOT NULL,
                  pass VARCHAR(255) NOT NULL,
                  xu INT NOT NULL DEFAULT 0,
                  luong INT NOT NULL DEFAULT 0,
                  gun TINYINT NOT NULL DEFAULT 0,
                  level INT NOT NULL DEFAULT 0,
                  level_percent INT NOT NULL DEFAULT 0,
                  exp INT NOT NULL DEFAULT 0,
                  next_exp INT NOT NULL DEFAULT 0,
                  cup INT NOT NULL DEFAULT 0,
                  point INT NOT NULL DEFAULT 0,
                  locked TINYINT NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_player_name (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            try {
                statement.executeUpdate("ALTER TABLE player ADD COLUMN IF NOT EXISTS locked TINYINT NOT NULL DEFAULT 0");
            } catch (SQLException mariaOnly) {
                if (mariaOnly.getErrorCode() != 1064) {
                    throw mariaOnly;
                }
                try {
                    statement.executeUpdate("ALTER TABLE player ADD COLUMN locked TINYINT NOT NULL DEFAULT 0");
                } catch (SQLException already) {
                    if (already.getErrorCode() != 1060) {
                        throw already;
                    }
                }
            }
            statement.executeUpdate("ALTER TABLE player MODIFY pass VARCHAR(255) NOT NULL");
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_log (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  kind VARCHAR(8) NOT NULL,
                  from_id INT NOT NULL,
                  from_name VARCHAR(32) NOT NULL,
                  to_id INT NULL,
                  to_name VARCHAR(32) NULL,
                  body VARCHAR(1000) NOT NULL,
                  PRIMARY KEY (id),
                  KEY idx_chat_at (at),
                  KEY idx_chat_from (from_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_inbox (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  player_id INT NOT NULL,
                  from_id INT NOT NULL,
                  from_name VARCHAR(32) NOT NULL,
                  body VARCHAR(1000) NOT NULL,
                  xu INT NOT NULL DEFAULT 0,
                  luong INT NOT NULL DEFAULT 0,
                  sent TINYINT NOT NULL DEFAULT 0,
                  gifted TINYINT NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  KEY idx_inbox_player (player_id, sent)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            try {
                statement.executeUpdate("ALTER TABLE chat_inbox ADD COLUMN gifted TINYINT NOT NULL DEFAULT 0");
            } catch (SQLException already) {
                if (already.getErrorCode() != 1060) {
                    throw already;
                }
            }
            statement.executeUpdate("UPDATE chat_inbox SET gifted=1 WHERE sent=1");
            try {
                statement.executeUpdate("ALTER TABLE admin_notice_who ADD COLUMN hidden TINYINT NOT NULL DEFAULT 0");
            } catch (SQLException ignore) {
                if (ignore.getErrorCode() != 1060 && ignore.getErrorCode() != 1146) {
                    throw ignore;
                }
            }
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS admin_notice (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  body VARCHAR(1000) NOT NULL,
                  xu INT NOT NULL DEFAULT 0,
                  luong INT NOT NULL DEFAULT 0,
                  only_online TINYINT NOT NULL DEFAULT 0,
                  forever TINYINT NOT NULL DEFAULT 0,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS admin_notice_who (
                  notice_id BIGINT NOT NULL,
                  player_id INT NOT NULL,
                  seen TINYINT NOT NULL DEFAULT 0,
                  gifted TINYINT NOT NULL DEFAULT 0,
                  hidden TINYINT NOT NULL DEFAULT 0,
                  PRIMARY KEY (notice_id, player_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
        }
        upgradePlainPasswords();
        seedCatalog();
    }

    private void upgradePlainPasswords() throws SQLException {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        java.util.List<String> plain = new java.util.ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery("SELECT id, pass FROM player")) {
            while (rows.next()) {
                String stored = rows.getString("pass");
                if (!PasswordHash.isMd5(stored) && !stored.startsWith("pbkdf2:")) {
                    ids.add(rows.getInt("id"));
                    plain.add(stored);
                }
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            storeHash(ids.get(i), plain.get(i));
        }
    }

    private void storeHash(int id, String password) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE player SET pass=? WHERE id=?")) {
            update.setString(1, PasswordHash.hash(password));
            update.setInt(2, id);
            update.executeUpdate();
        }
    }

    public long remainingWait(String name) {
        Long until = cooldownUntil.get(name);
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            cooldownUntil.remove(name, until);
            return 0;
        }
        return left;
    }

    public void markLeft(String name) {
        cooldownUntil.put(name, System.currentTimeMillis() + WAIT_MS);
    }

    public Login open(String name, String pass) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            """
            SELECT id, name, pass, locked, xu, luong, gun, level, level_percent, exp, next_exp, cup, point
            FROM player WHERE name=?
            """
        )) {
            query.setString(1, name);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    return Login.bad();
                }
                String stored = rows.getString("pass");
                if (!PasswordHash.verify(pass, stored)) {
                    return Login.bad();
                }
                int id = rows.getInt("id");
                if (!PasswordHash.isMd5(stored)) {
                    storeHash(id, pass);
                }
                if (rows.getInt("locked") != 0) {
                    return Login.locked();
                }
                Player live = online.get(name);
                if (live != null && live.id == rows.getInt("id")) {
                    return Login.ok(live);
                }
                Player loaded = read(rows);
                online.put(loaded.name, loaded);
                return Login.ok(loaded);
            }
        }
    }

    public PassResult changePass(int id, String oldPass, String next) throws SQLException {
        if (oldPass == null || oldPass.isEmpty()) {
            return PassResult.OLD_EMPTY;
        }
        if (next == null || next.isEmpty()) {
            return PassResult.NEW_EMPTY;
        }
        if (next.length() < 5 || next.length() > 12) {
            return PassResult.BAD_LENGTH;
        }
        try (PreparedStatement query = connection.prepareStatement("SELECT pass FROM player WHERE id=?")) {
            query.setInt(1, id);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next() || !PasswordHash.verify(oldPass, rows.getString("pass"))) {
                    return PassResult.WRONG;
                }
            }
        }
        storeHash(id, next);
        return PassResult.OK;
    }

    public void queueMail(int playerId, int fromId, String fromName, String body, int xu, int luong) throws SQLException {
        queueMail(playerId, fromId, fromName, body, xu, luong, false);
    }

    public void queueMail(int playerId, int fromId, String fromName, String body, int xu, int luong, boolean giftGiven) throws SQLException {
        String text = body.length() > 1000 ? body.substring(0, 1000) : body;
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO chat_inbox (player_id, from_id, from_name, body, xu, luong, gifted) VALUES (?,?,?,?,?,?,?)"
        )) {
            insert.setInt(1, playerId);
            insert.setInt(2, fromId);
            insert.setString(3, fromName);
            insert.setString(4, text);
            insert.setInt(5, Math.max(xu, 0));
            insert.setInt(6, Math.max(luong, 0));
            insert.setInt(7, giftGiven || (xu <= 0 && luong <= 0) ? 1 : 0);
            insert.executeUpdate();
        }
    }

    public List<Inbox> unpaidMail(int playerId) throws SQLException {
        return mailQuery(
            "SELECT id, from_id, from_name, body, xu, luong FROM chat_inbox WHERE player_id=? AND gifted=0 ORDER BY id",
            playerId
        );
    }

    public List<Inbox> savedMail(int playerId) throws SQLException {
        List<Inbox> rows = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
            """
            SELECT id, from_id, from_name, body, xu, luong FROM (
              SELECT id, from_id, from_name, body, xu, luong
              FROM chat_inbox WHERE player_id=? ORDER BY id DESC LIMIT 50
            ) recent ORDER BY id
            """
        )) {
            query.setInt(1, playerId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    rows.add(readInbox(result));
                }
            }
        }
        return rows;
    }

    private List<Inbox> mailQuery(String sql, int playerId) throws SQLException {
        List<Inbox> rows = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setInt(1, playerId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    rows.add(readInbox(result));
                }
            }
        }
        return rows;
    }

    private static Inbox readInbox(ResultSet result) throws SQLException {
        return new Inbox(
            result.getLong("id"),
            result.getInt("from_id"),
            result.getString("from_name"),
            result.getString("body"),
            result.getInt("xu"),
            result.getInt("luong")
        );
    }

    public void markGift(long id) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE chat_inbox SET gifted=1, sent=1 WHERE id=?")) {
            update.setLong(1, id);
            update.executeUpdate();
        }
    }

    public long createNotice(String body, int xu, int luong, boolean onlyOnline, boolean forever) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO admin_notice (body, xu, luong, only_online, forever) VALUES (?,?,?,?,?)",
            Statement.RETURN_GENERATED_KEYS
        )) {
            insert.setString(1, body);
            insert.setInt(2, Math.max(xu, 0));
            insert.setInt(3, Math.max(luong, 0));
            insert.setInt(4, onlyOnline ? 1 : 0);
            insert.setInt(5, forever ? 1 : 0);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void markNotice(long noticeId, int playerId, boolean seen, boolean gifted) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
            """
            INSERT INTO admin_notice_who (notice_id, player_id, seen, gifted) VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE seen=GREATEST(seen, VALUES(seen)), gifted=GREATEST(gifted, VALUES(gifted))
            """
        )) {
            update.setLong(1, noticeId);
            update.setInt(2, playerId);
            update.setInt(3, seen ? 1 : 0);
            update.setInt(4, gifted ? 1 : 0);
            update.executeUpdate();
        }
    }

    public List<Notice> dueNotices(int playerId) throws SQLException {
        List<Notice> rows = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
            """
            SELECT n.id, n.body, n.xu, n.luong, n.only_online, n.forever,
                   IFNULL(w.seen, 0) seen, IFNULL(w.gifted, 0) gifted
            FROM admin_notice n
            LEFT JOIN admin_notice_who w ON w.notice_id=n.id AND w.player_id=?
            WHERE (n.only_online=0 OR w.player_id IS NOT NULL)
              AND IFNULL(w.hidden, 0)=0
            ORDER BY n.id
            """
        )) {
            query.setInt(1, playerId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    rows.add(new Notice(
                        result.getLong("id"),
                        result.getString("body"),
                        result.getInt("xu"),
                        result.getInt("luong"),
                        result.getInt("only_online") != 0,
                        result.getInt("forever") != 0,
                        result.getInt("seen") != 0,
                        result.getInt("gifted") != 0
                    ));
                }
            }
        }
        return rows;
    }

    public List<NoteRow> adminNotes() throws SQLException {
        List<NoteRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                "SELECT id, at, body, only_online, forever FROM admin_notice ORDER BY id DESC LIMIT 100"
            )) {
            while (result.next()) {
                rows.add(new NoteRow(
                    result.getLong("id"),
                    result.getString("at"),
                    result.getString("body"),
                    result.getInt("only_online") != 0 ? "chỉ online" : "off và onl",
                    result.getInt("forever") != 0 ? "vĩnh viễn" : "1 lần"
                ));
            }
        }
        return rows;
    }

    public void hideMail(int playerId, String fromName, String body) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
            """
            INSERT INTO admin_notice_who (notice_id, player_id, seen, gifted, hidden)
            SELECT n.id, ?, 1, IFNULL(w.gifted, 0), 1
            FROM admin_notice n
            LEFT JOIN admin_notice_who w ON w.notice_id=n.id AND w.player_id=?
            WHERE n.body=?
            ON DUPLICATE KEY UPDATE hidden=1, seen=1
            """
        )) {
            update.setInt(1, playerId);
            update.setInt(2, playerId);
            update.setString(3, body);
            update.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement(
            "DELETE FROM chat_inbox WHERE player_id=? AND from_name=? AND body=?"
        )) {
            update.setInt(1, playerId);
            update.setString(2, fromName);
            update.setString(3, body);
            update.executeUpdate();
        }
    }

    public void hideAllMail(int playerId) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
            """
            INSERT INTO admin_notice_who (notice_id, player_id, seen, gifted, hidden)
            SELECT n.id, ?, 1, IFNULL(w.gifted, 0), 1
            FROM admin_notice n
            LEFT JOIN admin_notice_who w ON w.notice_id=n.id AND w.player_id=?
            ON DUPLICATE KEY UPDATE hidden=1, seen=1
            """
        )) {
            update.setInt(1, playerId);
            update.setInt(2, playerId);
            update.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement("DELETE FROM chat_inbox WHERE player_id=?")) {
            update.setInt(1, playerId);
            update.executeUpdate();
        }
    }

    public void deleteNotice(long id) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("DELETE FROM admin_notice_who WHERE notice_id=?")) {
            update.setLong(1, id);
            update.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement("DELETE FROM admin_notice WHERE id=?")) {
            update.setLong(1, id);
            update.executeUpdate();
        }
    }

    public List<LineRow> privateLines() throws SQLException {
        List<LineRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                "SELECT at, from_name, to_name, body FROM chat_log WHERE kind='riêng' AND to_name IS NOT NULL AND from_name<>'Admin' AND from_name<>'admin' ORDER BY id DESC LIMIT 300"
            )) {
            while (result.next()) {
                rows.add(new LineRow(
                    result.getString("at"),
                    result.getString("from_name"),
                    result.getString("to_name"),
                    result.getString("body")
                ));
            }
        }
        java.util.Collections.reverse(rows);
        return rows;
    }

    public List<Integer> playerIds() throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT id FROM player ORDER BY id")) {
            while (result.next()) {
                ids.add(result.getInt("id"));
            }
        }
        return ids;
    }

    public boolean playerExists(int id) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM player WHERE id=?")) {
            query.setInt(1, id);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    public void chatLog(String kind, int fromId, String fromName, Integer toId, String toName, String body) throws SQLException {
        String text = body.length() > 1000 ? body.substring(0, 1000) : body;
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO chat_log (kind, from_id, from_name, to_id, to_name, body) VALUES (?,?,?,?,?,?)"
        )) {
            insert.setString(1, kind);
            insert.setInt(2, fromId);
            insert.setString(3, fromName);
            if (toId == null) {
                insert.setNull(4, java.sql.Types.INTEGER);
            } else {
                insert.setInt(4, toId);
            }
            if (toName == null) {
                insert.setNull(5, java.sql.Types.VARCHAR);
            } else {
                insert.setString(5, toName);
            }
            insert.setString(6, text);
            insert.executeUpdate();
        }
    }

    public void save(Player player) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
            """
            UPDATE player SET xu=?, luong=?, gun=?, level=?, level_percent=?, exp=?, next_exp=?, cup=?, point=?
            WHERE id=?
            """
        )) {
            update.setInt(1, player.xu);
            update.setInt(2, player.luong);
            update.setInt(3, player.gun);
            update.setInt(4, player.level);
            update.setInt(5, player.levelPercent);
            update.setInt(6, player.exp);
            update.setInt(7, player.nextExp);
            update.setInt(8, player.cup);
            update.setInt(9, player.point);
            update.setInt(10, player.id);
            update.executeUpdate();
        }
    }

    private Player read(ResultSet rows) throws SQLException {
        Player player = new Player(
            rows.getInt("id"),
            rows.getString("name"),
            rows.getInt("xu"),
            rows.getInt("luong"),
            Database.BASE_HP
        );
        player.gun = rows.getInt("gun");
        player.level = rows.getInt("level");
        player.levelPercent = rows.getInt("level_percent");
        player.exp = rows.getInt("exp");
        player.nextExp = rows.getInt("next_exp");
        player.cup = rows.getInt("cup");
        player.point = rows.getInt("point");
        player.wearDefaults();
        return player;
    }

    public void offline(Player player) {
        online.remove(player.name, player);
    }

    public java.util.List<Player> copyOnline() {
        return java.util.List.copyOf(online.values());
    }

    public List<Item> catalogItems() throws SQLException {
        List<Item> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                "SELECT id, name, max_used, free, slot FROM cat_item ORDER BY id"
            )) {
            while (result.next()) {
                rows.add(new Item(
                    result.getInt("id"),
                    result.getString("name"),
                    result.getInt("max_used"),
                    result.getInt("free") != 0,
                    result.getInt("slot") != 0
                ));
            }
        }
        return rows;
    }

    public List<Monster> catalogBosses() throws SQLException {
        List<Monster> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT id, code FROM cat_boss ORDER BY id")) {
            while (result.next()) {
                rows.add(new Monster(result.getInt("id"), result.getString("code")));
            }
        }
        return rows;
    }

    public List<String> catalogRanks() throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT name FROM cat_rank ORDER BY ord")) {
            while (result.next()) {
                rows.add(result.getString("name"));
            }
        }
        return rows;
    }

    private void seedCatalog() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS cat_item (
                  id INT NOT NULL,
                  name VARCHAR(64) NOT NULL,
                  max_used INT NOT NULL,
                  free TINYINT NOT NULL,
                  slot TINYINT NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS cat_stat (
                  ord INT NOT NULL,
                  name VARCHAR(32) NOT NULL,
                  PRIMARY KEY (ord)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS cat_gun (
                  id INT NOT NULL,
                  code VARCHAR(32) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS cat_gun_name (
                  ord INT NOT NULL,
                  name VARCHAR(32) NOT NULL,
                  PRIMARY KEY (ord)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS cat_boss (
                  id INT NOT NULL,
                  code VARCHAR(32) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS cat_bullet (
                  id INT NOT NULL,
                  code VARCHAR(32) NOT NULL,
                  flag TINYINT NOT NULL,
                  is_double TINYINT NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS cat_rank (
                  ord INT NOT NULL,
                  name VARCHAR(32) NOT NULL,
                  PRIMARY KEY (ord)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS cat_pack (
                  pack VARCHAR(32) NOT NULL,
                  file VARCHAR(64) NOT NULL,
                  PRIMARY KEY (pack, file)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS cat_gap (
                  id INT NOT NULL AUTO_INCREMENT,
                  note VARCHAR(255) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            try (ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM cat_item")) {
                count.next();
                if (count.getInt(1) > 0) {
                    return;
                }
            }
        }
        JSONObject root = catalogJson();
        boolean auto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            insertItems(root.getJSONArray("items"));
            insertNames("cat_stat", root.getJSONArray("stats"));
            insertCodes("cat_gun", root.getJSONArray("guns"));
            insertNames("cat_gun_name", root.getJSONArray("gunDisplayNames"));
            insertCodes("cat_boss", root.getJSONArray("bosses"));
            insertBullets(root.getJSONArray("bullets"));
            insertRankNames(root.getJSONArray("ranks"));
            insertPacks(root.getJSONObject("filePacks"));
            insertNotes(root.getJSONArray("missingFromClient"));
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(auto);
        }
    }

    private JSONObject catalogJson() {
        try (InputStream in = PlayerDB.class.getResourceAsStream("/data/catalog.json")) {
            if (in == null) {
                throw new IllegalStateException("missing /data/catalog.json");
            }
            return new JSONObject(new JSONTokener(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("catalog", e);
        }
    }

    private void insertItems(JSONArray rows) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO cat_item (id, name, max_used, free, slot) VALUES (?,?,?,?,?)"
        )) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                insert.setInt(1, row.getInt("id"));
                insert.setString(2, row.getString("name"));
                insert.setInt(3, row.getInt("maxUsed"));
                insert.setInt(4, row.getBoolean("free") ? 1 : 0);
                insert.setInt(5, row.optBoolean("slot") ? 1 : 0);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void insertNames(String table, JSONArray rows) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO " + table + " (ord, name) VALUES (?,?)"
        )) {
            for (int i = 0; i < rows.length(); i++) {
                insert.setInt(1, i);
                insert.setString(2, rows.getString(i));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void insertCodes(String table, JSONArray rows) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO " + table + " (id, code) VALUES (?,?)"
        )) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                insert.setInt(1, row.getInt("id"));
                insert.setString(2, row.getString("const"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void insertBullets(JSONArray rows) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO cat_bullet (id, code, flag, is_double) VALUES (?,?,?,?)"
        )) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                insert.setInt(1, row.getInt("id"));
                insert.setString(2, row.getString("const"));
                insert.setInt(3, row.optBoolean("flag") ? 1 : 0);
                insert.setInt(4, row.optBoolean("double") ? 1 : 0);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void insertRankNames(JSONArray rows) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO cat_rank (ord, name) VALUES (?,?)"
        )) {
            for (int i = 0; i < rows.length(); i++) {
                insert.setInt(1, i);
                insert.setString(2, rows.getJSONObject(i).getString("name"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void insertPacks(JSONObject packs) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO cat_pack (pack, file) VALUES (?,?)"
        )) {
            for (String pack : packs.keySet()) {
                JSONArray files = packs.getJSONArray(pack);
                for (int i = 0; i < files.length(); i++) {
                    insert.setString(1, pack);
                    insert.setString(2, files.getString(i));
                    insert.addBatch();
                }
            }
            insert.executeBatch();
        }
    }

    private void insertNotes(JSONArray rows) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO cat_gap (note) VALUES (?)")) {
            for (int i = 0; i < rows.length(); i++) {
                insert.setString(1, rows.getString(i));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public static final class Notice {
        public final long id;
        public final String body;
        public final int xu;
        public final int luong;
        public final boolean onlyOnline;
        public final boolean forever;
        public final boolean seen;
        public final boolean gifted;

        public Notice(long id, String body, int xu, int luong, boolean onlyOnline, boolean forever, boolean seen, boolean gifted) {
            this.id = id;
            this.body = body;
            this.xu = xu;
            this.luong = luong;
            this.onlyOnline = onlyOnline;
            this.forever = forever;
            this.seen = seen;
            this.gifted = gifted;
        }
    }

    public static final class NoteRow {
        public final long id;
        public final String at;
        public final String body;
        public final String reach;
        public final String mode;

        public NoteRow(long id, String at, String body, String reach, String mode) {
            this.id = id;
            this.at = at;
            this.body = body;
            this.reach = reach;
            this.mode = mode;
        }
    }

    public static final class LineRow {
        public final String at;
        public final String from;
        public final String to;
        public final String body;

        public LineRow(String at, String from, String to, String body) {
            this.at = at;
            this.from = from;
            this.to = to;
            this.body = body;
        }
    }

    public static final class Inbox {
        public final long id;
        public final int fromId;
        public final String fromName;
        public final String body;
        public final int xu;
        public final int luong;

        public Inbox(long id, int fromId, String fromName, String body, int xu, int luong) {
            this.id = id;
            this.fromId = fromId;
            this.fromName = fromName;
            this.body = body;
            this.xu = xu;
            this.luong = luong;
        }
    }

    public static final class Login {
        public final boolean ok;
        public final boolean locked;
        public final Player player;

        private Login(boolean ok, boolean locked, Player player) {
            this.ok = ok;
            this.locked = locked;
            this.player = player;
        }

        static Login ok(Player player) {
            return new Login(true, false, player);
        }

        static Login bad() {
            return new Login(false, false, null);
        }

        static Login locked() {
            return new Login(false, true, null);
        }
    }

    public enum PassResult {
        OK,
        WRONG,
        OLD_EMPTY,
        NEW_EMPTY,
        BAD_LENGTH
    }
}
