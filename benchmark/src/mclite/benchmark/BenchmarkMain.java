package mclite.benchmark;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class BenchmarkMain {
    private static final Color BG = new Color(0x0D0D0D);
    private static final Color CARD = new Color(0x151515);
    private static final Color TEXT = new Color(0xF5F5F5);
    private static final Color MUTED = new Color(0xC9C9C9);
    private static final Color DIM = new Color(0x8E8E8E);
    private static final Color ACCENT = new Color(0xF2F2F2);

    private JLabel scoreLabel;
    private JTextArea info;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BenchmarkMain().show());
    }

    private void show() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        JFrame frame = new JFrame("MC Lite 跑分模块");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(720, 560));

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(24, 28, 20, 28));

        JLabel title = new JLabel("Minecraft 跑分");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(TEXT);
        JLabel subtitle = new JLabel("硬件扫描 + 参考分数，不修改游戏文件");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(DIM);
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(title);
        head.add(Box.createVerticalStrut(3));
        head.add(subtitle);
        root.add(head, BorderLayout.NORTH);

        JPanel card = new JPanel();
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        scoreLabel = new JLabel("尚未跑分");
        scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        scoreLabel.setForeground(ACCENT);
        scoreLabel.setAlignmentX(0f);
        card.add(scoreLabel);
        card.add(Box.createVerticalStrut(10));
        info = new JTextArea(hardwareInfo());
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setBackground(CARD);
        info.setForeground(MUTED);
        info.setFont(new Font("Monospaced", Font.PLAIN, 12));
        info.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane scroll = new JScrollPane(info);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(CARD);
        scroll.setPreferredSize(new Dimension(640, 260));
        card.add(scroll);
        root.add(card, BorderLayout.CENTER);

        JButton run = new JButton("开始跑分");
        run.setFont(new Font("SansSerif", Font.BOLD, 13));
        run.setFocusPainted(false);
        run.setBackground(ACCENT);
        run.setForeground(BG);
        run.setBorder(new EmptyBorder(9, 18, 9, 18));
        run.addActionListener(e -> {
            run.setEnabled(false);
            scoreLabel.setText("跑分中…");
            new Thread(() -> {
                long score = score();
                String level = level(score);
                SwingUtilities.invokeLater(() -> {
                    scoreLabel.setText("参考分数 " + score + "  ·  " + level);
                    run.setEnabled(true);
                });
            }, "mclite-benchmark").start();
        });
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BorderLayout());
        actions.add(run, BorderLayout.EAST);
        root.add(actions, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static String hardwareInfo() {
        StringBuilder text = new StringBuilder();
        text.append("系统      ").append(System.getProperty("os.name"))
                .append(" ").append(System.getProperty("os.version"))
                .append(" / ").append(System.getProperty("os.arch")).append('\n');
        text.append("处理器    ").append(cpuName()).append('\n');
        text.append("逻辑核心  ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        text.append("最大内存  ").append(Runtime.getRuntime().maxMemory() / 1024 / 1024)
                .append(" MB\n");
        text.append("显卡      ").append(gpuName()).append('\n');
        text.append("Java      ").append(System.getProperty("java.version")).append('\n');
        text.append("显示环境  ").append(GraphicsEnvironment.isHeadless()
                ? "无界面" : "已连接").append('\n');
        text.append('\n').append("说明：分数用于同一台机器前后对比，跨机器仅供参考。\n");
        return text.toString();
    }

    private static String cpuName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String value = command("powershell.exe", "-NoProfile", "-Command",
                        "(Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name)");
                if (!value.isBlank()) {
                    return value.trim();
                }
            } else if (os.contains("mac")) {
                String value = command("sysctl", "-n", "machdep.cpu.brand_string");
                if (value.isBlank()) {
                    value = command("sysctl", "-n", "hw.model");
                }
                if (!value.isBlank()) {
                    return value.trim();
                }
            } else {
                String value = command("sh", "-c",
                        "grep -m1 'model name' /proc/cpuinfo | cut -d: -f2");
                if (!value.isBlank()) {
                    return value.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "未知硬件";
    }

    private static String gpuName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String value = command("powershell.exe", "-NoProfile", "-Command",
                        "Get-CimInstance Win32_VideoController | Select-Object -First 1 -ExpandProperty Name");
                if (!value.isBlank()) {
                    return value.trim();
                }
            } else if (os.contains("mac")) {
                String value = command("sh", "-c",
                        "system_profiler SPDisplaysDataType | awk -F': ' '/Chipset Model/{print $2; exit}'");
                if (!value.isBlank()) {
                    return value.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "未知硬件";
    }

    private static String command(String... args) {
        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        result.append(line).append('\n');
                    }
                }
                return result.toString();
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static long score() {
        long best = 0;
        for (int round = 0; round < 3; round++) {
            long start = System.nanoTime();
            long value = 0x9E3779B97F4A7C15L;
            for (int i = 0; i < 12_000_000; i++) {
                value ^= value << 7;
                value ^= value >>> 9;
                value += i * 0xBF58476D1CE4E5B9L;
            }
            long elapsed = Math.max(1, System.nanoTime() - start);
            long current = Math.round(12_000_000.0 * 1_000_000_000.0 / elapsed / 1000.0);
            best = Math.max(best, current + Math.abs(value % 7));
        }
        long cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, best * Math.max(1, cores) / 4);
    }

    private static String level(long score) {
        if (score >= 60_000) {
            return "很高";
        }
        if (score >= 35_000) {
            return "较高";
        }
        if (score >= 18_000) {
            return "中等";
        }
        return "较低";
    }
}
