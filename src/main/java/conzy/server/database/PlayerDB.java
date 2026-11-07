/**
 * @author Conzy
 **/
package conzy.server.database;

import conzy.server.player.Player;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
        try (Connection setup = DriverManager.getConnection(server, config.dbUser, config.dbPassword);
            Statement statement = setup.createStatement()) {
            statement.executeUpdate(
                "CREATE DATABASE IF NOT EXISTS `" + config.dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
        } catch (SQLException ignored) {
        }
        connection = DriverManager.getConnection(server + config.dbName, config.dbUser, config.dbPassword);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS player (
                  id INT NOT NULL AUTO_INCREMENT,
                  name VARCHAR(32) NOT NULL,
                  pass VARCHAR(64) NOT NULL,
                  xu INT NOT NULL DEFAULT 0,
                  luong INT NOT NULL DEFAULT 0,
                  gun TINYINT NOT NULL DEFAULT 0,
                  level INT NOT NULL DEFAULT 0,
                  level_percent INT NOT NULL DEFAULT 0,
                  exp INT NOT NULL DEFAULT 0,
                  next_exp INT NOT NULL DEFAULT 0,
                  cup INT NOT NULL DEFAULT 0,
                  point INT NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_player_name (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            );
            statement.executeUpdate("ALTER TABLE player ADD COLUMN IF NOT EXISTS locked TINYINT NOT NULL DEFAULT 0");
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
                if (!rows.next() || !pass.equals(rows.getString("pass"))) {
                    return Login.bad();
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
        return player;
    }

    public void offline(Player player) {
        online.remove(player.name, player);
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
}
