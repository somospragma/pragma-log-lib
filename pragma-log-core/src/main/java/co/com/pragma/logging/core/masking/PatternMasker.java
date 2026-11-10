package co.com.pragma.logging.core.masking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Masks sensitive data found by pattern in final texts: messages, field values and stack traces (MSK-10). A
 * safety net for what escapes annotations and names, e.g. a card number concatenated into a message.
 *
 * <p>Each entry is a built-in name or a regular expression:
 * <ul>
 *   <li>{@code CARD}: card numbers (13 to 19 digits, spaces or dashes allowed) that pass the Luhn check; all
 *       digits but the first and last four are masked: {@code 4111 **** **** 1111}.</li>
 *   <li>{@code EMAIL}: the local part but its first letter is masked: {@code a***@acme.com}.</li>
 *   <li>{@code JWT}: the whole token is replaced by a fixed mask.</li>
 *   <li>Any other entry is a regex. With a capturing group only the first group is masked
 *       ({@code password=(\S+)}), otherwise the whole match; masked text keeps its length.</li>
 * </ul>
 *
 * <p>Thread-safe. Costs one scan per pattern on each text, so keep the list short.
 */
public final class PatternMasker {

    private static final PatternMasker NONE = new PatternMasker(List.of());
    private static final int FIXED_MASK_LENGTH = 8;

    private static final Pattern CARD = Pattern.compile("(?<![\\d-])\\d(?:[ -]?\\d){12,18}(?![\\d-])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");

    private record Rule(Pattern pattern, UnaryOperator<String> replace, boolean group) {
    }

    private final List<Rule> rules;

    private PatternMasker(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static PatternMasker none() {
        return NONE;
    }

    /**
     * Returns a masker of the given patterns: {@code CARD}, {@code EMAIL}, {@code JWT} or regular expressions.
     *
     * @throws IllegalArgumentException when a regex is invalid
     */
    public static PatternMasker of(List<String> patterns, char maskChar) {
        if (patterns == null || patterns.isEmpty()) {
            return NONE;
        }
        String fixed = String.valueOf(maskChar).repeat(FIXED_MASK_LENGTH);
        List<Rule> rules = new ArrayList<>();
        for (String entry : patterns) {
            if (entry == null || entry.isBlank()) {
                throw new IllegalArgumentException("pragma.log.masking.patterns must not contain blank entries");
            }
            switch (entry.trim().toUpperCase(Locale.ROOT)) {
                case "CARD" -> rules.add(new Rule(CARD, card -> maskCard(card, maskChar), false));
                case "EMAIL" -> rules.add(new Rule(EMAIL, email -> maskEmail(email, maskChar), false));
                case "JWT" -> rules.add(new Rule(JWT, token -> fixed, false));
                default -> rules.add(custom(entry, maskChar));
            }
        }
        return new PatternMasker(rules);
    }

    private static Rule custom(String regex, char maskChar) {
        try {
            Pattern pattern = Pattern.compile(regex);
            boolean group = pattern.matcher("").groupCount() > 0;
            return new Rule(pattern, text -> BuiltInMasks.full(text, maskChar), group);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("pragma.log.masking.patterns: invalid regex '" + regex + "': "
                    + e.getDescription(), e);
        }
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** The text with every match masked; {@code null} stays {@code null}. */
    public String mask(String text) {
        if (text == null || rules.isEmpty()) {
            return text;
        }
        String result = text;
        for (Rule rule : rules) {
            result = apply(rule, result);
        }
        return result;
    }

    private static String apply(Rule rule, String text) {
        Matcher matcher = rule.pattern().matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int last = 0;
        do {
            int group = rule.group() && matcher.group(1) != null ? 1 : 0;
            int start = matcher.start(group);
            int end = matcher.end(group);
            if (start < last) { // a group inside an earlier replacement
                continue;
            }
            out.append(text, last, start).append(rule.replace().apply(text.substring(start, end)));
            last = end;
        } while (matcher.find());
        return out.append(text, last, text.length()).toString();
    }

    /** Keeps the first and last four digits, and the separators; numbers that fail Luhn are not cards. */
    private static String maskCard(String card, char maskChar) {
        String digits = card.replaceAll("[ -]", "");
        if (!luhn(digits)) {
            return card;
        }
        StringBuilder out = new StringBuilder(card.length());
        int index = 0;
        for (int i = 0; i < card.length(); i++) {
            char c = card.charAt(i);
            if (Character.isDigit(c)) {
                out.append(index < 4 || index >= digits.length() - 4 ? c : maskChar);
                index++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        boolean doubled = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubled) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubled = !doubled;
        }
        return sum % 10 == 0;
    }

    private static String maskEmail(String email, char maskChar) {
        int at = email.indexOf('@');
        return email.charAt(0) + String.valueOf(maskChar).repeat(Math.max(at - 1, 0)) + email.substring(at);
    }
}
