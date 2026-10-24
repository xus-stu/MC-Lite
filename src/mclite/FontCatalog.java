package mclite;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FontCatalog {
    public record Choice(String label, String family) {
        @Override
        public String toString() {
            return label;
        }
    }

    private FontCatalog() {
    }

    public static List<Choice> choices() {
        List<Choice> result = new ArrayList<>();
        result.add(new Choice("系统默认", ""));
        Set<String> installed = new LinkedHashSet<>(List.of(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        String kaiti = pick(installed, "Kaiti SC", "Kaiti TC", "STKaiti", "KaiTi", "Kaiti",
                "AR PL UKai CN", "Noto Serif CJK SC");
        if (kaiti != null) {
            result.add(new Choice("楷体 · " + kaiti, kaiti));
        }
        String handwriting = pick(installed, "Hannotate SC", "Hannotate TC", "HanziPen SC",
                "HanziPen TC", "Xingkai SC", "STXingkai", "Weibei SC");
        if (handwriting != null && !handwriting.equals(kaiti)) {
            result.add(new Choice("手写楷体 · " + handwriting, handwriting));
        }
        List<String> ordinary = new ArrayList<>();
        for (String family : installed) {
            if (family == null || family.isBlank() || family.startsWith("@")) {
                continue;
            }
            if (!family.equals(kaiti) && !family.equals(handwriting)
                    && supportsChinese(family)) {
                ordinary.add(family);
            }
        }
        ordinary.sort(String.CASE_INSENSITIVE_ORDER);
        for (String family : ordinary) {
            result.add(new Choice(family, family));
        }
        return result;
    }

    public static boolean supportsChinese(String family) {
        if (family == null || family.isBlank()) {
            return true;
        }
        java.awt.Font font = new java.awt.Font(family, java.awt.Font.PLAIN, 12);
        return font.canDisplay('中') && font.canDisplay('字');
    }

    public static boolean available(String family) {
        if (family == null || family.isBlank()) {
            return true;
        }
        for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()) {
            if (name.equals(family)) {
                return true;
            }
        }
        return false;
    }

    private static String pick(Set<String> installed, String... candidates) {
        for (String candidate : candidates) {
            if (installed.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
