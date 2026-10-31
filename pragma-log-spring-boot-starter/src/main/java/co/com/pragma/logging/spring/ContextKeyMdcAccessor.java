package co.com.pragma.logging.spring;

import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

/**
 * Copies one application-defined Reactor Context key to the MDC entry of the same name on every thread that
 * runs a reactive operator (CTX-08). Values are written with {@code String.valueOf}; only this key is touched.
 */
final class ContextKeyMdcAccessor implements ThreadLocalAccessor<Object> {

    private final String key;

    ContextKeyMdcAccessor(String key) {
        this.key = key;
    }

    @Override
    public Object key() {
        return key;
    }

    @Override
    public Object getValue() {
        return MDC.get(key);
    }

    @Override
    public void setValue(Object value) {
        MDC.put(key, String.valueOf(value));
    }

    @Override
    public void setValue() {
        MDC.remove(key);
    }

    @Override
    public void restore(Object previousValue) {
        setValue(previousValue);
    }

    @Override
    public void restore() {
        MDC.remove(key);
    }
}
