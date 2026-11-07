/**
 * @author Conzy
 **/
package conzy.server.service;

import conzy.server.database.Database;
import conzy.server.network.PacketSender;
import conzy.server.network.Session;
import conzy.server.player.Player;
import java.io.DataInputStream;
import java.io.IOException;

public final class CombatService {
    public void start(LobbyService.Board board) {
        board.fighting = true;
        board.turnSeat = nextSeat(board, -1);
        board.turnAt = System.currentTimeMillis();
        board.each(session -> {
            PacketSender.start(session, board);
            PacketSender.wind(session, 0, 0);
            PacketSender.nextTurn(session, board.turnSeat);
        });
    }

    public void fire(Session session, DataInputStream in) throws IOException {
        LobbyService.Board board = boardOf(session);
        if (board == null || !board.fighting) {
            return;
        }
        int type = in.readUnsignedByte();
        int x = in.readShort();
        int y = in.readShort();
        int angle = in.readShort();
        in.readUnsignedByte();
        if (type == 17 || type == 19) {
            in.readUnsignedByte();
        }
        int nShoot = in.readUnsignedByte();
        int seat = session.player.seat;
        board.each(target -> PacketSender.fire(target, seat, type, x, y, angle, nShoot));
        advance(board);
    }

    public void move(Session session, DataInputStream in) throws IOException {
        LobbyService.Board board = boardOf(session);
        if (board == null) {
            return;
        }
        int x = in.readShort();
        int y = in.readShort();
        int seat = session.player.seat;
        board.each(target -> PacketSender.move(target, seat, x, y));
    }

    public void xy(Session session, DataInputStream in) throws IOException {
        LobbyService.Board board = boardOf(session);
        if (board == null) {
            return;
        }
        int x = in.readShort();
        int y = in.readShort();
        int seat = session.player.seat;
        board.each(target -> PacketSender.xy(target, seat, x, y));
    }

    public void useItem(Session session, DataInputStream in) throws IOException {
        LobbyService.Board board = boardOf(session);
        if (board == null) {
            return;
        }
        int item = in.readUnsignedByte();
        int seat = session.player.seat;
        board.each(target -> PacketSender.useItem(target, seat, item));
    }

    public void skip(Session session) {
        LobbyService.Board board = boardOf(session);
        if (board != null && board.fighting) {
            advance(board);
        }
    }

    public void stop(LobbyService.Board board) {
        board.fighting = false;
        board.turnSeat = -1;
    }

    public void tick(LobbyService.Board board) {
        if (!board.fighting) {
            return;
        }
        if (System.currentTimeMillis() - board.turnAt >= Database.TURN_SECONDS * 1000L) {
            advance(board);
        }
    }

    private void advance(LobbyService.Board board) {
        board.turnSeat = nextSeat(board, board.turnSeat);
        board.turnAt = System.currentTimeMillis();
        board.each(session -> PacketSender.nextTurn(session, board.turnSeat));
    }

    private int nextSeat(LobbyService.Board board, int from) {
        for (int step = 1; step <= Database.SEATS; step++) {
            int seat = (from + step) % Database.SEATS;
            if (board.seats[seat] != null) {
                return seat;
            }
        }
        return 0;
    }

    private LobbyService.Board boardOf(Session session) {
        Player player = session.player;
        if (player == null) {
            return null;
        }
        return player.board;
    }
}
