package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.format.ColorMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * pragma-log configuration from {@code application.yml} (ADR-09).
 *
 * <pre>{@code
 * pragma:
 *   log:
 *     fields:
 *       env: prod
 *     rename-fields:
 *       level: severity
 *     masking:
 *       fields: [password, documentId]
 *     http:
 *       headers: [X-Client-Id]
 *     context-keys: [userId, tenantId]
 * }</pre>
 */
@ConfigurationProperties(prefix = PragmaLogProperties.PREFIX)
public class PragmaLogProperties {

    public static final String PREFIX = "pragma.log";

    /** Whether pragma-log is active. When false, Spring Boot logging is left untouched. */
    private boolean enabled = true;

    /**
     * Output format: JSON (default) or PLAIN, readable text for local development with the same masking. Use a
     * Spring profile to pick PLAIN locally and JSON elsewhere.
     */
    private Format format = Format.JSON;

    /** Value of the "app" field. Defaults to spring.application.name. */
    private String appName;

    /** Value of the "version" field. Defaults to spring.application.version; omitted when neither is set. */
    private String version;

    /** Whether the "host" field is added: HOSTNAME (the pod name in Kubernetes) or the local host name. */
    private boolean includeHost = true;

    /** Whether the "pid" field is added. */
    private boolean includePid;

    /** Fixed fields added to every event, e.g. env: prod. */
    private Map<String, String> fields = new LinkedHashMap<>();

    /** Fields to remove: standard, fixed, MDC or key-value fields. */
    private List<String> hideFields = new ArrayList<>();

    /** Fields to rename, as current name: new name. */
    private Map<String, String> renameFields = new LinkedHashMap<>();

    /**
     * Reactor Context keys copied to the logs, e.g. userId for contextWrite(Context.of("userId", id)). In
     * Spring MVC, put these values in the MDC instead.
     */
    private List<String> contextKeys = new ArrayList<>();

    private final Masking masking = new Masking();
    private final Stacktrace stacktrace = new Stacktrace();
    private final Http http = new Http();
    private final Timestamp timestamp = new Timestamp();
    private final Color color = new Color();

    public enum Format {
        JSON,
        PLAIN
    }

    public static class Masking {

        /** Whether sensitive data is masked. */
        private boolean enabled = true;

        /** Mask character. */
        private String character = "*";

        /** Type applied to fields masked by name: FULL, PREFIX, SUFFIX, MIDDLE or HIDDEN. */
        private String defaultType = "FULL";

        /** Length used by PREFIX, SUFFIX and MIDDLE when the annotation does not set one. */
        private int defaultLength = 4;

        /** Field names masked without annotation. Replaces the secure default list when set. */
        private List<String> fields;

        /** Maximum nesting rendered for logged objects. */
        private int maxDepth = 10;

        /** Custom strategies, as name: fully qualified class name. */
        private Map<String, String> strategies = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCharacter() {
            return character;
        }

        public void setCharacter(String character) {
            this.character = character;
        }

        public String getDefaultType() {
            return defaultType;
        }

        public void setDefaultType(String defaultType) {
            this.defaultType = defaultType;
        }

        public int getDefaultLength() {
            return defaultLength;
        }

        public void setDefaultLength(int defaultLength) {
            this.defaultLength = defaultLength;
        }

        public List<String> getFields() {
            return fields;
        }

        public void setFields(List<String> fields) {
            this.fields = fields;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public Map<String, String> getStrategies() {
            return strategies;
        }

        public void setStrategies(Map<String, String> strategies) {
            this.strategies = strategies;
        }
    }

    public static class Timestamp {

        /**
         * DateTimeFormatter pattern, e.g. yyyy-MM-dd HH:mm:ss.SSS, or a constant in brackets, e.g. [ISO_INSTANT].
         * Defaults to [ISO_OFFSET_DATE_TIME] in JSON and yyyy-MM-dd HH:mm:ss.SSS in PLAIN.
         */
        private String pattern;

        /** Time zone, e.g. UTC or America/Bogota. Defaults to the system zone. */
        private String zone;

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }
    }

    public static class Color {

        /**
         * Colors of the PLAIN console output: AUTO (only on a terminal, never with NO_COLOR), ALWAYS (e.g. in an
         * IDE console) or NEVER. Log files never get colors.
         */
        private ColorMode mode = ColorMode.AUTO;

        /** Color per level as a name (red, bright_blue, faint, ...) or a hex value (#ff8800), e.g. INFO: blue. */
        private Map<String, String> levels = new LinkedHashMap<>();

        public ColorMode getMode() {
            return mode;
        }

        public void setMode(ColorMode mode) {
            this.mode = mode;
        }

        public Map<String, String> getLevels() {
            return levels;
        }

        public void setLevels(Map<String, String> levels) {
            this.levels = levels;
        }
    }

    public static class Stacktrace {

        /** Whether the stack trace is written. The "error" summary is always written. */
        private boolean include = true;

        /** Maximum frames per exception in the chain. */
        private int maxDepth = 50;

        /** Maximum characters of the whole stack trace. */
        private int maxLength = 16_384;

        /** Whether the root cause is written first. */
        private boolean rootCauseFirst;

        public boolean isInclude() {
            return include;
        }

        public void setInclude(boolean include) {
            this.include = include;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(int maxLength) {
            this.maxLength = maxLength;
        }

        public boolean isRootCauseFirst() {
            return rootCauseFirst;
        }

        public void setRootCauseFirst(boolean rootCauseFirst) {
            this.rootCauseFirst = rootCauseFirst;
        }
    }

    public static class Http {

        /** Whether HTTP request context is added to the MDC of every log written during a request. */
        private boolean enabled = true;

        /** Header that carries the request id; generated when missing and echoed in the response. */
        private String requestIdHeader = "X-Request-Id";

        /** Request headers copied to the logs (allow list). Sensitive names are masked. */
        private List<String> headers = new ArrayList<>();

        private final RequestLog requestLog = new RequestLog();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRequestIdHeader() {
            return requestIdHeader;
        }

        public void setRequestIdHeader(String requestIdHeader) {
            this.requestIdHeader = requestIdHeader;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public RequestLog getRequestLog() {
            return requestLog;
        }
    }

    public static class RequestLog {

        /** Whether one log with http.status and http.durationMs is written when each request ends. */
        private boolean enabled = true;

        /** Paths without that log, as Ant-style patterns. */
        private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Format getFormat() {
        return format;
    }

    public void setFormat(Format format) {
        this.format = format;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isIncludeHost() {
        return includeHost;
    }

    public void setIncludeHost(boolean includeHost) {
        this.includeHost = includeHost;
    }

    public boolean isIncludePid() {
        return includePid;
    }

    public void setIncludePid(boolean includePid) {
        this.includePid = includePid;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }

    public List<String> getHideFields() {
        return hideFields;
    }

    public void setHideFields(List<String> hideFields) {
        this.hideFields = hideFields;
    }

    public Map<String, String> getRenameFields() {
        return renameFields;
    }

    public void setRenameFields(Map<String, String> renameFields) {
        this.renameFields = renameFields;
    }

    public List<String> getContextKeys() {
        return contextKeys;
    }

    public void setContextKeys(List<String> contextKeys) {
        this.contextKeys = contextKeys;
    }

    public Masking getMasking() {
        return masking;
    }

    public Stacktrace getStacktrace() {
        return stacktrace;
    }

    public Http getHttp() {
        return http;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public Color getColor() {
        return color;
    }
}
