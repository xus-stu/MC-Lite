package mclite;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Theme {
    public static Color BG = new Color(0x0D0D0D);
    public static Color BG_SOFT = new Color(0x070707);
    public static Color CARD = new Color(0x151515);
    public static Color CARD_ALT = new Color(0x1F1F1F);
    public static Color BG_4 = new Color(0x292929);
    public static Color HOVER = new Color(0x303030);
    public static Color FIELD = new Color(0x1F1F1F);
    public static Color BORDER = new Color(0x343434);
    public static Color BORDER_LIGHT = new Color(0x4A4A4A);
    public static Color TEXT = new Color(0xF5F5F5);
    public static Color MUTED = new Color(0xC9C9C9);
    public static Color DIM = new Color(0x8E8E8E);
    public static Color ACCENT = new Color(0xF2F2F2);
    public static Color ACCENT_HOVER = new Color(0xFFFFFF);
    public static Color ACCENT_2 = new Color(0xBDBDBD);
    public static Color ACCENT_DARK = new Color(0x262626);
    public static Color ACCENT_TEXT = new Color(0x0A0A0A);
    public static Color SELECTED = new Color(0x303030);
    public static Color SEL = new Color(0xFF, 0xFF, 0xFF, 0x30);
    public static Color WARNING = new Color(0xD6D6D6);
    public static Color DANGER = new Color(0xC4C4C4);
    public static Color ORANGE = new Color(0xD6D6D6);

    public static String FAMILY = pickFamily();
    public static Font TITLE = new Font(FAMILY, Font.BOLD, 20);
    public static Font H1 = new Font(FAMILY, Font.BOLD, 15);
    public static Font H2 = new Font(FAMILY, Font.BOLD, 14);
    public static Font BODY = new Font(FAMILY, Font.PLAIN, 13);
    public static Font BODY_BOLD = new Font(FAMILY, Font.BOLD, 13);
    public static Font SMALL = new Font(FAMILY, Font.PLAIN, 12);
    public static Font TINY = new Font(FAMILY, Font.PLAIN, 11);
    public static Font MICRO = new Font(FAMILY, Font.PLAIN, 10);
    public static Font MONO = new Font(pickMonoFamily(), Font.PLAIN, 11);

    private Theme() {
    }

    private static float fontScale = 1f;

    public static void loadCss(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            parseCss(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            Log.warn("读取界面样式失败，将使用默认样式: " + ex.getMessage());
        }
    }

    public static void applyCss(Path file) {
        loadCss(file);
        rebuildFonts();
    }

    public static void setFontFamily(String family) {
        if (family != null && !family.isBlank() && mclite.FontCatalog.available(family)) {
            FAMILY = family;
        } else {
            FAMILY = pickFamily();
        }
        rebuildFonts();
    }

    public static String defaultFamily() {
        return pickFamily();
    }

    public static String defaultCss() {
        return """
                background: #0D0D0D
                background-soft: #070707
                card: #151515
                card-alt: #1F1F1F
                field: #1F1F1F
                text: #F5F5F5
                muted: #C9C9C9
                dim: #8E8E8E
                accent: #F2F2F2
                accent-text: #0A0A0A
                border: #343434
                danger: #C4C4C4
                font-size: 13
                """;
    }

    private static void parseCss(String text) {
        String clean = text.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
        clean = clean.replace('{', '\n').replace('}', '\n').replace(';', '\n');
        for (String line : clean.split("\\R")) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("@") || value.startsWith(".")
                    || value.startsWith("#") && !value.contains(":")) {
                continue;
            }
            int colon = value.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = value.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            String val = value.substring(colon + 1).trim();
            if (val.isEmpty()) {
                continue;
            }
            switch (key) {
                case "background", "background-color", "backgroud", "bg", "window", "window-background" -> BG = color(val, BG);
                case "background-soft", "bg-soft" -> BG_SOFT = color(val, BG_SOFT);
                case "card", "surface" -> CARD = color(val, CARD);
                case "card-alt", "card_alt", "alt" -> CARD_ALT = color(val, CARD_ALT);
                case "field", "input" -> FIELD = color(val, FIELD);
                case "hover" -> HOVER = color(val, HOVER);
                case "bg-4", "bg4" -> BG_4 = color(val, BG_4);
                case "selected" -> SELECTED = color(val, SELECTED);
                case "border" -> BORDER = color(val, BORDER);
                case "border-light" -> BORDER_LIGHT = color(val, BORDER_LIGHT);
                case "text", "color", "colour", "foreground" -> TEXT = color(val, TEXT);
                case "muted" -> MUTED = color(val, MUTED);
                case "dim" -> DIM = color(val, DIM);
                case "accent" -> {
                    ACCENT = color(val, ACCENT);
                    ACCENT_HOVER = brighter(ACCENT, 18);
                    ACCENT_2 = darker(ACCENT, 22);
                    SEL = alpha(ACCENT, 48);
                    SELECTED = color(val, SELECTED);
                }
                case "accent-text" -> ACCENT_TEXT = color(val, ACCENT_TEXT);
                case "danger" -> DANGER = color(val, DANGER);
                case "warning" -> WARNING = color(val, WARNING);
                case "font-family", "font" -> {
                    String family = val.replace("\"", "").replace("'", "").trim();
                    if (!family.isEmpty()) {
                        FAMILY = family;
                    }
                }
                case "font-size" -> {
                    try {
                        fontScale = Math.max(0.8f, Math.min(1.6f,
                                Float.parseFloat(val.replace("px", "").trim()) / 13f));
                    } catch (NumberFormatException ignored) {
                    }
                }
                default -> {

                }
            }
        }
        rebuildFonts();
    }

    private static Color color(String value, Color fallback) {
        String v = value.trim();
        try {
            if (v.startsWith("#")) {
                String hex = v.substring(1);
                if (hex.length() == 3) {
                    hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1)
                            + hex.charAt(2) + hex.charAt(2);
                }
                if (hex.length() == 6) {
                    return new Color(Integer.parseInt(hex, 16));
                }
                if (hex.length() == 8) {
                    return new Color((int) Long.parseLong(hex, 16), true);
                }
            }
            if (v.startsWith("rgb(") && v.endsWith(")")) {
                String[] parts = v.substring(4, v.length() - 1).split(",");
                if (parts.length >= 3) {
                    return new Color(Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()));
                }
            }
            return switch (v.toLowerCase(java.util.Locale.ROOT)) {
                case "black" -> Color.BLACK;
                case "white" -> Color.WHITE;
                case "gray", "grey" -> Color.GRAY;
                case "transparent" -> new Color(0, 0, 0, 0);
                default -> fallback;
            };
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static Color brighter(Color color, int amount) {
        return new Color(Math.min(255, color.getRed() + amount),
                Math.min(255, color.getGreen() + amount),
                Math.min(255, color.getBlue() + amount));
    }

    private static Color darker(Color color, int amount) {
        return new Color(Math.max(0, color.getRed() - amount),
                Math.max(0, color.getGreen() - amount),
                Math.max(0, color.getBlue() - amount));
    }

    private static void rebuildFonts() {
        TITLE = new Font(FAMILY, Font.BOLD, Math.round(20 * fontScale));
        H1 = new Font(FAMILY, Font.BOLD, Math.round(15 * fontScale));
        H2 = new Font(FAMILY, Font.BOLD, Math.round(14 * fontScale));
        BODY = new Font(FAMILY, Font.PLAIN, Math.round(13 * fontScale));
        BODY_BOLD = new Font(FAMILY, Font.BOLD, Math.round(13 * fontScale));
        SMALL = new Font(FAMILY, Font.PLAIN, Math.round(12 * fontScale));
        TINY = new Font(FAMILY, Font.PLAIN, Math.round(11 * fontScale));
        MICRO = new Font(FAMILY, Font.PLAIN, Math.round(10 * fontScale));
        MONO = new Font(pickMonoFamily(), Font.PLAIN, Math.round(11 * fontScale));
    }

    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {

        }
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", BODY);
        UIManager.put("TextField.background", FIELD);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.selectionBackground", SELECTED);
        UIManager.put("TextField.selectionForeground", TEXT);
        UIManager.put("TextField.border", BorderFactory.createEmptyBorder(7, 9, 7, 9));
        UIManager.put("TextArea.background", CARD);
        UIManager.put("TextArea.foreground", MUTED);
        UIManager.put("ComboBox.background", CARD_ALT);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", SELECTED);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ComboBox.buttonBackground", CARD_ALT);
        UIManager.put("ComboBox.buttonHighlight", CARD_ALT);
        UIManager.put("ComboBox.buttonShadow", BORDER);
        UIManager.put("ComboBox.buttonDarkShadow", BORDER);
        UIManager.put("Spinner.background", FIELD);
        UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("FormattedTextField.background", FIELD);
        UIManager.put("FormattedTextField.foreground", TEXT);
        UIManager.put("FormattedTextField.caretForeground", ACCENT);
        UIManager.put("FormattedTextField.selectionBackground", SELECTED);
        UIManager.put("FormattedTextField.selectionForeground", TEXT);
        UIManager.put("ScrollBar.thumb", BG_4);
        UIManager.put("ScrollBar.track", BG);
        UIManager.put("ScrollBar.background", BG);
        UIManager.put("ScrollPane.background", BG);
        UIManager.put("Viewport.background", BG);
        UIManager.put("List.background", BG);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", SEL);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("ToolTip.background", CARD_ALT);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("ToolTip.font", SMALL);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", BODY);
        UIManager.put("OptionPane.buttonFont", BODY_BOLD);
        UIManager.put("OptionPane.yesButtonText", "是");
        UIManager.put("OptionPane.noButtonText", "否");
        UIManager.put("OptionPane.cancelButtonText", "取消");
        UIManager.put("OptionPane.okButtonText", "确定");
        UIManager.put("Button.background", CARD_ALT);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.font", BODY_BOLD);
        UIManager.put("FileChooser.background", CARD);
        UIManager.put("FileChooser.foreground", TEXT);
        UIManager.put("FileChooser.listFont", BODY);
        UIManager.put("FileChooser.listViewBackground", BG);
        UIManager.put("FileChooser.listViewForeground", TEXT);
        UIManager.put("FileChooser.directoryForeground", TEXT);
        UIManager.put("FileChooser.fileForeground", TEXT);
        UIManager.put("FileChooser.controlsBackground", CARD);
        UIManager.put("FileChooser.cancelButtonText", "取消");
        UIManager.put("FileChooser.openButtonText", "选择");
    }

    private static String pickFamily() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        String[] preferred = {
                "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei UI", "Microsoft YaHei",
                "Noto Sans CJK SC", "Source Han Sans SC", "WenQuanYi Micro Hei", "SansSerif"
        };
        for (String family : preferred) {
            if (available.contains(family)) {
                return family;
            }
        }
        return Font.SANS_SERIF;
    }

    private static String pickMonoFamily() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : new String[]{"SF Mono", "Menlo", "JetBrains Mono", "Consolas", "Monospaced"}) {
            if (available.contains(family)) {
                return family;
            }
        }
        return Font.MONOSPACED;
    }

    public static Color mix(Color from, Color to, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * f),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * f),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * f),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * f));
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
