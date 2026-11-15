package mclite;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * 「功能」大区：启动器内置小工具的入口列表。
 *
 * <p>每个功能占一行（圆角卡片，悬停渐变），点开才进详情页；以后加功能只要往列表里加一项。</p>
 */
final class FeaturesPage {

    record Feature(String title, String subtitle, String tag, Runnable open) {
    }

    private final List<Feature> features;

    FeaturesPage(Runnable openSeed) {
        this.features = List.of(
                new Feature("种子查询", "查看存档种子、已生成遗迹和史莱姆区块", "种子 · 遗迹 · 史莱姆", openSeed)
        );
    }

    JPanel build() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        page.add(LauncherLayout.pageHeader("功能", "启动器内置的小工具，点开详情页使用", null),
                BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(true);
        body.setBackground(Theme.BG);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(20, 26, 28, 26));
        for (Feature feature : features) {
            body.add(new FeatureCard(feature));
            body.add(Ui.vgap(12));
        }
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = Ui.scroll(body);
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    /** 一行功能：标题 + 说明 + 右侧「打开」，整行可点。 */
    private final class FeatureCard extends JPanel {
        private float glow;
        private Timer anim;

        FeatureCard(Feature feature) {
            super(new BorderLayout(16, 0));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
            setBorder(new EmptyBorder(14, 18, 14, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel title = Ui.label(feature.title(), Theme.H2, Theme.TEXT);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel subtitle = Ui.label(feature.subtitle(), Theme.TINY, Theme.DIM);
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(title);
            text.add(Ui.vgap(4));
            text.add(subtitle);
            add(text, BorderLayout.CENTER);

            JPanel right = new JPanel();
            right.setOpaque(false);
            right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
            right.add(Ui.label(feature.tag(), Theme.TINY, Theme.MUTED));
            right.add(Ui.hgap(12));
            right.add(Ui.label("打开 ›", Theme.BODY_BOLD, Theme.ACCENT));
            add(right, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hover(true);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hover(false);
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    feature.open().run();
                }
            });
        }

        private void hover(boolean inside) {
            if (anim == null) {
                anim = new Timer(14, e -> {
                    float target = getClientProperty("hover") == Boolean.TRUE ? 1f : 0f;
                    glow += (target - glow) * 0.25f;
                    if (Math.abs(target - glow) < 0.02f) {
                        glow = target;
                        ((Timer) e.getSource()).stop();
                    }
                    repaint();
                });
            }
            putClientProperty("hover", inside);
            anim.restart();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.prepare(g);
            int arc = 14;
            g2.setColor(Theme.CARD);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            if (glow > 0.01f) {
                g2.setColor(new java.awt.Color(255, 255, 255, Math.round(glow * 14)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            }
            g2.setColor(glow > 0.5f ? Theme.alpha(Theme.ACCENT, 120) : Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
