/**
 * @author Conzy
 **/
package conzy.server.database;

import conzy.server.item.Item;
import conzy.server.map.GameMap;
import conzy.server.monster.Monster;
import conzy.server.skill.Skill;
import java.util.ArrayList;
import java.util.List;

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

//    public byte[] mapValues() { // tạm ẩn code để viết lại giá trị map tránh bị clear gc full cpu
//        return Bytes.out(out -> {
//            out.writeByte(maps.size());
//            for (GameMap map : maps) {
//                byte[] terrain = map.terrain == null ? new byte[0] : map.terrain;
//                out.writeByte(map.id);
//                out.writeShort(terrain.length);
//                out.write(terrain);
//                for (int i = 0; i < 5; i++) {
//                    short value = map.values != null && i < map.values.length ? map.values[i] : 0;
//                    out.writeShort(value);
//                }
//                out.writeUTF(map.name);
//                out.writeUTF(map.fileName == null ? "" : map.fileName);
//            }
//        });
//    }

    public Item item(int id) {// tìm item
        for (Item item : items) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }

    public void load() {
        items.clear();
        skills.clear();
        monsters.clear();
        ranks.clear();
        try {
            for (Item item : PlayerDB.get().catalogItems()) {
                items.add(item);
                if (!item.slot) {
                    skills.add(new Skill(item.id, item.name));
                }
            }
            monsters.addAll(PlayerDB.get().catalogBosses());
            ranks.addAll(PlayerDB.get().catalogRanks());
        } catch (Exception e) {
            throw new IllegalStateException("catalog", e);
        }
    }
}
