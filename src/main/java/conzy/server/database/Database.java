/**
 * @author Conzy
 **/
package conzy.server.database;

import conzy.server.item.Item;
import conzy.server.map.GameMap;
import conzy.server.monster.Monster;
import conzy.server.network.Bytes;
import conzy.server.skill.Skill;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public final class Database {
    public static final int BASE_HP = 1000;
    public static final int TURN_SECONDS = 20;
    public static final int SEATS = 8;
    public static final int RANDOM_MAP = 100;

    private static final Database INSTANCE = new Database();
    private final List<Item> items = new ArrayList<>();
    private final List<Skill> skills = new ArrayList<>();
    private final List<Monster> monsters = new ArrayList<>();
    private final List<String> ranks = new ArrayList<>();
    private final List<GameMap> maps = new ArrayList<>();

    private Database() {
        load();
    }

    public static Database get() {
        return INSTANCE;
    }

    public List<Item> items() {
        return items;
    }

    public List<Skill> skills() {
        return skills;
    }

    public List<Monster> monsters() {
        return monsters;
    }

    public List<String> ranks() {
        return ranks;
    }

    public List<GameMap> maps() {
        return maps;
    }

    public byte[] mapValues() {
        return Bytes.out(out -> {
            out.writeByte(maps.size());
            for (GameMap map : maps) {
                byte[] terrain = map.terrain == null ? new byte[0] : map.terrain;
                out.writeByte(map.id);
                out.writeShort(terrain.length);
                out.write(terrain);
                for (int i = 0; i < 5; i++) {
                    short value = map.values != null && i < map.values.length ? map.values[i] : 0;
                    out.writeShort(value);
                }
                out.writeUTF(map.name);
                out.writeUTF(map.fileName == null ? "" : map.fileName);
            }
        });
    }

    public Item item(int id) {
        for (Item item : items) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }

    private void load() {
        try (InputStream in = Database.class.getResourceAsStream("/data/catalog.json")) {
            if (in == null) {
                throw new IllegalStateException("missing /data/catalog.json");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(new JSONTokener(text));
            JSONArray itemArr = root.getJSONArray("items");
            for (int i = 0; i < itemArr.length(); i++) {
                JSONObject row = itemArr.getJSONObject(i);
                Item item = new Item(
                    row.getInt("id"),
                    row.getString("name"),
                    row.getInt("maxUsed"),
                    row.getBoolean("free"),
                    row.optBoolean("slot")
                );
                items.add(item);
                if (!item.slot) {
                    skills.add(new Skill(item.id, item.name));
                }
            }
            JSONArray bosses = root.getJSONArray("bosses");
            for (int i = 0; i < bosses.length(); i++) {
                JSONObject row = bosses.getJSONObject(i);
                monsters.add(new Monster(row.getInt("id"), row.getString("const")));
            }
            JSONArray rankArr = root.getJSONArray("ranks");
            for (int i = 0; i < rankArr.length(); i++) {
                ranks.add(rankArr.getJSONObject(i).getString("name"));
            }
        } catch (Exception e) {
            throw new IllegalStateException("catalog", e);
        }
    }
}
