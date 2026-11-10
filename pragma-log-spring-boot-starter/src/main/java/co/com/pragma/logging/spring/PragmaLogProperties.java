package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.format.ColorMode;
import co.com.pragma.logging.core.http.HttpBodyCapture;
import co.com.pragma.logging.core.http.HttpLogSettings;
import co.com.pragma.logging.logback.PragmaAsyncAppender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

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
     * Output format: JSON (default), XML, CSV, CUSTOM (pragma.log.custom-formatter) or PLAIN, readable text for
     * local development with the same masking. Use a Spring profile to pick PLAIN locally and JSON elsewhere.
     */
    private Format format = Format.JSON;

    /** Class name of a LogEventFormatter with a public no-arg constructor, for format: custom. */
    private String customFormatter;

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

    /**
     * Fields to rename, as current name: new name. A new name such as $.http.request.id nests the field inside
     * objects.
     */
    private Map<String, String> renameFields = new LinkedHashMap<>();

    /** Fields written first, in this order, by original or new name. The rest keep their natural order. */
    private List<String> fieldOrder = new ArrayList<>();

    /** LogEnricher classes with a public no-arg constructor; their fields are added to every log. Beans work too. */
    private List<String> enrichers = new ArrayList<>();

    /** Fields every log must have. A missing one is written with required-placeholder and reported as an alert. */
    private List<String> requiredFields = new ArrayList<>();

    /** Value of a missing required field. */
    private String requiredPlaceholder = "unknown";

    /**
     * Name of an object that groups MDC and key-value fields, e.g. extra. Fields named in field-order,
     * rename-fields or required-fields stay at the root. Not set: everything at the root.
     */
    private String extraField;

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
    private final Alerts alerts = new Alerts();
    private final Async async = new Async();
    private final Json json = new Json();
    private final Csv csv = new Csv();

    /**
     * Maximum characters of the message and of each text field; longer texts are cut with
     * "...[truncated N chars]". 0 for no limit. The stack trace has its own limit.
     */
    private int maxFieldLength;

    public enum Format {
        JSON,
        XML,
        CSV,
        CUSTOM,
        PLAIN
    }

    public static class Async {

        /**
         * Whether logging calls return right away and another thread writes the logs. Nothing is discarded: when the
         * queue is full the caller waits, unless never-block is true. Pending logs are written when the application
         * stops.
         */
        private boolean enabled;

        /** Logs waiting to be written. */
        private int queueSize = PragmaAsyncAppender.DEFAULT_QUEUE_SIZE;

        /** Whether logs are dropped instead of making the caller wait when the queue is full. */
        private boolean neverBlock;

        /** How long pending logs may take to be written when the application stops. */
        private Duration maxFlushTime = Duration.ofMillis(PragmaAsyncAppender.DEFAULT_MAX_FLUSH_TIME_MS);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = queueSize;
        }

        public boolean isNeverBlock() {
            return neverBlock;
        }

        public void setNeverBlock(boolean neverBlock) {
            this.neverBlock = neverBlock;
        }

        public Duration getMaxFlushTime() {
            return maxFlushTime;
        }

        public void setMaxFlushTime(Duration maxFlushTime) {
            this.maxFlushTime = maxFlushTime;
        }
    }

    public static class Json {

        /** JSON over several lines, for local reading; log shippers expect one event per line. */
        private boolean pretty;

        public boolean isPretty() {
            return pretty;
        }

        public void setPretty(boolean pretty) {
            this.pretty = pretty;
        }
    }

    public static class Csv {

        /** Columns, by output name; nested fields with dots. Default: timestamp, level, logger, thread, message. */
        private List<String> columns = new ArrayList<>();

        /** Column separator. */
        private String delimiter = ",";

        /** Whether a log file starts with the column names. */
        private boolean header;

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns;
        }

        public String getDelimiter() {
            return delimiter;
        }

        public void setDelimiter(String delimiter) {
            this.delimiter = delimiter;
        }

        public boolean isHeader() {
            return header;
        }

        public void setHeader(boolean header) {
            this.header = header;
        }
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

        /**
         * Patterns masked in messages, text fields and stack traces: CARD (Luhn-checked card numbers), EMAIL,
         * JWT, or a regex (with a group, only the group is masked). Each pattern scans every log: keep it short.
         */
        private List<String> patterns = new ArrayList<>();

        public List<String> getPatterns() {
            return patterns;
        }

        public void setPatterns(List<String> patterns) {
            this.patterns = patterns;
        }

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

    public static class Alerts {

        /** Size of an encoded log above which listeners are notified, e.g. 64KB. Not set: no size alerts. */
        private DataSize maxLogSize;

        /** Whether alerts are also written to standard error, at most once per minute per logger. */
        private boolean console = true;

        /**
         * Whether a log bigger than max-log-size is cut until it fits: its biggest fields are shortened and
         * "truncated": true is added. Off by default: the log is written whole.
         */
        private boolean truncate;

        /** LogAlertListener classes with a public no-arg constructor. LogAlertListener beans are added too. */
        private List<String> listeners = new ArrayList<>();

        public DataSize getMaxLogSize() {
            return maxLogSize;
        }

        public void setMaxLogSize(DataSize maxLogSize) {
            this.maxLogSize = maxLogSize;
        }

        public boolean isConsole() {
            return console;
        }

        public void setConsole(boolean console) {
            this.console = console;
        }

        public boolean isTruncate() {
            return truncate;
        }

        public void setTruncate(boolean truncate) {
            this.truncate = truncate;
        }

        public List<String> getListeners() {
            return listeners;
        }

        public void setListeners(List<String> listeners) {
            this.listeners = listeners;
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

        /**
         * Request headers copied to the logs (allow list); "*" copies all of them except exclude-headers. Sensitive
         * names are masked.
         */
        private List<String> headers = new ArrayList<>();

        /** Headers "*" never copies; headers listed explicitly in headers are always copied. */
        private List<String> excludeHeaders = new ArrayList<>(HttpLogSettings.DEFAULT_EXCLUDED_HEADERS);

        /** Query parameters copied to the logs as http.query.NAME; "*" copies all of them. Sensitive names are masked. */
        private List<String> queryParams = new ArrayList<>();

        private final RequestLog requestLog = new RequestLog();

        private final Body body = new Body();

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

        public List<String> getExcludeHeaders() {
            return excludeHeaders;
        }

        public void setExcludeHeaders(List<String> excludeHeaders) {
            this.excludeHeaders = excludeHeaders;
        }

        public List<String> getQueryParams() {
            return queryParams;
        }

        public void setQueryParams(List<String> queryParams) {
            this.queryParams = queryParams;
        }

        public RequestLog getRequestLog() {
            return requestLog;
        }

        public Body getBody() {
            return body;
        }

        HttpLogSettings settings() {
            return new HttpLogSettings(requestIdHeader, headers, excludeHeaders, queryParams);
        }
    }

    public static class Body {

        /**
         * Whether request and response bodies are added to the request log (http.request.body, http.response.body).
         * JSON and form bodies are masked by field name like any logged object; other text only by patterns.
         */
        private boolean enabled;

        /** Whether the request body is captured. */
        private boolean request = true;

        /** Whether the response body is captured. */
        private boolean response = true;

        /** Bytes kept from each body; longer bodies are cut. */
        private DataSize maxSize = DataSize.ofBytes(HttpBodyCapture.DEFAULT_MAX_SIZE);

        /** Captured content types; type/* and application/*+json are accepted. Binary bodies are never captured. */
        private List<String> contentTypes = new ArrayList<>(HttpBodyCapture.DEFAULT_CONTENT_TYPES);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequest() {
            return request;
        }

        public void setRequest(boolean request) {
            this.request = request;
        }

        public boolean isResponse() {
            return response;
        }

        public void setResponse(boolean response) {
            this.response = response;
        }

        public DataSize getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(DataSize maxSize) {
            this.maxSize = maxSize;
        }

        public List<String> getContentTypes() {
            return contentTypes;
        }

        public void setContentTypes(List<String> contentTypes) {
            this.contentTypes = contentTypes;
        }

        HttpBodyCapture capture() {
            return enabled
                    ? new HttpBodyCapture(request, response, Math.toIntExact(maxSize.toBytes()), contentTypes)
                    : HttpBodyCapture.none();
        }
    }

    public static class RequestLog {

        /** Whether one log with http.status and http.durationMs is written when each request ends. */
        private boolean enabled = true;

        /** Whether one more log is written when each request starts ("HTTP GET /orders started"). */
        private boolean entry;

        /** Paths without that log, as Ant-style patterns. */
        private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEntry() {
            return entry;
        }

        public void setEntry(boolean entry) {
            this.entry = entry;
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

    public String getCustomFormatter() {
        return customFormatter;
    }

    public void setCustomFormatter(String customFormatter) {
        this.customFormatter = customFormatter;
    }

    public Async getAsync() {
        return async;
    }

    public Json getJson() {
        return json;
    }

    public Csv getCsv() {
        return csv;
    }

    public List<String> getEnrichers() {
        return enrichers;
    }

    public void setEnrichers(List<String> enrichers) {
        this.enrichers = enrichers;
    }

    public List<String> getFieldOrder() {
        return fieldOrder;
    }

    public void setFieldOrder(List<String> fieldOrder) {
        this.fieldOrder = fieldOrder;
    }

    public List<String> getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(List<String> requiredFields) {
        this.requiredFields = requiredFields;
    }

    public String getRequiredPlaceholder() {
        return requiredPlaceholder;
    }

    public void setRequiredPlaceholder(String requiredPlaceholder) {
        this.requiredPlaceholder = requiredPlaceholder;
    }

    public String getExtraField() {
        return extraField;
    }

    public void setExtraField(String extraField) {
        this.extraField = extraField;
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

    public Alerts getAlerts() {
        return alerts;
    }

    public int getMaxFieldLength() {
        return maxFieldLength;
    }

    public void setMaxFieldLength(int maxFieldLength) {
        this.maxFieldLength = maxFieldLength;
    }
}
