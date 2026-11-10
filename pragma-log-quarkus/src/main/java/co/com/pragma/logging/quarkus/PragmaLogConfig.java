package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.format.ColorMode;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.configuration.MemorySize;
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
     * Output format: JSON (default), XML, CSV, CUSTOM (pragma.log.custom-formatter) or PLAIN, readable text for
     * local development with the same masking. Use a profile to pick PLAIN locally, e.g.
     * %dev.pragma.log.format=plain.
     */
    @WithDefault("json")
    Format format();

    /** Class name of a LogEventFormatter with a public no-arg constructor, for format=custom. */
    Optional<String> customFormatter();

    Json json();

    Csv csv();

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

    /** Fields to rename, as current name=new name. A new name such as $.http.request.id nests the field. */
    Map<String, String> renameFields();

    /** Fields written first, in this order, by original or new name. The rest keep their natural order. */
    Optional<List<String>> fieldOrder();

    /** Fields every log must have. A missing one is written with required-placeholder and reported as an alert. */
    Optional<List<String>> requiredFields();

    /** Value of a missing required field. */
    @WithDefault("unknown")
    String requiredPlaceholder();

    /**
     * Name of an object that groups MDC and key-value fields, e.g. extra. Fields named in field-order,
     * rename-fields or required-fields stay at the root. Not set: everything at the root.
     */
    Optional<String> extraField();

    /** LogEnricher classes with a public no-arg constructor; their fields are added to every log. */
    Optional<List<String>> enrichers();

    Masking masking();

    Stacktrace stacktrace();

    Http http();

    Timestamp timestamp();

    Color color();

    Alerts alerts();

    /**
     * Maximum characters of the message and of each text field; longer texts are cut with
     * "...[truncated N chars]". 0 for no limit. The stack trace has its own limit.
     */
    @WithDefault("0")
    int maxFieldLength();

    enum Format {
        JSON,
        XML,
        CSV,
        CUSTOM,
        PLAIN
    }

    interface Json {

        /** JSON over several lines, for local reading; log shippers expect one event per line. */
        @WithDefault("false")
        boolean pretty();
    }

    interface Csv {

        /** Columns, by output name; nested fields with dots. Default: timestamp, level, logger, thread, message. */
        Optional<List<String>> columns();

        /** Column separator. */
        @WithDefault(",")
        String delimiter();

        /** Whether a log file starts with the column names. */
        @WithDefault("false")
        boolean header();
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

        /**
         * Patterns masked in messages, text fields and stack traces: CARD (Luhn-checked card numbers), EMAIL,
         * JWT, or a regex (with a group, only the group is masked). Each pattern scans every log: keep it short.
         */
        Optional<List<String>> patterns();
    }

    interface Alerts {

        /** Size of an encoded log above which listeners are notified, e.g. 64K. Not set: no size alerts. */
        Optional<MemorySize> maxLogSize();

        /** Whether alerts are also written to standard error, at most once per minute per logger. */
        @WithDefault("true")
        boolean console();

        /** LogAlertListener classes with a public no-arg constructor. */
        Optional<List<String>> listeners();
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

        /**
         * Request headers copied to the logs (allow list); "*" copies all of them except exclude-headers. Sensitive
         * names are masked.
         */
        Optional<List<String>> headers();

        /** Headers "*" never copies; headers listed explicitly in headers are always copied. */
        @WithDefault("Authorization,Proxy-Authorization,Cookie,Set-Cookie")
        List<String> excludeHeaders();

        /** Query parameters copied to the logs as http.query.NAME; "*" copies all of them. Sensitive names are masked. */
        Optional<List<String>> queryParams();

        RequestLog requestLog();

        Body body();
    }

    interface Body {

        /**
         * Whether request and response bodies are added to the request log (http.request.body,
         * http.response.body), for Quarkus REST endpoints. JSON and form request bodies are masked by field name;
         * response entities are masked like any logged object.
         */
        @WithDefault("false")
        boolean enabled();

        /** Whether the request body is captured. */
        @WithDefault("true")
        boolean request();

        /** Whether the response body is captured. */
        @WithDefault("true")
        boolean response();

        /** Bytes kept from each request body; longer bodies are cut. */
        @WithDefault("4K")
        MemorySize maxSize();

        /** Captured request content types; type/* and application/*+json are accepted. */
        @WithDefault("application/json,application/*+json,application/x-www-form-urlencoded,application/xml,text/*")
        List<String> contentTypes();
    }

    interface RequestLog {

        /** Whether one log with http.status and http.durationMs is written when each request ends. */
        @WithDefault("true")
        boolean enabled();

        /** Whether one more log is written when each request starts ("HTTP GET /orders started"). */
        @WithDefault("false")
        boolean entry();

        /** Paths without that log; a trailing /** matches a whole subtree, * one path segment. */
        @WithDefault("/q/**")
        List<String> excludePaths();
    }
}
