package mclite;

public final class VersionIds {
    private VersionIds() {
    }

    public static boolean validFileName(String name) {
        if (name == null || name.isEmpty() || name.length() > 240 || name.equals(".")
                || name.equals("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || c == '/' || c == '\\' || c == ':'
                    || c == '*' || c == '?' || c == '"' || c == '<' || c == '>'
                    || c == '|') {
                return false;
            }
        }
        return true;
    }

    public static boolean valid(String id) {
        if (id == null || id.isEmpty() || id.length() > 120 || id.equals(".")
                || id.equals("..")) {
            return false;
        }
        if (Character.isWhitespace(id.charAt(0))
                || Character.isWhitespace(id.charAt(id.length() - 1))
                || id.endsWith(".")) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isISOControl(c) || c == '/' || c == '\\' || c == ':'
                    || c == '*' || c == '?' || c == '"' || c == '<' || c == '>'
                    || c == '|') {
                return false;
            }
        }
        return true;
    }
}
