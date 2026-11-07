package dev.mclite.island.mc26;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 26.x 界面的静态底图，纯 Java2D 绘制。
 *
 * <p>这里只画“不会变”的东西：卡片、描边、渐变、阴影、分隔线。
 * 文字、悬停行、开关、滑杆仍由界面每帧自绘。绘制结果会由
 * {@link UISurface26} 烘焙成一张 DynamicTexture，因此这里的代码只跑一次，
 * 不需要为帧率做妥协，可以放心开抗锯齿和渐变。</p>
 *
 * <p>这个类刻意不 import 任何 Minecraft 类型，方便用普通 javac 单独渲染 PNG
 * 预览版式。</p>
 */
final class UiPaint26 {

    // ---- 面板尺寸（26 外壳与底图共用，避免两边各写一份数字） ----
    static final int PLAYER_W = 740;
    static final int PLAYER_H = 490;
    static final int PLAYER_HEADER = 60;
    static final int PLAYER_LEFT_X = 18;
    static final int PLAYER_LEFT_Y = 72;
    static final int PLAYER_LEFT_W = 278;
    static final int PLAYER_LEFT_H = 334;
    static final int PLAYER_RIGHT_X = 306;
    static final int PLAYER_RIGHT_Y = 72;
    static final int PLAYER_RIGHT_W = 416;
    static final int PLAYER_RIGHT_H = 334;
    static final int PLAYER_BAR_X = 18;
    static final int PLAYER_BAR_Y = 420;
    static final int PLAYER_BAR_W = 704;
    static final int PLAYER_BAR_H = 52;
    static final int PLAYER_ART_X = PLAYER_RIGHT_X + 18;
    static final int PLAYER_ART_Y = PLAYER_RIGHT_Y + 18;
    static final int PLAYER_ART_SIZE = 120;
    static final int PLAYER_QUEUE_HEADER = 38;

    static final int SETTINGS_W = 760;
    static final int SETTINGS_H = 500;
    static final int SET_PREVIEW_X = 20;
    static final int SET_PREVIEW_Y = 16;
    static final int SET_PREVIEW_W = 720;
    static final int SET_PREVIEW_H = 72;
    static final int SET_NAV_X = 20;
    static final int SET_NAV_Y = 104;
    static final int SET_NAV_W = 180;
    static final int SET_NAV_H = 320;
    static final int SET_CONTENT_X = 212;
    static final int SET_CONTENT_Y = 104;
    static final int SET_CONTENT_W = 528;
    static final int SET_CONTENT_H = 320;
    static final int SET_VIEW_X = 224;
    static final int SET_VIEW_Y = 140;
    static final int SET_VIEW_W = 504;
    static final int SET_VIEW_H = 272;
    static final int SET_FOOTER_LINE = 434;
    static final int SET_DONE_X = 656;
    static final int SET_DONE_Y = 448;
    static final int SET_DONE_W = 84;
    static final int SET_DONE_H = 28;

    static final int BILI_W = 720;
    static final int BILI_H = 460;
    static final int BILI_SEARCH_X = 22;
    static final int BILI_SEARCH_Y = 52;
    static final int BILI_SEARCH_W = 676;
    static final int BILI_SEARCH_H = 44;
    static final int BILI_LIST_X = 22;
    static final int BILI_LIST_Y = 108;
    static final int BILI_LIST_W = 676;
    static final int BILI_LIST_H = 300;

    static final int LOGIN_W = 400;
    static final int LOGIN_H = 340;
    static final int LOGIN_QR_X = 106;
    static final int LOGIN_QR_Y = 56;
    static final int LOGIN_QR_W = 188;
    static final int LOGIN_QR_H = 188;
    static final int LOGIN_QR_INNER = 176;

    static final int LRC_W = 560;
    static final int LRC_H = 200;

    // ---- 配色（与灵动岛/按钮保持同一套） ----
    static final Color TOP = new Color(24, 31, 38, 246);
    static final Color BOTTOM = new Color(12, 16, 21, 248);
    static final Color CARD_TOP = new Color(20, 27, 34, 238);
    static final Color CARD_BOTTOM = new Color(10, 14, 19, 238);
    static final Color WELL_TOP = new Color(8, 11, 15, 210);
    static final Color WELL_BOTTOM = new Color(6, 9, 12, 210);
    static final Color ACCENT = new Color(108, 198, 255);
    static final Color TEXT = new Color(242, 245, 248);
    static final Color DIM = new Color(147, 160, 173);
    static final Color LINE = new Color(255, 255, 255, 22);
    static final Color LINE_STRONG = new Color(255, 255, 255, 40);

