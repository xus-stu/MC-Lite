package mclite;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Formatting {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private Formatting() {
    }

    public static String bytes(long value) {
        if (value < 0) {
            return "未知";
        }
        double amount = value;
        int unit = 0;
        while (amount >= 1024 && unit < UNITS.length - 1) {
            amount /= 1024;
            unit++;
        }
        if (unit == 0) {
            return value + " B";
        }
        return String.format(Locale.ROOT, amount >= 100 ? "%.0f %s" : "%.1f %s", amount, UNITS[unit]);
    }

    public static String date(String isoInstant) {
        if (isoInstant == null || isoInstant.isBlank()) {
            return "未知日期";
        }
        try {
            return DATE.format(Instant.parse(isoInstant));
        } catch (RuntimeException ex) {
            return isoInstant.length() >= 10 ? isoInstant.substring(0, 10) : isoInstant;
        }
    }

    public static String typeName(String type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case "release" -> "正式版";
            case "snapshot" -> "快照";
            case "old_beta" -> "旧 Beta";
            case "old_alpha" -> "旧 Alpha";
            default -> type;
        };
    }

    public static String html(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
