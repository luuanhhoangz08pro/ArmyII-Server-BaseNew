/**
 * @author Conzy
 **/
package conzy.server.map;

public final class GameMap {
    public final int id;
    public final String name;
    public final String fileName;
    public final byte[] terrain;
    public final short[] values;

    public GameMap(int id, String name, String fileName, byte[] terrain, short[] values) {
        this.id = id;
        this.name = name;
        this.fileName = fileName;
        this.terrain = terrain;
        this.values = values;
    }
}
