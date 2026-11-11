package co.com.pragma.logging.spring;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;

/**
 * The object a controller returned as response body, kept for the completion log (ENR-04). Logging the object
 * instead of the bytes written lets the encoder mask it like any logged object, so {@code @MaskedLogField} applies,
 * as in Quarkus. Text, binary and streamed bodies are not kept: their bytes are logged as before.
 *
 * <p>Used by Spring MVC apps without Reactor: it must not reference reactive types.
 */
final class ResponseBodyObject {

    /** Request or exchange attribute that holds the instance of the current request. */
    static final String ATTRIBUTE = ResponseBodyObject.class.getName();

    /** Elements kept from a {@code Flux} body; more are summarized in a final marker. */
    static final int MAX_ELEMENTS = 100;

    private volatile Object value;
    private List<Object> elements;
    private int dropped;

    /** Whether a body object is logged as an object; otherwise its bytes are. */
    static boolean loggable(Object body) {
        return body != null && !(body instanceof CharSequence) && !(body instanceof byte[])
                && !(body instanceof Resource) && !(body instanceof InputStream) && !(body instanceof DataBuffer)
                && !body.getClass().getName().startsWith("reactor.core.publisher.");
    }

    void set(Object body) {
        value = loggable(body) ? body : null;
    }

    /** Adds one element of a multi-value body. */
    synchronized void add(Object element) {
        if (!loggable(element)) {
            return;
        }
        if (elements == null) {
            elements = new ArrayList<>();
            value = Collections.unmodifiableList(elements);
        }
        if (elements.size() < MAX_ELEMENTS) {
            elements.add(element);
        } else {
            dropped++;
        }
    }

    /** The body to log, or {@code null} when none was kept. */
    synchronized Object value() {
        if (elements != null && dropped > 0) {
            List<Object> summary = new ArrayList<>(elements);
            summary.add("...[" + dropped + " more elements]");
            return summary;
        }
        return elements != null ? List.copyOf(elements) : value;
    }
}
