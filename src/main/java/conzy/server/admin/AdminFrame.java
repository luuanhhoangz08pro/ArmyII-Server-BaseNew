/**
 * @author Conzy
 **/
package conzy.server.admin;

import conzy.server.database.PlayerDB;
import conzy.server.server.GameServer;
import conzy.server.service.LobbyService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.lang.management.ManagementFactory;
import java.util.concurrent.RejectedExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

public final class AdminFrame extends JFrame {
    private final GameServer server;
    private final JLabel peopleValue = new JLabel("0");
    private final JLabel botValue = new JLabel("0");
    private final JLabel threadValue = new JLabel("0");
    private final JLabel cpuValue = new JLabel("-");
    private final JLabel maintainInfo = new JLabel("Server đang mở cửa");
    private final JLabel scheduleInfo = new JLabel("Chưa bật lịch");
    private final JLabel status = new JLabel(" ");
    private final JTextField notice = new JTextField();
    private final JSpinner giftXu = new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 100));
    private final JSpinner giftLuong = new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 10));
    private final JCheckBox onlyOnline = new JCheckBox("Chỉ người đang online");
    private final JCheckBox forever = new JCheckBox("Vĩnh viễn, vào lại vẫn thấy");
    private final JSpinner duration = new JSpinner(new SpinnerNumberModel(30, 1, 720, 1));
    private final JComboBox<String> unit = new JComboBox<>(new String[] {"phút", "giờ"});
    private final JSpinner everyHours = new JSpinner(new SpinnerNumberModel(6, 1, 168, 1));
    private final JSpinner holdMinutes = new JSpinner(new SpinnerNumberModel(15, 1, 180, 1));
    private final JCheckBox auto = new JCheckBox("Lặp tự động");
    private final JSpinner botCount = new JSpinner(new SpinnerNumberModel(3, 1, 7, 1));
    private final DefaultTableModel players = new DefaultTableModel(
        new Object[] {"Id", "Tên", "Loại", "Ghế", "Cấp", "Sẵn sàng"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable playerTable = new JTable(players);
    private final DefaultTableModel notices = new DefaultTableModel(
        new Object[] {"Id", "Thời gian", "Gửi tới", "Hiện", "Nội dung"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable noticeTable = new JTable(notices);
    private final DefaultTableModel talks = new DefaultTableModel(new Object[] {"Người gửi", "Người nhận", "Mới nhất"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable talkTable = new JTable(talks);
    private final DefaultTableModel lines = new DefaultTableModel(new Object[] {"Thời gian", "Người gửi", "Nội dung"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable lineTable = new JTable(lines);
    private java.util.List<PlayerDB.LineRow> privateRows = java.util.List.of();
    private final Timer timer;
    private boolean painting;

    public AdminFrame(GameServer server) {
        super("Army II - Quản lý");
        this.server = server;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(860, 520);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Color.WHITE);
        setLayout(new BorderLayout(8, 8));

        add(stats(), BorderLayout.NORTH);
        add(tabs(), BorderLayout.CENTER);
        add(side(), BorderLayout.EAST);
        status.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        add(status, BorderLayout.SOUTH);

        playerTable.getColumnModel().getColumn(0).setMinWidth(0);
        playerTable.getColumnModel().getColumn(0).setMaxWidth(0);
        noticeTable.getColumnModel().getColumn(0).setMinWidth(0);
        noticeTable.getColumnModel().getColumn(0).setMaxWidth(0);
        noticeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        talkTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        talkTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showTalk();
            }
        });
        auto.addActionListener(event -> {
            if (!painting) {
                saveSchedule();
            }
        });

        timer = new Timer(1000, event -> refresh());
        timer.start();
        refresh();
        reloadMail();
    }

    private JPanel noticeComposer() {
        JPanel box = new JPanel(new BorderLayout(6, 0));
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        box.add(new JLabel("Nội dung"), BorderLayout.WEST);
        box.add(notice, BorderLayout.CENTER);
        JPanel gift = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        gift.setBackground(Color.WHITE);
        gift.add(new JLabel("Xu"));
        gift.add(giftXu);
        gift.add(new JLabel("Lượng"));
        gift.add(giftLuong);
        JButton send = new JButton("Gửi thông báo");
        send.addActionListener(event -> sendNotice());
        gift.add(send);
        gift.add(onlyOnline);
        gift.add(forever);
        box.add(gift, BorderLayout.EAST);
        return box;
    }

    private void sendNotice() {
        String text = notice.getText().trim();
        if (text.isEmpty()) {
            status.setText("Nhập nội dung thông báo");
            return;
        }
        int xu = (Integer) giftXu.getValue();
        int luong = (Integer) giftLuong.getValue();
        boolean onlineOnly = onlyOnline.isSelected();
        boolean keep = forever.isSelected();
        onGame(() -> {
            int sent = server.lobby().announce(text, xu, luong, onlineOnly, keep);
            SwingUtilities.invokeLater(() -> {
                notice.setText("");
                giftXu.setValue(0);
                giftLuong.setValue(0);
                status.setText("Đã gửi hộp thư cho " + sent + " người");
                reloadMail();
            });
        });
    }

    private JTabbedPane tabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Người", new JScrollPane(playerTable));
        JPanel notes = new JPanel(new BorderLayout());
        notes.setBackground(Color.WHITE);
        JPanel noteTop = new JPanel(new BorderLayout());
        noteTop.setBackground(Color.WHITE);
        noteTop.add(noticeComposer(), BorderLayout.NORTH);
        JButton drop = new JButton("Xóa thông báo đang chọn");
        drop.addActionListener(event -> deleteNotice());
        noteTop.add(drop, BorderLayout.SOUTH);
        notes.add(noteTop, BorderLayout.NORTH);
        notes.add(new JScrollPane(noticeTable), BorderLayout.CENTER);
        tabs.addTab("Thông báo admin", notes);
        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(talkTable),
            new JScrollPane(lineTable)
        );
        split.setDividerLocation(280);
        tabs.addTab("Tin người chơi", split);
        return tabs;
    }

    private void deleteNotice() {
        int row = noticeTable.getSelectedRow();
        if (row < 0) {
            status.setText("Chọn một thông báo");
            return;
        }
        Object id = notices.getValueAt(noticeTable.convertRowIndexToModel(row), 0);
        if (!(id instanceof Long noticeId)) {
            return;
        }
        onGame(() -> {
            try {
                PlayerDB.get().deleteNotice(noticeId);
            } catch (java.sql.SQLException e) {
                SwingUtilities.invokeLater(() -> status.setText("Không xóa được thông báo"));
                return;
            }
            SwingUtilities.invokeLater(() -> status.setText("Đã xóa thông báo"));
            reloadMail();
        });
    }

    private void showTalk() {
        int row = talkTable.getSelectedRow();
        lines.setRowCount(0);
        if (row < 0) {
            return;
        }
        int model = talkTable.convertRowIndexToModel(row);
        String from = String.valueOf(talks.getValueAt(model, 0));
        String to = String.valueOf(talks.getValueAt(model, 1));
        for (PlayerDB.LineRow line : privateRows) {
            if (from.equals(line.from) && to.equals(line.to)) {
                lines.addRow(new Object[] {line.at, line.from, line.body});
            }
        }
    }

    private void reloadMail() {
        onGame(() -> {
            try {
                java.util.List<PlayerDB.NoteRow> notes = PlayerDB.get().adminNotes();
                java.util.List<PlayerDB.LineRow> chats = PlayerDB.get().privateLines();
                SwingUtilities.invokeLater(() -> fillMail(notes, chats));
            } catch (java.sql.SQLException e) {
                SwingUtilities.invokeLater(() -> status.setText("Không đọc được tin"));
            }
        });
    }

    private void fillMail(java.util.List<PlayerDB.NoteRow> notes, java.util.List<PlayerDB.LineRow> chats) {
        int note = noticeTable.getSelectedRow();
        int talk = talkTable.getSelectedRow();
        notices.setRowCount(0);
        for (PlayerDB.NoteRow row : notes) {
            notices.addRow(new Object[] {row.id, row.at, row.reach, row.mode, row.body});
        }
        if (note >= 0 && note < noticeTable.getRowCount()) {
            noticeTable.setRowSelectionInterval(note, note);
        }
        privateRows = chats;
        java.util.LinkedHashMap<String, PlayerDB.LineRow> last = new java.util.LinkedHashMap<>();
        for (PlayerDB.LineRow row : chats) {
            last.put(row.from + "\n" + row.to, row);
        }
        talks.setRowCount(0);
        for (PlayerDB.LineRow row : last.values()) {
            talks.addRow(new Object[] {row.from, row.to, row.at});
        }
        if (talk >= 0 && talk < talkTable.getRowCount()) {
            talkTable.setRowSelectionInterval(talk, talk);
        } else {
            lines.setRowCount(0);
        }
    }

    private JPanel stats() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        row.add(pair("Người online", peopleValue));
        row.add(pair("Bot", botValue));
        row.add(pair("Thread", threadValue));
        row.add(pair("CPU", cpuValue));
        row.add(new JLabel("Cổng " + server.port()));
        return row;
    }

    private static JPanel pair(String caption, JLabel value) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setBackground(Color.WHITE);
        panel.add(new JLabel(caption + ":"));
        panel.add(value);
        return panel;
    }

    private JPanel side() {
        JPanel side = new JPanel(new GridLayout(3, 1, 0, 8));
        side.setBackground(Color.WHITE);
        side.setPreferredSize(new Dimension(340, 0));
        side.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));
        side.add(maintainBox());
        side.add(scheduleBox());
        side.add(botBox());
        return side;
    }

    private JPanel maintainBox() {
        JPanel box = box("Bảo trì");
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        form.setBackground(Color.WHITE);
        form.add(duration);
        form.add(unit);
        form.add(new JButton("Bắt đầu") {{
            addActionListener(event -> startMaintenance());
        }});
        form.add(new JButton("Dừng") {{
            addActionListener(event -> onGame(server.lobby()::stopMaintenance));
        }});
        box.add(form);
        box.add(maintainInfo);
        return box;
    }

    private JPanel scheduleBox() {
        JPanel box = box("Bảo trì tự động");
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        form.setBackground(Color.WHITE);
        form.add(new JLabel("Mỗi"));
        form.add(everyHours);
        form.add(new JLabel("giờ, trong"));
        form.add(holdMinutes);
        form.add(new JLabel("phút"));
        box.add(form);
        box.add(auto);
        box.add(scheduleInfo);
        return box;
    }

    private JPanel botBox() {
        JPanel box = box("Bot");
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        form.setBackground(Color.WHITE);
        form.add(new JLabel("Số"));
        form.add(botCount);
        form.add(new JButton("Buff") {{
            addActionListener(event -> buff());
        }});
        form.add(new JButton("Xóa chọn") {{
            addActionListener(event -> removeSelected());
        }});
        form.add(new JButton("Xóa hết") {{
            addActionListener(event -> onGame(server.lobby()::removeBots));
        }});
        box.add(form);
        return box;
    }

    private static JPanel box(String title) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY),
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        return panel;
    }

    private void startMaintenance() {
        int amount = (Integer) duration.getValue();
        boolean hours = "giờ".equals(unit.getSelectedItem());
        long ms = amount * (hours ? 3_600_000L : 60_000L);
        onGame(() -> server.lobby().beginMaintenance(ms));
    }

    private void saveSchedule() {
        boolean on = auto.isSelected();
        long every = ((Integer) everyHours.getValue()) * 3_600_000L;
        long hold = ((Integer) holdMinutes.getValue()) * 60_000L;
        onGame(() -> server.lobby().setAutoMaintenance(on, every, hold));
    }

    private void buff() {
        int count = (Integer) botCount.getValue();
        onGame(() -> {
            String text = server.lobby().buffBots(count);
            SwingUtilities.invokeLater(() -> status.setText(text));
        });
    }

    private void removeSelected() {
        int row = playerTable.getSelectedRow();
        if (row < 0) {
            status.setText("Chọn một bot trong bảng");
            return;
        }
        int model = playerTable.convertRowIndexToModel(row);
        Object id = players.getValueAt(model, 0);
        Object kind = players.getValueAt(model, 2);
        if (!"bot".equals(kind) || !(id instanceof Integer botId)) {
            status.setText("Chỉ xóa được bot");
            return;
        }
        onGame(() -> server.lobby().removeBot(botId));
    }

    private void refresh() {
        int threads = serverThreads();
        double cpu = systemCpu();
        SwingUtilities.invokeLater(() -> {
            threadValue.setText(Integer.toString(threads));
            cpuValue.setText(cpu < 0 ? "-" : Math.round(cpu * 100) + "%");
        });
        onGame(() -> {
            LobbyService.Snapshot snap = server.lobby().snapshot();
            SwingUtilities.invokeLater(() -> show(snap));
        });
    }

    private void show(LobbyService.Snapshot snap) {
        players.setRowCount(0);
        for (LobbyService.PlayerView player : snap.players) {
            players.addRow(new Object[] {
                player.id,
                player.name,
                player.bot ? "bot" : "người",
                player.seat < 0 ? "" : player.seat,
                player.level,
                player.ready ? "có" : "không"
            });
        }
        peopleValue.setText(Integer.toString(snap.humans));
        botValue.setText(Integer.toString(snap.bots));
        painting = true;
        auto.setSelected(snap.auto);
        painting = false;
        maintainInfo.setText(maintainText(snap));
        scheduleInfo.setText(scheduleText(snap));
    }

    private static String maintainText(LobbyService.Snapshot snap) {
        if (!snap.maintenance) {
            return "Server đang mở cửa";
        }
        if (snap.maintenanceUntil <= 0) {
            return "Đang bảo trì";
        }
        long left = Math.max(0, snap.maintenanceUntil - System.currentTimeMillis());
        long minutes = left / 60_000;
        long seconds = (left / 1000) % 60;
        return String.format("Đang bảo trì, còn %d:%02d", minutes, seconds);
    }

    private static String scheduleText(LobbyService.Snapshot snap) {
        if (!snap.auto) {
            return "Chưa bật lịch";
        }
        long hours = Math.max(1, snap.repeatEveryMs / 3_600_000L);
        long minutes = Math.max(1, snap.repeatHoldMs / 60_000L);
        if (snap.maintenance || snap.nextMaintenanceAt <= 0) {
            return "Mỗi " + hours + " giờ, trong " + minutes + " phút";
        }
        long left = Math.max(0, snap.nextMaintenanceAt - System.currentTimeMillis());
        long h = left / 3_600_000L;
        long m = (left / 60_000L) % 60;
        return "Mỗi " + hours + " giờ, trong " + minutes + " phút. Lần sau " + h + "g" + m + "p";
    }

    private static int serverThreads() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            String name = thread.getName().toLowerCase();
            if (name.startsWith("game") || name.startsWith("nio") || name.contains("netty")) {
                count++;
            }
        }
        return count;
    }

    private static double systemCpu() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getCpuLoad();
        }
        return -1;
    }

    private void onGame(Runnable work) {
        try {
            server.game().execute(work);
        } catch (RejectedExecutionException e) {
            timer.stop();
            status.setText("Server đã tắt");
        }
    }
}
