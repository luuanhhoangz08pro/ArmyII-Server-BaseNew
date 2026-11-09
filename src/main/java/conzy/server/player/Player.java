/**
 * @author Conzy
 **/
package conzy.server.player;

import conzy.server.network.Session;
import conzy.server.service.LobbyService;

public final class Player {
    public final int id;
    public final String name;
    public int xu;
    public int luong;
    public int hp;
    public int maxHp;
    public int gun;
    public int level;
    public int levelPercent;
    public int exp;
    public int nextExp;
    public int cup;
    public int point;
    public final int[] ability = new int[5];
    public final short[][] equip = new short[10][5];
    public boolean ready;
    public boolean training;
    public boolean bot;
    public int seat = -1;
    public Session session;
    public LobbyService.Board board;

    public Player(int id, String name, int xu, int luong, int maxHp) {
        this.id = id;
        this.name = name;
        this.xu = xu;
        this.luong = luong;
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public void wearDefaults() {
        // First id of type 0, 1 and 2 in equipdata2. Type 2 is the armor sheet: body and legs.
        short[] gun = {2, 2, 2, 0, 2, 2, 2, 2, 2, 2};
        short[] hat = {0, 0, 0, 0, 0, 0, 0, 0, 1, 0};
        short[] body = {1, 1, 1, 0, 1, 1, 1, 1, 0, 1};
        for (int i = 0; i < 10; i++) {
            equip[i][0] = gun[i];
            equip[i][1] = hat[i];
            equip[i][2] = body[i];
        }
    }

    public void addXu(int amount) {
        if (amount > 0) {
            xu += amount;
        }
    }

    public void addLuong(int amount) {
        if (amount > 0) {
            luong += amount;
        }
    }

    public boolean canAffordXu(int amount) {
        return amount >= 0 && xu >= amount;
    }

    public boolean canAffordLuong(int amount) {
        return amount >= 0 && luong >= amount;
    }

    public boolean spendXu(int amount) {
        if (!canAffordXu(amount)) {
            return false;
        }
        xu -= amount;
        return true;
    }

    public boolean spendLuong(int amount) {
        if (!canAffordLuong(amount)) {
            return false;
        }
        luong -= amount;
        return true;
    }

    public void addHp(int amount) {
        hp += amount;
        if (hp > maxHp) {
            hp = maxHp;
        }
        if (hp < 0) {
            hp = 0;
        }
    }
}
