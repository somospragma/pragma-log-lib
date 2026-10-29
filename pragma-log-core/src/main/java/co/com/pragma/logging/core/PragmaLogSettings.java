package co.com.pragma.logging.core;

import co.com.pragma.logging.core.masking.MaskingSettings;
import java.util.Objects;

/**
 * Immutable, validated settings shared by every adapter (ADR-09).
 * Each framework fills it from its native configuration source.
 *
 * @param appName name of the application, emitted as the {@value StandardFields#APP} field
 * @param masking masking configuration
 */
public record PragmaLogSettings(String appName, MaskingSettings masking) {

    public static final String DEFAULT_APP_NAME = "app";

    public PragmaLogSettings {
        Objects.requireNonNull(appName, "pragma.log.appName must not be null");
        if (appName.isBlank()) {
            throw new IllegalArgumentException("pragma.log.appName must not be blank");
        }
        Objects.requireNonNull(masking, "pragma.log.masking must not be null");
    }

    public static PragmaLogSettings defaults() {
        return new PragmaLogSettings(DEFAULT_APP_NAME, MaskingSettings.defaults());
    }
}
