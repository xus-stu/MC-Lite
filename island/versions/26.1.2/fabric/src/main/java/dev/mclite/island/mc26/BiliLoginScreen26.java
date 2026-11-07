package dev.mclite.island.mc26;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.mclite.island.bili.BiliApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** B 站扫码登录页。二维码只在这里绘制，不常驻 HUD。 */
final class BiliLoginScreen26 extends Screen {

    private static final int QR_SIZE = UiPaint26.LOGIN_QR_INNER;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int DIM = 0xFF93A0AD;
    private static final int MUTED = 0xFF6B7885;
    private static final int ACCENT = 0xFF8FD3FF;
    private static final int ERROR = 0xFFFF8A8A;

    private final McliteIsland26Client client;
    private final Runnable onSuccess;
    private final Screen parent;

    private volatile BitMatrix qr;
    private volatile String status = "正在生成二维码…";
    private volatile boolean active = true;
    private String qrKey = "";
    private Thread pollThread;
    private volatile int generation;

    private int panelX;
    private int panelY;
    private UISurface26.Surface background;
    private final UiText26 statusCache = new UiText26();

    BiliLoginScreen26(McliteIsland26Client client, Runnable onSuccess) {
        this(client, onSuccess, null);
    }

    BiliLoginScreen26(McliteIsland26Client client, Runnable onSuccess, Screen parent) {
        super(Component.literal("登录 B 站"));
        this.client = client;
        this.onSuccess = onSuccess;
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = Math.max(8, (width - UiPaint26.LOGIN_W) / 2);
        panelY = Math.max(8, (height - UiPaint26.LOGIN_H) / 2);
        background = UISurface26.get("login", UiPaint26.LOGIN_W, UiPaint26.LOGIN_H,
                UiPaint26::login);

        int cx = panelX + UiPaint26.LOGIN_W / 2;
        addRenderableWidget(UiButton26.primary("刷新二维码", cx - 110, panelY + 288, 100, 28,
                this::startQr));
        addRenderableWidget(UiButton26.ghost("取消", cx + 10, panelY + 288, 100, 28,
                this::onClose));
        startQr();
    }

    private void startQr() {
        active = false;
        Thread old = pollThread;
        if (old != null) {
            old.interrupt();
        }
        active = true;
        int myGeneration = ++generation;
        qr = null;
        status = "正在生成二维码…";
        Thread thread = new Thread(() -> {
            try {
                BiliApi.Qr qrData = client.bili().qrGenerate();
                qrKey = qrData.key();
                BitMatrix matrix = new QRCodeWriter().encode(qrData.url(),
                        BarcodeFormat.QR_CODE, 33, 33,
                        Map.of(EncodeHintType.MARGIN, 0));
                qr = matrix;
                status = "请用 B 站 App 扫码";
                while (active && generation == myGeneration) {
                    Thread.sleep(2000L);
                    if (generation != myGeneration) {
                        break;
                    }
                    BiliApi.QrPoll poll = client.bili().qrPoll(qrKey);
                    switch (poll.code()) {
                        case 0 -> {
                            client.bili().saveCookies(client.biliCookieFile());
                            status = "登录成功";
                            Minecraft.getInstance().execute(() -> {
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                            });
                            active = false;
                            if (parent != null) {
                                try {
                                    Thread.sleep(650L);
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                }
                                Minecraft.getInstance().execute(() -> {
                                    if (generation == myGeneration
                                            && Minecraft.getInstance().screen == BiliLoginScreen26.this) {
                                        Minecraft.getInstance().setScreen(parent);
                                    }
                                });
                            }
                        }
                        case 86090 -> status = "已扫码，请在手机上确认";
                        case 86038 -> {
                            status = "二维码已过期，请刷新";
                            active = false;
                        }
                        case 86101 -> status = "请用 B 站 App 扫码";
                        default -> status = poll.message().isBlank()
                                ? "等待扫码…" : poll.message();
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                status = "登录失败: " + ex.getMessage();
            }
        }, "mclite-island-bili-login");
        thread.setDaemon(true);
        pollThread = thread;
        thread.start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        Font font = minecraft.font;
        if (background != null) {
            UISurface26.draw(extractor, background, panelX, panelY);
        } else {
            UiDraw26.panel(extractor, UiDraw26.CARD, panelX, panelY,
                    UiPaint26.LOGIN_W, UiPaint26.LOGIN_H, 20);
            UiDraw26.panel(extractor, UiDraw26.WHITE, panelX + UiPaint26.LOGIN_QR_X,
                    panelY + UiPaint26.LOGIN_QR_Y, UiPaint26.LOGIN_QR_W,
                    UiPaint26.LOGIN_QR_H, 14);
        }

        int titleWidth = Math.round(font.width("登录 B 站") * 1.3f);
        drawScaledText(extractor, font, "登录 B 站", panelX + 200 - titleWidth / 2, panelY + 18,
                1.3f, TEXT);
        UiDraw26.textCentered(extractor, font, "cookie 仅保存在本机实例目录",
                panelX + 200, panelY + 38, DIM);

        int qx = panelX + UiPaint26.LOGIN_QR_X + 6;
        int qy = panelY + UiPaint26.LOGIN_QR_Y + 6;
        BitMatrix matrix = qr;
        if (matrix != null) {
            int modules = matrix.getWidth();
            float module = QR_SIZE / (float) modules;
            for (int row = 0; row < modules; row++) {
                int col = 0;
                while (col < modules) {
                    if (!matrix.get(col, row)) {
                        col++;
                        continue;
                    }
                    int start = col;
                    while (col < modules && matrix.get(col, row)) {
                        col++;
                    }
                    int px = qx + Math.round(start * module);
                    int px2 = qx + Math.round(col * module);
                    int py = qy + Math.round(row * module);
                    int py2 = qy + Math.round((row + 1) * module);
                    extractor.fill(px, py, px2, py2, 0xFF101418);
                }
            }
        } else {
            UiDraw26.textCentered(extractor, font, "生成中…", qx + QR_SIZE / 2,
                    qy + QR_SIZE / 2 - 4, MUTED);
        }

        String state = status;
        int color = state.startsWith("登录成功") ? ACCENT
                : state.startsWith("登录失败") || state.contains("过期") ? ERROR : DIM;
        statusCache.set(font, state, 340);
        UiDraw26.textCentered(extractor, font, statusCache.text(), panelX + 200, panelY + 262, color);
        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    private static void drawScaledText(GuiGraphicsExtractor extractor, Font font, String text,
                                       int x, int y, float scale, int color) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        extractor.text(font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    @Override
    public void onClose() {
        active = false;
        Thread thread = pollThread;
        if (thread != null) {
            thread.interrupt();
        }
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
