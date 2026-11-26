package mclite.ui.effects;

import mclite.Theme;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.function.BooleanSupplier;

/**
 * 「界面指针柔光」效果（v2，平滑版）。
 *
 * <p>独立模块：不拦鼠标事件、不改组件。鼠标位置先存入目标值，由 60fps 定时器插值逼近，
 * 每帧只重绘「旧圆 + 新圆」的并集区域，避免残影和频闪；鼠标静止/离开后淡出并停表。</p>
 */
public final class PointerGlow {

    private static final int RADIUS = 220;
    private static final int TICK_MS = 16;
    private static final long IDLE_MS = 140;

    private final Window window;
    private final BooleanSupplier enabled;

    private float targetX = -1f;
    private float targetY = -1f;
    private float x = -1f;
    private float y = -1f;
    private float alpha;
    private long lastMove;
    private boolean running;
    private Timer timer;

    public PointerGlow(Window window, BooleanSupplier enabled) {
        this.window = window;
        this.enabled = enabled;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent mouse)) {
                return;
            }
            int id = mouse.getID();
            if (id != MouseEvent.MOUSE_MOVED && id != MouseEvent.MOUSE_DRAGGED) {
                return;
            }
            if (!(mouse.getSource() instanceof Component source)) {
                return;
            }
            if (SwingUtilities.getWindowAncestor(source) != window) {
                return;
            }
            onPointer(SwingUtilities.convertPoint(source, mouse.getX(), mouse.getY(), window));
        }, AWTEvent.MOUSE_MOTION_EVENT_MASK);
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                stop();
            }
        });
    }

    private void onPointer(Point point) {
        if (!enabled.getAsBoolean() || !window.isShowing()) {
            return;
        }
        targetX = point.x;
        targetY = point.y;
        lastMove = System.currentTimeMillis();
        if (x < 0) {
            x = targetX;
            y = targetY;
        }
        ensureTimer();
    }

    private void ensureTimer() {
        if (timer == null) {
            timer = new Timer(TICK_MS, event -> tick());
        }
        if (!timer.isRunning()) {
            timer.start();
        }
        running = true;
    }

    private void tick() {
        boolean idle = System.currentTimeMillis() - lastMove > IDLE_MS;
        float targetAlpha = idle ? 0f : 1f;
        float oldX = x;
        float oldY = y;
        float oldAlpha = alpha;

        // 位置插值：离目标越远步长越大，接近时收敛，避免抖动。
        if (targetX >= 0f) {
            float dx = targetX - x;
            float dy = targetY - y;
            float distance = (float) Math.hypot(dx, dy);
            if (distance > 0.4f) {
                float ease = Math.min(1f, 0.28f + distance / 400f);
                x += dx * ease;
                y += dy * ease;
            } else {
                x = targetX;
                y = targetY;
            }
        }
        alpha += (targetAlpha - alpha) * (targetAlpha > alpha ? 0.35f : 0.18f);
        if (alpha < 0.004f) {
            alpha = 0f;
        }
        if (alpha > 0.996f) {
            alpha = 1f;
        }

        boolean moved = Math.abs(x - oldX) + Math.abs(y - oldY) > 0.4f;
        boolean faded = Math.abs(alpha - oldAlpha) > 0.004f;
        if (moved || faded) {
            repaintUnion(oldX, oldY, x, y);
        }
        if (!moved && !faded && alpha <= 0f) {
            timer.stop();
            running = false;
            targetX = -1f;
            targetY = -1f;
        }
    }

    private void repaintUnion(float fromX, float fromY, float toX, float toY) {
        int left = Math.round(Math.min(fromX, toX)) - RADIUS;
        int top = Math.round(Math.min(fromY, toY)) - RADIUS;
        int right = Math.round(Math.max(fromX, toX)) + RADIUS;
        int bottom = Math.round(Math.max(fromY, toY)) + RADIUS;
        window.repaint(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    /** 由窗口 paint 在内容绘制完成后调用。 */
    public void paint(Graphics g) {
        if (!enabled.getAsBoolean() || alpha <= 0.02f || x < 0f) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), RADIUS,
                new float[]{0f, 1f}, new Color[]{
                Theme.alpha(Theme.ACCENT, Math.round(24 * alpha)),
                Theme.alpha(Theme.ACCENT, 0)}));
        g2.fillOval(Math.round(x) - RADIUS, Math.round(y) - RADIUS, RADIUS * 2, RADIUS * 2);
        g2.dispose();
    }

    private void stop() {
        if (timer != null) {
            timer.stop();
        }
        alpha = 0f;
        running = false;
    }
}
