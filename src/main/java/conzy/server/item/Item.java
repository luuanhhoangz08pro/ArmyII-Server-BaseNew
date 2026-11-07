/**
 * @author Conzy
 **/
package conzy.server.item;

public final class Item {
    public final int id;
    public final String name;
    public final int maxUsed;
    public final boolean free;
    public final boolean slot;

    public Item(int id, String name, int maxUsed, boolean free, boolean slot) {
        this.id = id;
        this.name = name;
        this.maxUsed = maxUsed;
        this.free = free;
        this.slot = slot;
    }
}
