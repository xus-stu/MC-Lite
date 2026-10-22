package mclite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class Rules {
    private Rules() {
    }

    public record Env(String osName, String osArch, String osVersion, Map<String, Boolean> features) {
        public Env {
            features = features == null ? Map.of() : Map.copyOf(features);
        }

        public static Env current() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String name;
            if (os.contains("mac") || os.contains("darwin")) {
                name = "osx";
            } else if (os.contains("win")) {
                name = "windows";
            } else {
                name = "linux";
            }
            String archProperty = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            String arch;
            if (archProperty.contains("aarch64") || archProperty.contains("arm64")) {
                arch = "arm64";
            } else if (archProperty.contains("x86_64") || archProperty.contains("amd64")) {
                arch = "x86_64";
            } else if (archProperty.contains("x86") || archProperty.contains("i386")
                    || archProperty.contains("i686")) {
                arch = "x86";
            } else {
                arch = archProperty;
            }
            Map<String, Boolean> features = new LinkedHashMap<>();
            features.put("has_custom_resolution", true);
            features.put("is_demo_user", false);
            features.put("has_quick_plays_support", false);
            features.put("is_quick_play_singleplayer", false);
            features.put("is_quick_play_multiplayer", false);
            features.put("is_quick_play_realms", false);
            return new Env(name, arch, System.getProperty("os.version", ""), features);
        }

        public boolean feature(String key) {
            return Boolean.TRUE.equals(features.get(key));
        }
    }

    public record OsRule(String name, String arch, String version) {
        private boolean matches(Env env) {
            if (name != null && !name.equals(env.osName())) {
                return false;
            }
            if (arch != null && !arch.equals(env.osArch())) {
                return false;
            }
            if (version != null) {
                try {
                    if (!Pattern.compile(version).matcher(env.osVersion()).find()) {
                        return false;
                    }
                } catch (PatternSyntaxException ex) {
                    return false;
                }
            }
            return true;
        }
    }

    public record Rule(String action, OsRule os, Map<String, Boolean> features) {
        public boolean matches(Env env) {
            if (os != null && !os.matches(env)) {
                return false;
            }
            if (features != null) {
                for (Map.Entry<String, Boolean> entry : features.entrySet()) {
                    if (env.feature(entry.getKey()) != Boolean.TRUE.equals(entry.getValue())) {
                        return false;
                    }
                }
            }
            return true;
        }

        public static List<Rule> parseList(Object raw) {
            List<Object> list = Json.arr(raw);
            if (list.isEmpty()) {
                return List.of();
            }
            List<Rule> rules = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> map = Json.obj(item);
                String action = Json.str(map, "action");
                if (action == null) {
                    continue;
                }
                Map<String, Object> osMap = Json.obj(map.get("os"));
                OsRule os = osMap.isEmpty() ? null : new OsRule(
                        Json.str(osMap, "name"),
                        Json.str(osMap, "arch"),
                        Json.str(osMap, "version"));
                Map<String, Boolean> features = null;
                Map<String, Object> featureMap = Json.obj(map.get("features"));
                if (!featureMap.isEmpty()) {
                    features = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : featureMap.entrySet()) {
                        if (entry.getValue() instanceof Boolean value) {
                            features.put(entry.getKey(), value);
                        }
                    }
                }
                rules.add(new Rule(action, os, features == null ? null : Collections.unmodifiableMap(features)));
            }
            return Collections.unmodifiableList(rules);
        }
    }

    public static boolean allows(List<Rule> rules, Env env) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (Rule rule : rules) {
            if (rule.matches(env)) {
                allowed = "allow".equals(rule.action());
            }
        }
        return allowed;
    }

    public static List<String> splitLegacy(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (quoted) {
                if (c == quote) {
                    quoted = false;
                } else {
                    current.append(c);
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quoted = true;
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
