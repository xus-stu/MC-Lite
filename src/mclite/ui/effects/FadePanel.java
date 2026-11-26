package mclite.ui.effects;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * 淡入 + 轻微上移的页面切换动画面板（模块化：与启动器其它代码解耦）。
 */
public class FadePanel extends JPanel {
    private float alpha = 1f;
    private int offset;
    private long started;
    private Timer timer;

    public FadePanel() {
        this(new java.awt.BorderLayout());
    }

    public FadePanel(java.awt.LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    public void fadeIn() {
        if (!isShowing()) {
            alpha = 1f;
            offset = 0;
            repaint();
            return;
        }
        alpha = 0f;
        offset = 10;
        started = System.currentTimeMillis();
        if (timer == null) {
            timer = new Timer(16, e -> {
                float elapsed = (System.currentTimeMillis() - started) / 260f;
                float t = Math.min(1f, elapsed);
                float eased = 1f - (1f - t) * (1f - t) * (1f - t);
                alpha = eased;
                offset = Math.round((1f - eased) * 10f);
                repaint();
                if (t >= 1f) {
                    ((Timer) e.getSource()).stop();
                    offset = 0;
                }
            });
        }
        timer.restart();
    }

    @Override
    protected void paintChildren(Graphics g) {
        if (alpha >= 0.999f && offset == 0) {
            super.paintChildren(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.clipRect(0, 0, getWidth(), getHeight());
        g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(Math.max(0f, alpha)));
        g2.translate(0, offset);
        super.paintChildren(g2);
        g2.dispose();
    }
}
