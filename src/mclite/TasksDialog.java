package mclite;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

public final class TasksDialog extends JDialog {
    private final TaskCenter center;
    private final DefaultListModel<DownloadTask> model = new DefaultListModel<>();
    private final JList<DownloadTask> list = new JList<>(model);
    private final Timer refresh;

    public TasksDialog(Frame owner, TaskCenter center, Runnable cancelAction) {
        super(owner, "下载任务", false);
        this.center = center;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(720, 500);
        setMinimumSize(new Dimension(600, 380));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        JLabel title = Ui.label("下载任务", Theme.H1, Theme.TEXT);
        header.add(title, BorderLayout.WEST);
        JLabel hint = Ui.label("任务会在后台继续；关闭此窗口不会取消下载", Theme.MONO, Theme.DIM);
        header.add(hint, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        list.setBackground(Theme.BG);
        list.setSelectionBackground(Theme.BG);
        list.setFixedCellHeight(92);
        list.setCellRenderer(new TaskRenderer());
        list.setBorder(new EmptyBorder(12, 0, 8, 0));
        root.add(Ui.scrollDark(list), BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new javax.swing.BoxLayout(bottom, javax.swing.BoxLayout.X_AXIS));
        Ui.AccentButton cancel = Ui.smallButton("取消当前任务", Ui.AccentButton.Style.DANGER);
        cancel.addActionListener(e -> cancelAction.run());
        Ui.AccentButton clear = Ui.smallButton("清空已完成", Ui.AccentButton.Style.GHOST);
        clear.addActionListener(e -> {
            center.clearFinished();
            refreshNow();
        });
        Ui.AccentButton close = Ui.smallButton("关闭", Ui.AccentButton.Style.SECONDARY);
        close.addActionListener(e -> dispose());
        bottom.add(cancel);
        bottom.add(Ui.hgap(8));
        bottom.add(clear);
        bottom.add(javax.swing.Box.createHorizontalGlue());
        bottom.add(close);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        refresh = new Timer(280, e -> refreshNow());
        refresh.start();
        refreshNow();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                refresh.stop();
            }
        });
    }

    private void refreshNow() {
        List<DownloadTask> tasks = center.snapshot();
        model.clear();
        for (DownloadTask task : tasks) {
            model.addElement(task);
        }
        list.repaint();
    }

    private final class TaskRenderer extends JPanel implements ListCellRenderer<DownloadTask> {
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();
        private final JLabel stats = new JLabel();
        private final Ui.ThinProgress progress = new Ui.ThinProgress();
        private final JLabel badge = new JLabel();

        private TaskRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout(12, 4));
            setBorder(new EmptyBorder(7, 10, 7, 10));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
            title.setFont(Theme.BODY_BOLD);
            title.setForeground(Theme.TEXT);
            detail.setFont(Theme.MONO);
            detail.setForeground(Theme.DIM);
            stats.setFont(Theme.MONO);
            stats.setForeground(Theme.MUTED);
            text.add(title);
            text.add(Ui.vgap(2));
            text.add(detail);
            text.add(Ui.vgap(1));
            text.add(stats);
            add(text, BorderLayout.NORTH);
            progress.setPreferredSize(new Dimension(200, 4));
            add(progress, BorderLayout.CENTER);
            badge.setFont(Theme.MONO);
            badge.setForeground(Theme.DIM);
            badge.setHorizontalAlignment(JLabel.RIGHT);
            add(badge, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends DownloadTask> list,
                                                      DownloadTask task, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            title.setText(task.title());
            detail.setText("<html><body style='width:500px'>来源："
                    + Formatting.html(task.source()) + "  ·  "
                    + Formatting.html(task.text()) + "</body></html>");
            String remaining = task.remainingText();
            stats.setText(remaining.isEmpty() ? task.startedAt()
                    : task.startedAt() + "  ·  " + remaining);
            badge.setText(switch (task.state()) {
                case RUNNING -> "进行中";
                case DONE -> "完成";
                case FAILED -> "失败";
                case CANCELLED -> "已取消";
            });
            badge.setForeground(switch (task.state()) {
                case RUNNING -> Theme.ACCENT;
                case DONE -> Theme.DIM;
                case FAILED -> Theme.DANGER;
                case CANCELLED -> Theme.MUTED;
            });
            progress.setIndeterminate(task.state() == DownloadTask.State.RUNNING
                    && task.total() <= 0);
            progress.setValue(task.percent() >= 0 ? task.percent() / 100.0 : 0);
            putClientProperty("selected", isSelected);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.prepare(g);
            g2.setColor(Boolean.TRUE.equals(getClientProperty("selected"))
                    ? Theme.SEL : Theme.CARD);
            g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 9, 9);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
