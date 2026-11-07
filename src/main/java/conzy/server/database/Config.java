/**
 * @author Conzy
 **/
package conzy.server.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Config {
    private static Config current;

    public final int port;
    public final String dbHost;
    public final int dbPort;
    public final String dbName;
    public final String dbUser;
    public final String dbPassword;
    public final String agent;
    public final int provider;
    public final Path bigImage;

    private Config(Map<String, String> values) {
        port = integer(values, "server.port", 19150);
        dbHost = text(values, "database.host", "127.0.0.1");
        dbPort = integer(values, "database.port", 3306);
        dbName = text(values, "database.name", "army2");
        dbUser = text(values, "database.user", "army2");
        dbPassword = text(values, "database.password", "army2");
        agent = text(values, "client.agent", "abc");
        provider = integer(values, "client.provider", 1);
        bigImage = Path.of(text(values, "resource.bigImage", "res/BigImage"));
    }

    public static Config get() {
        return current;
    }

    public static Config load(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("missing " + file.toAbsolutePath());
        }
        Map<String, String> values = new HashMap<>();
        String section = "";
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim();
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 1) {
                    continue;
                }
                String key = section.isEmpty() ? line.substring(0, eq).trim() : section + "." + line.substring(0, eq).trim();
                values.put(key, line.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException("config", e);
        }
        current = new Config(values);
        return current;
    }

    private static String text(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        String value = values.get(key);
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return Integer.parseInt(value);
    }
}
