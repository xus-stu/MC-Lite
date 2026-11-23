package mclite;

import java.awt.Color;
import java.util.List;

public record ThemePreset(String name, String css, Color background, Color card,
                          Color accent, Color text) {
    public static final List<ThemePreset> ALL = List.of(
            preset("暖语 · 绿（暗）", "#0D1411", "#142019", "#6FBF8B", "#E9F5EC",
                    "background: #0D1411\nbackground-soft: #08100C\ncard: #142019\n"
                            + "card-alt: #1B2B22\nfield: #1B2B22\nbg-4: #283A2F\nhover: #263C30\n"
                            + "text: #E9F5EC\nmuted: #C1D8C7\ndim: #8FAF99\naccent: #6FBF8B\n"
                            + "accent-text: #07130C\nborder: #2B4436\nborder-light: #3E5B49\n"
                            + "danger: #D9A0A0\nwarning: #D6C08A\nfont-size: 13\n"),
            preset("暖语 · 绿（亮）", "#F2F8F3", "#FFFFFF", "#3F8F5E", "#1A3223",
                    "background: #F2F8F3\nbackground-soft: #E9F2EB\ncard: #FFFFFF\n"
                            + "card-alt: #EAF3EC\nfield: #FFFFFF\nbg-4: #DCEADF\nhover: #D9EBDD\n"
                            + "text: #1A3223\nmuted: #456A52\ndim: #6C8A76\naccent: #3F8F5E\n"
                            + "accent-text: #FFFFFF\nborder: #CBDDCE\nborder-light: #B4CEB9\n"
                            + "danger: #A64B4B\nwarning: #8A6A27\nfont-size: 13\n"),
            preset("暖语 · 橙（暗）", "#1A130D", "#221A12", "#E59A4A", "#F8EDE2",
                    "background: #1A130D\nbackground-soft: #120D09\ncard: #221A12\n"
                            + "card-alt: #2E2117\nfield: #2E2117\nbg-4: #3A2A1D\nhover: #3A2A1D\n"
                            + "text: #F8EDE2\nmuted: #DCC6B0\ndim: #AA8C72\naccent: #E59A4A\n"
                            + "accent-text: #231305\nborder: #493524\nborder-light: #624931\n"
                            + "danger: #D8957C\nwarning: #D8B26A\nfont-size: 13\n"),
            preset("暖语 · 橙（亮）", "#FFF8EF", "#FFFFFF", "#C77722", "#382415",
                    "background: #FFF8EF\nbackground-soft: #F8EDE0\ncard: #FFFFFF\n"
                            + "card-alt: #F8EDE0\nfield: #FFFFFF\nbg-4: #F0DFC8\nhover: #F2E4D2\n"
                            + "text: #382415\nmuted: #75583E\ndim: #9A7D60\naccent: #C77722\n"
                            + "accent-text: #FFFFFF\nborder: #E4D1BB\nborder-light: #D3B99A\n"
                            + "danger: #A04532\nwarning: #8A5A19\nfont-size: 13\n"),
            preset("暖语 · 品红（暗）", "#160F15", "#20161F", "#D86AA8", "#F9EAF2",
                    "background: #160F15\nbackground-soft: #100A0F\ncard: #20161F\n"
                            + "card-alt: #2C1D29\nfield: #2C1D29\nbg-4: #3A2736\nhover: #3A2736\n"
                            + "text: #F9EAF2\nmuted: #D9BFCC\ndim: #A78698\naccent: #D86AA8\n"
                            + "accent-text: #1D0A15\nborder: #47303F\nborder-light: #644558\n"
                            + "danger: #D98A95\nwarning: #D3B16E\nfont-size: 13\n"),
            preset("暖语 · 品红（亮）", "#FDF3F8", "#FFFFFF", "#B13C7B", "#3A162C",
                    "background: #FDF3F8\nbackground-soft: #F8E8F0\ncard: #FFFFFF\n"
                            + "card-alt: #F8E8F0\nfield: #FFFFFF\nbg-4: #F0D6E4\nhover: #F3DDE9\n"
                            + "text: #3A162C\nmuted: #775064\ndim: #9C7688\naccent: #B13C7B\n"
                            + "accent-text: #FFFFFF\nborder: #E6CBD9\nborder-light: #D5AFC3\n"
                            + "danger: #9B3045\nwarning: #8A5B1D\nfont-size: 13\n"),
            preset("灰 · 暗", "#0D0D0D", "#151515", "#F2F2F2", "#F5F5F5",
                    "background: #0D0D0D\nbackground-soft: #070707\ncard: #151515\n"
                            + "card-alt: #1F1F1F\nfield: #1F1F1F\nbg-4: #292929\nhover: #303030\n"
                            + "text: #F5F5F5\nmuted: #C9C9C9\ndim: #8E8E8E\naccent: #F2F2F2\n"
                            + "accent-text: #0A0A0A\nborder: #343434\nborder-light: #4A4A4A\n"
                            + "danger: #C4C4C4\nwarning: #D6D6D6\nfont-size: 13\n"),
            preset("灰 · 亮", "#F5F5F5", "#FFFFFF", "#4A4A4A", "#1A1A1A",
                    "background: #F5F5F5\nbackground-soft: #ECECEC\ncard: #FFFFFF\n"
                            + "card-alt: #ECECEC\nfield: #FFFFFF\nbg-4: #DEDEDE\nhover: #E5E5E5\n"
                            + "text: #1A1A1A\nmuted: #555555\ndim: #777777\naccent: #4A4A4A\n"
                            + "accent-text: #FFFFFF\nborder: #D2D2D2\nborder-light: #BDBDBD\n"
                            + "danger: #A04444\nwarning: #866321\nfont-size: 13\n"),
            preset("暖语 · 蓝（暗）", "#0D121B", "#141D29", "#6FA8E8", "#E8F2FC",
                    "background: #0D121B\nbackground-soft: #080D14\ncard: #141D29\n"
                            + "card-alt: #1C2939\nfield: #1C2939\nbg-4: #28384C\nhover: #28384C\n"
                            + "text: #E8F2FC\nmuted: #B9CDE0\ndim: #8199B2\naccent: #6FA8E8\n"
                            + "accent-text: #08131F\nborder: #2B3D53\nborder-light: #40556D\n"
                            + "danger: #D79B9B\nwarning: #D6C084\nfont-size: 13\n"),
            preset("暖语 · 蓝（亮）", "#F1F6FC", "#FFFFFF", "#2F6FA8", "#142B40",
                    "background: #F1F6FC\nbackground-soft: #E6EFF9\ncard: #FFFFFF\n"
                            + "card-alt: #E6EFF9\nfield: #FFFFFF\nbg-4: #D4E3F2\nhover: #D9E7F5\n"
                            + "text: #142B40\nmuted: #47647D\ndim: #71869A\naccent: #2F6FA8\n"
                            + "accent-text: #FFFFFF\nborder: #C9D9E8\nborder-light: #ADC5DA\n"
                            + "danger: #9F4343\nwarning: #84621E\nfont-size: 13\n"),
            compact("暖语 · 紫（暗）", "#140F1B", "#1D1626", "#B08CE8", "#EFE7F9"),
            compact("暖语 · 紫（亮）", "#F7F3FD", "#FFFFFF", "#7B4FC0", "#2A1B3F"),
            compact("暖语 · 青（暗）", "#0C1517", "#142124", "#5FC7C7", "#E3F7F7"),
            compact("暖语 · 青（亮）", "#F0FAFA", "#FFFFFF", "#1F8A8A", "#123333"),
            compact("暖语 · 红（暗）", "#1A0F0F", "#241616", "#E86F6F", "#F9E9E9"),
            compact("暖语 · 红（亮）", "#FFF5F5", "#FFFFFF", "#C13C3C", "#3A1414")
    );

    /**
     * 精简主题：只给背景/卡片/强调/文字四个颜色，其余颜色按比例推导，
     * 这样每加一套主题只多几十字节，不会让 JAR 膨胀。
     */
    private static ThemePreset compact(String name, String bgHex, String cardHex,
                                       String accentHex, String textHex) {
        Color bg = Color.decode(bgHex);
        Color card = Color.decode(cardHex);
        Color accent = Color.decode(accentHex);
        Color text = Color.decode(textHex);
        boolean dark = luminance(bg) < 0.5f;
        String css = "background: " + hex(bg)
                + "\nbackground-soft: " + hex(Theme.mix(bg, dark ? Color.BLACK : Color.WHITE, 0.06f))
                + "\ncard: " + hex(card)
                + "\ncard-alt: " + hex(Theme.mix(card, text, 0.08f))
                + "\nfield: " + hex(Theme.mix(card, text, 0.08f))
                + "\nbg-4: " + hex(Theme.mix(card, text, 0.16f))
                + "\nhover: " + hex(Theme.mix(card, text, 0.12f))
                + "\ntext: " + hex(text)
                + "\nmuted: " + hex(Theme.mix(text, bg, 0.4f))
                + "\ndim: " + hex(Theme.mix(text, bg, 0.6f))
                + "\naccent: " + hex(accent)
                + "\naccent-text: " + (luminance(accent) > 0.55f ? "#0B0B0B" : "#FFFFFF")
                + "\nborder: " + hex(Theme.mix(card, text, 0.22f))
                + "\nborder-light: " + hex(Theme.mix(card, text, 0.34f))
                + "\ndanger: #D98A8A\nwarning: #D6C08A\nfont-size: 13\n";
        return new ThemePreset(name, css, bg, card, accent, text);
    }

    private static float luminance(Color color) {
        return (color.getRed() * 0.299f + color.getGreen() * 0.587f + color.getBlue() * 0.114f) / 255f;
    }

    private static String hex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static ThemePreset preset(String name, String bg, String card, String accent,
                                      String text, String css) {
        return new ThemePreset(name, css, Color.decode(bg), Color.decode(card),
                Color.decode(accent), Color.decode(text));
    }
}
