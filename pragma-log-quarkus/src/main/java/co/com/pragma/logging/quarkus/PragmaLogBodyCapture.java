package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.http.HttpLogContext;
import io.vertx.ext.web.RoutingContext;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hands the bodies captured by the Quarkus REST providers to the completion log of {@link PragmaLogHttpFilter}
 * (ENR-04), through the {@link RoutingContext} of the request: the same object on the event loop and on worker
 * threads.
 */
final class PragmaLogBodyCapture {

    private static final String REQUEST_BODY = PragmaLogBodyCapture.class.getName() + ".request";
    private static final String RESPONSE_BODY = PragmaLogBodyCapture.class.getName() + ".response";

    private PragmaLogBodyCapture() {
    }

    static void request(RoutingContext routing, Object body) {
        if (routing != null && body != null) {
            routing.put(REQUEST_BODY, body);
        }
    }

    static void response(RoutingContext routing, Object body) {
        if (routing != null && body != null) {
            routing.put(RESPONSE_BODY, body);
        }
    }

    /** The captured bodies by key-value name; empty when nothing was captured. */
    static Map<String, Object> captured(RoutingContext routing) {
        Map<String, Object> bodies = new LinkedHashMap<>();
        Object request = routing.get(REQUEST_BODY);
        if (request != null) {
            bodies.put(HttpLogContext.REQUEST_BODY, request);
        }
        Object response = routing.get(RESPONSE_BODY);
        if (response != null) {
            bodies.put(HttpLogContext.RESPONSE_BODY, response);
        }
        return bodies;
    }
}
