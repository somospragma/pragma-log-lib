package co.com.pragma.logging.core.http;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the request context takes from each request (CTX-02, ENR-02, ENR-03), the same in every framework.
 *
 * @param requestIdHeader header that carries the request id
 * @param headers         headers copied to the context; {@code *} copies all of them except {@code excludeHeaders}
 * @param excludeHeaders  headers {@code *} never copies; headers listed explicitly are always copied
 * @param queryParams     query parameters copied to the context; {@code *} copies all of them
 */
public record HttpLogSettings(String requestIdHeader, List<String> headers, List<String> excludeHeaders,
        List<String> queryParams) {

    /** Copies every header or query parameter. */
    public static final String ALL = "*";

    /** Headers {@code *} leaves out by default: credentials and session data. */
    public static final List<String> DEFAULT_EXCLUDED_HEADERS = List.of("Authorization", "Proxy-Authorization",
            "Cookie", "Set-Cookie");

    public HttpLogSettings {
        headers = List.copyOf(headers);
        excludeHeaders = List.copyOf(excludeHeaders);
        queryParams = List.copyOf(queryParams);
    }

    /** Request id header and explicit headers only, like before {@code *} and query parameters existed. */
    public static HttpLogSettings of(String requestIdHeader, List<String> headers) {
        return new HttpLogSettings(requestIdHeader, headers, DEFAULT_EXCLUDED_HEADERS, List.of());
    }

    boolean allHeaders() {
        return headers.contains(ALL);
    }

    boolean allQueryParams() {
        return queryParams.contains(ALL);
    }

    /** Lower-case names of the excluded headers. */
    Set<String> excludedHeaderNames() {
        Set<String> names = new TreeSet<>();
        excludeHeaders.forEach(header -> names.add(header.toLowerCase(Locale.ROOT)));
        return names;
    }
}
