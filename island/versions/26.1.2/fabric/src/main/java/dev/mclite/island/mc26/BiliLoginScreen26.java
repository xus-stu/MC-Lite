package dev.mclite.island.mc26;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.mclite.island.bili.BiliApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** B 站扫码登录页。二维码只在这里绘制，不常驻 HUD。 */
final class BiliLoginScreen26 extends Screen {

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 320;
    private static final int QR_SIZE = 176;

    private final McliteIsland26Client client;
    private final Runnable onSuccess;

    private volatile BitMatrix qr;
    private volatile String status = "正在生成二维码…";
    private volatile boolean active = true;
    private String qrKey = "";
    private Thread pollThread;

    BiliLoginScreen26(McliteIsland26Client client, Runnable onSuccess) {
        super(Component.literal("登录 B 站"));
        this.client = client;
        this.onSuccess = onSuccess;
    }

    private static Component text(String value) {
        return Component.literal(value == null ? "" : value);
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int bottom = height / 2 + PANEL_H / 2;
        addRenderableWidget(Button.builder(text("刷新二维码"), b -> startQr())
                .bounds(cx - 110, bottom - 44, 100, 20).build());
        addRenderableWidget(Button.builder(text("取消"), b -> onClose())
                .bounds(cx + 10, bottom - 44, 100, 20).build());
        startQr();
    }

    private void startQr() {
        active = false;
        Thread old = pollThread;
        if (old != null) {
            old.interrupt();
        }
        active = true;
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
                while (active) {
                    Thread.sleep(2000L);
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
        var font = minecraft.font;
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        UiDraw26.panel(extractor, UiDraw26.CARD, x, y, PANEL_W, PANEL_H, 18);

        String title = "登录 B 站";
        extractor.text(font, title, x + (PANEL_W - font.width(title)) / 2, y + 18, 0xFFF2F5F8, false);
        String hint = "cookie 仅保存在本机实例目录";
        extractor.text(font, hint, x + (PANEL_W - font.width(hint)) / 2, y + 36, 0xFF93A0AD, false);

        int qx = x + (PANEL_W - QR_SIZE) / 2;
        int qy = y + 56;
        UiDraw26.panel(extractor, UiDraw26.WHITE, qx - 6, qy - 6, QR_SIZE + 12, QR_SIZE + 12, 8);
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
            String loading = "生成中…";
            extractor.text(font, loading, qx + (QR_SIZE - font.width(loading)) / 2,
                    qy + QR_SIZE / 2, 0xFF5A6570, false);
        }

        String state = status;
        int color = state.startsWith("登录成功") ? 0xFF6CC6FF
                : state.startsWith("登录失败") || state.contains("过期") ? 0xFFFF6B6B : 0xFF93A0AD;
        extractor.text(font, state, x + (PANEL_W - font.width(state)) / 2,
                qy + QR_SIZE + 16, color, false);
        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    private static void fillRounded(GuiGraphicsExtractor extractor, int x, int y,
                                    int width, int height, int radius, int color) {
        int safe = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int edge = Math.min(row, height - 1 - row);
            int inset = 0;
            if (edge < safe) {
                int dx = safe - edge;
                inset = safe - (int) Math.round(Math.sqrt(Math.max(0, safe * safe - dx * dx)));
            }
            extractor.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    @Override
    public void onClose() {
        active = false;
        Thread thread = pollThread;
        if (thread != null) {
            thread.interrupt();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
