package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.format.ColorMode;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * pragma-log configuration from {@code application.properties} (ADR-09), with the same keys as the Spring
 * Boot starter.
 *
 * <pre>{@code
 * pragma.log.fields.env=prod
 * pragma.log.rename-fields.level=severity
 * pragma.log.masking.fields=password,documentId
 * pragma.log.http.headers=X-Client-Id
 * }</pre>
 */
@ConfigMapping(prefix = "pragma.log")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface PragmaLogConfig {

    /** Whether pragma-log formats the console and file logs. When false, Quarkus logging is left untouched. */
    @WithDefault("true")
    boolean enabled();

    /**
     * Output format: JSON (default) or PLAIN, readable text for local development with the same masking. Use a
     * profile to pick PLAIN locally, e.g. %dev.pragma.log.format=plain.
     */
    @WithDefault("json")
    Format format();

    /** Value of the "app" field. Defaults to quarkus.application.name. */
    Optional<String> appName();

    /** Value of the "version" field. Defaults to quarkus.application.version. */
    Optional<String> version();

    /** Whether the "host" field is added: HOSTNAME (the pod name in Kubernetes) or the local host name. */
    @WithDefault("true")
    boolean includeHost();

    /** Whether the "pid" field is added. */
    @WithDefault("false")
    boolean includePid();

    /** Fixed fields added to every event, e.g. env=prod. */
    Map<String, String> fields();

    /** Fields to remove: standard, fixed, MDC or key-value fields. */
    Optional<List<String>> hideFields();

    /** Fields to rename, as current name=new name. */
    Map<String, String> renameFields();

    Masking masking();

    Stacktrace stacktrace();

    Http http();

    Timestamp timestamp();

    Color color();

    enum Format {
        JSON,
        PLAIN
    }

    interface Masking {

        /** Whether sensitive data is masked. */
        @WithDefault("true")
        boolean enabled();

        /** Mask character. */
        @WithDefault("*")
        String character();

        /** Type applied to fields masked by name: FULL, PREFIX, SUFFIX, MIDDLE or HIDDEN. */
        @WithDefault("FULL")
        String defaultType();

        /** Length used by PREFIX, SUFFIX and MIDDLE when the annotation does not set one. */
        @WithDefault("4")
        int defaultLength();

        /** Field names masked without annotation. Replaces the secure default list when set. */
        Optional<List<String>> fields();

        /** Maximum nesting rendered for logged objects. */
        @WithDefault("10")
        int maxDepth();

        /** Custom strategies, as name=fully qualified class name. */
        Map<String, String> strategies();
    }

    interface Timestamp {

        /**
         * DateTimeFormatter pattern, e.g. yyyy-MM-dd HH:mm:ss.SSS, or a constant in brackets, e.g. [ISO_INSTANT].
         * Defaults to [ISO_OFFSET_DATE_TIME] in JSON and yyyy-MM-dd HH:mm:ss.SSS in PLAIN.
         */
        Optional<String> pattern();

        /** Time zone, e.g. UTC or America/Bogota. Defaults to the system zone. */
        Optional<String> zone();
    }

    interface Color {

        /**
         * Colors of the PLAIN console output: AUTO (only on a terminal, never with NO_COLOR), ALWAYS (e.g. in an
         * IDE console) or NEVER. Log files never get colors.
         */
        @WithDefault("auto")
        ColorMode mode();

        /** Color per level as a name (red, bright_blue, faint, ...) or a hex value (#ff8800), e.g. INFO=blue. */
        Map<String, String> levels();
    }

    interface Stacktrace {

        /** Whether the stack trace is written; the "error" summary is always written. */
        @WithDefault("true")
        boolean include();

        /** Frames per exception of the cause chain. */
        @WithDefault("50")
        int maxDepth();

        /** Maximum characters of the stack trace. */
        @WithDefault("16384")
        int maxLength();

        /** Whether the root cause is written first. */
        @WithDefault("false")
        boolean rootCauseFirst();
    }

    interface Http {

        /** Whether HTTP request context is added to the MDC of every log written during a request. */
        @WithDefault("true")
        boolean enabled();

        /** Header that carries the request id; generated when missing and echoed in the response. */
        @WithDefault("X-Request-Id")
        String requestIdHeader();

        /** Request headers copied to the logs (allow list). Sensitive names are masked. */
        Optional<List<String>> headers();

        RequestLog requestLog();
    }

    interface RequestLog {

        /** Whether one log with http.status and http.durationMs is written when each request ends. */
        @WithDefault("true")
        boolean enabled();

        /** Paths without that log; a trailing /** matches a whole subtree, * one path segment. */
        @WithDefault("/q/**")
        List<String> excludePaths();
    }
}
