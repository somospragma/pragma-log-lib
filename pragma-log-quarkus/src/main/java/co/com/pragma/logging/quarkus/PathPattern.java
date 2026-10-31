package co.com.pragma.logging.quarkus;

import java.util.regex.Pattern;

/** Minimal Ant-style path patterns: {@code /q/**} matches a subtree, {@code *} one path segment. */
final class PathPattern {

    private PathPattern() {
    }

    static boolean matches(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        if (pattern.indexOf('*') < 0) {
            return pattern.equals(path);
        }
        StringBuilder regex = new StringBuilder();
        for (String part : pattern.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append("[^/]*");
            }
            regex.append(Pattern.quote(part));
        }
        return path.matches(regex.toString());
    }
}
