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