    private UiPaint26() {
    }

    static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_DITHERING,
                RenderingHints.VALUE_DITHER_ENABLE);
    }

    // =====================================================================
    // 播放器：720 x 460，双栏卡片 + 底部传输栏
    // =====================================================================

    static void player(Graphics2D g, int w, int h) {
        prepare(g);
        shell(g, w, h, 22);

        // 右上角的柔光：让大封面后面有一点颜色，不至于整页死黑
        Shape clip = g.getClip();
        g.clip(new RoundRectangle2D.Double(0, 0, w, h, 44, 44));
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(PLAYER_ART_X + PLAYER_ART_SIZE * 0.5f,
                        PLAYER_ART_Y + PLAYER_ART_SIZE * 0.5f),
                PLAYER_ART_SIZE * 1.15f,
                new float[]{0f, 1f},
                new Color[]{new Color(108, 198, 255, 30), new Color(108, 198, 255, 0)}));
        g.fillRect(0, 0, w, h);
        g.setClip(clip);

        icon(g, 20, 14);
        hairline(g, 20, 1, w - 40, new Color(108, 198, 255, 46));
        line(g, 20, PLAYER_HEADER, w - 40, LINE);
        innerCard(g, PLAYER_LEFT_X, PLAYER_LEFT_Y, PLAYER_LEFT_W, PLAYER_LEFT_H, 16,
                CARD_TOP, CARD_BOTTOM);
        innerCard(g, PLAYER_RIGHT_X, PLAYER_RIGHT_Y, PLAYER_RIGHT_W, PLAYER_RIGHT_H, 16,
                CARD_TOP, CARD_BOTTOM);
        innerCard(g, PLAYER_BAR_X, PLAYER_BAR_Y, PLAYER_BAR_W, PLAYER_BAR_H, 14,
                CARD_TOP, CARD_BOTTOM);

        // 队列标题和列表之间的细分隔线
        line(g, PLAYER_LEFT_X + 16, PLAYER_LEFT_Y + PLAYER_QUEUE_HEADER,
                PLAYER_LEFT_W - 32, LINE);
        // 右栏标题区的细分隔线（歌词从下面开始）
        line(g, PLAYER_ART_X, PLAYER_ART_Y + PLAYER_ART_SIZE + 8,
                PLAYER_RIGHT_W - 36, LINE);

        // 大封面的底槽：图片没加载好时也有一个体面的占位
        RoundRectangle2D slot = new RoundRectangle2D.Double(PLAYER_ART_X, PLAYER_ART_Y,
                PLAYER_ART_SIZE, PLAYER_ART_SIZE, 14, 14);
        g.setPaint(new GradientPaint(PLAYER_ART_X, PLAYER_ART_Y,
                new Color(36, 54, 72, 230),
                PLAYER_ART_X + PLAYER_ART_SIZE, PLAYER_ART_Y + PLAYER_ART_SIZE,
                new Color(15, 23, 32, 240)));
        g.fill(slot);
        g.setColor(new Color(255, 255, 255, 30));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(PLAYER_ART_X + 0.5, PLAYER_ART_Y + 0.5,
                PLAYER_ART_SIZE - 1, PLAYER_ART_SIZE - 1, 14, 14));
    }

    /** 圆形传输按钮的底图：0 普通、1 hover、2 主按钮。 */
    static void transportButton(Graphics2D g, int w, int h, int kind) {
        prepare(g);
        int pad = 6;
        int size = Math.min(w, h) - pad * 2;
        int x = (w - size) / 2;
        int y = (h - size) / 2;
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(x, y + 2, size, size);
        Color top = kind == 2 ? new Color(124, 208, 255, 255)
                : kind == 1 ? new Color(54, 78, 100, 255) : new Color(35, 47, 60, 250);
        Color bottom = kind == 2 ? new Color(54, 150, 220, 255)
                : kind == 1 ? new Color(34, 50, 66, 255) : new Color(20, 28, 38, 250);
        g.setPaint(new GradientPaint(x, y, top, x, y + size, bottom));
        g.fillOval(x, y, size, size);
        g.setColor(new Color(255, 255, 255, kind == 2 ? 120 : 40));
        g.setStroke(new BasicStroke(1f));
        g.drawOval(x + 1, y + 1, size - 2, size - 2);
        // 顶部内侧高光
        g.setColor(new Color(255, 255, 255, kind == 2 ? 60 : 22));
        g.fillOval(x + 4, y + 3, size - 8, Math.max(4, size / 3));
    }

    // =====================================================================
    // 设置：左侧分类 + 右侧滚动内容 + 顶部灵动岛预览
    // =====================================================================

    static void settings(Graphics2D g, int w, int h) {
        prepare(g);
        shell(g, w, h, 20);

        // 顶部直接是灵动岛预览舞台，不再额外留一条标题栏
        // （1920 宽度下页头与预览会打架，设置页用预览本身当页头）。
        int px = SET_PREVIEW_X;
        int py = SET_PREVIEW_Y;
        int pw = SET_PREVIEW_W;
        int ph = SET_PREVIEW_H;
        Shape clip = g.getClip();
        g.clip(new RoundRectangle2D.Double(px, py, pw, ph, 14, 14));
        g.setColor(new Color(8, 12, 16, 225));
        g.fillRect(px, py, pw, ph);
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(px + pw / 2f, py + ph / 2f + 2f),
                pw * 0.34f,
                new float[]{0f, 1f},
                new Color[]{new Color(108, 198, 255, 26), new Color(108, 198, 255, 0)}));
        g.fillRect(px, py, pw, ph);
        g.setClip(clip);
        roundBorder(g, px, py, pw, ph, 14);

        innerCard(g, SET_NAV_X, SET_NAV_Y, SET_NAV_W, SET_NAV_H, 14,
                CARD_TOP, CARD_BOTTOM);
        innerCard(g, SET_CONTENT_X, SET_CONTENT_Y, SET_CONTENT_W, SET_CONTENT_H, 14,
                CARD_TOP, CARD_BOTTOM);
        line(g, 22, SET_FOOTER_LINE, w - 44, LINE);
    }

    // =====================================================================
    // B 站浏览：顶部搜索栏 + 列表卡片
    // =====================================================================

    static void bili(Graphics2D g, int w, int h) {
        prepare(g);
        shell(g, w, h, 20);
        icon(g, 22, 20);
        hairline(g, 22, 1, w - 44, new Color(108, 198, 255, 46));
        line(g, 22, 50, w - 44, LINE);

        innerCard(g, BILI_SEARCH_X, BILI_SEARCH_Y, BILI_SEARCH_W, BILI_SEARCH_H, 12,
                CARD_TOP, CARD_BOTTOM);
        // 输入框内凹槽：EditBox 本身关掉原版边框
        well(g, BILI_SEARCH_X + 12, BILI_SEARCH_Y + 10, 502, 24, 8);

        innerCard(g, BILI_LIST_X, BILI_LIST_Y, BILI_LIST_W, BILI_LIST_H, 14,
                CARD_TOP, CARD_BOTTOM);
        line(g, 22, 410, w - 44, LINE);
    }

    // =====================================================================
    // 扫码登录：居中小卡片
    // =====================================================================

    static void login(Graphics2D g, int w, int h) {
        prepare(g);
        shell(g, w, h, 20);
        hairline(g, (w - 96) / 2, 1, 96, new Color(108, 198, 255, 60));
        // 二维码白底卡片
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(LOGIN_QR_X, LOGIN_QR_Y + 3,
                LOGIN_QR_W, LOGIN_QR_H, 14, 14));
        RoundRectangle2D qr = new RoundRectangle2D.Double(LOGIN_QR_X, LOGIN_QR_Y,
                LOGIN_QR_W, LOGIN_QR_H, 14, 14);
        g.setPaint(new GradientPaint(0, LOGIN_QR_Y, new Color(250, 252, 254),
                0, LOGIN_QR_Y + LOGIN_QR_H, new Color(232, 238, 244)));
        g.fill(qr);
        g.setColor(new Color(255, 255, 255, 80));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(LOGIN_QR_X + 0.5, LOGIN_QR_Y + 0.5,
                LOGIN_QR_W - 1, LOGIN_QR_H - 1, 14, 14));
    }

    // =====================================================================
    // 导入歌词：小卡片
    // =====================================================================

    static void lrc(Graphics2D g, int w, int h) {
        prepare(g);
        shell(g, w, h, 18);
        icon(g, 22, 20);
        hairline(g, 22, 1, w - 44, new Color(108, 198, 255, 46));
        innerCard(g, 24, 64, 512, 36, 12, CARD_TOP, CARD_BOTTOM);
        well(g, 36, 71, 488, 22, 8);
        line(g, 24, 104, 512, LINE);
    }

    // =====================================================================
    // 基础绘制
    // =====================================================================

    static void shell(Graphics2D g, int w, int h, int radius) {
        shadow(g, 0, 0, w, h, radius);
        RoundRectangle2D body = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, radius, radius);
        g.setPaint(new GradientPaint(0, 0, TOP, 0, h, BOTTOM));
        g.fill(body);

        Shape old = g.getClip();
        g.clip(body);
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 12),
                0, 92, new Color(255, 255, 255, 0)));
        g.fillRect(0, 0, w, 92);
        g.setClip(old);

        g.setColor(LINE_STRONG);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(1.0, 1.0, w - 2, h - 2,
                Math.max(1, radius - 1), Math.max(1, radius - 1)));
        // 顶部 1px 高光，替代原版那种整圈像素边
        hairline(g, radius, 1, w - radius * 2, new Color(255, 255, 255, 34));
    }

    static void innerCard(Graphics2D g, int x, int y, int w, int h, int radius,
                          Color top, Color bottom) {
        g.setColor(new Color(0, 0, 0, 64));
        g.fill(new RoundRectangle2D.Double(x, y + 2, w, h, radius, radius));
        RoundRectangle2D r = new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1,
                radius, radius);
        g.setPaint(new GradientPaint(0, y, top, 0, y + h, bottom));
        g.fill(r);
        g.setColor(LINE);
        g.setStroke(new BasicStroke(1f));
        g.draw(r);
        hairline(g, x + radius, y + 1, w - radius * 2, new Color(255, 255, 255, 14));
    }

    static void well(Graphics2D g, int x, int y, int w, int h, int radius) {
        RoundRectangle2D r = new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1,
                radius, radius);
        g.setPaint(new GradientPaint(0, y, WELL_TOP, 0, y + h, WELL_BOTTOM));
        g.fill(r);
        g.setColor(new Color(255, 255, 255, 18));
        g.setStroke(new BasicStroke(1f));
        g.draw(r);
    }

    static void roundBorder(Graphics2D g, int x, int y, int w, int h, int radius) {
        g.setColor(LINE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, radius, radius));
    }

    static void line(Graphics2D g, int x, int y, int w, Color color) {
        g.setColor(color);
        g.fillRect(x, y, w, 1);
    }

    static void hairline(Graphics2D g, int x, int y, int w, Color color) {
        g.setColor(color);
        g.fillRect(x, y, w, 1);
    }

    /** 柔和投影：由外到内叠几层低透明度圆角矩形，烘焙后看不出阶梯。 */
    static void shadow(Graphics2D g, int x, int y, int w, int h, int radius) {
        for (int spread = 10; spread >= 1; spread--) {
            int alpha = 2 + (10 - spread);
            g.setColor(new Color(0, 0, 0, alpha));
            g.fill(new RoundRectangle2D.Double(x - spread, y - spread + 4,
                    w + spread * 2, h + spread * 2,
                    radius + spread * 2, radius + spread * 2));
        }
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new RoundRectangle2D.Double(x + 1, y + 3, w - 2, h - 2, radius, radius));
    }

    /** 左上角的小波形图标，让标题区不至于是纯文字。 */
    static void icon(Graphics2D g, int x, int y) {
        g.setColor(new Color(108, 198, 255, 44));
        g.fill(new RoundRectangle2D.Double(x - 2, y, 18, 17, 7, 7));
        g.setColor(new Color(108, 198, 255, 235));
        g.fill(new RoundRectangle2D.Double(x, y + 6, 2.5, 5, 1.25, 1.25));
        g.fill(new RoundRectangle2D.Double(x + 5, y + 2, 2.5, 13, 1.25, 1.25));
        g.fill(new RoundRectangle2D.Double(x + 10, y + 4, 2.5, 9, 1.25, 1.25));
    }
}
