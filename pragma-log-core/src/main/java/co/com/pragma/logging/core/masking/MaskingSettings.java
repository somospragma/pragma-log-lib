package co.com.pragma.logging.core.masking;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global masking configuration (MSK-02, MSK-09).
 *
 * @param enabled       master switch; when {@code false} nothing is masked
 * @param character     mask character
 * @param defaultType   type applied to fields matched by name ({@link #fields()})
 * @param defaultLength length used when an annotation does not set one
 * @param fields        field names masked even without annotation, at any depth (case and separators ignored:
 *                      {@code access_token} matches {@code accessToken})
 * @param maxDepth      maximum object nesting rendered; deeper values are replaced by a marker
 * @param strategies    custom strategies by name
 */
public record MaskingSettings(
        boolean enabled,
        char character,
        MaskType defaultType,
        int defaultLength,
        Set<String> fields,
        int maxDepth,
        Map<String, MaskingStrategy> strategies) {

    /** Field names masked by default: secure out of the box, replaceable through configuration. */
    public static final Set<String> DEFAULT_FIELDS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "accessToken", "refreshToken", "idToken",
            "apiKey", "authToken", "authorization", "proxyAuthorization", "cookie", "setCookie", "cardNumber",
            "cvv", "cvc", "pin");

    public static final char DEFAULT_CHARACTER = '*';
    public static final int DEFAULT_LENGTH = 4;
    public static final int DEFAULT_MAX_DEPTH = 10;

    public MaskingSettings {
        Objects.requireNonNull(defaultType, "pragma.log.masking.defaultType must not be null");
        if (defaultType == MaskType.CUSTOM) {
            throw new IllegalArgumentException("pragma.log.masking.defaultType cannot be CUSTOM");
        }
        if (defaultLength < 0) {
            throw new IllegalArgumentException("pragma.log.masking.defaultLength must be >= 0");
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("pragma.log.masking.maxDepth must be >= 1");
        }
        fields = Objects.requireNonNull(fields, "pragma.log.masking.fields must not be null").stream()
                .map(MaskingSettings::normalize)
                .collect(Collectors.toUnmodifiableSet());
        strategies = Map.copyOf(Objects.requireNonNull(strategies, "pragma.log.masking.strategies must not be null"));
    }

    public static MaskingSettings defaults() {
        return new MaskingSettings(true, DEFAULT_CHARACTER, MaskType.FULL, DEFAULT_LENGTH, DEFAULT_FIELDS,
                DEFAULT_MAX_DEPTH, Map.of());
    }

    /**
     * Whether a field, MDC or key-value name must be masked by name. Dotted names are also checked by
     * their last segment, without an {@code x-} header prefix: {@code http.header.x-api-key} matches
     * {@code apiKey} (MSK-11).
     */
    public boolean isSensitiveName(String name) {
        if (!enabled || name == null) {
            return false;
        }
        if (fields.contains(normalize(name))) {
            return true;
        }
        String last = name.substring(name.lastIndexOf('.') + 1);
        if (last.regionMatches(true, 0, "x-", 0, 2)) {
            last = last.substring(2);
        }
        return last.length() < name.length() && fields.contains(normalize(last));
    }

    private static String normalize(String name) {
        return name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
