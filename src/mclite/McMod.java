package mclite;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class McMod {
    private static final Pattern ITEM = Pattern.compile(
            "<div class=\"result-item\">(.*?)(?=<div class=\"result-item\">|<div class=\"search-result-pages\">|$)",
            Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile(
            "href=\"(https://www\\.mcmod\\.cn/class/(\\d+)\\.html)\"[^>]*>(.*?)</a>",
            Pattern.DOTALL);
    private static final Pattern BODY = Pattern.compile(
            "<div class=\"body\">(.*?)</div>", Pattern.DOTALL);

    private final Net net;

    public McMod() {
        this.net = new Net(ProgressSink.NOOP, new CancellationToken());
    }

    public record Entry(int id, String url, String title, String englishName, String summary) {
    }

    public List<Entry> search(String query) throws IOException {
        return search(query, 1);
    }

    public List<Entry> search(String query, int filter) throws IOException {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String url = "https://search.mcmod.cn/s?key="
                + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&filter=" + filter;
        String html = new String(net.getUnverifiedBytes(url, "MC百科搜索"), StandardCharsets.UTF_8);
        List<Entry> entries = new ArrayList<>();
        Matcher itemMatcher = ITEM.matcher(html);
        while (itemMatcher.find() && entries.size() < 20) {
            String block = itemMatcher.group(1);
            Matcher link = LINK.matcher(block);
            if (!link.find()) {
                continue;
            }
            String rawTitle = strip(link.group(3));
            Matcher body = BODY.matcher(block);
            String summary = body.find() ? strip(body.group(1)) : "";
            if (summary.length() > 220) {
                summary = summary.substring(0, 218) + "…";
            }
            entries.add(new Entry(Integer.parseInt(link.group(2)), link.group(1), rawTitle,
                    englishName(rawTitle), summary));
        }
        return entries;
    }

    private static String englishName(String title) {
        Matcher parentheses = Pattern.compile("\\(([^()]*)\\)\\s*$").matcher(title);
        if (parentheses.find()) {
            String value = parentheses.group(1).trim();
            if (value.matches(".*[A-Za-z].*")) {
                return value;
            }
        }
        String afterTag = title.replaceFirst("^\\[[^]]*]\\s*", "").trim();
        return afterTag.matches(".*[A-Za-z].*") ? afterTag : "";
    }

    private static String strip(String html) {
        return cleanText(html);
    }

    /** Strip HTML and MC百科 BBCode (for example [h1=简介]) from a summary. */
    public static String cleanText(String html) {
        if (html == null) {
            return "";
        }
        String text = html
                .replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("(?s)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\[/?[a-zA-Z][^\\]]*]", " ")
                .replaceAll("\\[/?\\*]", " · ");
        text = decodeNumericEntities(text);
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String decodeNumericEntities(String text) {
        java.util.regex.Matcher matcher = Pattern.compile("&#(\\d+);").matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            try {
                int codePoint = Integer.parseInt(matcher.group(1));
                matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
                        new String(Character.toChars(codePoint))));
            } catch (RuntimeException ex) {
                matcher.appendReplacement(out, matcher.group());
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
